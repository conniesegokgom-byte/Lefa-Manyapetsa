package com.example.ui.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.PulseCallRepository
import com.example.model.CallSession
import com.example.model.UserPublicProfile
import com.example.ui.components.CameraXStagePreview
import com.example.ui.components.RoleBadgePill
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PulseEmerald
import com.example.ui.theme.SignalAmber
import kotlinx.coroutines.delay

@Composable
fun IncomingCallOverlay(
    call: CallSession,
    callerProfile: UserPublicProfile?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCallerVerifiedOwner = callerProfile?.isOwner == true || callerProfile?.isVerified == true
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            color = ObsidianBg.copy(alpha = 0.96f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 10.dp,
            shadowElevation = 14.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .border(1.5.dp, PulseEmerald.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                .testTag("incoming_call_overlay")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        displayName = call.callerName,
                        colorHex = callerProfile?.avatarColorHex ?: "#00E5A3",
                        size = 52.dp,
                        isLive = true,
                        isVerifiedOwner = isCallerVerifiedOwner
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (call.isVideoCall) "Incoming Video Call" else "Incoming Voice Call",
                            style = MaterialTheme.typography.labelMedium,
                            color = PulseEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = call.callerName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isCallerVerifiedOwner) {
                                Spacer(modifier = Modifier.width(6.dp))
                                VerifiedBadgeIcon(size = 16.dp)
                            }
                        }
                        if (callerProfile?.isOwner == true) {
                            Spacer(modifier = Modifier.height(4.dp))
                            RoleBadgePill(role = UserPublicProfile.ROLE_OWNER, isVerified = true)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDecline,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiveCoral,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("incoming_call_decline")
                    ) {
                        Icon(Icons.Rounded.CallEnd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Decline", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onAnswer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PulseEmerald,
                            contentColor = Color(0xFF002116)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("incoming_call_answer")
                    ) {
                        Icon(
                            imageVector = if (call.isVideoCall) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Answer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallRoomScreen(
    call: CallSession,
    currentUserId: String,
    otherProfile: UserPublicProfile?,
    onToggleVideo: (Boolean) -> Unit,
    onEndCall: (elapsedSeconds: Long) -> Unit
) {
    val context = LocalContext.current
    val isCaller = call.callerId == currentUserId
    val otherName = call.otherPartyName(currentUserId)
    val isOtherVerifiedOwner = otherProfile?.isOwner == true || otherProfile?.isVerified == true

    var elapsedSeconds by remember(call.id) { mutableLongStateOf(0L) }
    var isMicMuted by remember(call.id) { mutableStateOf(false) }
    var isLocalVideoOn by remember(call.id) {
        mutableStateOf(
            if (isCaller) call.isVideoEnabledByCaller else (call.isVideoCall || call.isVideoEnabledByCallee)
        )
    }
    var useFrontCamera by remember { mutableStateOf(true) }
    var cameraAvailable by remember { mutableStateOf(true) }
    var isSpeakerOn by remember(call.id) { mutableStateOf(call.isVideoCall) }

    BackHandler {
        onEndCall(elapsedSeconds)
    }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true || hasCameraPermission
        hasMicPermission = grants[Manifest.permission.RECORD_AUDIO] == true || hasMicPermission
    }

    LaunchedEffect(call.id, isLocalVideoOn) {
        val needed = buildList {
            if (!hasMicPermission) add(Manifest.permission.RECORD_AUDIO)
            if (isLocalVideoOn && !hasCameraPermission) add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // Speakerphone AudioManager wiring (safe fallback on PC / emulator)
    DisposableEffect(isSpeakerOn) {
        val audioManager = runCatching {
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }.getOrNull()
        runCatching {
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = isSpeakerOn
        }
        onDispose {
            runCatching {
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = false
            }
        }
    }

    // Ringback tone while outgoing call is ringing
    LaunchedEffect(call.id, call.status) {
        if (call.isRinging && isCaller) {
            val toneGen = runCatching {
                ToneGenerator(AudioManager.STREAM_VOICE_CALL, 45)
            }.getOrNull()
            try {
                while (call.isRinging) {
                    runCatching { toneGen?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000) }
                    delay(2800L)
                }
            } finally {
                runCatching { toneGen?.release() }
            }
        }
    }

    // Connected duration timer
    LaunchedEffect(call.id, call.isConnected) {
        if (call.isConnected) {
            while (true) {
                delay(1000L)
                elapsedSeconds += 1L
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "call_pulse")
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "call_ring_scale"
    )

    val remoteVideoActive = if (isCaller) call.isVideoEnabledByCallee else call.isVideoEnabledByCaller

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("active_call_screen")
    ) {
        // Video Stage or Ambient Studio Backdrop
        if (isLocalVideoOn && hasCameraPermission && cameraAvailable) {
            CameraXStagePreview(
                useFrontCamera = useFrontCamera,
                onCameraUnavailable = { cameraAvailable = false },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                PulseEmerald.copy(alpha = 0.2f),
                                ObsidianBg,
                                Color(0xFF060A0F)
                            )
                        )
                    )
            )
        }

        // Top Call Status Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 14.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (call.isVideoCall || isLocalVideoOn) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = null,
                        tint = PulseEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            call.isRinging -> if (isCaller) "Calling..." else "Connecting..."
                            call.isConnected -> "Connected • ${PulseCallRepository.formatDuration(elapsedSeconds)}"
                            else -> "Call ${call.status}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Center Participant Card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (call.isRinging) {
                    Box(
                        modifier = Modifier
                            .size(144.dp)
                            .scale(ringScale)
                            .clip(CircleShape)
                            .border(2.dp, PulseEmerald.copy(alpha = 0.55f), CircleShape)
                    )
                }
                UserAvatar(
                    displayName = otherName,
                    colorHex = otherProfile?.avatarColorHex ?: "#00E5A3",
                    size = 110.dp,
                    isLive = call.isConnected,
                    isVerifiedOwner = isOtherVerifiedOwner
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = otherName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (isOtherVerifiedOwner) {
                    Spacer(modifier = Modifier.width(8.dp))
                    VerifiedBadgeIcon(size = 20.dp)
                }
            }
            if (otherProfile?.isOwner == true) {
                Spacer(modifier = Modifier.height(6.dp))
                RoleBadgePill(role = UserPublicProfile.ROLE_OWNER, isVerified = true)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    call.isRinging -> "Waiting for $otherName to answer..."
                    remoteVideoActive -> "$otherName's video is active"
                    isLocalVideoOn && !cameraAvailable -> "Audio active (no camera detected on device)"
                    else -> "Encrypted PulseLive Real-Time Audio"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB7C5D1)
            )
        }

        // Bottom Call Controls Dock
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 22.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                // Mic Mute
                FilledIconButton(
                    onClick = { isMicMuted = !isMicMuted },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isMicMuted) SignalAmber else Color.White.copy(alpha = 0.14f),
                        contentColor = if (isMicMuted) Color(0xFF1F1600) else Color.White
                    ),
                    modifier = Modifier
                        .size(54.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("call_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        contentDescription = if (isMicMuted) "Unmute Microphone" else "Mute Microphone"
                    )
                }

                // Video Toggle
                FilledIconButton(
                    onClick = {
                        val next = !isLocalVideoOn
                        isLocalVideoOn = next
                        onToggleVideo(next)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isLocalVideoOn) PulseEmerald else Color.White.copy(alpha = 0.14f),
                        contentColor = if (isLocalVideoOn) Color(0xFF002116) else Color.White
                    ),
                    modifier = Modifier
                        .size(54.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("call_video_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isLocalVideoOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        contentDescription = if (isLocalVideoOn) "Turn Off Camera" else "Turn On Camera"
                    )
                }

                if (isLocalVideoOn && cameraAvailable) {
                    FilledIconButton(
                        onClick = { useFrontCamera = !useFrontCamera },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.14f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("call_flip_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = "Flip Camera"
                        )
                    }
                }

                // Speakerphone Toggle
                FilledIconButton(
                    onClick = { isSpeakerOn = !isSpeakerOn },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isSpeakerOn) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(54.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("call_speaker_button")
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                        contentDescription = if (isSpeakerOn) "Speaker On" else "Earpiece Mode"
                    )
                }

                // End Call
                FilledIconButton(
                    onClick = { onEndCall(elapsedSeconds) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = LiveCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(58.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("end_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "End Call"
                    )
                }
            }
        }
    }
}
