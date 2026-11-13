const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_ceo_001";
const OWNER_EMAIL = "conniesegokgom@gmail.com";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 1000);
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

test("Unauthenticated caller: rejected on all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users_public").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("chat_rooms").where("isPublic", "==", true).get());
  await assertFails(unauthDb.collection("livestreams").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("feed_posts").where("visibility", "==", "public").get());
});

test("Owner & Anti-Privilege-Escalation: verified CEO gets OFFICIAL owner badge, spoofed/regular users blocked", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const spoofedDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTimestamp();

  // Verified owner creates OFFICIAL verified profile & System Bot profile
  await assertSucceeds(
    ownerDb.collection("users_public").doc(OWNER_UID).set({
      userId: OWNER_UID,
      displayName: "OFFICIAL",
      bio: "CEO & Creator of PulseLive",
      statusText: "Official Owner",
      avatarColorHex: "#FFB703",
      role: "owner",
      isVerified: true,
      discoverable: true,
      isLive: false,
      activeStreamId: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    ownerDb.collection("users_public").doc("pulse_system_bot").set({
      userId: "pulse_system_bot",
      displayName: "PulseLive Bot",
      bio: "Official System Bot",
      statusText: "Always Online",
      avatarColorHex: "#00E5A3",
      role: "bot",
      isVerified: true,
      discoverable: true,
      isLive: false,
      activeStreamId: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Email spoofing test (matching email but email_verified == false) MUST fail
  await assertFails(
    spoofedDb.collection("users_public").doc("spoof_uid").set({
      userId: "spoof_uid",
      displayName: "OFFICIAL",
      role: "owner",
      isVerified: true,
      discoverable: true,
      isLive: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular user cannot self-assign verified or owner role
  await assertFails(
    aliceDb.collection("users_public").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      role: "owner",
      isVerified: true,
      discoverable: true,
      isLive: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular user registers cleanly as unverified member, cannot escalate on update
  await assertSucceeds(
    aliceDb.collection("users_public").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      bio: "Creator",
      statusText: "Available",
      avatarColorHex: "#00E5A3",
      role: "member",
      isVerified: false,
      discoverable: true,
      isLive: false,
      activeStreamId: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users_public").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("System Bot rooms & Chat isolation: personal bot replies work, non-owners blocked from public bot announcements", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastTimestamp();

  // Alice creates her personal bot room and sends /help + receives bot_reply
  const botRoomId = `bot_${ALICE_UID}`;
  await assertSucceeds(
    aliceDb.collection("chat_rooms").doc(botRoomId).set({
      title: "PulseLive System Bot",
      roomType: "bot",
      createdBy: ALICE_UID,
      participantUids: [ALICE_UID],
      participantNames: { [ALICE_UID]: "Alice" },
      isPublic: false,
      lastMessageText: "",
      lastMessageSenderId: "",
      lastMessageSenderName: "",
      linkedStreamId: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("chat_rooms").doc(botRoomId).collection("messages").doc("b1").set({
      roomId: botRoomId,
      senderId: "pulse_system_bot",
      senderName: "PulseLive Bot",
      text: "Welcome! Type /help, /stats, or /live.",
      messageType: "bot_reply",
      participantUids: [ALICE_UID],
      isPublic: false,
      createdAt: pastTimestamp(),
    })
  );

  // Regular user cannot spoof a System Bot announcement in a public lounge
  await assertSucceeds(
    ownerDb.collection("chat_rooms").doc("pulse_global_lounge").set({
      title: "PulseLive Lounge",
      roomType: "lounge",
      createdBy: OWNER_UID,
      participantUids: [OWNER_UID],
      participantNames: { [OWNER_UID]: "OFFICIAL" },
      isPublic: true,
      lastMessageText: "",
      lastMessageSenderId: "",
      lastMessageSenderName: "",
      linkedStreamId: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("chat_rooms").doc("pulse_global_lounge").collection("messages").doc("spoof_bot").set({
      roomId: "pulse_global_lounge",
      senderId: "pulse_system_bot",
      senderName: "PulseLive Bot",
      text: "Fake announcement",
      messageType: "bot_announcement",
      participantUids: [OWNER_UID],
      isPublic: true,
      createdAt: pastTimestamp(),
    })
  );
  // Owner CAN post official bot_announcement
  await assertSucceeds(
    ownerDb.collection("chat_rooms").doc("pulse_global_lounge").collection("messages").doc("real_bot").set({
      roomId: "pulse_global_lounge",
      senderId: "pulse_system_bot",
      senderName: "PulseLive Bot",
      text: "Official update from CEO!",
      messageType: "bot_announcement",
      participantUids: [OWNER_UID],
      isPublic: true,
      createdAt: pastTimestamp(),
    })
  );

  // Private DM isolation check
  await assertSucceeds(
    aliceDb.collection("chat_rooms").doc("dm_alice_bob").set({
      title: "Alice & Bob",
      roomType: "direct",
      createdBy: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      isPublic: false,
      lastMessageText: "",
      lastMessageSenderId: "",
      lastMessageSenderName: "",
      linkedStreamId: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(charlieDb.collection("chat_rooms").doc("dm_alice_bob").get());
  await assertSucceeds(bobDb.collection("chat_rooms").doc("dm_alice_bob").get());
});

test("Livestreams & Feeds: terminal state lock, owner moderation, and atomic batch likes", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTimestamp();

  // Stream lifecycle
  await assertSucceeds(
    aliceDb.collection("livestreams").doc("stream_1").set({
      hostId: ALICE_UID,
      hostName: "Alice",
      title: "Acoustic Set",
      category: "Music",
      status: "live",
      visibility: "public",
      viewerCount: 1,
      likeCount: 0,
      linkedRoomId: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("livestreams").doc("stream_1").update({
      status: "ended",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("livestreams").doc("stream_1").update({
      status: "live",
      updatedAt: pastTimestamp(),
    })
  );

  // Feed post + atomic like
  await assertSucceeds(
    aliceDb.collection("feed_posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      caption: "Hello PulseLive!",
      category: "Update",
      visibility: "public",
      likeCount: 0,
      commentCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  const likeBatch = bobDb.batch();
  likeBatch.set(bobDb.collection("feed_posts").doc("post_1").collection("likes").doc(BOB_UID), {
    postId: "post_1",
    userId: BOB_UID,
    postVisibility: "public",
    createdAt: pastTimestamp(),
  });
  likeBatch.update(bobDb.collection("feed_posts").doc("post_1"), {
    likeCount: 1,
    updatedAt: pastTimestamp(),
  });
  await assertSucceeds(likeBatch.commit());

  // Image URL post creation succeeds and non-author delete fails; Owner moderates
  await assertSucceeds(
    aliceDb.collection("feed_posts").doc("post_img_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      caption: "",
      category: "Photo",
      imageUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4",
      visibility: "public",
      likeCount: 0,
      commentCount: 0,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Bob cannot delete Alice's post, but Owner can moderate
  await assertFails(bobDb.collection("feed_posts").doc("post_1").delete());
  await assertSucceeds(ownerDb.collection("feed_posts").doc("post_1").delete());
});

test("Calls: participant isolation, ringing->connected->ended lifecycle, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastTimestamp();

  // Alice calls Bob
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      roomId: "dm_alice_bob",
      callType: "video",
      status: "ringing",
      isVideoEnabledByCaller: true,
      isVideoEnabledByCallee: false,
      durationSeconds: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unrelated Charlie cannot read or hijack the call
  await assertFails(charlieDb.collection("calls").doc("call_1").get());
  await assertFails(
    charlieDb.collection("calls").doc("call_1").update({
      status: "connected",
      updatedAt: pastTimestamp(),
    })
  );

  // Bob answers call and enables video, then ends call
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "connected",
      isVideoEnabledByCallee: true,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 42,
      updatedAt: pastTimestamp(),
    })
  );

  // Terminal call cannot be reopened
  await assertFails(
    aliceDb.collection("calls").doc("call_1").update({
      status: "connected",
      updatedAt: pastTimestamp(),
    })
  );
});
