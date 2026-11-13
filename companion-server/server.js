require("dotenv").config();
const fs = require("node:fs");
const fsp = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const { pipeline } = require("node:stream/promises");
const { Readable } = require("node:stream");
const http = require("node:http");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");
const crypto = require("node:crypto");
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const { WebSocketServer } = require("ws");

const execFileAsync = promisify(execFile);

const PORT = Number(process.env.PORT || 8443);
const BEARER_TOKEN = process.env.GATEWAY_BEARER_TOKEN || "";
const HOST_LABEL = process.env.HOST_LABEL || "linux-kvm-node-01";
const ADB_BIN = process.env.ADB_BIN || "adb";
const EMULATOR_BIN = process.env.EMULATOR_BIN || "emulator";

// Map VM slot IDs (e.g., VM-001) to real ADB serials (e.g., emulator-5554) and AVD names
// Load from VM_REGISTRY_JSON env or configure dynamically via /api/vms/enroll
const vmRegistry = new Map();
if (process.env.VM_REGISTRY_JSON) {
  try {
    const parsed = JSON.parse(process.env.VM_REGISTRY_JSON);
    Object.entries(parsed).forEach(([vmId, cfg]) => vmRegistry.set(vmId, cfg));
  } catch (err) {
    console.error("Invalid VM_REGISTRY_JSON:", err.message);
  }
}

const app = express();
app.use(
  helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        scriptSrc: ["'self'"],
        imgSrc: ["'self'", "data:", "blob:"],
        styleSrc: ["'self'", "'unsafe-inline'"],
      },
    },
  })
);
app.use(
  cors({
    origin: process.env.ALLOWED_ORIGINS ? process.env.ALLOWED_ORIGINS.split(",") : false,
  })
);
app.use(express.json({ limit: "1mb" }));

function authenticateGateway(req, res, next) {
  if (!BEARER_TOKEN) {
    return res.status(503).json({
      error: "GATEWAY_BEARER_TOKEN is not configured on the host server.",
    });
  }
  const authHeader = req.headers.authorization || "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  const expectedBuf = Buffer.from(BEARER_TOKEN);
  const actualBuf = Buffer.from(token);
  if (
    expectedBuf.length !== actualBuf.length ||
    !crypto.timingSafeEqual(expectedBuf, actualBuf)
  ) {
    return res.status(401).json({ error: "Unauthorized gateway token." });
  }
  return next();
}

async function listActiveAdbSerials() {
  try {
    const { stdout } = await execFileAsync(ADB_BIN, ["devices"], { timeout: 4000 });
    return stdout
      .split("\n")
      .slice(1)
      .map((line) => line.trim().split(/\s+/))
      .filter((parts) => parts.length >= 2 && parts[1] === "device")
      .map((parts) => parts[0]);
  } catch {
    return [];
  }
}

app.get("/api/health", authenticateGateway, async (_req, res) => {
  const serials = await listActiveAdbSerials();
  res.json({
    ok: true,
    activeEmulators: serials.length,
    hostLabel: HOST_LABEL,
    version: "1.0.0",
  });
});

app.post("/api/vms/:vmId/enroll", authenticateGateway, (req, res) => {
  const { vmId } = req.params;
  const { adbSerial, avdName, ipAddress } = req.body || {};
  if (!/^VM-(0\d\d|100)$/.test(vmId)) {
    return res.status(400).json({ error: "vmId must be VM-001..VM-100" });
  }
  vmRegistry.set(vmId, {
    adbSerial: String(adbSerial || ""),
    avdName: String(avdName || ""),
    ipAddress: String(ipAddress || ""),
  });
  return res.json({ accepted: true, message: `Enrolled ${vmId} on ${HOST_LABEL}` });
});

async function detectAdbIpAddress(adbSerial) {
  if (!adbSerial) return "";
  try {
    const { stdout } = await execFileAsync(
      ADB_BIN,
      ["-s", adbSerial, "shell", "ip", "-f", "inet", "addr", "show"],
      { timeout: 3000 }
    );
    const match = stdout.match(/inet\s+(?!127\.0\.0\.1)(\d+\.\d+\.\d+\.\d+)/);
    return match ? match[1] : "";
  } catch {
    return "";
  }
}

async function detectSignedInGoogleEmail(adbSerial) {
  if (!adbSerial) return "";
  try {
    const { stdout } = await execFileAsync(
      ADB_BIN,
      ["-s", adbSerial, "shell", "dumpsys", "account"],
      { timeout: 3500 }
    );
    const match = stdout.match(/Account\s*\{name=([^,\s}]+),\s*type=com\.google\}/i);
    return match ? match[1].trim() : "";
  } catch {
    return "";
  }
}

app.get("/api/vms/status", authenticateGateway, async (_req, res) => {
  const activeSerials = new Set(await listActiveAdbSerials());
  const entries = Array.from(vmRegistry.entries());
  const vms = await mapWithConcurrency(entries, 12, async ([vmId, entry]) => {
    const isOnline = Boolean(entry.adbSerial && activeSerials.has(entry.adbSerial));
    const [detectedIp, signedInGmail] = isOnline
      ? await Promise.all([
          detectAdbIpAddress(entry.adbSerial),
          detectSignedInGoogleEmail(entry.adbSerial),
        ])
      : ["", ""];
    return {
      vmId,
      status: isOnline ? "ONLINE" : "OFFLINE",
      connectivity: isOnline ? "WIFI" : "DISCONNECTED",
      ipAddress: detectedIp || entry.ipAddress || "",
      androidVersion: "",
      signedInGmail,
    };
  });
  return res.json({ vms });
});

app.post("/api/vms/:vmId/lifecycle", authenticateGateway, async (req, res) => {
  const { vmId } = req.params;
  const { action } = req.body || {};
  const entry = vmRegistry.get(vmId);
  if (!entry) {
    return res.json({
      accepted: false,
      message: `${vmId} is not enrolled on ${HOST_LABEL}`,
      results: [{ vmId, status: "HOST_OFFLINE", message: "Not enrolled on this host" }],
    });
  }

  try {
    if (action === "STOP" && entry.adbSerial) {
      await execFileAsync(ADB_BIN, ["-s", entry.adbSerial, "emu", "kill"], { timeout: 6000 });
      return res.json({ accepted: true, message: `Stopped ${vmId}` });
    }
    if (action === "START" && entry.avdName) {
      execFile(EMULATOR_BIN, ["-avd", entry.avdName, "-no-window", "-no-audio", "-gpu", "swiftshader_indirect"]);
      return res.json({ accepted: true, message: `Booting ${vmId} (${entry.avdName})` });
    }
    if (action === "RESTART" && entry.adbSerial) {
      await execFileAsync(ADB_BIN, ["-s", entry.adbSerial, "reboot"], { timeout: 6000 });
      return res.json({ accepted: true, message: `Rebooting ${vmId}` });
    }
    return res.json({ accepted: false, message: `Missing AVD/serial configuration for ${action}` });
  } catch (err) {
    return res.status(500).json({ accepted: false, message: err.message });
  }
});

async function mapWithConcurrency(items, limit, fn) {
  const list = Array.isArray(items) ? items : [];
  const results = new Array(list.length);
  let cursor = 0;
  async function worker() {
    while (cursor < list.length) {
      const idx = cursor++;
      results[idx] = await fn(list[idx], idx);
    }
  }
  const workers = Array.from({ length: Math.min(Math.max(1, limit), list.length || 1) }, () => worker());
  await Promise.all(workers);
  return results;
}

function extractPlayStorePackage(rawInput) {
  const trimmed = String(rawInput || "").trim();
  if (!trimmed) return "";
  if (/^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/.test(trimmed)) {
    return trimmed;
  }
  try {
    const normalized = trimmed.includes("://")
      ? trimmed
      : trimmed.includes("play.google.com")
        ? `https://${trimmed}`
        : "";
    if (!normalized) return "";
    const parsed = new URL(normalized);
    const pkg = (parsed.searchParams.get("id") || "").trim();
    return /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/.test(pkg) ? pkg : "";
  } catch {
    return "";
  }
}

async function openUrlOnVm(entry, vmId, targetUrl, safePkg) {
  if (!entry || !entry.adbSerial) {
    return { vmId, status: "HOST_OFFLINE", message: "No active ADB serial mapped" };
  }
  try {
    let launchUrl = targetUrl;
    if (safePkg) {
      const { stdout: pmOut } = await execFileAsync(
        ADB_BIN,
        ["-s", entry.adbSerial, "shell", "pm", "path", safePkg],
        { timeout: 4000 }
      );
      if (!pmOut || !pmOut.includes("package:")) {
        const hint =
          safePkg === "com.android.vending"
            ? `Google Play Store (com.android.vending) not installed on ${vmId}; use a Google Play system image or APK install.`
            : `App ${safePkg} is not installed on ${vmId}`;
        return { vmId, status: "FAILED", message: hint };
      }
      if (safePkg === "com.android.vending") {
        const playPkg = extractPlayStorePackage(targetUrl);
        if (playPkg) {
          launchUrl = `market://details?id=${playPkg}`;
        }
      }
    }
    const args = ["-s", entry.adbSerial, "shell", "am", "start", "-f", "0x10000000", "-a", "android.intent.action.VIEW", "-d", launchUrl];
    if (safePkg) {
      args.push("-p", safePkg);
    }
    const { stdout, stderr } = await execFileAsync(ADB_BIN, args, { timeout: 6000 });
    const combined = `${stdout || ""}\n${stderr || ""}`;
    if (/Error:|unable to resolve Intent/i.test(combined)) {
      return {
        vmId,
        status: "FAILED",
        message: combined.trim().slice(0, 180),
      };
    }
    return {
      vmId,
      status: "OPENED",
      message: safePkg ? `Opened in ${safePkg} on ${entry.adbSerial}` : `Opened on ${entry.adbSerial}`,
    };
  } catch (err) {
    return { vmId, status: "FAILED", message: err.message };
  }
}

app.post("/api/urls/dispatch", authenticateGateway, async (req, res) => {
  const { vmIds = [], url = "", targetPackage = "" } = req.body || {};
  let parsedUrl;
  try {
    parsedUrl = new URL(url);
    if (!["http:", "https:", "market:"].includes(parsedUrl.protocol)) {
      throw new Error("Only http, https, or market URLs allowed");
    }
  } catch (err) {
    return res.status(400).json({ accepted: false, message: err.message, results: [] });
  }

  const safePkg = targetPackage ? String(targetPackage).replace(/[^a-zA-Z0-9_.]/g, "") : "";
  const results = await mapWithConcurrency(vmIds, 12, (vmId) => {
    const entry = vmRegistry.get(vmId);
    return openUrlOnVm(entry, vmId, parsedUrl.toString(), safePkg);
  });
  return res.json({ accepted: true, message: "Processed URL dispatch", results });
});

app.post("/api/apps/playstore", authenticateGateway, async (req, res) => {
  const { vmIds = [], packageOrUrl = "" } = req.body || {};
  const packageId = extractPlayStorePackage(packageOrUrl);
  if (!packageId) {
    return res.status(400).json({
      accepted: false,
      message: "Provide a valid Google Play URL or package ID (e.g., com.whatsapp).",
      results: [],
    });
  }
  const marketUrl = `market://details?id=${packageId}`;
  const results = await mapWithConcurrency(vmIds, 12, (vmId) => {
    const entry = vmRegistry.get(vmId);
    return openUrlOnVm(entry, vmId, marketUrl, "com.android.vending");
  });
  return res.json({ accepted: true, packageId, message: `Dispatched Play Store page for ${packageId}`, results });
});

async function launchInstalledPackage(adbSerial, safePkg) {
  let launched = false;
  try {
    const { stdout: resolvedOut } = await execFileAsync(
      ADB_BIN,
      ["-s", adbSerial, "shell", "cmd", "package", "resolve-activity", "--brief", safePkg],
      { timeout: 4000 }
    );
    const component = resolvedOut
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line.includes("/"))
      .pop();
    if (component) {
      const { stdout: startOut, stderr: startErr } = await execFileAsync(
        ADB_BIN,
        ["-s", adbSerial, "shell", "am", "start", "-n", component],
        { timeout: 6000 }
      );
      if (!/Error:/i.test(`${startOut || ""}\n${startErr || ""}`)) {
        launched = true;
      }
    }
  } catch {
    // fallback below via monkey launcher
  }

  if (!launched) {
    await execFileAsync(
      ADB_BIN,
      ["-s", adbSerial, "shell", "monkey", "-p", safePkg, "-c", "android.intent.category.LAUNCHER", "1"],
      { timeout: 6000 }
    );
  }
}

app.post("/api/apps/launch", authenticateGateway, async (req, res) => {
  const { vmIds = [], packageName = "", appName = "" } = req.body || {};
  const safePkg = String(packageName || "").replace(/[^a-zA-Z0-9_.]/g, "");
  if (!safePkg) {
    return res.status(400).json({
      accepted: false,
      message: "Valid packageName is required to launch an app.",
      results: [],
    });
  }
  const displayName = String(appName || safePkg).trim() || safePkg;
  const results = await mapWithConcurrency(vmIds, 12, async (vmId) => {
    const entry = vmRegistry.get(vmId);
    if (!entry || !entry.adbSerial) {
      return { vmId, status: "HOST_OFFLINE", message: "No active ADB serial mapped" };
    }
    try {
      const { stdout: pmOut } = await execFileAsync(
        ADB_BIN,
        ["-s", entry.adbSerial, "shell", "pm", "path", safePkg],
        { timeout: 4000 }
      );
      if (!pmOut || !pmOut.includes("package:")) {
        return {
          vmId,
          status: "FAILED",
          message: `${displayName} (${safePkg}) is not installed on ${vmId}. Tap Install & Open to push it directly.`,
        };
      }
      await launchInstalledPackage(entry.adbSerial, safePkg);
      return {
        vmId,
        status: "OPENED",
        message: `Opened ${displayName} on ${entry.adbSerial}`,
      };
    } catch (err) {
      return { vmId, status: "FAILED", message: err.message };
    }
  });
  return res.json({ accepted: true, message: `Processed app launch for ${displayName}`, results });
});

app.post("/api/apps/upload-apk", authenticateGateway, async (req, res) => {
  const uploadId = String(req.query.uploadId || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const rawName = String(req.query.fileName || "base.apk").replace(/[^a-zA-Z0-9._-]/g, "");
  const safeFile = rawName.endsWith(".apk") ? rawName : `${rawName}.apk`;
  if (!uploadId || uploadId.length < 6) {
    return res.status(400).json({ accepted: false, message: "Invalid uploadId" });
  }
  const uploadDir = path.join(os.tmpdir(), `vmmaster-upload-${uploadId}`);
  try {
    await fsp.mkdir(uploadDir, { recursive: true });
    const destPath = path.join(uploadDir, safeFile);
    await pipeline(req, fs.createWriteStream(destPath));
    return res.json({ accepted: true, fileName: safeFile });
  } catch (err) {
    return res.status(500).json({ accepted: false, message: err.message });
  }
});

app.post("/api/apps/install-uploaded", authenticateGateway, async (req, res) => {
  const { uploadId = "", vmIds = [], packageName = "", appName = "", openAfterInstall = true } = req.body || {};
  const cleanUploadId = String(uploadId || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const safePkg = String(packageName || "").replace(/[^a-zA-Z0-9_.]/g, "");
  const displayName = String(appName || safePkg).trim() || safePkg;
  if (!cleanUploadId || !safePkg) {
    return res.status(400).json({ accepted: false, message: "uploadId and packageName are required", results: [] });
  }

  const uploadDir = path.join(os.tmpdir(), `vmmaster-upload-${cleanUploadId}`);
  const results = [];
  try {
    const entries = await fsp.readdir(uploadDir).catch(() => []);
    const apkPaths = entries.filter((f) => f.endsWith(".apk")).sort().map((f) => path.join(uploadDir, f));
    if (apkPaths.length === 0) {
      return res.status(400).json({ accepted: false, message: "No uploaded APK files found", results: [] });
    }

    const batchResults = await mapWithConcurrency(vmIds, 8, async (vmId) => {
      const entry = vmRegistry.get(vmId);
      if (!entry || !entry.adbSerial) {
        return { vmId, status: "HOST_OFFLINE", message: "No active ADB serial mapped" };
      }
      try {
        if (apkPaths.length === 1) {
          await execFileAsync(ADB_BIN, ["-s", entry.adbSerial, "install", "-r", apkPaths[0]], { timeout: 60000 });
        } else {
          await execFileAsync(
            ADB_BIN,
            ["-s", entry.adbSerial, "install-multiple", "-r", ...apkPaths],
            { timeout: 90000 }
          );
        }
        if (openAfterInstall) {
          await launchInstalledPackage(entry.adbSerial, safePkg);
          return { vmId, status: "OPENED", message: `Installed & opened ${displayName} on ${entry.adbSerial}` };
        }
        return { vmId, status: "COMPLETED", message: `Installed ${displayName} on ${entry.adbSerial}` };
      } catch (err) {
        return { vmId, status: "FAILED", message: err.message };
      }
    });
    results.push(...batchResults);
  } finally {
    await fsp.rm(uploadDir, { recursive: true, force: true }).catch(() => {});
  }
  return res.json({ accepted: true, message: `Processed uploaded app ${displayName}`, results });
});

async function downloadApkToTemp(apkSourceUrl) {
  const parsed = new URL(apkSourceUrl);
  if (!["http:", "https:"].includes(parsed.protocol)) {
    throw new Error("APK source URL must use http or https");
  }
  const response = await fetch(parsed.toString(), { redirect: "follow" });
  if (!response.ok || !response.body) {
    throw new Error(`Failed to download APK (HTTP ${response.status})`);
  }
  const tmpFile = path.join(os.tmpdir(), `vmmaster-${crypto.randomUUID()}.apk`);
  await pipeline(Readable.fromWeb(response.body), fs.createWriteStream(tmpFile));
  return tmpFile;
}

app.get("/api/vms/:vmId/screen.png", authenticateGateway, async (req, res) => {
  const { vmId } = req.params;
  const entry = vmRegistry.get(vmId);
  if (!entry || !entry.adbSerial) {
    return res.status(404).json({ error: `${vmId} is not mapped to an active ADB serial.` });
  }
  try {
    const { stdout } = await execFileAsync(
      ADB_BIN,
      ["-s", entry.adbSerial, "exec-out", "screencap", "-p"],
      { encoding: "buffer", maxBuffer: 15 * 1024 * 1024, timeout: 6000 }
    );
    res.setHeader("Content-Type", "image/png");
    res.setHeader("Cache-Control", "no-store");
    return res.send(stdout);
  } catch (err) {
    return res.status(502).json({ error: err.message });
  }
});

app.post("/api/vms/:vmId/tap", authenticateGateway, async (req, res) => {
  const { vmId } = req.params;
  const entry = vmRegistry.get(vmId);
  if (!entry || !entry.adbSerial) {
    return res.status(404).json({ error: `${vmId} is not mapped to an active ADB serial.` });
  }
  const x = Math.max(0, Math.round(Number(req.body?.x || 0)));
  const y = Math.max(0, Math.round(Number(req.body?.y || 0)));
  try {
    await execFileAsync(ADB_BIN, ["-s", entry.adbSerial, "shell", "input", "tap", String(x), String(y)], {
      timeout: 4000,
    });
    return res.json({ accepted: true });
  } catch (err) {
    return res.status(502).json({ error: err.message });
  }
});

app.post("/api/vms/:vmId/swipe", authenticateGateway, async (req, res) => {
  const { vmId } = req.params;
  const entry = vmRegistry.get(vmId);
  if (!entry || !entry.adbSerial) {
    return res.status(404).json({ error: `${vmId} is not mapped to an active ADB serial.` });
  }
  const x1 = Math.max(0, Math.round(Number(req.body?.x1 || 0)));
  const y1 = Math.max(0, Math.round(Number(req.body?.y1 || 0)));
  const x2 = Math.max(0, Math.round(Number(req.body?.x2 || 0)));
  const y2 = Math.max(0, Math.round(Number(req.body?.y2 || 0)));
  const duration = Math.min(1200, Math.max(80, Math.round(Number(req.body?.durationMs || 220))));
  try {
    await execFileAsync(
      ADB_BIN,
      ["-s", entry.adbSerial, "shell", "input", "swipe", String(x1), String(y1), String(x2), String(y2), String(duration)],
      { timeout: 4000 }
    );
    return res.json({ accepted: true });
  } catch (err) {
    return res.status(502).json({ error: err.message });
  }
});

app.post("/api/accounts/open-signin", authenticateGateway, async (req, res) => {
  const { vmIds = [] } = req.body || {};
  const results = await mapWithConcurrency(vmIds, 12, async (vmId) => {
    const entry = vmRegistry.get(vmId);
    if (!entry || !entry.adbSerial) {
      return { vmId, status: "HOST_OFFLINE", message: "No active ADB serial mapped" };
    }
    try {
      const { stdout, stderr } = await execFileAsync(
        ADB_BIN,
        [
          "-s",
          entry.adbSerial,
          "shell",
          "am",
          "start",
          "-f",
          "0x10000000",
          "-a",
          "android.settings.ADD_ACCOUNT_SETTINGS",
          "--esa",
          "account_types",
          "com.google",
        ],
        { timeout: 6000 }
      );
      const combined = `${stdout || ""}\n${stderr || ""}`;
      if (!/Error:|unable to resolve Intent/i.test(combined)) {
        return {
          vmId,
          status: "OPENED",
          message: `Opened Google Sign-In screen on ${entry.adbSerial}`,
        };
      }
      await launchInstalledPackage(entry.adbSerial, "com.android.vending");
      return {
        vmId,
        status: "OPENED",
        message: `Opened Play Store sign-in prompt on ${entry.adbSerial}`,
      };
    } catch (err) {
      return { vmId, status: "FAILED", message: err.message };
    }
  });
  return res.json({ accepted: true, message: "Opened Google Sign-In flow", results });
});

app.post("/api/apps/clear-session", authenticateGateway, async (req, res) => {
  const { vmIds = [], packageName = "", openAfterClear = true } = req.body || {};
  const safePkg = String(packageName || "").replace(/[^a-zA-Z0-9_.]/g, "");
  if (!safePkg) {
    return res.status(400).json({
      accepted: false,
      message: "Valid packageName is required to reset app session.",
      results: [],
    });
  }
  const results = await mapWithConcurrency(vmIds, 12, async (vmId) => {
    const entry = vmRegistry.get(vmId);
    if (!entry || !entry.adbSerial) {
      return { vmId, status: "HOST_OFFLINE", message: "No active ADB serial mapped" };
    }
    try {
      const { stdout: pmOut } = await execFileAsync(
        ADB_BIN,
        ["-s", entry.adbSerial, "shell", "pm", "path", safePkg],
        { timeout: 4000 }
      );
      if (!pmOut || !pmOut.includes("package:")) {
        return {
          vmId,
          status: "FAILED",
          message: `${safePkg} is not installed on ${vmId}`,
        };
      }
      await execFileAsync(ADB_BIN, ["-s", entry.adbSerial, "shell", "pm", "clear", safePkg], {
        timeout: 6000,
      });
      if (openAfterClear) {
        await launchInstalledPackage(entry.adbSerial, safePkg);
      }
      return {
        vmId,
        status: openAfterClear ? "OPENED" : "COMPLETED",
        message: openAfterClear
          ? `Reset session & opened ${safePkg} on ${entry.adbSerial}`
          : `Cleared session data for ${safePkg} on ${entry.adbSerial}`,
      };
    } catch (err) {
      return { vmId, status: "FAILED", message: err.message };
    }
  });
  return res.json({ accepted: true, message: `Reset session for ${safePkg}`, results });
});

function formatAdbInputText(rawText) {
  const normalized = String(rawText || "").replace(/[\r\n]/g, "").slice(0, 200);
  let escaped = "";
  for (const ch of normalized) {
    if (ch === " ") {
      escaped += "%s";
    } else if (/['"\\`$&;|()<>*?[\]~#]/.test(ch)) {
      escaped += `\\${ch}`;
    } else {
      escaped += ch;
    }
  }
  return escaped;
}

app.post("/api/vms/:vmId/input-text", authenticateGateway, async (req, res) => {
  const { vmId } = req.params;
  const entry = vmRegistry.get(vmId);
  if (!entry || !entry.adbSerial) {
    return res.status(404).json({ accepted: false, message: `${vmId} is not mapped to an active ADB serial.` });
  }
  const raw = String(req.body?.text || "").replace(/[\r\n]/g, "");
  if (!raw) {
    return res.status(400).json({ accepted: false, message: "Text must not be empty." });
  }
  const safeInput = formatAdbInputText(raw);
  try {
    await execFileAsync(ADB_BIN, ["-s", entry.adbSerial, "shell", "input", "text", safeInput], {
      timeout: 5000,
    });
    return res.json({ accepted: true, message: `Typed text into ${vmId}` });
  } catch (err) {
    return res.status(502).json({ accepted: false, message: err.message });
  }
});

app.post("/api/vms/:vmId/keyevent", authenticateGateway, async (req, res) => {
  const { vmId } = req.params;
  const entry = vmRegistry.get(vmId);
  if (!entry || !entry.adbSerial) {
    return res.status(404).json({ error: `${vmId} is not mapped to an active ADB serial.` });
  }
  const allowedKeys = new Set([3, 4, 24, 25, 26, 61, 66, 67, 82, 187]);
  const keyCode = Number(req.body?.keyCode || 0);
  if (!allowedKeys.has(keyCode)) {
    return res.status(400).json({ error: "Unsupported Android keyCode" });
  }
  try {
    await execFileAsync(ADB_BIN, ["-s", entry.adbSerial, "shell", "input", "keyevent", String(keyCode)], {
      timeout: 4000,
    });
    return res.json({ accepted: true });
  } catch (err) {
    return res.status(502).json({ error: err.message });
  }
});

app.get("/viewer.js", (_req, res) => {
  res.setHeader("Content-Type", "application/javascript; charset=utf-8");
  return res.send(`(function () {
  const vmId = document.body.dataset.vmId || "";
  const img = document.getElementById("scr");
  const err = document.getElementById("err");
  const st = document.getElementById("st");
  const params = new URLSearchParams(location.search);
  const token = params.get("token") || "";
  const assignedEmail = params.get("email") || "";
  const fillEmailBtn = document.getElementById("btn-fill-email");
  const signInBtn = document.getElementById("btn-google-signin");
  const txtInput = document.getElementById("txt-input");
  const sendTxtBtn = document.getElementById("btn-send-text");
  const hdrs = Object.assign({ "Content-Type": "application/json" }, token ? { Authorization: "Bearer " + token } : {});
  let dragStart = null;
  if (fillEmailBtn) {
    if (assignedEmail) {
      fillEmailBtn.textContent = "Type " + assignedEmail;
      fillEmailBtn.style.display = "inline-flex";
      fillEmailBtn.addEventListener("click", async () => {
        await fetch("/api/vms/" + encodeURIComponent(vmId) + "/input-text", {
          method: "POST", headers: hdrs,
          body: JSON.stringify({ text: assignedEmail })
        }).catch(() => {});
        setTimeout(refreshFrame, 250);
      });
    } else {
      fillEmailBtn.style.display = "none";
    }
  }
  if (signInBtn) {
    signInBtn.addEventListener("click", async () => {
      await fetch("/api/accounts/open-signin", {
        method: "POST", headers: hdrs,
        body: JSON.stringify({ vmIds: [vmId] })
      }).catch(() => {});
      setTimeout(refreshFrame, 350);
    });
  }
  if (sendTxtBtn && txtInput) {
    sendTxtBtn.addEventListener("click", async () => {
      const text = txtInput.value || "";
      if (!text) return;
      await fetch("/api/vms/" + encodeURIComponent(vmId) + "/input-text", {
        method: "POST", headers: hdrs,
        body: JSON.stringify({ text })
      }).catch(() => {});
      txtInput.value = "";
      setTimeout(refreshFrame, 250);
    });
  }
  async function refreshFrame() {
    try {
      const res = await fetch("/api/vms/" + encodeURIComponent(vmId) + "/screen.png?t=" + Date.now(), {
        headers: token ? { Authorization: "Bearer " + token } : {}
      });
      if (!res.ok) throw new Error("HTTP " + res.status);
      const blob = await res.blob();
      const nextUrl = URL.createObjectURL(blob);
      const prevUrl = img.dataset.objUrl;
      img.src = nextUrl;
      img.dataset.objUrl = nextUrl;
      if (prevUrl) URL.revokeObjectURL(prevUrl);
      img.style.display = "block";
      err.style.display = "none";
      st.textContent = "Live";
    } catch (e) {
      img.style.display = "none";
      err.style.display = "block";
      err.textContent = "Screen offline (" + e.message + ")";
      st.textContent = "Offline";
    }
  }
  function toScreenXY(ev) {
    const rect = img.getBoundingClientRect();
    const x = Math.round(((ev.clientX - rect.left) / rect.width) * img.naturalWidth);
    const y = Math.round(((ev.clientY - rect.top) / rect.height) * img.naturalHeight);
    return { x, y };
  }
  img.addEventListener("pointerdown", (ev) => {
    if (!img.naturalWidth || !img.naturalHeight) return;
    dragStart = toScreenXY(ev);
  });
  img.addEventListener("pointerup", async (ev) => {
    if (!dragStart || !img.naturalWidth || !img.naturalHeight) return;
    const end = toScreenXY(ev);
    const dist = Math.hypot(end.x - dragStart.x, end.y - dragStart.y);
    if (dist > 24) {
      await fetch("/api/vms/" + encodeURIComponent(vmId) + "/swipe", {
        method: "POST", headers: hdrs,
        body: JSON.stringify({ x1: dragStart.x, y1: dragStart.y, x2: end.x, y2: end.y, durationMs: 220 })
      }).catch(() => {});
    } else {
      await fetch("/api/vms/" + encodeURIComponent(vmId) + "/tap", {
        method: "POST", headers: hdrs,
        body: JSON.stringify({ x: end.x, y: end.y })
      }).catch(() => {});
    }
    dragStart = null;
    setTimeout(refreshFrame, 220);
  });
  document.querySelectorAll("[data-key]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const keyCode = Number(btn.dataset.key || 0);
      await fetch("/api/vms/" + encodeURIComponent(vmId) + "/keyevent", {
        method: "POST", headers: hdrs,
        body: JSON.stringify({ keyCode })
      }).catch(() => {});
      setTimeout(refreshFrame, 220);
    });
  });
  refreshFrame();
  setInterval(refreshFrame, 900);
})();`);
});

app.get("/viewer/:vmId", (req, res) => {
  const safeVmId = String(req.params.vmId || "").replace(/[^A-Za-z0-9_-]/g, "");
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  return res.send(`<!doctype html>
<html>
<head>
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Remote Screen • ${safeVmId}</title>
  <style>
    body { margin: 0; background: #090B11; color: #F4F6FB; font-family: system-ui, sans-serif; display: flex; flex-direction: column; align-items: center; min-height: 100vh; }
    .bar { width: 100%; padding: 8px 12px; font-size: 12px; background: #121622; border-bottom: 1px solid #283046; display: flex; justify-content: space-between; box-sizing: border-box; }
    .wrap { flex: 1; display: flex; align-items: center; justify-content: center; width: 100%; padding: 6px; box-sizing: border-box; }
    img { max-width: 100%; max-height: 66vh; border-radius: 14px; border: 1px solid #283046; cursor: crosshair; touch-action: none; }
    .nav { width: 100%; display: flex; flex-wrap: wrap; gap: 6px; justify-content: center; padding: 6px 8px; box-sizing: border-box; }
    .nav button, .send-btn { background: #1B2234; color: #F4F6FB; border: 1px solid #334155; border-radius: 10px; padding: 7px 11px; font-size: 11px; font-weight: 600; cursor: pointer; }
    .primary-btn { background: #2563EB !important; border-color: #3B82F6 !important; }
    .type-row { width: 100%; display: flex; gap: 6px; padding: 0 10px 10px; box-sizing: border-box; }
    .type-row input { flex: 1; background: #121622; color: #F4F6FB; border: 1px solid #334155; border-radius: 10px; padding: 7px 10px; font-size: 12px; }
    .err { color: #FCA5A5; font-size: 13px; text-align: center; padding: 16px; display: none; }
  </style>
</head>
<body data-vm-id="${safeVmId}">
  <div class="bar"><span>${safeVmId}</span><span id="st">Connecting...</span></div>
  <div class="wrap">
    <img id="scr" alt="VM Screen" />
    <div id="err" class="err">Waiting for live ADB emulator screen on ${safeVmId}...</div>
  </div>
  <div class="nav">
    <button type="button" data-key="4">Back</button>
    <button type="button" data-key="3">Home</button>
    <button type="button" data-key="187">Recents</button>
    <button type="button" data-key="66">Enter</button>
    <button type="button" id="btn-google-signin">Google Sign-In</button>
    <button type="button" id="btn-fill-email" class="primary-btn" style="display:none">Type Gmail</button>
  </div>
  <div class="type-row">
    <input id="txt-input" type="text" placeholder="Type text into focused screen field..." />
    <button type="button" id="btn-send-text" class="send-btn">Send</button>
  </div>
  <script src="/viewer.js"></script>
</body>
</html>`);
});

app.post("/api/apps/manage", authenticateGateway, async (req, res) => {
  const { vmIds = [], action, packageName, apkPath, apkSourceUrl } = req.body || {};
  const safePkg = String(packageName || "").replace(/[^a-zA-Z0-9_.]/g, "");
  let tempApkPath = "";
  let downloadError = "";

  if ((action === "INSTALL_APK" || action === "UPDATE_APK") && !apkPath && apkSourceUrl) {
    try {
      tempApkPath = await downloadApkToTemp(String(apkSourceUrl).trim());
    } catch (err) {
      downloadError = err.message;
    }
  }

  const effectiveApk = apkPath ? String(apkPath) : tempApkPath;
  const results = [];
  try {
    const batchResults = await mapWithConcurrency(vmIds, 10, async (vmId) => {
      const entry = vmRegistry.get(vmId);
      if (!entry || !entry.adbSerial) {
        return { vmId, status: "HOST_OFFLINE", message: "No active ADB serial mapped" };
      }
      try {
        if (action === "UNINSTALL_APK") {
          await execFileAsync(ADB_BIN, ["-s", entry.adbSerial, "uninstall", safePkg], { timeout: 15000 });
          return { vmId, status: "COMPLETED", message: `Uninstalled ${safePkg}` };
        }
        if (action === "INSTALL_APK" || action === "UPDATE_APK") {
          if (downloadError) {
            return { vmId, status: "FAILED", message: downloadError };
          }
          if (effectiveApk) {
            await execFileAsync(ADB_BIN, ["-s", entry.adbSerial, "install", "-r", effectiveApk], { timeout: 45000 });
            return { vmId, status: "COMPLETED", message: `Installed ${safePkg}` };
          }
          return { vmId, status: "QUEUED", message: "Awaiting APK source URL or host path" };
        }
        return { vmId, status: "FAILED", message: `Unsupported action ${action}` };
      } catch (err) {
        return { vmId, status: "FAILED", message: err.message };
      }
    });
    results.push(...batchResults);
  } finally {
    if (tempApkPath) {
      await fsp.unlink(tempApkPath).catch(() => {});
    }
  }
  return res.json({ accepted: true, message: "Processed app command", results });
});

// WebRTC Signaling WebSocket for authorized remote screen viewing
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/webrtc-signal" });

const rooms = new Map();
wss.on("connection", (ws, req) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const roomId = url.searchParams.get("vmId") || "default";
  if (!rooms.has(roomId)) rooms.set(roomId, new Set());
  rooms.get(roomId).add(ws);

  ws.on("message", (data) => {
    for (const peer of rooms.get(roomId) || []) {
      if (peer !== ws && peer.readyState === 1) {
        peer.send(data.toString());
      }
    }
  });
  ws.on("close", () => {
    rooms.get(roomId)?.delete(ws);
  });
});

server.listen(PORT, () => {
  console.log(`100X VM Host Gateway (${HOST_LABEL}) listening on port ${PORT}`);
});
