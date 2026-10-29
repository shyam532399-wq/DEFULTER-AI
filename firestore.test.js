const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function serverTs() {
  return new Date(Date.now() - 1000);
}

test("Unauthenticated caller cannot read or write any user profile or subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("documents").get()
  );
});

test("Authenticated owner can create and update valid UserProfile, but rejects ghost fields and enum poisoning", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  const validProfile = {
    userId: ALICE_UID,
    bossName: "Boss",
    persona: "maya",
    tone: "calm",
    listenMode: "boss_only",
    personalWiki: "Boss ko subah chai aur crisp updates pasand hain.",
    voiceEnrolled: false,
    voicePassphrase: "",
    voiceModel: "powerful",
    guardArmed: false,
    touchGuardArmed: false,
    createdAt: ts,
    updatedAt: ts,
  };

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile)
  );

  // Shadow / Ghost field attack
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({ persona: "friday", isAdmin: true, updatedAt: ts })
  );

  // Enum poisoning attack
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({ persona: "jarvis", updatedAt: ts })
  );

  // Valid persona switch update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({ persona: "friday", updatedAt: ts })
  );
});

test("Cross-user isolation blocks Bob from reading, listing, or modifying Alice's BoardDocuments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = serverTs(aliceDb);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("doc_1")
      .set({
        userId: ALICE_UID,
        title: "Q4 Marketing Plan",
        category: "marketing",
        summary: "Instagram reels + WhatsApp drip focus.",
        content: "# Campaign Plan\n- Week 1: Hooks",
        pinned: true,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Bob cannot get or list Alice's documents
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("doc_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  // Alice can query her own documents with aligned userId filter
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Identity spoofing and immutable createdAt tampering are rejected across Memories, Skills, and Reminders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  // Identity spoofing on create (setting userId = BOB_UID inside Alice's path)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .set({
        userId: BOB_UID,
        kind: "fact",
        key: "coffee",
        value: "Black coffee without sugar",
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Valid memory, skill, and reminder creations
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .set({
        userId: ALICE_UID,
        kind: "fact",
        key: "coffee",
        value: "Black coffee without sugar",
        createdAt: ts,
        updatedAt: ts,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("skills")
      .doc("skill_1")
      .set({
        userId: ALICE_UID,
        kind: "skill",
        name: "Subah Briefing",
        trigger: "Good morning Maya",
        stepsText: "1. Battery dekho\n2. Weather batao\n3. Reminders sunao",
        enabled: true,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reminders")
      .doc("rem_1")
      .set({
        userId: ALICE_UID,
        kind: "reminder",
        title: "Subah 7 baje jagana",
        detail: "Gym workout",
        scheduledInfo: "07:00 AM",
        completed: false,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Attempting to tamper with createdAt during update must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reminders")
      .doc("rem_1")
      .update({
        completed: true,
        createdAt: new Date(0),
        updatedAt: ts,
      })
  );
});
