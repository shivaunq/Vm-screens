import React, { useEffect, useMemo, useState } from "react";
import { onAuthStateChanged, signInWithPopup, signOut } from "firebase/auth";
import {
  collection,
  doc,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  writeBatch,
} from "firebase/firestore";
import { Cloud, Cpu, Link2, LogOut, Play, Server, Terminal } from "lucide-react";
import { auth, db, googleProvider } from "./firebase";

const LOCAL_STORAGE_KEY = "vmmaster_local_state_v1";

function loadLocalState() {
  try {
    const raw = localStorage.getItem(LOCAL_STORAGE_KEY);
    return raw ? JSON.parse(raw) : { vms: [], logs: [], gatewayUrl: "", bearerToken: "" };
  } catch {
    return { vms: [], logs: [], gatewayUrl: "", bearerToken: "" };
  }
}

export default function App() {
  const [user, setUser] = useState(null);
  const [localState, setLocalState] = useState(loadLocalState);
  const [cloudVms, setCloudVms] = useState([]);
  const [cloudLogs, setCloudLogs] = useState([]);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [search, setSearch] = useState("");
  const [urlInput, setUrlInput] = useState("");
  const [gatewayUrl, setGatewayUrl] = useState(() => loadLocalState().gatewayUrl || "");
  const [bearerToken, setBearerToken] = useState(() => loadLocalState().bearerToken || "");

  useEffect(() => onAuthStateChanged(auth, setUser), []);

  useEffect(() => {
    localStorage.setItem(
      LOCAL_STORAGE_KEY,
      JSON.stringify({ ...localState, gatewayUrl, bearerToken })
    );
  }, [localState, gatewayUrl, bearerToken]);

  useEffect(() => {
    if (!user) return undefined;
    const unsubVms = onSnapshot(
      query(collection(db, "users", user.uid, "vms"), orderBy("slotNumber", "asc")),
      (snap) => setCloudVms(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
    );
    const unsubLogs = onSnapshot(
      query(collection(db, "users", user.uid, "audit_logs"), orderBy("createdAt", "desc"), limit(40)),
      (snap) => setCloudLogs(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
    );
    return () => {
      unsubVms();
      unsubLogs();
    };
  }, [user]);

  const vms = user ? cloudVms : localState.vms;
  const logs = user ? cloudLogs : localState.logs;

  const filteredVms = useMemo(() => {
    const q = search.trim().toLowerCase();
    return vms.filter(
      (v) =>
        !q ||
        v.vmId?.toLowerCase().includes(q) ||
        v.name?.toLowerCase().includes(q) ||
        v.ipAddress?.toLowerCase().includes(q)
    );
  }, [vms, search]);

  async function init100Slots() {
    if (user) {
      const col = collection(db, "users", user.uid, "vms");
      for (let start = 1; start <= 100; start += 50) {
        const batch = writeBatch(db);
        for (let i = start; i < start + 50; i++) {
          const vmId = `VM-${String(i).padStart(3, "0")}`;
          batch.set(doc(col, vmId), {
            userId: user.uid,
            vmId,
            slotNumber: i,
            name: `Slot ${vmId}`,
            groupId: "unassigned",
            androidVersion: "Unassigned",
            status: "UNPROVISIONED",
            connectivity: "DISCONNECTED",
            hostUrl: "",
            streamUrl: "",
            lastError: "",
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp(),
          });
        }
        await batch.commit();
      }
      return;
    }

    const nowIso = new Date().toISOString();
    const generated = Array.from({ length: 100 }, (_, idx) => {
      const i = idx + 1;
      const vmId = `VM-${String(i).padStart(3, "0")}`;
      return {
        id: vmId,
        vmId,
        slotNumber: i,
        name: `Slot ${vmId}`,
        groupId: "unassigned",
        androidVersion: "Unassigned",
        status: "UNPROVISIONED",
        connectivity: "DISCONNECTED",
        updatedAt: nowIso,
      };
    });
    setLocalState((prev) => ({
      ...prev,
      vms: generated,
      logs: [
        {
          id: `log_${Date.now()}`,
          action: "INIT_100_SLOTS",
          targetScope: "VM-001..VM-100",
          details: "Initialized 100 unprovisioned VM slots in local operator mode",
          result: "SUCCESS",
        },
        ...prev.logs,
      ],
    }));
  }

  async function dispatchUrls(all100) {
    if (!urlInput.trim()) return;
    const targets = all100 ? vms : vms.filter((v) => selectedIds.has(v.vmId));
    if (targets.length === 0) return;

    if (user) {
      const cmdCol = collection(db, "users", user.uid, "vm_commands");
      for (let i = 0; i < targets.length; i += 50) {
        const batch = writeBatch(db);
        targets.slice(i, i + 50).forEach((vm) => {
          const cmdRef = doc(cmdCol);
          batch.set(cmdRef, {
            userId: user.uid,
            vmId: vm.vmId,
            commandType: "OPEN_URL",
            targetPayload: urlInput.trim(),
            targetPackage: "com.android.chrome",
            deliveryStatus: vm.status === "UNPROVISIONED" ? "HOST_OFFLINE" : "QUEUED",
            statusMessage:
              vm.status === "UNPROVISIONED"
                ? `Slot ${vm.vmId} unprovisioned`
                : "Queued in Firestore awaiting gateway",
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp(),
          });
        });
        await batch.commit();
      }
      return;
    }

    let resultLabel = "HOST_OFFLINE";
    let detailMsg = `Queued locally for ${targets.length} VMs`;
    if (gatewayUrl.trim()) {
      try {
        const resp = await fetch(`${gatewayUrl.trim().replace(/\/$/, "")}/api/urls/dispatch`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(bearerToken.trim() ? { Authorization: `Bearer ${bearerToken.trim()}` } : {}),
          },
          body: JSON.stringify({
            vmIds: targets.map((t) => t.vmId),
            url: urlInput.trim(),
            targetPackage: "com.android.chrome",
          }),
        });
        resultLabel = resp.ok ? "SUCCESS" : "FAILED";
        detailMsg = resp.ok
          ? `Dispatched ${urlInput.trim()} via gateway`
          : `Gateway HTTP ${resp.status}`;
      } catch (err) {
        detailMsg = `Gateway unreachable: ${err.message}`;
      }
    }

    setLocalState((prev) => ({
      ...prev,
      logs: [
        {
          id: `log_${Date.now()}`,
          action: "DISPATCH_URL",
          targetScope: all100 ? "ALL_100_VMS" : `${targets.length} VMs`,
          details: detailMsg,
          result: resultLabel,
        },
        ...prev.logs,
      ],
    }));
  }

  const online = vms.filter((v) => v.status === "ONLINE").length;
  const running = vms.filter((v) => v.status === "RUNNING").length;
  const errors = vms.filter((v) => v.status === "ERROR").length;
  const offline = vms.length - online - running - errors;

  return (
    <div className="min-h-screen bg-[#090B11] text-slate-100 p-4 md:p-8 max-w-7xl mx-auto space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-[#283046] pb-4">
        <div className="flex items-center gap-3">
          <Cpu className="w-7 h-7 text-violet-400" />
          <div>
            <h1 className="text-xl font-bold">100X Cloud Android VM Master</h1>
            <p className="text-xs text-slate-400 font-mono">
              {user ? `Cloud Sync: ${user.email}` : "Local Operator Mode (No Login Required)"}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {vms.length === 0 && (
            <button
              onClick={init100Slots}
              className="px-4 py-2 rounded-xl bg-violet-600 hover:bg-violet-500 text-sm font-semibold flex items-center gap-2"
            >
              <Play className="w-4 h-4" /> Initialize 100 Slots
            </button>
          )}
          {user ? (
            <button
              onClick={() => signOut(auth)}
              className="px-3 py-2 rounded-xl border border-[#283046] hover:bg-[#1A1F30] text-sm flex items-center gap-2"
            >
              <LogOut className="w-4 h-4" /> Local Mode
            </button>
          ) : (
            <button
              onClick={() => signInWithPopup(auth, googleProvider)}
              className="px-3 py-2 rounded-xl border border-[#283046] hover:bg-[#1A1F30] text-sm flex items-center gap-2"
            >
              <Cloud className="w-4 h-4 text-violet-400" /> Optional Cloud Sync
            </button>
          )}
        </div>
      </header>

      <section className="grid grid-cols-2 md:grid-cols-5 gap-3">
        <Kpi label="Total Slots" value={`${vms.length} / 100`} />
        <Kpi label="Online" value={online} />
        <Kpi label="Running" value={running} />
        <Kpi label="Offline / Unprov." value={offline} />
        <Kpi label="Errors" value={errors} />
      </section>

      <section className="rounded-2xl border border-[#283046] bg-[#121622] p-4 grid grid-cols-1 md:grid-cols-2 gap-3">
        <div className="flex items-center gap-2 bg-[#090B11] border border-[#283046] rounded-xl px-3 py-2">
          <Server className="w-4 h-4 text-violet-400" />
          <input
            value={gatewayUrl}
            onChange={(e) => setGatewayUrl(e.target.value)}
            placeholder="HTTPS Host Gateway URL (https://...)"
            className="bg-transparent w-full text-sm focus:outline-none"
          />
        </div>
        <input
          type="password"
          value={bearerToken}
          onChange={(e) => setBearerToken(e.target.value)}
          placeholder="Gateway Bearer Token (optional)"
          className="bg-[#090B11] border border-[#283046] rounded-xl px-3 py-2 text-sm focus:outline-none"
        />
      </section>

      <section className="rounded-2xl border border-[#283046] bg-[#121622] p-4 flex flex-col md:flex-row gap-3">
        <div className="flex-1 flex items-center gap-2 bg-[#090B11] border border-[#283046] rounded-xl px-3 py-2">
          <Link2 className="w-4 h-4 text-blue-400" />
          <input
            value={urlInput}
            onChange={(e) => setUrlInput(e.target.value)}
            placeholder="Paste testing URL (https://...)"
            className="bg-transparent w-full text-sm focus:outline-none"
          />
        </div>
        <button
          onClick={() => dispatchUrls(false)}
          disabled={selectedIds.size === 0 || !urlInput.trim()}
          className="px-4 py-2 rounded-xl bg-violet-600 disabled:opacity-40 text-sm font-semibold"
        >
          OPEN SELECTED ({selectedIds.size})
        </button>
        <button
          onClick={() => dispatchUrls(true)}
          disabled={vms.length === 0 || !urlInput.trim()}
          className="px-4 py-2 rounded-xl bg-blue-600 disabled:opacity-40 text-sm font-semibold"
        >
          OPEN ALL (100)
        </button>
      </section>

      <section className="space-y-3">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search VM-001..100, IP, name..."
          className="w-full md:w-80 bg-[#121622] border border-[#283046] rounded-xl px-3 py-2 text-sm"
        />
        <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-8 gap-2">
          {filteredVms.map((vm) => {
            const active = selectedIds.has(vm.vmId);
            return (
              <button
                key={vm.vmId}
                onClick={() => {
                  const next = new Set(selectedIds);
                  active ? next.delete(vm.vmId) : next.add(vm.vmId);
                  setSelectedIds(next);
                }}
                className={`p-2.5 rounded-xl border text-left transition ${
                  active
                    ? "border-violet-500 bg-violet-950/40"
                    : "border-[#283046] bg-[#121622] hover:border-blue-500/50"
                }`}
              >
                <div className="font-mono text-xs font-bold">{vm.vmId}</div>
                <div className="text-[11px] text-slate-400 truncate">{vm.name}</div>
                <div className="text-[10px] font-mono text-cyan-300/90 truncate">
                  {vm.ipAddress || "IP: Unassigned"}
                </div>
                <div className="text-[10px] text-slate-500 mt-0.5">{vm.status}</div>
              </button>
            );
          })}
        </div>
      </section>

      <section className="rounded-2xl border border-[#283046] bg-[#121622] p-4 space-y-2">
        <div className="flex items-center gap-2 text-sm font-semibold">
          <Terminal className="w-4 h-4 text-violet-400" /> Audit Trail ({logs.length})
        </div>
        <div className="space-y-1.5 max-h-60 overflow-y-auto text-xs font-mono text-slate-300">
          {logs.map((l) => (
            <div key={l.id} className="border-b border-[#283046]/60 pb-1">
              [{l.result}] {l.action} ({l.targetScope}) — {l.details}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function Kpi({ label, value }) {
  return (
    <div className="rounded-xl border border-[#283046] bg-[#121622] p-3">
      <div className="text-xs text-slate-400">{label}</div>
      <div className="text-2xl font-bold mt-1">{value}</div>
    </div>
  );
}
