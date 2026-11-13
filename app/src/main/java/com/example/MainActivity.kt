package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.PulseLiveRepository
import com.example.model.CallSession
import com.example.model.UserPublicProfile
import com.example.ui.auth.AuthScreen
import com.example.ui.calls.ActiveCallRoomScreen
import com.example.ui.calls.CallsTabScreen
import com.example.ui.calls.IncomingCallOverlay
import com.example.ui.chats.ChatRoomScreen
import com.example.ui.chats.ChatsTabScreen
import com.example.ui.components.ProfileSettingsSheet
import com.example.ui.components.RoleBadgePill
import com.example.ui.components.UserAvatar
import com.example.ui.feeds.FeedsTabScreen
import com.example.ui.live.LiveStudioAndRoomScreen
import com.example.ui.live.LiveTabScreen
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.PulseEmerald
import com.example.ui.theme.PulseLiveTheme
import com.example.viewmodel.PulseLiveViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

const val ROUTE_CHATS = "chats"
const val ROUTE_CALLS = "calls"
const val ROUTE_LIVE = "live"
const val ROUTE_FEEDS = "feeds"

private enum class PulseTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHATS(ROUTE_CHATS, R.string.tab_chats, Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    CALLS(ROUTE_CALLS, R.string.tab_calls, Icons.Rounded.Call, Icons.Outlined.Call),
    LIVE(ROUTE_LIVE, R.string.tab_live, Icons.Rounded.Sensors, Icons.Outlined.Sensors),
    FEEDS(ROUTE_FEEDS, R.string.tab_feeds, Icons.Rounded.DynamicFeed, Icons.Outlined.DynamicFeed)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseLiveTheme {
                PulseLiveRoot()
            }
        }
    }
}

@Composable
fun PulseLiveRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
    } else {
        key(user.uid) {
            PulseLiveAuthenticatedShell(
                currentUserId = user.uid,
                initialDisplayName = user.displayName ?: user.email?.substringBefore('@') ?: "Creator"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseLiveAuthenticatedShell(
    currentUserId: String,
    initialDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: PulseLiveViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                PulseLiveViewModel(
                    repository = PulseLiveRepository(app),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName
                )
            }
        }
    )

    val profile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val userDirectory by viewModel.userDirectoryMap.collectAsStateWithLifecycle()
    val chatRoomsState by viewModel.chatRoomsState.collectAsStateWithLifecycle()
    val communityUsersState by viewModel.communityUsersState.collectAsStateWithLifecycle()
    val livestreamsState by viewModel.livestreamsState.collectAsStateWithLifecycle()
    val feedPostsState by viewModel.feedPostsState.collectAsStateWithLifecycle()
    val likedPostIds by viewModel.likedPostIds.collectAsStateWithLifecycle()
    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    val isRefreshingFeeds by viewModel.isRefreshingFeeds.collectAsStateWithLifecycle()

    val activeChatRoom by viewModel.activeChatRoom.collectAsStateWithLifecycle()
    val activeRoomMessagesState by viewModel.activeRoomMessagesState.collectAsStateWithLifecycle()
    val activeStream by viewModel.activeStream.collectAsStateWithLifecycle()
    val activeStreamCommentsState by viewModel.activeStreamCommentsState.collectAsStateWithLifecycle()
    val activeFeedCommentsState by viewModel.activeFeedCommentsState.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val incomingRingingCall by viewModel.incomingRingingCall.collectAsStateWithLifecycle()

    val isVerifiedOwner = viewModel.isVerifiedOwner || profile.isOwner

    var selectedTab by remember { mutableStateOf(PulseTab.CHATS) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showGoLiveSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val openCall = activeCall
        val openStream = activeStream
        val openRoom = activeChatRoom

        when {
            openCall != null -> {
                val otherId = openCall.otherPartyId(currentUserId)
                ActiveCallRoomScreen(
                    call = openCall,
                    currentUserId = currentUserId,
                    otherProfile = userDirectory[otherId],
                    onToggleVideo = { enabled -> viewModel.toggleCallVideo(openCall, enabled) },
                    onEndCall = { elapsed -> viewModel.endActiveCall(openCall, elapsed) }
                )
            }

            openStream != null -> {
                LiveStudioAndRoomScreen(
                    stream = openStream,
                    currentUserId = currentUserId,
                    isVerifiedOwner = isVerifiedOwner,
                    userDirectory = userDirectory,
                    commentsState = activeStreamCommentsState,
                    onLeave = {
                        viewModel.leaveLivestream(openStream.id, isHost = openStream.hostId == currentUserId)
                    },
                    onEndBroadcast = {
                        viewModel.endLivestream(openStream.id, openStream.hostId)
                    },
                    onSendHeart = { viewModel.sendStreamHeart(openStream.id) },
                    onSendComment = { text -> viewModel.postStreamComment(openStream.id, text) }
                )
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp
                    val isDesktopSplit = maxWidth >= 840.dp

                    if (!isDesktopSplit && openRoom != null) {
                        ChatRoomScreen(
                            room = openRoom,
                            currentUserId = currentUserId,
                            isVerifiedOwner = isVerifiedOwner,
                            userDirectory = userDirectory,
                            messagesState = activeRoomMessagesState,
                            desktopPaneMode = false,
                            onBack = { viewModel.selectChatRoom(null) },
                            onSendMessage = { text, imageBase64 ->
                                viewModel.sendChatMessage(openRoom, text, imageBase64)
                            },
                            onGoLiveFromChat = {
                                viewModel.startLivestream(
                                    title = "${openRoom.displayTitleFor(currentUserId)} Live",
                                    category = "Chill",
                                    linkedRoom = openRoom,
                                    onStreamStarted = { /* transitions via activeStream */ }
                                )
                            },
                            onJoinStreamById = { streamId ->
                                viewModel.selectLivestream(streamId, isHost = false)
                            },
                            onStartVoiceCall = {
                                viewModel.startCallFromRoom(openRoom, CallSession.TYPE_VOICE)
                            },
                            onStartVideoCall = {
                                viewModel.startCallFromRoom(openRoom, CallSession.TYPE_VIDEO)
                            }
                        )
                    } else {
                        Scaffold(
                            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                            topBar = {
                                TopAppBar(
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    ),
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.app_name),
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isVerifiedOwner) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                RoleBadgePill(role = UserPublicProfile.ROLE_OWNER, isVerified = true)
                                            }
                                            if (isDesktopSplit) {
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Surface(
                                                    color = PulseEmerald.copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.DesktopWindows,
                                                            contentDescription = null,
                                                            tint = PulseEmerald,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "DESKTOP",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = PulseEmerald,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    actions = {
                                        Box(modifier = Modifier.padding(end = 14.dp)) {
                                            UserAvatar(
                                                displayName = profile.displayName,
                                                colorHex = profile.avatarColorHex,
                                                size = 38.dp,
                                                isLive = profile.isLive,
                                                isVerifiedOwner = isVerifiedOwner || profile.isVerified,
                                                onClick = { showProfileSheet = true }
                                            )
                                        }
                                    }
                                )
                            },
                            bottomBar = {
                                if (!useRail) {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        windowInsets = WindowInsets.navigationBars
                                    ) {
                                        PulseTab.entries.forEach { tab ->
                                            val isSelected = selectedTab == tab
                                            NavigationBarItem(
                                                selected = isSelected,
                                                onClick = { selectedTab = tab },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = stringResource(tab.labelRes)
                                                    )
                                                },
                                                label = { Text(stringResource(tab.labelRes)) },
                                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (useRail) {
                                    NavigationRail(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxHeight()
                                    ) {
                                        Spacer(modifier = Modifier.weight(1f))
                                        PulseTab.entries.forEach { tab ->
                                            val isSelected = selectedTab == tab
                                            NavigationRailItem(
                                                selected = isSelected,
                                                onClick = { selectedTab = tab },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = stringResource(tab.labelRes)
                                                    )
                                                },
                                                label = { Text(stringResource(tab.labelRes)) },
                                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                                            )
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    when (selectedTab) {
                                        PulseTab.CHATS -> {
                                            if (isDesktopSplit) {
                                                Row(modifier = Modifier.fillMaxSize()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(360.dp)
                                                            .fillMaxHeight()
                                                    ) {
                                                        ChatsTabScreen(
                                                            currentUserId = currentUserId,
                                                            chatRoomsState = chatRoomsState,
                                                            communityUsersState = communityUsersState,
                                                            userDirectory = userDirectory,
                                                            livestreamsState = livestreamsState,
                                                            selectedRoomId = openRoom?.id,
                                                            onOpenRoom = { room -> viewModel.selectChatRoom(room) },
                                                            onOpenSystemBot = { viewModel.openSystemBotRoom() },
                                                            onOpenDirectChat = { user ->
                                                                viewModel.openDirectChat(user) { /* activeChatRoom updates */ }
                                                            },
                                                            onCreateGroupOrLounge = { title, members, isPublic ->
                                                                viewModel.createGroupOrLounge(title, members, isPublic) { /* opens */ }
                                                            },
                                                            onWatchStream = { stream ->
                                                                viewModel.selectLivestream(stream.id, isHost = stream.hostId == currentUserId)
                                                            },
                                                            onGoLiveClick = {
                                                                selectedTab = PulseTab.LIVE
                                                                showGoLiveSheet = true
                                                            }
                                                        )
                                                    }

                                                    VerticalDivider(
                                                        thickness = 1.dp,
                                                        color = MaterialTheme.colorScheme.outlineVariant
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight()
                                                    ) {
                                                        if (openRoom != null) {
                                                            ChatRoomScreen(
                                                                room = openRoom,
                                                                currentUserId = currentUserId,
                                                                isVerifiedOwner = isVerifiedOwner,
                                                                userDirectory = userDirectory,
                                                                messagesState = activeRoomMessagesState,
                                                                desktopPaneMode = true,
                                                                onBack = { viewModel.selectChatRoom(null) },
                                                                onSendMessage = { text, imageBase64 ->
                                                                    viewModel.sendChatMessage(openRoom, text, imageBase64)
                                                                },
                                                                onGoLiveFromChat = {
                                                                    viewModel.startLivestream(
                                                                        title = "${openRoom.displayTitleFor(currentUserId)} Live",
                                                                        category = "Chill",
                                                                        linkedRoom = openRoom,
                                                                        onStreamStarted = {}
                                                                    )
                                                                },
                                                                onJoinStreamById = { streamId ->
                                                                    viewModel.selectLivestream(streamId, isHost = false)
                                                                },
                                                                onStartVoiceCall = {
                                                                    viewModel.startCallFromRoom(openRoom, CallSession.TYPE_VOICE)
                                                                },
                                                                onStartVideoCall = {
                                                                    viewModel.startCallFromRoom(openRoom, CallSession.TYPE_VIDEO)
                                                                }
                                                            )
                                                        } else {
                                                            DesktopChatWelcomePane(
                                                                onOpenSystemBot = { viewModel.openSystemBotRoom() },
                                                                onOpenCalls = { selectedTab = PulseTab.CALLS }
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                ChatsTabScreen(
                                                    currentUserId = currentUserId,
                                                    chatRoomsState = chatRoomsState,
                                                    communityUsersState = communityUsersState,
                                                    userDirectory = userDirectory,
                                                    livestreamsState = livestreamsState,
                                                    selectedRoomId = openRoom?.id,
                                                    onOpenRoom = { room -> viewModel.selectChatRoom(room) },
                                                    onOpenSystemBot = { viewModel.openSystemBotRoom() },
                                                    onOpenDirectChat = { user ->
                                                        viewModel.openDirectChat(user) { /* opens room */ }
                                                    },
                                                    onCreateGroupOrLounge = { title, members, isPublic ->
                                                        viewModel.createGroupOrLounge(title, members, isPublic) { /* opens */ }
                                                    },
                                                    onWatchStream = { stream ->
                                                        viewModel.selectLivestream(stream.id, isHost = stream.hostId == currentUserId)
                                                    },
                                                    onGoLiveClick = {
                                                        selectedTab = PulseTab.LIVE
                                                        showGoLiveSheet = true
                                                    }
                                                )
                                            }
                                        }

                                        PulseTab.CALLS -> CallsTabScreen(
                                            currentUserId = currentUserId,
                                            callsState = callsState,
                                            communityUsersState = communityUsersState,
                                            userDirectory = userDirectory,
                                            isDesktopSplit = isDesktopSplit,
                                            onStartUserCall = { user, callType ->
                                                viewModel.startCallWithUser(user, callType)
                                            },
                                            onRedialCall = { call, callType ->
                                                viewModel.redialCall(call, callType)
                                            }
                                        )

                                        PulseTab.LIVE -> LiveTabScreen(
                                            livestreamsState = livestreamsState,
                                            userDirectory = userDirectory,
                                            isVerifiedOwner = isVerifiedOwner,
                                            showGoLiveSheet = showGoLiveSheet,
                                            onShowGoLiveSheet = { showGoLiveSheet = it },
                                            onStartBroadcast = { title, category ->
                                                viewModel.startLivestream(title, category) { /* opens room */ }
                                            },
                                            onJoinStream = { stream ->
                                                viewModel.selectLivestream(stream.id, isHost = stream.hostId == currentUserId)
                                            },
                                            onModeratorEndStream = { stream ->
                                                viewModel.endLivestream(stream.id, stream.hostId)
                                            }
                                        )

                                        PulseTab.FEEDS -> FeedsTabScreen(
                                            currentUserId = currentUserId,
                                            currentUserProfile = profile,
                                            isVerifiedOwner = isVerifiedOwner,
                                            userDirectory = userDirectory,
                                            feedPostsState = feedPostsState,
                                            likedPostIds = likedPostIds,
                                            activeCommentsState = activeFeedCommentsState,
                                            isRefreshing = isRefreshingFeeds,
                                            onRefresh = { viewModel.refreshFeeds() },
                                            onCreatePost = { caption, category, imageBase64, imageUrl ->
                                                viewModel.createFeedPost(caption, category, imageBase64, imageUrl)
                                            },
                                            onPublishOfficialAnnouncement = { text ->
                                                viewModel.publishOfficialAnnouncement(text)
                                            },
                                            onToggleLike = { post, currentlyLiked ->
                                                viewModel.toggleFeedLike(post, currentlyLiked)
                                            },
                                            onOpenComments = { postId ->
                                                viewModel.selectFeedPostForComments(postId)
                                            },
                                            onAddComment = { postId, text ->
                                                viewModel.addFeedComment(postId, text)
                                            },
                                            onDeletePost = { postId ->
                                                viewModel.deleteFeedPost(postId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Global Incoming Call Overlay (renders across all screens when not already in a call)
        val ringingCall = incomingRingingCall
        if (ringingCall != null && openCall == null) {
            IncomingCallOverlay(
                call = ringingCall,
                callerProfile = userDirectory[ringingCall.callerId],
                onAnswer = { viewModel.answerIncomingCall(ringingCall) },
                onDecline = { viewModel.declineIncomingCall(ringingCall) },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (showProfileSheet) {
        ProfileSettingsSheet(
            profile = profile,
            onDismiss = { showProfileSheet = false },
            onSaveProfile = { displayName, bio, statusText ->
                viewModel.updateProfile(displayName, bio, statusText)
            },
            onSignOut = {
                showProfileSheet = false
                signOutUser(credentialManager, scope)
            }
        )
    }
}

@Composable
private fun DesktopChatWelcomePane(
    onOpenSystemBot: () -> Unit,
    onOpenCalls: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp)
            .testTag("desktop_chat_welcome_pane")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(PulseEmerald.copy(alpha = 0.16f))
                    .border(1.5.dp, PulseEmerald.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DesktopWindows,
                    contentDescription = null,
                    tint = PulseEmerald,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "PulseLive for Desktop & Tablet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select any conversation on the left to chat with Enter-to-send, start a Voice or Video Call, or jump into a Live Lounge.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onOpenSystemBot,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PulseEmerald,
                        contentColor = Color(0xFF002116)
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Pulse Bot", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenCalls,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Voice & Video Calls")
                }
            }
        }
    }
}
