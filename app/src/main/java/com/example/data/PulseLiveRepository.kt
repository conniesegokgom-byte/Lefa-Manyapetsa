package com.example.data

import android.content.Context
import com.example.R
import com.example.model.ChatMessage
import com.example.model.ChatRoom
import com.example.model.FeedComment
import com.example.model.FeedLike
import com.example.model.FeedPost
import com.example.model.Livestream
import com.example.model.StreamComment
import com.example.model.UserPublicProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PulseLiveRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    val botRepository = PulseBotRepository(db, auth)
    val callRepository = PulseCallRepository(db, auth)

    fun isCurrentUserVerifiedOwner(): Boolean = botRepository.isCurrentUserVerifiedOwner()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- User Public Profiles ---

    suspend fun ensureUserProfile(defaultDisplayName: String): Result<UserPublicProfile> = runCatching {
        val uid = requireUserId()
        val isOwner = isCurrentUserVerifiedOwner()
        val docRef = db.collection("users_public").document(uid)
        val querySnap = try {
            db.collection("users_public")
                .whereEqualTo("userId", uid)
                .limit(1)
                .get()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users_public", auth)
            throw e
        }

        if (!querySnap.isEmpty) {
            val existing = querySnap.documents.first().toObject(UserPublicProfile::class.java, ESTIMATE)
            if (existing != null) {
                if (isOwner) {
                    if (existing.role != UserPublicProfile.ROLE_OWNER ||
                        !existing.isVerified ||
                        existing.displayName != UserPublicProfile.OWNER_DISPLAY_NAME
                    ) {
                        val upgradePayload = mapOf(
                            "displayName" to UserPublicProfile.OWNER_DISPLAY_NAME,
                            "role" to UserPublicProfile.ROLE_OWNER,
                            "isVerified" to true,
                            "bio" to existing.bio.ifBlank { "CEO & Creator of PulseLive" },
                            "statusText" to "Official Owner",
                            "avatarColorHex" to "#FFB703",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        try {
                            docRef.update(upgradePayload).await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
                            throw e
                        }
                        botRepository.ensureSystemBotProfile()
                        return@runCatching existing.copy(
                            displayName = UserPublicProfile.OWNER_DISPLAY_NAME,
                            role = UserPublicProfile.ROLE_OWNER,
                            isVerified = true,
                            bio = existing.bio.ifBlank { "CEO & Creator of PulseLive" },
                            statusText = "Official Owner",
                            avatarColorHex = "#FFB703"
                        )
                    }
                    botRepository.ensureSystemBotProfile()
                }
                return@runCatching existing
            }
        }

        val palette = listOf("#00E5A3", "#FF4D6D", "#38BDF8", "#A78BFA", "#FB7185")
        val cleanName = if (isOwner) {
            UserPublicProfile.OWNER_DISPLAY_NAME
        } else {
            defaultDisplayName.trim().ifBlank { "Creator" }.take(50)
        }
        val role = if (isOwner) UserPublicProfile.ROLE_OWNER else UserPublicProfile.ROLE_MEMBER
        val bio = if (isOwner) "CEO & Creator of PulseLive" else "Live on PulseLive"
        val statusText = if (isOwner) "Official Owner" else "Available"
        val colorHex = if (isOwner) "#FFB703" else palette[kotlin.math.abs(uid.hashCode()) % palette.size]

        val payload = mapOf(
            "userId" to uid,
            "displayName" to cleanName,
            "bio" to bio,
            "statusText" to statusText,
            "avatarColorHex" to colorHex,
            "role" to role,
            "isVerified" to isOwner,
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
        if (isOwner) {
            botRepository.ensureSystemBotProfile()
        }
        UserPublicProfile(
            id = uid,
            userId = uid,
            displayName = cleanName,
            bio = bio,
            statusText = statusText,
            avatarColorHex = colorHex,
            role = role,
            isVerified = isOwner,
            discoverable = true,
            isLive = false
        )
    }

    suspend fun updateUserProfile(displayName: String, bio: String, statusText: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users_public").document(uid)
        val payload = mapOf(
            "displayName" to displayName.trim().ifBlank { "Creator" }.take(50),
            "bio" to bio.trim().take(180),
            "statusText" to statusText.trim().take(80),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    fun observeCommunityUsers(): Flow<List<UserPublicProfile>> = flow {
        val uid = requireUserId()
        val path = "users_public"
        emitAll(
            db.collection(path)
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserPublicProfile::class.java, ESTIMATE)
                        .sortedWith(
                            compareByDescending<UserPublicProfile> { it.isOwner }
                                .thenByDescending { it.isBot }
                                .thenByDescending { it.isLive }
                                .thenBy { it.displayName.lowercase() }
                        )
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path, uid) }
        )
    }

    // --- Chat Rooms & Messages ---

    suspend fun ensureCommunityLounge(userName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanName = if (isCurrentUserVerifiedOwner()) {
            UserPublicProfile.OWNER_DISPLAY_NAME
        } else {
            userName.take(50).ifBlank { "Creator" }
        }
        val existingLounges = try {
            db.collection("chat_rooms")
                .whereEqualTo("isPublic", true)
                .limit(1)
                .get()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "chat_rooms", auth)
            throw e
        }
        if (!existingLounges.isEmpty) return@runCatching

        val docRef = db.collection("chat_rooms").document("pulse_global_lounge")
        val payload = mapOf(
            "title" to "PulseLive Lounge",
            "roomType" to "lounge",
            "createdBy" to uid,
            "participantUids" to listOf(uid),
            "participantNames" to mapOf(uid to cleanName),
            "isPublic" to true,
            "lastMessageText" to "Welcome to the live lounge! Drop a message or start a stream.",
            "lastMessageSenderId" to uid,
            "lastMessageSenderName" to cleanName,
            "linkedStreamId" to "",
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

    fun observeChatRooms(): Flow<List<ChatRoom>> = flow {
        val uid = requireUserId()
        val myRoomsFlow = db.collection("chat_rooms")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { it.toObjects(ChatRoom::class.java, ESTIMATE) }
            .catch { error -> logAndRethrow(error, OperationType.LIST, "chat_rooms", uid) }

        val publicRoomsFlow = db.collection("chat_rooms")
            .whereEqualTo("isPublic", true)
            .snapshots()
            .map { it.toObjects(ChatRoom::class.java, ESTIMATE) }
            .catch { error -> logAndRethrow(error, OperationType.LIST, "chat_rooms", uid) }

        emitAll(
            combine(myRoomsFlow, publicRoomsFlow) { mine, pub ->
                (mine + pub)
                    .distinctBy { it.id }
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    fun observeChatRoom(roomId: String): Flow<ChatRoom?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("chat_rooms").document(roomId)
        emitAll(
            docRef.snapshots()
                .map { snap -> snap.toObject(ChatRoom::class.java, ESTIMATE) }
                .catch { error -> logAndRethrow(error, OperationType.GET, docRef.path, uid) }
        )
    }

    fun observeMessages(roomId: String, isPublic: Boolean): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("chat_rooms").document(roomId).collection("messages")
        val query = if (isPublic) {
            colRef.whereEqualTo("isPublic", true)
        } else {
            colRef.whereArrayContains("participantUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, colRef.path, uid) }
        )
    }

    suspend fun createOrOpenDirectChat(
        currentUserName: String,
        otherUser: UserPublicProfile
    ): Result<ChatRoom> {
        if (otherUser.userId == UserPublicProfile.SYSTEM_BOT_ID || otherUser.isBot) {
            return botRepository.ensurePersonalBotRoom(currentUserName)
        }
        return runCatching {
            val uid = requireUserId()
            val participants = listOf(uid, otherUser.userId).distinct().sorted()
            val deterministicId = if (participants.size == 1) {
                "dm_self_${uid}"
            } else {
                "dm_${participants[0].take(20)}_${participants[1].take(20)}"
            }
            val docRef = db.collection("chat_rooms").document(deterministicId)

        // Check existing rooms where user participates rather than blind get() on potentially non-existent room
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

        val existingRoom = existingSnap.toObjects(ChatRoom::class.java, ESTIMATE)
            .firstOrNull { it.id == deterministicId || (it.roomType == "direct" && it.participantUids.toSet() == participants.toSet()) }

        if (existingRoom != null) return@runCatching existingRoom

        val namesMap = mapOf(
            uid to currentUserName.take(50).ifBlank { "Me" },
            otherUser.userId to otherUser.displayName.take(50).ifBlank { "User" }
        )
        val title = otherUser.displayName.take(80).ifBlank { "Direct Chat" }
        val payload = mapOf(
            "title" to title,
            "roomType" to "direct",
            "createdBy" to uid,
            "participantUids" to participants,
            "participantNames" to namesMap,
            "isPublic" to false,
            "lastMessageText" to "",
            "lastMessageSenderId" to "",
            "lastMessageSenderName" to "",
            "linkedStreamId" to "",
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
            ChatRoom(
                id = deterministicId,
                title = title,
                roomType = "direct",
                createdBy = uid,
                participantUids = participants,
                participantNames = namesMap,
                isPublic = false
            )
        }
    }

    suspend fun createGroupOrLounge(
        title: String,
        currentUserName: String,
        selectedMembers: List<UserPublicProfile>,
        isPublicLounge: Boolean
    ): Result<ChatRoom> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("chat_rooms").document()
        val participantUids = (listOf(uid) + selectedMembers.map { it.userId }).distinct().take(30)
        val participantNames = buildMap {
            put(uid, currentUserName.take(50).ifBlank { "Me" })
            selectedMembers.forEach { member ->
                put(member.userId, member.displayName.take(50).ifBlank { "Member" })
            }
        }
        val cleanTitle = title.trim().ifBlank { if (isPublicLounge) "Live Lounge" else "Group Chat" }.take(80)
        val roomType = if (isPublicLounge) "lounge" else "group"
        val payload = mapOf(
            "title" to cleanTitle,
            "roomType" to roomType,
            "createdBy" to uid,
            "participantUids" to participantUids,
            "participantNames" to participantNames,
            "isPublic" to isPublicLounge,
            "lastMessageText" to "",
            "lastMessageSenderId" to "",
            "lastMessageSenderName" to "",
            "linkedStreamId" to "",
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
        ChatRoom(
            id = docRef.id,
            title = cleanTitle,
            roomType = roomType,
            createdBy = uid,
            participantUids = participantUids,
            participantNames = participantNames,
            isPublic = isPublicLounge
        )
    }

    suspend fun sendMessage(
        room: ChatRoom,
        senderName: String,
        text: String,
        messageType: String = "text",
        imageBase64: String = "",
        linkedStreamId: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanText = text.trim().take(1000)
        val cleanImage = imageBase64.take(350000)
        require(cleanText.isNotEmpty() || cleanImage.isNotEmpty()) {
            "Message cannot be empty"
        }

        val roomRef = db.collection("chat_rooms").document(room.id)
        val msgRef = roomRef.collection("messages").document()
        val cleanSender = senderName.trim().ifBlank { "User" }.take(50)

        val msgPayload = buildMap<String, Any> {
            put("roomId", room.id)
            put("senderId", uid)
            put("senderName", cleanSender)
            put("text", cleanText)
            put("messageType", messageType)
            put("participantUids", room.participantUids.ifEmpty { listOf(uid) })
            put("isPublic", room.isPublic)
            put("createdAt", FieldValue.serverTimestamp())
            if (cleanImage.isNotEmpty()) put("imageBase64", cleanImage)
            if (linkedStreamId.isNotBlank()) put("linkedStreamId", linkedStreamId)
        }

        val preview = when (messageType) {
            "live_invite" -> "Started a livestream: $cleanText"
            "image" -> if (cleanText.isNotEmpty()) "Photo: $cleanText" else "Sent a photo"
            else -> cleanText
        }.take(500)

        val roomUpdate = buildMap<String, Any> {
            put("lastMessageText", preview)
            put("lastMessageSenderId", uid)
            put("lastMessageSenderName", cleanSender)
            put("updatedAt", FieldValue.serverTimestamp())
            if (linkedStreamId.isNotBlank()) put("linkedStreamId", linkedStreamId)
        }

        val batch = db.batch()
        batch.set(msgRef, msgPayload)
        batch.update(roomRef, roomUpdate)
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path, auth)
            throw e
        }
        msgRef.id
    }

    // --- Livestreams ---

    fun observeLivestreams(): Flow<List<Livestream>> = flow {
        val uid = requireUserId()
        val path = "livestreams"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Livestream::class.java, ESTIMATE)
                        .sortedWith(
                            compareByDescending<Livestream> { it.status == "live" }
                                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeLivestream(streamId: String): Flow<Livestream?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("livestreams").document(streamId)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(Livestream::class.java, ESTIMATE) }
                .catch { error -> logAndRethrow(error, OperationType.GET, docRef.path, uid) }
        )
    }

    fun observeStreamComments(streamId: String): Flow<List<StreamComment>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("livestreams").document(streamId).collection("comments")
        emitAll(
            colRef.whereEqualTo("streamVisibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(StreamComment::class.java, ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, colRef.path, uid) }
        )
    }

    suspend fun startLivestream(
        hostName: String,
        title: String,
        category: String,
        linkedRoom: ChatRoom? = null
    ): Result<Livestream> = runCatching {
        val uid = requireUserId()
        val streamRef = db.collection("livestreams").document()
        val profileRef = db.collection("users_public").document(uid)
        val cleanHost = hostName.trim().ifBlank { "Creator" }.take(50)
        val cleanTitle = title.trim().ifBlank { "$cleanHost's Live Room" }.take(100)
        val validCategory = category.takeIf { it in STREAM_CATEGORIES } ?: "Chill"

        val streamPayload = buildMap<String, Any> {
            put("hostId", uid)
            put("hostName", cleanHost)
            put("title", cleanTitle)
            put("category", validCategory)
            put("status", "live")
            put("visibility", "public")
            put("viewerCount", 1L)
            put("likeCount", 0L)
            if (linkedRoom != null) put("linkedRoomId", linkedRoom.id)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        try {
            streamRef.set(streamPayload).await()
            // Update user public status if profile exists
            val profileQuery = db.collection("users_public")
                .whereEqualTo("userId", uid)
                .limit(1)
                .get()
                .await()
            if (!profileQuery.isEmpty) {
                profileRef.update(
                    mapOf(
                        "isLive" to true,
                        "activeStreamId" to streamRef.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            if (linkedRoom != null) {
                sendMessage(
                    room = linkedRoom,
                    senderName = cleanHost,
                    text = cleanTitle,
                    messageType = "live_invite",
                    linkedStreamId = streamRef.id
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, streamRef.path, auth)
            throw e
        }

        Livestream(
            id = streamRef.id,
            hostId = uid,
            hostName = cleanHost,
            title = cleanTitle,
            category = validCategory,
            status = "live",
            visibility = "public",
            viewerCount = 1L,
            likeCount = 0L,
            linkedRoomId = linkedRoom?.id ?: ""
        )
    }

    suspend fun endLivestream(streamId: String, hostId: String? = null): Result<Unit> = runCatching {
        val uid = requireUserId()
        val targetHostUid = hostId?.takeIf { it.isNotBlank() } ?: uid
        val streamRef = db.collection("livestreams").document(streamId)
        val profileRef = db.collection("users_public").document(targetHostUid)
        try {
            streamRef.update(
                mapOf(
                    "status" to "ended",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val profileQuery = db.collection("users_public")
                .whereEqualTo("userId", targetHostUid)
                .limit(1)
                .get()
                .await()
            if (!profileQuery.isEmpty) {
                profileRef.update(
                    mapOf(
                        "isLive" to false,
                        "activeStreamId" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, streamRef.path, auth)
            throw e
        }
    }

    suspend fun sendStreamHeart(streamId: String): Result<Unit> = runCatching {
        requireUserId()
        val streamRef = db.collection("livestreams").document(streamId)
        try {
            streamRef.update(
                mapOf(
                    "likeCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, streamRef.path, auth)
            throw e
        }
    }

    suspend fun updateViewerPresence(streamId: String, delta: Long): Result<Unit> = runCatching {
        requireUserId()
        val streamRef = db.collection("livestreams").document(streamId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(streamRef)
                if (snap.exists() && snap.getString("status") == "live") {
                    val current = snap.getLong("viewerCount") ?: 1L
                    val next = (current + delta).coerceAtLeast(1L)
                    tx.update(
                        streamRef,
                        mapOf(
                            "viewerCount" to next,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, streamRef.path, auth)
            throw e
        }
    }

    suspend fun postStreamComment(streamId: String, authorName: String, text: String): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanText = text.trim().take(300)
        require(cleanText.isNotEmpty()) { "Comment cannot be empty" }
        val docRef = db.collection("livestreams").document(streamId).collection("comments").document()
        val payload = mapOf(
            "streamId" to streamId,
            "authorId" to uid,
            "authorName" to authorName.trim().ifBlank { "Viewer" }.take(50),
            "text" to cleanText,
            "streamVisibility" to "public",
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
        docRef.id
    }

    // --- Social Feed Posts ---

    fun observeFeedPosts(): Flow<List<FeedPost>> = flow {
        val uid = requireUserId()
        val path = "feed_posts"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FeedPost::class.java, ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeUserLikedPostIds(): Flow<Set<String>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collectionGroup("likes")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FeedLike::class.java, ESTIMATE)
                        .map { it.postId }
                        .toSet()
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, "likes", uid) }
        )
    }

    suspend fun refreshFeedPosts(): Result<List<FeedPost>> = runCatching {
        requireUserId()
        val path = "feed_posts"
        try {
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .get(Source.SERVER)
                .await()
                .toObjects(FeedPost::class.java, ESTIMATE)
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            throw e
        }
    }

    suspend fun createFeedPost(
        authorName: String,
        caption: String,
        category: String,
        imageBase64: String = "",
        imageUrl: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanCaption = caption.trim().take(1000)
        val cleanImage = imageBase64.take(350000)
        val cleanImageUrl = imageUrl.trim().take(2000)
        if (cleanImageUrl.isNotEmpty()) {
            require(
                cleanImageUrl.startsWith("https://", ignoreCase = true) ||
                    cleanImageUrl.startsWith("http://", ignoreCase = true)
            ) {
                "Image URL must start with https:// or http://"
            }
        }
        require(cleanCaption.isNotEmpty() || cleanImage.isNotEmpty() || cleanImageUrl.isNotEmpty()) {
            "Post must contain text or an image"
        }
        val validCategory = category.takeIf { it in FEED_CATEGORIES } ?: "Update"
        val docRef = db.collection("feed_posts").document()
        val payload = buildMap<String, Any> {
            put("authorId", uid)
            put("authorName", authorName.trim().ifBlank { "Creator" }.take(50))
            put("caption", cleanCaption)
            put("category", validCategory)
            if (cleanImage.isNotEmpty()) put("imageBase64", cleanImage)
            if (cleanImageUrl.isNotEmpty()) put("imageUrl", cleanImageUrl)
            put("visibility", "public")
            put("likeCount", 0L)
            put("commentCount", 0L)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
        docRef.id
    }

    suspend fun toggleFeedLike(post: FeedPost, currentlyLiked: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val postRef = db.collection("feed_posts").document(post.id)
        val likeRef = postRef.collection("likes").document(uid)
        val batch = db.batch()
        if (currentlyLiked) {
            batch.delete(likeRef)
            batch.update(
                postRef,
                mapOf(
                    "likeCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                likeRef,
                mapOf(
                    "postId" to post.id,
                    "userId" to uid,
                    "postVisibility" to "public",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                postRef,
                mapOf(
                    "likeCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path, auth)
            throw e
        }
    }

    fun observeFeedComments(postId: String): Flow<List<FeedComment>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("feed_posts").document(postId).collection("comments")
        emitAll(
            colRef.whereEqualTo("postVisibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FeedComment::class.java, ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, colRef.path, uid) }
        )
    }

    suspend fun addFeedComment(postId: String, authorName: String, text: String): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanText = text.trim().take(500)
        require(cleanText.isNotEmpty()) { "Comment cannot be empty" }
        val postRef = db.collection("feed_posts").document(postId)
        val commentRef = postRef.collection("comments").document()
        val batch = db.batch()
        batch.set(
            commentRef,
            mapOf(
                "postId" to postId,
                "authorId" to uid,
                "authorName" to authorName.trim().ifBlank { "User" }.take(50),
                "text" to cleanText,
                "postVisibility" to "public",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            postRef,
            mapOf(
                "commentCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path, auth)
            throw e
        }
        commentRef.id
    }

    suspend fun deleteFeedPost(postId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("feed_posts").document(postId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    private fun logAndRethrow(error: Throwable, operationType: OperationType, path: String, uid: String): Nothing {
        val firestoreError = error.unwrapFirestoreException()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path, auth)
        }
        throw error
    }

    companion object {
        private val ESTIMATE = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        val STREAM_CATEGORIES = listOf("Chill", "Music", "Gaming", "Q&A", "IRL", "News")
        val FEED_CATEGORIES = listOf("Update", "Photo", "Live Clip", "Announcement", "Vibe")
    }
}
