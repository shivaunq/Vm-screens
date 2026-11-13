const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_admin_001";
const BOB_UID = "bob_admin_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTime() {
  return new Date();
}

function validVmPayload(uid, vmId = "VM-001", slotNumber = 1) {
  const now = serverTime();
  return {
    userId: uid,
    vmId,
    slotNumber,
    name: `Cloud Node ${vmId}`,
    groupId: "unassigned",
    androidVersion: "Unassigned",
    status: "UNPROVISIONED",
    connectivity: "DISCONNECTED",
    hostUrl: "",
    streamUrl: "",
    lastError: "",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list VMs or audit logs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/vms`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/audit_logs`).get());
});

test("Owner can create and read VM-001 slot with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/vms`)
      .doc("VM-001")
      .set(validVmPayload(ALICE_UID, "VM-001", 1))
  );
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/vms`).get());
});

test("Cross-user access to another admin's VMs and commands is rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection(`users/${BOB_UID}/vms`)
      .doc("VM-001")
      .set(validVmPayload(BOB_UID, "VM-001", 1));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.doc(`users/${BOB_UID}/vms/VM-001`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/vms`).get());
});

test("Shadow update with ghost field is rejected on VM slot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const payload = {
    ...validVmPayload(ALICE_UID, "VM-001", 1),
    isSuperAdmin: true,
  };
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/vms/VM-001`).set(payload)
  );
});

test("Client cannot escalate role in AdminProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = serverTime();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/profile/admin`).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "OPERATOR",
      fleetInitialized: false,
      gatewayBaseUrl: "",
      webrtcSignalingUrl: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/profile/admin`).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "OWNER_ADMIN",
      fleetInitialized: false,
      gatewayBaseUrl: "",
      webrtcSignalingUrl: "",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Audit logs are strictly append-only (update and delete rejected)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb.doc(`users/${ALICE_UID}/audit_logs/log_001`);
  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      actorEmail: "alice@example.com",
      action: "INIT_FLEET",
      targetScope: "VM-001..VM-100",
      details: "Initialized 100 unprovisioned VM slots",
      result: "SUCCESS",
      createdAt: serverTime(),
    })
  );
  await assertFails(logRef.update({ details: "tampered" }));
  await assertFails(logRef.delete());
});

test("Command terminal state locking prevents modifying COMPLETED commands", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = serverTime();
  const cmdRef = aliceDb.doc(`users/${ALICE_UID}/vm_commands/cmd_001`);
  await assertSucceeds(
    cmdRef.set({
      userId: ALICE_UID,
      vmId: "VM-001",
      commandType: "OPEN_URL",
      targetPayload: "https://example.org",
      targetPackage: "com.android.chrome",
      deliveryStatus: "COMPLETED",
      statusMessage: "Opened cleanly",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    cmdRef.update({
      deliveryStatus: "FAILED",
      statusMessage: "Mutated after terminal",
      updatedAt: serverTime(),
    })
  );
});

test("Owner can assign per-VM Gmail and valid accountStatus; invalid status rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const vmRef = aliceDb.doc(`users/${ALICE_UID}/vms/VM-001`);
  await assertSucceeds(vmRef.set(validVmPayload(ALICE_UID, "VM-001", 1)));

  await assertSucceeds(
    vmRef.update({
      assignedGmail: "screen01.user@gmail.com",
      accountStatus: "GMAIL_ASSIGNED",
      updatedAt: serverTime(),
    })
  );

  await assertFails(
    vmRef.update({
      assignedGmail: "screen01.user@gmail.com",
      accountStatus: "HACKED_STATE",
      updatedAt: serverTime(),
    })
  );
});

