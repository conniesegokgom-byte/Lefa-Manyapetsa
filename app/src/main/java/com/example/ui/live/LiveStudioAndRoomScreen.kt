package com.example.ui.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.Livestream
import com.example.model.StreamComment
import com.example.model.UserPublicProfile
import com.example.ui.components.CameraXStagePreview
import com.example.ui.components.LiveBadge
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.components.desktopEnterSubmit
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PulseEmerald
import com.example.ui.theme.SignalAmber
import com.example.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private data class FloatingHeart(val id: Int, val offsetX: Float)

@Composable
fun LiveStudioAndRoomScreen(
    stream: Livestream,
    currentUserId: String,
    isVerifiedOwner: Boolean,
    userDirectory: Map<String, UserPublicProfile>,
    commentsState: UiState<List<StreamComment>>,
    onLeave: () -> Unit,
    onEndBroadcast: () -> Unit,
    onSendHeart: () -> Unit,
    onSendComment: (String) -> Unit
) {
    val isHost = stream.hostId == currentUserId
    val canEndBroadcast = (isHost || isVerifiedOwner) && stream.isLiveNow
    val hostProfile = userDirectory[stream.hostId]
    val isHostVerifiedOwner = hostProfile?.isOwner == true || hostProfile?.isVerified == true

    BackHandler {
        if (isHost && stream.isLiveNow) onEndBroadcast() else onLeave()
    }

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var useFrontCamera by remember { mutableStateOf(true) }
    var isMicMuted by remember { mutableStateOf(false) }
    var cameraAvailable by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true || hasCameraPermission
        hasMicPermission = grants[Manifest.permission.RECORD_AUDIO] == true || hasMicPermission
    }

    LaunchedEffect(isHost) {
        if (isHost && (!hasCameraPermission || !hasMicPermission)) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    var commentText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val comments = (commentsState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size - 1)
        }
    }

    // Floating heart animations
    val hearts = remember { mutableStateListOf<FloatingHeart>() }
    var heartSeed by remember { mutableIntStateOf(0) }
    var lastLikeCount by remember { mutableIntStateOf(stream.likeCount.toInt()) }
    LaunchedEffect(stream.likeCount) {
        if (stream.likeCount.toInt() > lastLikeCount) {
            heartSeed += 1
            hearts.add(FloatingHeart(heartSeed, Random.nextFloat() * 60f - 30f))
        }
        lastLikeCount = stream.likeCount.toInt()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // 1. Broadcast Stage
        if (isHost && stream.isLiveNow && hasCameraPermission && cameraAvailable) {
            CameraXStagePreview(
                useFrontCamera = useFrontCamera,
                onCameraUnavailable = { cameraAvailable = false },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            StudioAmbientStage(
                hostName = stream.hostName,
                isLive = stream.isLiveNow,
                isVerifiedOwner = isHostVerifiedOwner,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gradient readability scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // 2. Top Overlay Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        UserAvatar(
                            displayName = stream.hostName,
                            colorHex = "#FF4D6D",
                            size = 34.dp,
                            isLive = stream.isLiveNow,
                            isVerifiedOwner = isHostVerifiedOwner
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = stream.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stream.hostName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isHostVerifiedOwner) SignalAmber else Color(0xFFB7C5D1),
                                    fontWeight = if (isHostVerifiedOwner) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isHostVerifiedOwner) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerifiedBadgeIcon(size = 13.dp)
                                }
                                Text(
                                    text = " • ${stream.category}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFB7C5D1),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (stream.isLiveNow) {
                        LiveBadge()
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = "Viewers",
                                tint = PulseEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${stream.viewerCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = {
                            if (canEndBroadcast) onEndBroadcast() else onLeave()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (canEndBroadcast) LiveCoral else Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("leave_or_end_stream_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = if (canEndBroadcast) "End Broadcast" else "Leave Stream"
                        )
                    }
                }
            }

            // Host Hardware Controls Row
            if (isHost && stream.isLiveNow) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!hasCameraPermission) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PulseEmerald, contentColor = Color(0xFF002116)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enable Camera", style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (cameraAvailable) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = CircleShape
                        ) {
                            IconButton(
                                onClick = { useFrontCamera = !useFrontCamera },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cameraswitch,
                                    contentDescription = "Flip Camera",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Surface(
                        color = if (isMicMuted) LiveCoral.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.55f),
                        shape = CircleShape
                    ) {
                        IconButton(
                            onClick = { isMicMuted = !isMicMuted },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = if (isMicMuted) "Unmute Mic" else "Mute Mic",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 3. Floating Hearts Layer
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 92.dp)
                .size(width = 80.dp, height = 220.dp)
        ) {
            hearts.forEach { heart ->
                key(heart.id) {
                    FloatingHeartBurst(
                        offsetX = heart.offsetX,
                        onFinished = { hearts.removeAll { it.id == heart.id } }
                    )
                }
            }
        }

        // 4. Bottom Live Chat Overlay + Composer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (!stream.isLiveNow) {
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "This broadcast has ended",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onLeave) {
                            Text("Back to Live Lobby")
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .height(170.dp)
            ) {
                items(comments, key = { it.id }) { comment ->
                    val commenter = userDirectory[comment.authorId]
                    val isCommenterVerified = commenter?.isOwner == true || commenter?.isVerified == true
                    Surface(
                        color = Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = comment.authorName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCommenterVerified) SignalAmber else PulseEmerald,
                                fontWeight = FontWeight.Bold
                            )
                            if (isCommenterVerified) {
                                Spacer(modifier = Modifier.width(3.dp))
                                VerifiedBadgeIcon(size = 12.dp)
                            }
                            Text(
                                text = ": ${comment.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (stream.isLiveNow) {
                val sendLiveComment = {
                    if (commentText.isNotBlank()) {
                        onSendComment(commentText)
                        commentText = ""
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { if (it.length <= 300) commentText = it },
                        placeholder = { Text("Say something live...", color = Color(0xFFB7C5D1)) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Black.copy(alpha = 0.55f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.45f),
                            focusedBorderColor = PulseEmerald,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .desktopEnterSubmit(sendLiveComment)
                            .testTag("stream_comment_input")
                    )

                    FilledIconButton(
                        onClick = sendLiveComment,
                        enabled = commentText.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = PulseEmerald,
                            contentColor = Color(0xFF002116)
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("stream_send_comment_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = "Send live comment")
                    }

                    FilledIconButton(
                        onClick = {
                            heartSeed += 1
                            hearts.add(FloatingHeart(heartSeed, Random.nextFloat() * 50f - 25f))
                            onSendHeart()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = LiveCoral,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("stream_heart_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Favorite, contentDescription = "Send heart")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioAmbientStage(
    hostName: String,
    isLive: Boolean,
    isVerifiedOwner: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "studio_wave")
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )
    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_live_stage),
            contentDescription = "Broadcast stage",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp)
        ) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(138.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .border(2.dp, LiveCoral.copy(alpha = 0.45f), CircleShape)
                )
            }
            UserAvatar(
                displayName = hostName,
                colorHex = if (isVerifiedOwner) "#FFB703" else "#00E5A3",
                size = 100.dp,
                isLive = isLive,
                isVerifiedOwner = isVerifiedOwner
            )
        }
    }
}

@Composable
private fun FloatingHeartBurst(
    offsetX: Float,
    onFinished: () -> Unit
) {
    val yAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        yAnim.animateTo(-200f, animationSpec = tween(1100))
        alphaAnim.animateTo(0f, animationSpec = tween(250))
        delay(50)
        onFinished()
    }
    Icon(
        imageVector = Icons.Rounded.Favorite,
        contentDescription = null,
        tint = LiveCoral,
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), yAnim.value.roundToInt()) }
            .alpha(alphaAnim.value)
            .size(28.dp)
    )
}
