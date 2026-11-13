package com.example.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ChatRoom
import com.example.model.Livestream
import com.example.model.UserPublicProfile
import com.example.ui.components.LiveBadge
import com.example.ui.components.RoleBadgePill
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.PulseEmerald
import com.example.ui.theme.SignalAmber
import com.example.util.MediaUtils
import com.example.viewmodel.UiState

@Composable
fun ChatsTabScreen(
    currentUserId: String,
    chatRoomsState: UiState<List<ChatRoom>>,
    communityUsersState: UiState<List<UserPublicProfile>>,
    userDirectory: Map<String, UserPublicProfile>,
    livestreamsState: UiState<List<Livestream>>,
    selectedRoomId: String? = null,
    onOpenRoom: (ChatRoom) -> Unit,
    onOpenSystemBot: () -> Unit,
    onOpenDirectChat: (UserPublicProfile) -> Unit,
    onCreateGroupOrLounge: (title: String, members: List<UserPublicProfile>, isPublic: Boolean) -> Unit,
    onWatchStream: (Livestream) -> Unit,
    onGoLiveClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showNewChatSheet by remember { mutableStateOf(false) }

    val activeStreams = remember(livestreamsState) {
        (livestreamsState as? UiState.Success)?.data?.filter { it.isLiveNow }.orEmpty()
    }
    val communityUsers = remember(communityUsersState) {
        (communityUsersState as? UiState.Success)?.data.orEmpty()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // 1. Live, System Bot & Status Strip
            item(key = "live_status_strip") {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                    Text(
                        text = "Live & Active Now",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Go Live Quick Circle
                        item(key = "go_live_circle") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(68.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onGoLiveClick)
                                    .padding(vertical = 4.dp)
                                    .testTag("chats_go_live_shortcut")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(PulseEmerald.copy(alpha = 0.28f), LiveCoral.copy(alpha = 0.28f))
                                            )
                                        )
                                        .border(1.5.dp, PulseEmerald, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Sensors,
                                        contentDescription = "Go Live",
                                        tint = PulseEmerald,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = "Go Live",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // System Bot Quick Circle
                        item(key = "system_bot_circle") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onOpenSystemBot)
                                    .padding(vertical = 4.dp)
                                    .testTag("chats_system_bot_shortcut")
                            ) {
                                UserAvatar(
                                    displayName = UserPublicProfile.SYSTEM_BOT_NAME,
                                    colorHex = "#00E5A3",
                                    size = 56.dp,
                                    isBot = true
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = "Pulse Bot",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PulseEmerald,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Active Livestreams
                        items(activeStreams, key = { "stream_${it.id}" }) { stream ->
                            val hostProfile = userDirectory[stream.hostId]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(70.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onWatchStream(stream) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(contentAlignment = Alignment.BottomCenter) {
                                    UserAvatar(
                                        displayName = stream.hostName,
                                        colorHex = "#FF4D6D",
                                        size = 56.dp,
                                        isLive = true,
                                        isVerifiedOwner = hostProfile?.isOwner == true || hostProfile?.isVerified == true
                                    )
                                    Surface(
                                        color = LiveCoral,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stream.hostName,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (hostProfile?.isOwner == true || hostProfile?.isVerified == true) {
                                        Spacer(modifier = Modifier.width(2.dp))
                                        VerifiedBadgeIcon(size = 12.dp)
                                    }
                                }
                            }
                        }

                        // Community members with verified/owner highlights
                        items(
                            communityUsers.filter {
                                it.userId != currentUserId &&
                                    !it.isLive &&
                                    !it.isBot &&
                                    it.userId != UserPublicProfile.SYSTEM_BOT_ID
                            }.take(12),
                            key = { "user_${it.userId}" }
                        ) { user ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenDirectChat(user) }
                                    .padding(vertical = 4.dp)
                            ) {
                                UserAvatar(
                                    displayName = user.displayName,
                                    colorHex = user.avatarColorHex,
                                    size = 56.dp,
                                    isLive = false,
                                    isVerifiedOwner = user.isOwner || user.isVerified
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (user.isOwner || user.isVerified) {
                                        Spacer(modifier = Modifier.width(2.dp))
                                        VerifiedBadgeIcon(size = 12.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Search Bar
            item(key = "chat_search_bar") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search chats, lounges, or PulseLive Bot...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("chats_search_input")
                )
            }

            // 3. Chat Rooms List
            when (chatRoomsState) {
                is UiState.Loading -> {
                    item(key = "chats_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item(key = "chats_error") {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = chatRoomsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val filtered = chatRoomsState.data
                        .filter { room ->
                            val title = room.displayTitleFor(currentUserId)
                            searchQuery.isBlank() ||
                                title.contains(searchQuery, ignoreCase = true) ||
                                room.lastMessageText.contains(searchQuery, ignoreCase = true)
                        }
                        .sortedWith(
                            compareByDescending<ChatRoom> { it.isBotRoom }
                                .thenByDescending { it.isPublic }
                        )

                    if (filtered.isEmpty()) {
                        item(key = "chats_empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddComment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No conversations yet",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap the + button to start a direct chat, message PulseLive Bot, or join a live lounge.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { room ->
                            val otherUid = if (room.roomType == "direct") {
                                room.participantUids.firstOrNull { it != currentUserId }
                            } else null
                            val otherProfile = otherUid?.let { userDirectory[it] }
                            ChatRoomRow(
                                room = room,
                                currentUserId = currentUserId,
                                otherProfile = otherProfile,
                                hasActiveStream = activeStreams.any { it.id == room.linkedStreamId },
                                isSelected = room.id == selectedRoomId,
                                onClick = { onOpenRoom(room) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewChatSheet = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .minimumInteractiveComponentSize()
                .testTag("new_chat_fab")
        ) {
            Icon(imageVector = Icons.Rounded.AddComment, contentDescription = "New Chat")
        }
    }

    if (showNewChatSheet) {
        NewChatModalSheet(
            currentUserId = currentUserId,
            communityUsers = communityUsers,
            onDismiss = { showNewChatSheet = false },
            onOpenSystemBot = {
                showNewChatSheet = false
                onOpenSystemBot()
            },
            onSelectDirectUser = { user ->
                showNewChatSheet = false
                onOpenDirectChat(user)
            },
            onCreateRoom = { title, members, isPublic ->
                showNewChatSheet = false
                onCreateGroupOrLounge(title, members, isPublic)
            }
        )
    }
}

@Composable
private fun ChatRoomRow(
    room: ChatRoom,
    currentUserId: String,
    otherProfile: UserPublicProfile?,
    hasActiveStream: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val displayTitle = room.displayTitleFor(currentUserId)
    val isBot = room.isBotRoom
    val isVerifiedOwnerOther = otherProfile?.isOwner == true || otherProfile?.isVerified == true
    val badgeColor = when {
        isBot -> "#00E5A3"
        isVerifiedOwnerOther -> "#FFB703"
        room.roomType == "lounge" -> "#FFB703"
        room.roomType == "group" -> "#38BDF8"
        else -> "#00E5A3"
    }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f) else Color.Transparent,
        shape = if (isSelected) RoundedCornerShape(14.dp) else RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSelected) Modifier.padding(horizontal = 6.dp) else Modifier)
            .clickable(onClick = onClick)
            .testTag("chat_room_row_${room.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            UserAvatar(
                displayName = displayTitle,
                colorHex = badgeColor,
                size = 52.dp,
                isLive = hasActiveStream,
                isBot = isBot,
                isVerifiedOwner = isVerifiedOwnerOther
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isBot) {
                            Spacer(modifier = Modifier.width(6.dp))
                            RoleBadgePill(role = UserPublicProfile.ROLE_BOT, isVerified = true)
                        } else if (isVerifiedOwnerOther) {
                            Spacer(modifier = Modifier.width(6.dp))
                            VerifiedBadgeIcon(size = 16.dp)
                            if (otherProfile?.isOwner == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                RoleBadgePill(role = UserPublicProfile.ROLE_OWNER, isVerified = true)
                            }
                        } else if (room.isPublic) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = "Public Lounge",
                                tint = SignalAmber,
                                modifier = Modifier.size(15.dp)
                            )
                        } else if (room.roomType == "group") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = "Group Chat",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = MediaUtils.formatRelativeTime(room.updatedAt ?: room.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = room.lastMessageText.ifBlank {
                            when {
                                isBot -> "Type /help, /stats, or /live"
                                room.isPublic -> "Open community lounge — tap to chat"
                                else -> "Tap to start chatting"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasActiveStream) {
                        Spacer(modifier = Modifier.width(8.dp))
                        LiveBadge()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatModalSheet(
    currentUserId: String,
    communityUsers: List<UserPublicProfile>,
    onDismiss: () -> Unit,
    onOpenSystemBot: () -> Unit,
    onSelectDirectUser: (UserPublicProfile) -> Unit,
    onCreateRoom: (title: String, members: List<UserPublicProfile>, isPublic: Boolean) -> Unit
) {
    var modeGroup by remember { mutableStateOf(false) }
    var roomTitle by remember { mutableStateOf("") }
    var isPublicLounge by remember { mutableStateOf(false) }
    val selectedMembers = remember { mutableStateListOf<UserPublicProfile>() }
    val otherUsers = remember(communityUsers, currentUserId) {
        communityUsers.filter {
            it.userId != currentUserId &&
                !it.isBot &&
                it.userId != UserPublicProfile.SYSTEM_BOT_ID
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Start a Conversation",
                style = MaterialTheme.typography.headlineSmall
            )

            Surface(
                color = PulseEmerald.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, PulseEmerald.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenSystemBot)
                    .testTag("sheet_open_system_bot")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = PulseEmerald,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = UserPublicProfile.SYSTEM_BOT_NAME,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            RoleBadgePill(role = UserPublicProfile.ROLE_BOT, isVerified = true)
                        }
                        Text(
                            text = "Run /help, /stats, /live, or /announce commands",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !modeGroup,
                    onClick = { modeGroup = false },
                    label = { Text("Direct Message") }
                )
                FilterChip(
                    selected = modeGroup,
                    onClick = { modeGroup = true },
                    label = { Text("Group / Live Lounge") }
                )
            }

            if (modeGroup) {
                OutlinedTextField(
                    value = roomTitle,
                    onValueChange = { if (it.length <= 80) roomTitle = it },
                    label = { Text("Room or Lounge Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_room_title_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Public Community Lounge", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Anyone on PulseLive can discover and join",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPublicLounge,
                        onCheckedChange = { isPublicLounge = it }
                    )
                }
            }

            if (otherUsers.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (modeGroup) {
                            "Create a public lounge or invite friends once they sign into PulseLive."
                        } else {
                            "No other members have signed in yet. Try chatting with PulseLive Bot above or create a Public Live Lounge!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(otherUsers, key = { it.userId }) { user ->
                        val isChecked = selectedMembers.any { it.userId == user.userId }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (!modeGroup) {
                                        onSelectDirectUser(user)
                                    } else {
                                        if (isChecked) selectedMembers.removeAll { it.userId == user.userId }
                                        else selectedMembers.add(user)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            UserAvatar(
                                displayName = user.displayName,
                                colorHex = user.avatarColorHex,
                                size = 40.dp,
                                isLive = user.isLive,
                                isVerifiedOwner = user.isOwner || user.isVerified
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                    if (user.isOwner || user.isVerified) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        RoleBadgePill(role = user.role, isVerified = user.isVerified)
                                    }
                                }
                                Text(
                                    user.statusText.ifBlank { user.bio },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (modeGroup) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedMembers.add(user)
                                        else selectedMembers.removeAll { it.userId == user.userId }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (modeGroup) {
                Button(
                    onClick = { onCreateRoom(roomTitle, selectedMembers.toList(), isPublicLounge) },
                    enabled = roomTitle.isNotBlank() && (isPublicLounge || selectedMembers.isNotEmpty()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("create_room_submit_button")
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPublicLounge) "Create Public Lounge" else "Create Group Chat")
                }
            }
        }
    }
}
