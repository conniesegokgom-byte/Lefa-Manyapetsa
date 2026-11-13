package com.example.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PulseCallRepository
import com.example.model.CallSession
import com.example.model.UserPublicProfile
import com.example.ui.components.RoleBadgePill
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.PulseEmerald
import com.example.ui.theme.SignalAmber
import com.example.util.MediaUtils
import com.example.viewmodel.UiState

@Composable
fun CallsTabScreen(
    currentUserId: String,
    callsState: UiState<List<CallSession>>,
    communityUsersState: UiState<List<UserPublicProfile>>,
    userDirectory: Map<String, UserPublicProfile>,
    isDesktopSplit: Boolean = false,
    onStartUserCall: (user: UserPublicProfile, callType: String) -> Unit,
    onRedialCall: (call: CallSession, callType: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val callableUsers = remember(communityUsersState, currentUserId, searchQuery) {
        (communityUsersState as? UiState.Success)?.data.orEmpty()
            .filter {
                it.userId != currentUserId &&
                    !it.isBot &&
                    it.userId != UserPublicProfile.SYSTEM_BOT_ID &&
                    (searchQuery.isBlank() || it.displayName.contains(searchQuery, ignoreCase = true))
            }
            .sortedWith(
                compareByDescending<UserPublicProfile> { it.isOwner }
                    .thenByDescending { it.isVerified }
                    .thenBy { it.displayName.lowercase() }
            )
    }

    if (isDesktopSplit) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Pane: Quick Dial Directory
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Quick Dial",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search people to call...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("calls_search_input")
                )

                if (callableUsers.isEmpty()) {
                    Text(
                        text = "Invite teammates or creators to PulseLive to place direct voice & video calls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(callableUsers, key = { it.userId }) { user ->
                            QuickDialUserRow(
                                user = user,
                                onVoiceCall = { onStartUserCall(user, CallSession.TYPE_VOICE) },
                                onVideoCall = { onStartUserCall(user, CallSession.TYPE_VIDEO) }
                            )
                        }
                    }
                }
            }

            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Right Pane: Call History
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    Text(
                        text = "Recent Calls",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    CallHistoryList(
                        currentUserId = currentUserId,
                        callsState = callsState,
                        userDirectory = userDirectory,
                        onRedialCall = onRedialCall
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "calls_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search people or call history...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("calls_search_input")
                )
            }

            item(key = "quick_dial_strip") {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Quick Dial",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    if (callableUsers.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Other members appear here for 1-tap voice & video calls once they sign into PulseLive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(callableUsers.take(15), key = { "dial_${it.userId}" }) { user ->
                                QuickDialCompactCard(
                                    user = user,
                                    onVoiceCall = { onStartUserCall(user, CallSession.TYPE_VOICE) },
                                    onVideoCall = { onStartUserCall(user, CallSession.TYPE_VIDEO) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "call_history_header") {
                Text(
                    text = "Recent Calls",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            when (callsState) {
                is UiState.Loading -> {
                    item(key = "calls_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item(key = "calls_error") {
                        Text(
                            text = callsState.message,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val filteredCalls = callsState.data.filter { call ->
                        searchQuery.isBlank() ||
                            call.otherPartyName(currentUserId).contains(searchQuery, ignoreCase = true)
                    }
                    if (filteredCalls.isEmpty()) {
                        item(key = "calls_empty") {
                            EmptyCallsCard()
                        }
                    } else {
                        items(filteredCalls, key = { it.id }) { call ->
                            CallHistoryRow(
                                call = call,
                                currentUserId = currentUserId,
                                otherProfile = userDirectory[call.otherPartyId(currentUserId)],
                                onRedialVoice = { onRedialCall(call, CallSession.TYPE_VOICE) },
                                onRedialVideo = { onRedialCall(call, CallSession.TYPE_VIDEO) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryList(
    currentUserId: String,
    callsState: UiState<List<CallSession>>,
    userDirectory: Map<String, UserPublicProfile>,
    onRedialCall: (CallSession, String) -> Unit
) {
    when (callsState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Text(
                text = callsState.message,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(20.dp)
            )
        }
        is UiState.Success -> {
            if (callsState.data.isEmpty()) {
                EmptyCallsCard()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(callsState.data, key = { it.id }) { call ->
                        CallHistoryRow(
                            call = call,
                            currentUserId = currentUserId,
                            otherProfile = userDirectory[call.otherPartyId(currentUserId)],
                            onRedialVoice = { onRedialCall(call, CallSession.TYPE_VOICE) },
                            onRedialVideo = { onRedialCall(call, CallSession.TYPE_VIDEO) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCallsCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PulseEmerald.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = null,
                    tint = PulseEmerald,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No calls yet",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the phone or video button on any member above or inside a chat room to start a real-time call.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickDialUserRow(
    user: UserPublicProfile,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    val isVerifiedOwner = user.isOwner || user.isVerified
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            UserAvatar(
                displayName = user.displayName,
                colorHex = user.avatarColorHex,
                size = 42.dp,
                isVerifiedOwner = isVerifiedOwner
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isVerifiedOwner) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadgeIcon(size = 14.dp)
                    }
                }
                Text(
                    text = user.statusText.ifBlank { "Available to call" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onVoiceCall,
                colors = IconButtonDefaults.iconButtonColors(contentColor = PulseEmerald),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quick_voice_call_${user.userId}")
            ) {
                Icon(Icons.Rounded.Call, contentDescription = "Voice Call ${user.displayName}")
            }
            IconButton(
                onClick = onVideoCall,
                colors = IconButtonDefaults.iconButtonColors(contentColor = SignalAmber),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quick_video_call_${user.userId}")
            ) {
                Icon(Icons.Rounded.Videocam, contentDescription = "Video Call ${user.displayName}")
            }
        }
    }
}

@Composable
private fun QuickDialCompactCard(
    user: UserPublicProfile,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    val isVerifiedOwner = user.isOwner || user.isVerified
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(154.dp)
            .border(
                width = 1.dp,
                color = if (user.isOwner) SignalAmber.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            UserAvatar(
                displayName = user.displayName,
                colorHex = user.avatarColorHex,
                size = 46.dp,
                isVerifiedOwner = isVerifiedOwner
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isVerifiedOwner) {
                    Spacer(modifier = Modifier.width(3.dp))
                    VerifiedBadgeIcon(size = 13.dp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onVoiceCall,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = PulseEmerald.copy(alpha = 0.18f),
                        contentColor = PulseEmerald
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("quick_voice_call_${user.userId}")
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = "Voice Call ${user.displayName}")
                }
                IconButton(
                    onClick = onVideoCall,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = SignalAmber.copy(alpha = 0.18f),
                        contentColor = SignalAmber
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("quick_video_call_${user.userId}")
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = "Video Call ${user.displayName}")
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(
    call: CallSession,
    currentUserId: String,
    otherProfile: UserPublicProfile?,
    onRedialVoice: () -> Unit,
    onRedialVideo: () -> Unit
) {
    val isOutgoing = call.callerId == currentUserId
    val isMissedOrDeclined = call.status == CallSession.STATUS_MISSED || call.status == CallSession.STATUS_DECLINED
    val otherName = call.otherPartyName(currentUserId)
    val isOtherVerifiedOwner = otherProfile?.isOwner == true || otherProfile?.isVerified == true

    val directionIcon = when {
        isMissedOrDeclined -> Icons.AutoMirrored.Rounded.CallMissed
        isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
        else -> Icons.AutoMirrored.Rounded.CallReceived
    }
    val directionTint = when {
        isMissedOrDeclined -> LiveCoral
        call.isActive -> PulseEmerald
        isOutgoing -> PulseEmerald
        else -> Color(0xFF38BDF8)
    }

    val statusText = when (call.status) {
        CallSession.STATUS_RINGING -> if (isOutgoing) "Ringing..." else "Incoming ring"
        CallSession.STATUS_CONNECTED -> "Live now"
        CallSession.STATUS_DECLINED -> "Declined"
        CallSession.STATUS_MISSED -> if (isOutgoing) "Unanswered" else "Missed"
        else -> if (call.durationSeconds > 0L) {
            "Ended • ${PulseCallRepository.formatDuration(call.durationSeconds)}"
        } else {
            "Ended"
        }
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (call.isVideoCall) onRedialVideo else onRedialVoice)
            .testTag("call_history_row_${call.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            UserAvatar(
                displayName = otherName,
                colorHex = otherProfile?.avatarColorHex ?: "#00E5A3",
                size = 48.dp,
                isVerifiedOwner = isOtherVerifiedOwner
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = otherName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isMissedOrDeclined) LiveCoral else MaterialTheme.colorScheme.onSurface
                    )
                    if (isOtherVerifiedOwner) {
                        Spacer(modifier = Modifier.width(5.dp))
                        VerifiedBadgeIcon(size = 15.dp)
                    }
                    if (otherProfile?.isOwner == true) {
                        Spacer(modifier = Modifier.width(6.dp))
                        RoleBadgePill(role = UserPublicProfile.ROLE_OWNER, isVerified = true)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = directionTint,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${if (call.isVideoCall) "Video" else "Voice"} • $statusText • ${MediaUtils.formatRelativeTime(call.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRedialVoice,
                colors = IconButtonDefaults.iconButtonColors(contentColor = PulseEmerald),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("redial_voice_${call.id}")
            ) {
                Icon(Icons.Rounded.Call, contentDescription = "Voice call $otherName")
            }
            IconButton(
                onClick = onRedialVideo,
                colors = IconButtonDefaults.iconButtonColors(contentColor = SignalAmber),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("redial_video_${call.id}")
            ) {
                Icon(Icons.Rounded.Videocam, contentDescription = "Video call $otherName")
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
