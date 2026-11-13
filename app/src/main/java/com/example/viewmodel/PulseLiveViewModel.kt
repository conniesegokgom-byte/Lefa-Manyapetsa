package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PulseLiveRepository
import com.example.data.unwrapFirestoreException
import com.example.model.CallSession
import com.example.model.ChatMessage
import com.example.model.ChatRoom
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.Livestream
import com.example.model.StreamComment
import com.example.model.UserPublicProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class PulseLiveViewModel(
    private val repository: PulseLiveRepository,
    val currentUserId: String,
    private val initialDisplayName: String
) : ViewModel() {

    val isVerifiedOwner: Boolean = repository.isCurrentUserVerifiedOwner()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _currentUserProfile = MutableStateFlow(
        UserPublicProfile(
            id = currentUserId,
            userId = currentUserId,
            displayName = if (isVerifiedOwner) UserPublicProfile.OWNER_DISPLAY_NAME else initialDisplayName.ifBlank { "Creator" },
            bio = if (isVerifiedOwner) "CEO & Creator of PulseLive" else "Live on PulseLive",
            statusText = if (isVerifiedOwner) "Official Owner" else "Available",
            avatarColorHex = if (isVerifiedOwner) "#FFB703" else "#00E5A3",
            role = if (isVerifiedOwner) UserPublicProfile.ROLE_OWNER else UserPublicProfile.ROLE_MEMBER,
            isVerified = isVerifiedOwner
        )
    )
    val currentUserProfile: StateFlow<UserPublicProfile> = _currentUserProfile.asStateFlow()

    val communityUsersState: StateFlow<UiState<List<UserPublicProfile>>> =
        repository.observeCommunityUsers()
            .asUiState("community users")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val userDirectoryMap: StateFlow<Map<String, UserPublicProfile>> =
        communityUsersState
            .map { state ->
                val map = ((state as? UiState.Success)?.data.orEmpty())
                    .associateBy { it.userId }
                    .toMutableMap()
                if (!map.containsKey(UserPublicProfile.SYSTEM_BOT_ID)) {
                    map[UserPublicProfile.SYSTEM_BOT_ID] = UserPublicProfile(
                        id = UserPublicProfile.SYSTEM_BOT_ID,
                        userId = UserPublicProfile.SYSTEM_BOT_ID,
                        displayName = UserPublicProfile.SYSTEM_BOT_NAME,
                        bio = "Official System Bot & Community Assistant",
                        statusText = "Always Online • Type /help",
                        avatarColorHex = "#00E5A3",
                        role = UserPublicProfile.ROLE_BOT,
                        isVerified = true
                    )
                }
                map.toMap()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    val chatRoomsState: StateFlow<UiState<List<ChatRoom>>> =
        repository.observeChatRooms()
            .asUiState("chat rooms")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val livestreamsState: StateFlow<UiState<List<Livestream>>> =
        repository.observeLivestreams()
            .asUiState("livestreams")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val feedOrderCounter = AtomicInteger(0)
    private val feedStreamTrigger = MutableStateFlow(0)
    private val _manualFeedSnapshot = MutableStateFlow<Pair<Int, List<FeedPost>>?>(null)

    val feedPostsState: StateFlow<UiState<List<FeedPost>>> = combine(
        feedStreamTrigger
            .flatMapLatest { repository.observeFeedPosts().asUiState("feed posts") }
            .map { state -> feedOrderCounter.incrementAndGet() to state },
        _manualFeedSnapshot
    ) { (liveSeq, liveState), manualSnapshot ->
        if (manualSnapshot != null && manualSnapshot.first > liveSeq) {
            UiState.Success(manualSnapshot.second)
        } else {
            liveState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val likedPostIds: StateFlow<Set<String>> =
        repository.observeUserLikedPostIds()
            .catch { error ->
                val fsErr = error.unwrapFirestoreException()
                if (fsErr == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing liked post IDs", fsErr ?: error)
                emit(emptySet())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    // Active Chat Room Observation
    private val selectedRoomParams = MutableStateFlow<Pair<String, Boolean>?>(null)

    val activeChatRoom: StateFlow<ChatRoom?> = selectedRoomParams
        .flatMapLatest { params ->
            if (params == null) flowOf(null)
            else repository.observeChatRoom(params.first).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeRoomMessagesState: StateFlow<UiState<List<ChatMessage>>> = selectedRoomParams
        .flatMapLatest { params ->
            if (params == null) flowOf(UiState.Success(emptyList()))
            else repository.observeMessages(params.first, params.second).asUiState("messages")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    // Active Livestream Observation
    private val selectedStreamId = MutableStateFlow<String?>(null)

    val activeStream: StateFlow<Livestream?> = selectedStreamId
        .flatMapLatest { streamId ->
            if (streamId == null) flowOf(null)
            else repository.observeLivestream(streamId).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeStreamCommentsState: StateFlow<UiState<List<StreamComment>>> = selectedStreamId
        .flatMapLatest { streamId ->
            if (streamId == null) flowOf(UiState.Success(emptyList()))
            else repository.observeStreamComments(streamId).asUiState("stream comments")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    // Active Feed Post Comments Observation
    private val selectedFeedPostId = MutableStateFlow<String?>(null)

    val activeFeedCommentsState: StateFlow<UiState<List<FeedComment>>> = selectedFeedPostId
        .flatMapLatest { postId ->
            if (postId == null) flowOf(UiState.Success(emptyList()))
            else repository.observeFeedComments(postId).asUiState("post comments")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    // Real-time Calls Observation & Active Call State
    val callsState: StateFlow<UiState<List<CallSession>>> =
        repository.callRepository.observeUserCalls()
            .asUiState("calls")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _activeCallId = MutableStateFlow<String?>(null)
    private val _pendingActiveCall = MutableStateFlow<CallSession?>(null)

    val activeCall: StateFlow<CallSession?> = combine(
        _activeCallId,
        _pendingActiveCall,
        callsState
    ) { activeId, pending, state ->
        if (activeId == null) null
        else {
            val synced = (state as? UiState.Success)?.data?.firstOrNull { it.id == activeId }
            when {
                synced != null && synced.isActive -> synced
                synced != null && !synced.isActive -> null
                else -> pending?.takeIf { it.id == activeId }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val incomingRingingCall: StateFlow<CallSession?> = combine(
        callsState,
        _activeCallId
    ) { state, activeId ->
        (state as? UiState.Success)?.data?.firstOrNull { call ->
            call.isRinging && call.callerId != currentUserId && call.id != activeId
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch {
            val resolvedName = repository.ensureUserProfile(initialDisplayName)
                .onSuccess { profile -> _currentUserProfile.value = profile }
                .onFailure { Log.w(TAG, "Profile init warning", it) }
                .getOrNull()?.displayName ?: _currentUserProfile.value.displayName

            repository.ensureCommunityLounge(resolvedName)
                .onFailure { Log.w(TAG, "Lounge init warning", it) }

            repository.botRepository.ensurePersonalBotRoom(resolvedName)
                .onFailure { Log.w(TAG, "System bot room init warning", it) }
        }

        // Keep local currentUserProfile synced from directory
        viewModelScope.launch {
            communityUsersState.collect { state ->
                if (state is UiState.Success) {
                    state.data.firstOrNull { it.userId == currentUserId }?.let {
                        _currentUserProfile.value = it
                    }
                }
            }
        }

        // Clear active call state if remote party ended or declined
        viewModelScope.launch {
            combine(_activeCallId, callsState) { activeId, state ->
                activeId to (state as? UiState.Success)?.data?.firstOrNull { it.id == activeId }
            }.collect { (activeId, call) ->
                if (activeId != null && call != null && !call.isActive) {
                    _activeCallId.value = null
                    _pendingActiveCall.value = null
                    val label = when (call.status) {
                        CallSession.STATUS_DECLINED -> "Call declined"
                        CallSession.STATUS_MISSED -> "No answer"
                        else -> "Call ended"
                    }
                    _events.tryEmit(label)
                }
            }
        }
    }

    fun selectChatRoom(room: ChatRoom?) {
        selectedRoomParams.value = room?.let { it.id to it.isPublic }
    }

    fun openSystemBotRoom() {
        val existingBotRoom = (chatRoomsState.value as? UiState.Success)
            ?.data?.firstOrNull { it.isBotRoom }
        if (existingBotRoom != null) {
            selectChatRoom(existingBotRoom)
            return
        }
        viewModelScope.launch {
            repository.botRepository.ensurePersonalBotRoom(_currentUserProfile.value.displayName)
                .onSuccess { selectChatRoom(it) }
                .onFailure { _events.tryEmit(it.message ?: "Could not open System Bot") }
        }
    }

    fun selectLivestream(streamId: String?, isHost: Boolean = false) {
        val previousId = selectedStreamId.value
        if (previousId != null && previousId != streamId) {
            viewModelScope.launch { repository.updateViewerPresence(previousId, -1L) }
        }
        selectedStreamId.value = streamId
        if (streamId != null && !isHost) {
            viewModelScope.launch { repository.updateViewerPresence(streamId, 1L) }
        }
    }

    fun leaveLivestream(streamId: String, isHost: Boolean) {
        if (!isHost) {
            viewModelScope.launch { repository.updateViewerPresence(streamId, -1L) }
        }
        selectedStreamId.value = null
    }

    fun selectFeedPostForComments(postId: String?) {
        selectedFeedPostId.value = postId
    }

    fun updateProfile(displayName: String, bio: String, statusText: String) {
        val finalName = if (isVerifiedOwner) UserPublicProfile.OWNER_DISPLAY_NAME else displayName
        viewModelScope.launch {
            repository.updateUserProfile(finalName, bio, statusText)
                .onSuccess { _events.tryEmit("Profile updated") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to update profile") }
        }
    }

    fun openDirectChat(otherUser: UserPublicProfile, onRoomReady: (ChatRoom) -> Unit) {
        viewModelScope.launch {
            repository.createOrOpenDirectChat(_currentUserProfile.value.displayName, otherUser)
                .onSuccess { room ->
                    selectChatRoom(room)
                    onRoomReady(room)
                }
                .onFailure { _events.tryEmit(it.message ?: "Could not open chat") }
        }
    }

    fun createGroupOrLounge(
        title: String,
        members: List<UserPublicProfile>,
        isPublicLounge: Boolean,
        onRoomReady: (ChatRoom) -> Unit
    ) {
        viewModelScope.launch {
            repository.createGroupOrLounge(
                title = title,
                currentUserName = _currentUserProfile.value.displayName,
                selectedMembers = members,
                isPublicLounge = isPublicLounge
            ).onSuccess { room ->
                selectChatRoom(room)
                onRoomReady(room)
            }.onFailure {
                _events.tryEmit(it.message ?: "Could not create chat")
            }
        }
    }

    fun sendChatMessage(
        room: ChatRoom,
        text: String,
        imageBase64: String = ""
    ) {
        viewModelScope.launch {
            if (room.isBotRoom && text.isNotBlank()) {
                repository.botRepository.sendBotCommandAndReply(
                    room = room,
                    senderName = _currentUserProfile.value.displayName,
                    commandText = text
                ).onFailure {
                    _events.tryEmit(it.message ?: "PulseLive Bot could not reply")
                }
            } else {
                val msgType = if (imageBase64.isNotBlank()) "image" else "text"
                repository.sendMessage(
                    room = room,
                    senderName = _currentUserProfile.value.displayName,
                    text = text,
                    messageType = msgType,
                    imageBase64 = imageBase64
                ).onFailure {
                    _events.tryEmit(it.message ?: "Failed to send message")
                }
            }
        }
    }

    fun publishOfficialAnnouncement(text: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.botRepository.publishOfficialAnnouncement(text)
                .onSuccess {
                    _events.tryEmit("Official announcement broadcasted!")
                    onSuccess()
                }
                .onFailure {
                    _events.tryEmit(it.message ?: "Failed to publish announcement")
                }
        }
    }

    fun startLivestream(
        title: String,
        category: String,
        linkedRoom: ChatRoom? = null,
        onStreamStarted: (Livestream) -> Unit
    ) {
        viewModelScope.launch {
            repository.startLivestream(
                hostName = _currentUserProfile.value.displayName,
                title = title,
                category = category,
                linkedRoom = linkedRoom
            ).onSuccess { stream ->
                selectedStreamId.value = stream.id
                onStreamStarted(stream)
            }.onFailure {
                _events.tryEmit(it.message ?: "Could not start livestream")
            }
        }
    }

    fun endLivestream(streamId: String, hostId: String? = null, onEnded: () -> Unit = {}) {
        viewModelScope.launch {
            repository.endLivestream(streamId, hostId)
                .onSuccess {
                    selectedStreamId.value = null
                    _events.tryEmit("Broadcast ended")
                    onEnded()
                }
                .onFailure {
                    _events.tryEmit(it.message ?: "Failed to end stream")
                }
        }
    }

    fun sendStreamHeart(streamId: String) {
        viewModelScope.launch {
            repository.sendStreamHeart(streamId)
                .onFailure { _events.tryEmit("Stream ended or unavailable") }
        }
    }

    fun postStreamComment(streamId: String, text: String) {
        viewModelScope.launch {
            repository.postStreamComment(
                streamId = streamId,
                authorName = _currentUserProfile.value.displayName,
                text = text
            ).onFailure {
                _events.tryEmit(it.message ?: "Failed to send live comment")
            }
        }
    }

    private val _isRefreshingFeeds = MutableStateFlow(false)
    val isRefreshingFeeds: StateFlow<Boolean> = _isRefreshingFeeds.asStateFlow()

    fun refreshFeeds() {
        if (_isRefreshingFeeds.value) return
        viewModelScope.launch {
            _isRefreshingFeeds.value = true
            try {
                if (feedPostsState.value is UiState.Error) {
                    feedStreamTrigger.update { it + 1 }
                }
                repository.refreshFeedPosts()
                    .onSuccess { posts ->
                        _manualFeedSnapshot.value = feedOrderCounter.incrementAndGet() to posts
                    }
                    .onFailure { _events.tryEmit(it.message ?: "Could not refresh feeds") }
            } finally {
                _isRefreshingFeeds.value = false
            }
        }
    }

    fun createFeedPost(
        caption: String,
        category: String,
        imageBase64: String = "",
        imageUrl: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createFeedPost(
                authorName = _currentUserProfile.value.displayName,
                caption = caption,
                category = category,
                imageBase64 = imageBase64,
                imageUrl = imageUrl
            ).onSuccess {
                _events.tryEmit("Published to Feeds")
                onSuccess()
            }.onFailure {
                _events.tryEmit(it.message ?: "Failed to publish post")
            }
        }
    }

    fun toggleFeedLike(post: FeedPost, isCurrentlyLiked: Boolean) {
        viewModelScope.launch {
            repository.toggleFeedLike(post, isCurrentlyLiked)
                .onFailure { _events.tryEmit(it.message ?: "Failed to update reaction") }
        }
    }

    fun addFeedComment(postId: String, text: String) {
        viewModelScope.launch {
            repository.addFeedComment(
                postId = postId,
                authorName = _currentUserProfile.value.displayName,
                text = text
            ).onFailure { _events.tryEmit(it.message ?: "Failed to post comment") }
        }
    }

    fun deleteFeedPost(postId: String) {
        viewModelScope.launch {
            repository.deleteFeedPost(postId)
                .onSuccess { _events.tryEmit("Post deleted") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to delete post") }
        }
    }

    fun startCallWithUser(
        user: UserPublicProfile,
        callType: String,
        roomId: String = ""
    ) {
        if (user.isBot || user.userId == UserPublicProfile.SYSTEM_BOT_ID) {
            _events.tryEmit("PulseLive Bot uses chat commands — calls are for community members")
            return
        }
        viewModelScope.launch {
            repository.callRepository.startCall(
                callerName = _currentUserProfile.value.displayName,
                calleeId = user.userId,
                calleeName = user.displayName,
                participantUids = listOf(currentUserId, user.userId),
                callType = callType,
                roomId = roomId
            ).onSuccess { created ->
                _pendingActiveCall.value = created
                _activeCallId.value = created.id
            }.onFailure {
                _events.tryEmit(it.message ?: "Could not start call")
            }
        }
    }

    fun startCallFromRoom(room: ChatRoom, callType: String) {
        if (room.isBotRoom || room.isPublic) {
            _events.tryEmit("Voice & video calls work in direct or group chats")
            return
        }
        val otherUid = room.participantUids.firstOrNull { it != currentUserId }
        if (otherUid == null || otherUid == UserPublicProfile.SYSTEM_BOT_ID) {
            _events.tryEmit("No callable participant in this room")
            return
        }
        val calleeName = room.displayTitleFor(currentUserId)
        viewModelScope.launch {
            repository.callRepository.startCall(
                callerName = _currentUserProfile.value.displayName,
                calleeId = otherUid,
                calleeName = calleeName,
                participantUids = room.participantUids,
                callType = callType,
                roomId = room.id
            ).onSuccess { created ->
                _pendingActiveCall.value = created
                _activeCallId.value = created.id
            }.onFailure {
                _events.tryEmit(it.message ?: "Could not start call")
            }
        }
    }

    fun redialCall(call: CallSession, callType: String) {
        val otherId = call.otherPartyId(currentUserId)
        val otherName = call.otherPartyName(currentUserId)
        if (otherId.isBlank() || otherId == UserPublicProfile.SYSTEM_BOT_ID) {
            _events.tryEmit("Cannot redial this contact")
            return
        }
        viewModelScope.launch {
            repository.callRepository.startCall(
                callerName = _currentUserProfile.value.displayName,
                calleeId = otherId,
                calleeName = otherName,
                participantUids = call.participantUids.ifEmpty { listOf(currentUserId, otherId) },
                callType = callType,
                roomId = call.roomId
            ).onSuccess { created ->
                _pendingActiveCall.value = created
                _activeCallId.value = created.id
            }.onFailure {
                _events.tryEmit(it.message ?: "Could not redial call")
            }
        }
    }

    fun answerIncomingCall(call: CallSession) {
        _pendingActiveCall.value = call.copy(status = CallSession.STATUS_CONNECTED)
        _activeCallId.value = call.id
        viewModelScope.launch {
            repository.callRepository.answerCall(call)
                .onFailure {
                    _activeCallId.value = null
                    _pendingActiveCall.value = null
                    _events.tryEmit(it.message ?: "Could not answer call")
                }
        }
    }

    fun declineIncomingCall(call: CallSession) {
        val linkedRoom = if (call.roomId.isNotBlank()) {
            (chatRoomsState.value as? UiState.Success)?.data?.firstOrNull { it.id == call.roomId }
        } else null
        viewModelScope.launch {
            repository.callRepository.endOrDeclineCall(
                call = call,
                finalStatus = CallSession.STATUS_DECLINED,
                durationSeconds = 0L,
                actorName = _currentUserProfile.value.displayName,
                linkedRoom = linkedRoom
            ).onFailure {
                _events.tryEmit(it.message ?: "Failed to decline call")
            }
        }
    }

    fun toggleCallVideo(call: CallSession, enabled: Boolean) {
        viewModelScope.launch {
            repository.callRepository.toggleCallVideo(call, enabled)
                .onFailure { _events.tryEmit("Could not update call video") }
        }
    }

    fun endActiveCall(call: CallSession, elapsedSeconds: Long) {
        _activeCallId.value = null
        _pendingActiveCall.value = null
        val finalStatus = when {
            call.isConnected || elapsedSeconds > 0L -> CallSession.STATUS_ENDED
            call.callerId == currentUserId -> CallSession.STATUS_MISSED
            else -> CallSession.STATUS_DECLINED
        }
        val linkedRoom = if (call.roomId.isNotBlank()) {
            (chatRoomsState.value as? UiState.Success)?.data?.firstOrNull { it.id == call.roomId }
        } else null
        viewModelScope.launch {
            repository.callRepository.endOrDeclineCall(
                call = call,
                finalStatus = finalStatus,
                durationSeconds = elapsedSeconds,
                actorName = _currentUserProfile.value.displayName,
                linkedRoom = linkedRoom
            ).onFailure {
                _events.tryEmit(it.message ?: "Failed to close call cleanly")
            }
        }
    }

    private fun <T> Flow<T>.asUiState(label: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }

    private companion object {
        const val TAG = "PulseLiveVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
