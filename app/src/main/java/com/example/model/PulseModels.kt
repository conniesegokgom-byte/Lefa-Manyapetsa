package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserPublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val bio: String = "",
    val statusText: String = "",
    val avatarColorHex: String = "#00E5A3",
    val role: String = ROLE_MEMBER,
    val isVerified: Boolean = false,
    val discoverable: Boolean = true,
    val isLive: Boolean = false,
    val activeStreamId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isOwner: Boolean get() = role == ROLE_OWNER
    val isBot: Boolean get() = role == ROLE_BOT

    companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
        const val ROLE_BOT = "bot"
        const val SYSTEM_BOT_ID = "pulse_system_bot"
        const val OWNER_DISPLAY_NAME = "OFFICIAL"
        const val SYSTEM_BOT_NAME = "PulseLive Bot"
    }
}

@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val title: String = "",
    val roomType: String = "direct",
    val createdBy: String = "",
    val participantUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val isPublic: Boolean = false,
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageSenderName: String = "",
    val linkedStreamId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isBotRoom: Boolean get() = roomType == "bot"

    fun displayTitleFor(currentUserId: String): String {
        if (roomType == "bot") return UserPublicProfile.SYSTEM_BOT_NAME
        if (roomType != "direct") return title
        val otherEntry = participantNames.entries.firstOrNull { it.key != currentUserId }
        return otherEntry?.value?.takeIf { it.isNotBlank() } ?: title
    }
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = "text",
    val imageBase64: String = "",
    val linkedStreamId: String = "",
    val participantUids: List<String> = emptyList(),
    val isPublic: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val isBotMessage: Boolean
        get() = senderId == UserPublicProfile.SYSTEM_BOT_ID ||
            messageType == "bot_reply" ||
            messageType == "bot_announcement"
}

@IgnoreExtraProperties
data class Livestream(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val title: String = "",
    val category: String = "Chill",
    val status: String = "live",
    val visibility: String = "public",
    val viewerCount: Long = 1L,
    val likeCount: Long = 0L,
    val linkedRoomId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isLiveNow: Boolean get() = status == "live"
}

@IgnoreExtraProperties
data class StreamComment(
    @DocumentId val id: String = "",
    val streamId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val streamVisibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FeedPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val caption: String = "",
    val category: String = "Update",
    val imageBase64: String = "",
    val imageUrl: String = "",
    val visibility: String = "public",
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val hasImage: Boolean get() = imageUrl.isNotBlank() || imageBase64.isNotBlank()
}

@IgnoreExtraProperties
data class FeedLike(
    @DocumentId val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val postVisibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FeedComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val postVisibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val participantUids: List<String> = emptyList(),
    val roomId: String = "",
    val callType: String = TYPE_VOICE,
    val status: String = STATUS_RINGING,
    val isVideoEnabledByCaller: Boolean = false,
    val isVideoEnabledByCallee: Boolean = false,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isRinging: Boolean get() = status == STATUS_RINGING
    val isConnected: Boolean get() = status == STATUS_CONNECTED
    val isActive: Boolean get() = status == STATUS_RINGING || status == STATUS_CONNECTED
    val isVideoCall: Boolean get() = callType == TYPE_VIDEO

    fun otherPartyName(currentUserId: String): String =
        if (callerId == currentUserId) calleeName else callerName

    fun otherPartyId(currentUserId: String): String =
        if (callerId == currentUserId) calleeId else callerId

    companion object {
        const val TYPE_VOICE = "voice"
        const val TYPE_VIDEO = "video"
        const val STATUS_RINGING = "ringing"
        const val STATUS_CONNECTED = "connected"
        const val STATUS_ENDED = "ended"
        const val STATUS_DECLINED = "declined"
        const val STATUS_MISSED = "missed"
    }
}
