package com.example.ui.chats

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ChatMessage
import com.example.model.ChatRoom
import com.example.model.UserPublicProfile
import com.example.ui.components.Base64ImageCard
import com.example.ui.components.LiveBadge
import com.example.ui.components.RoleBadgePill
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.components.desktopEnterSubmit
import com.example.ui.theme.IncomingBubbleDark
import com.example.ui.theme.IncomingBubbleLight
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.OutgoingBubbleDark
import com.example.ui.theme.OutgoingBubbleLight
import com.example.ui.theme.PulseEmerald
import com.example.ui.theme.SignalAmber
import com.example.util.MediaUtils
import com.example.viewmodel.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    room: ChatRoom,
    currentUserId: String,
    isVerifiedOwner: Boolean,
    userDirectory: Map<String, UserPublicProfile>,
    messagesState: UiState<List<ChatMessage>>,
    desktopPaneMode: Boolean = false,
    onBack: () -> Unit,
    onSendMessage: (text: String, imageBase64: String) -> Unit,
    onGoLiveFromChat: () -> Unit,
    onJoinStreamById: (String) -> Unit,
    onStartVoiceCall: () -> Unit = {},
    onStartVideoCall: () -> Unit = {}
) {
    if (!desktopPaneMode) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messageInput by remember { mutableStateOf("") }
    var pendingImageBase64 by remember { mutableStateOf("") }

    val otherUid = remember(room, currentUserId) {
        if (room.roomType == "direct") room.participantUids.firstOrNull { it != currentUserId } else null
    }
    val otherProfile = otherUid?.let { userDirectory[it] }
    val isOtherVerifiedOwner = otherProfile?.isOwner == true || otherProfile?.isVerified == true

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    pendingImageBase64 = MediaUtils.encodeUriToBase64(context, uri).orEmpty()
                }
            }
        }
    )

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    if (!desktopPaneMode) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to Chats"
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            displayName = room.displayTitleFor(currentUserId),
                            colorHex = when {
                                room.isBotRoom -> "#00E5A3"
                                isOtherVerifiedOwner || room.isPublic -> "#FFB703"
                                else -> "#00E5A3"
                            },
                            size = 38.dp,
                            isBot = room.isBotRoom,
                            isVerifiedOwner = isOtherVerifiedOwner
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = room.displayTitleFor(currentUserId),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (room.isBotRoom) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    VerifiedBadgeIcon(isBot = true, size = 16.dp)
                                } else if (isOtherVerifiedOwner) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    VerifiedBadgeIcon(size = 16.dp)
                                }
                            }
                            Text(
                                text = when (room.roomType) {
                                    "bot" -> "Official System Bot • Type /help"
                                    "lounge" -> "Public Live Lounge"
                                    "group" -> "${room.participantUids.size} members"
                                    else -> if (otherProfile?.isOwner == true) "Verified CEO • Owner" else "Direct Chat"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (!room.isBotRoom && !room.isPublic) {
                        IconButton(
                            onClick = onStartVoiceCall,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = PulseEmerald),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_voice_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Voice Call"
                            )
                        }
                        IconButton(
                            onClick = onStartVideoCall,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = SignalAmber),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_video_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Video Call"
                            )
                        }
                    }
                    if (!room.isBotRoom) {
                        FilledTonalButton(
                            onClick = onGoLiveFromChat,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = LiveCoral.copy(alpha = 0.18f),
                                contentColor = LiveCoral
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("chat_go_live_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sensors,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Go Live", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (room.isBotRoom) {
                        val quickCommands = remember(isVerifiedOwner) {
                            buildList {
                                add("/help")
                                add("/stats")
                                add("/live")
                                add("/tips")
                                if (isVerifiedOwner) add("/announce ")
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            items(quickCommands, key = { it }) { cmd ->
                                AssistChip(
                                    onClick = {
                                        if (cmd.startsWith("/announce")) {
                                            messageInput = "/announce "
                                        } else {
                                            onSendMessage(cmd, "")
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = cmd.trim(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    modifier = Modifier.testTag("bot_quick_${cmd.trim().removePrefix("/")}")
                                )
                            }
                        }
                    }

                    if (pendingImageBase64.isNotBlank() && !room.isBotRoom) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .size(92.dp)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            Base64ImageCard(
                                base64 = pendingImageBase64,
                                contentDescription = "Selected photo attachment",
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { pendingImageBase64 = "" },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!room.isBotRoom) {
                            IconButton(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_attach_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = "Attach photo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        val sendCurrentMessage = {
                            val textToSend = messageInput
                            val imageToSend = if (room.isBotRoom) "" else pendingImageBase64
                            if (textToSend.isNotBlank() || imageToSend.isNotBlank()) {
                                onSendMessage(textToSend, imageToSend)
                                messageInput = ""
                                pendingImageBase64 = ""
                            }
                        }

                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { if (it.length <= 1000) messageInput = it },
                            placeholder = {
                                Text(
                                    if (room.isBotRoom) "Enter command (/help, /stats, /live)..."
                                    else "Message..."
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .desktopEnterSubmit(sendCurrentMessage)
                                .testTag("chat_message_input")
                        )

                        FilledIconButton(
                            onClick = sendCurrentMessage,
                            enabled = messageInput.isNotBlank() || (!room.isBotRoom && pendingImageBase64.isNotBlank()),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = PulseEmerald,
                                contentColor = Color(0xFF002116)
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send message"
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (messagesState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Text(
                        text = messagesState.message,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp)
                        ) {
                            Text(
                                text = if (room.isBotRoom) "PulseLive System Bot Ready" else "Say hello!",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (room.isBotRoom) {
                                    "Tap a command chip below or type /help to explore community stats and tools."
                                } else {
                                    "Send a text, share a photo, or tap Go Live to invite everyone here to your broadcast."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val senderProfile = userDirectory[msg.senderId]
                                ChatBubbleItem(
                                    message = msg,
                                    isMine = msg.senderId == currentUserId,
                                    senderProfile = senderProfile,
                                    onWatchLive = { streamId -> onJoinStreamById(streamId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    isMine: Boolean,
    senderProfile: UserPublicProfile?,
    onWatchLive: (String) -> Unit
) {
    if (message.messageType == "call_log") {
        val isVideoLog = message.text.contains("Video", ignoreCase = true)
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(
                    1.dp,
                    PulseEmerald.copy(alpha = 0.35f),
                    RoundedCornerShape(16.dp)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isVideoLog) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = null,
                        tint = PulseEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = message.text.ifBlank { "Call update" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = MediaUtils.formatClockTime(message.createdAt).ifBlank { "Now" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val isDark = isSystemInDarkTheme()
    val isAnnouncement = message.messageType == "bot_announcement"
    val isBotMsg = message.isBotMessage
    val isSenderOwner = senderProfile?.isOwner == true
    val isSenderVerified = isBotMsg || isSenderOwner || senderProfile?.isVerified == true

    val bubbleColor = when {
        isAnnouncement -> SignalAmber.copy(alpha = if (isDark) 0.18f else 0.16f)
        isBotMsg -> PulseEmerald.copy(alpha = if (isDark) 0.14f else 0.12f)
        isMine && isDark -> OutgoingBubbleDark
        isMine && !isDark -> OutgoingBubbleLight
        !isMine && isDark -> IncomingBubbleDark
        else -> IncomingBubbleLight
    }
    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }
    val borderMod = when {
        isAnnouncement -> Modifier.border(1.dp, SignalAmber.copy(alpha = 0.55f), bubbleShape)
        isBotMsg -> Modifier.border(1.dp, PulseEmerald.copy(alpha = 0.35f), bubbleShape)
        else -> Modifier
    }

    Row(
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            tonalElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .then(borderMod)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isAnnouncement) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = null,
                            tint = SignalAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "OFFICIAL ANNOUNCEMENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (!isMine || isBotMsg) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSenderOwner) SignalAmber else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSenderVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadgeIcon(isBot = isBotMsg, size = 14.dp)
                        }
                        if (isSenderOwner) {
                            Spacer(modifier = Modifier.width(6.dp))
                            RoleBadgePill(role = UserPublicProfile.ROLE_OWNER, isVerified = true)
                        } else if (isBotMsg) {
                            Spacer(modifier = Modifier.width(6.dp))
                            RoleBadgePill(role = UserPublicProfile.ROLE_BOT, isVerified = true)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (message.messageType == "live_invite" && message.linkedStreamId.isNotBlank()) {
                    LiveBadge()
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message.text.ifBlank { "Live Broadcast Started" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onWatchLive(message.linkedStreamId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiveCoral,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Watch Live")
                    }
                } else {
                    if (message.imageBase64.isNotBlank()) {
                        Base64ImageCard(
                            base64 = message.imageBase64,
                            contentDescription = "Chat photo from ${message.senderName}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        if (message.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = MediaUtils.formatClockTime(message.createdAt).ifBlank { "Just now" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isMine && !isBotMsg) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = "Delivered",
                            tint = PulseEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
