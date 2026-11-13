package com.example.data

import com.example.model.ChatRoom
import com.example.model.UserPublicProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class PulseBotRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    fun isCurrentUserVerifiedOwner(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email.equals(OWNER_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    suspend fun ensureSystemBotProfile() {
        if (!isCurrentUserVerifiedOwner()) return
        val existingBot = try {
            db.collection("users_public")
                .whereEqualTo("userId", UserPublicProfile.SYSTEM_BOT_ID)
                .limit(1)
                .get()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users_public", auth)
            throw e
        }
        if (!existingBot.isEmpty) return

        val docRef = db.collection("users_public").document(UserPublicProfile.SYSTEM_BOT_ID)
        val payload = mapOf(
            "userId" to UserPublicProfile.SYSTEM_BOT_ID,
            "displayName" to UserPublicProfile.SYSTEM_BOT_NAME,
            "bio" to "Official System Bot & Community Assistant",
            "statusText" to "Always Online • Type /help",
            "avatarColorHex" to "#00E5A3",
            "role" to UserPublicProfile.ROLE_BOT,
            "isVerified" to true,
            "discoverable" to true,
            "isLive" to false,
            "activeStreamId" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun ensurePersonalBotRoom(userName: String): Result<ChatRoom> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before creating bot room.")
        val botRoomId = "bot_$uid"
        val existingSnap = try {
            db.collection("chat_rooms")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "chat_rooms", auth)
            throw e
        }

        val existingRoom = existingSnap.toObjects(ChatRoom::class.java)
            .firstOrNull { it.id == botRoomId || it.roomType == "bot" }
        if (existingRoom != null) return@runCatching existingRoom

        val isOwner = isCurrentUserVerifiedOwner()
        val welcomeText = buildWelcomeMessage(isOwner)
        val cleanName = userName.trim().ifBlank { if (isOwner) UserPublicProfile.OWNER_DISPLAY_NAME else "Creator" }.take(50)
        val roomRef = db.collection("chat_rooms").document(botRoomId)
        val participantNames = mapOf(
            uid to cleanName,
            UserPublicProfile.SYSTEM_BOT_ID to UserPublicProfile.SYSTEM_BOT_NAME
        )
        val roomPayload = mapOf(
            "title" to UserPublicProfile.SYSTEM_BOT_NAME,
            "roomType" to "bot",
            "createdBy" to uid,
            "participantUids" to listOf(uid),
            "participantNames" to participantNames,
            "isPublic" to false,
            "lastMessageText" to welcomeText.take(500),
            "lastMessageSenderId" to UserPublicProfile.SYSTEM_BOT_ID,
            "lastMessageSenderName" to UserPublicProfile.SYSTEM_BOT_NAME,
            "linkedStreamId" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            roomRef.set(roomPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, roomRef.path, auth)
            throw e
        }

        val welcomeRef = roomRef.collection("messages").document("welcome")
        val msgPayload = mapOf(
            "roomId" to botRoomId,
            "senderId" to UserPublicProfile.SYSTEM_BOT_ID,
            "senderName" to UserPublicProfile.SYSTEM_BOT_NAME,
            "text" to welcomeText,
            "messageType" to "bot_reply",
            "participantUids" to listOf(uid),
            "isPublic" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            welcomeRef.set(msgPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, welcomeRef.path, auth)
            throw e
        }

        ChatRoom(
            id = botRoomId,
            title = UserPublicProfile.SYSTEM_BOT_NAME,
            roomType = "bot",
            createdBy = uid,
            participantUids = listOf(uid),
            participantNames = participantNames,
            isPublic = false,
            lastMessageText = welcomeText.take(500),
            lastMessageSenderId = UserPublicProfile.SYSTEM_BOT_ID,
            lastMessageSenderName = UserPublicProfile.SYSTEM_BOT_NAME
        )
    }

    suspend fun sendBotCommandAndReply(
        room: ChatRoom,
        senderName: String,
        commandText: String
    ): Result<String> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before messaging PulseLive Bot.")
        val cleanText = commandText.trim().take(1000)
        require(cleanText.isNotEmpty()) { "Command cannot be empty" }

        val roomRef = db.collection("chat_rooms").document(room.id)
        val userMsgRef = roomRef.collection("messages").document()
        val cleanSender = senderName.trim().ifBlank { "Creator" }.take(50)
        val participants = room.participantUids.ifEmpty { listOf(uid) }

        val userMsgPayload = mapOf(
            "roomId" to room.id,
            "senderId" to uid,
            "senderName" to cleanSender,
            "text" to cleanText,
            "messageType" to "text",
            "participantUids" to participants,
            "isPublic" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            userMsgRef.set(userMsgPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, userMsgRef.path, auth)
            throw e
        }

        val replyText = resolveCommandReply(cleanText)
        val botMsgRef = roomRef.collection("messages").document()
        val botMsgPayload = mapOf(
            "roomId" to room.id,
            "senderId" to UserPublicProfile.SYSTEM_BOT_ID,
            "senderName" to UserPublicProfile.SYSTEM_BOT_NAME,
            "text" to replyText.take(1000),
            "messageType" to "bot_reply",
            "participantUids" to participants,
            "isPublic" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val roomUpdate = mapOf(
            "lastMessageText" to replyText.take(500),
            "lastMessageSenderId" to UserPublicProfile.SYSTEM_BOT_ID,
            "lastMessageSenderName" to UserPublicProfile.SYSTEM_BOT_NAME,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(botMsgRef, botMsgPayload)
        batch.update(roomRef, roomUpdate)
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, botMsgRef.path, auth)
            throw e
        }
        botMsgRef.id
    }

    suspend fun publishOfficialAnnouncement(announcementText: String): Result<String> = runCatching {
        check(isCurrentUserVerifiedOwner()) {
            "Only the verified Owner (OFFICIAL) can broadcast official announcements."
        }
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Owner must be signed in.")
        val cleanBody = announcementText.trim().take(900)
        require(cleanBody.isNotEmpty()) { "Announcement cannot be empty" }

        val postRef = db.collection("feed_posts").document()
        val postPayload = mapOf(
            "authorId" to UserPublicProfile.SYSTEM_BOT_ID,
            "authorName" to "${UserPublicProfile.SYSTEM_BOT_NAME} • OFFICIAL",
            "caption" to cleanBody,
            "category" to "Announcement",
            "visibility" to "public",
            "likeCount" to 0L,
            "commentCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            postRef.set(postPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path, auth)
            throw e
        }

        // Also broadcast into public lounge rooms
        val publicLounges = db.collection("chat_rooms")
            .whereEqualTo("isPublic", true)
            .limit(3)
            .get()
            .await()

        for (loungeDoc in publicLounges.documents) {
            val roomRef = loungeDoc.reference
            val msgRef = roomRef.collection("messages").document()
            val currentParticipants = (loungeDoc.get("participantUids") as? List<*>)
                ?.filterIsInstance<String>()
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(uid)
            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "roomId" to loungeDoc.id,
                    "senderId" to UserPublicProfile.SYSTEM_BOT_ID,
                    "senderName" to UserPublicProfile.SYSTEM_BOT_NAME,
                    "text" to cleanBody,
                    "messageType" to "bot_announcement",
                    "participantUids" to currentParticipants,
                    "isPublic" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                roomRef,
                mapOf(
                    "lastMessageText" to "OFFICIAL Announcement: $cleanBody".take(500),
                    "lastMessageSenderId" to UserPublicProfile.SYSTEM_BOT_ID,
                    "lastMessageSenderName" to UserPublicProfile.SYSTEM_BOT_NAME,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
        }

        postRef.id
    }

    private suspend fun resolveCommandReply(rawInput: String): String {
        val lower = rawInput.lowercase()
        val isOwner = isCurrentUserVerifiedOwner()
        return when {
            lower.startsWith("/announce") -> {
                if (!isOwner) {
                    "Access restricted: only the verified Owner (OFFICIAL) can publish /announce broadcasts."
                } else {
                    val body = rawInput.removePrefix("/announce").removePrefix("/ANNOUNCE").trim()
                    if (body.isBlank()) {
                        "Usage: /announce <message>\nExample: /announce Welcome to PulseLive tonight at 8PM!"
                    } else {
                        publishOfficialAnnouncement(body).getOrThrow()
                        "Broadcast complete! Posted as a verified PulseLive Bot announcement to Feeds and PulseLive Lounge."
                    }
                }
            }
            lower.startsWith("/live") -> {
                val streamsSnap = db.collection("livestreams")
                    .whereEqualTo("visibility", "public")
                    .get()
                    .await()
                val liveNow = streamsSnap.documents.filter { it.getString("status") == "live" }
                if (liveNow.isEmpty()) {
                    "No streams are on air right now. Tap Go Live in the Live tab or any chat room to start broadcasting!"
                } else {
                    val summary = liveNow.take(5).joinToString("\n") { doc ->
                        val title = doc.getString("title") ?: "Live Room"
                        val host = doc.getString("hostName") ?: "Creator"
                        val viewers = doc.getLong("viewerCount") ?: 1L
                        "• $title by $host ($viewers watching)"
                    }
                    "On Air Now (${liveNow.size}):\n$summary"
                }
            }
            lower.startsWith("/stats") -> {
                val usersSnap = db.collection("users_public")
                    .whereEqualTo("discoverable", true)
                    .get()
                    .await()
                val streamsSnap = db.collection("livestreams")
                    .whereEqualTo("visibility", "public")
                    .get()
                    .await()
                val postsSnap = db.collection("feed_posts")
                    .whereEqualTo("visibility", "public")
                    .get()
                    .await()
                val liveCount = streamsSnap.documents.count { it.getString("status") == "live" }
                "PulseLive Community Snapshot:\n• Verified & Active Profiles: ${usersSnap.size()}\n• Broadcasts Live Now: $liveCount (${streamsSnap.size()} total)\n• Feed Posts Shared: ${postsSnap.size()}"
            }
            lower.startsWith("/tips") -> {
                "PulseLive Studio Tips:\n1. Tap 'Go Live' inside any chat room to auto-send a Watch Live invite card.\n2. Attach photos in Chats & Feeds via the zero-permission Photo Picker.\n3. Tap the verified badge on OFFICIAL or PulseLive Bot posts to spot official updates."
            }
            else -> buildWelcomeMessage(isOwner)
        }
    }

    private fun buildWelcomeMessage(isOwner: Boolean): String {
        val base = StringBuilder().apply {
            if (isOwner) {
                append("Hello OFFICIAL (Verified CEO & Owner)! I'm PulseLive Bot, your system assistant.\n")
            } else {
                append("Hi! I'm PulseLive Bot, your real-time system assistant.\n")
            }
            append("Available commands:\n")
            append("• /help — Show all bot commands\n")
            append("• /stats — Live community counts\n")
            append("• /live — Who is broadcasting right now\n")
            append("• /tips — Creator & livestream shortcuts")
            if (isOwner) {
                append("\n• /announce <text> — Broadcast an official announcement to Lounge & Feeds")
            }
        }
        return base.toString()
    }

    companion object {
        const val OWNER_EMAIL = "conniesegokgom@gmail.com"
    }
}
