package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.UserPublicProfile
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.PulseEmerald
import com.example.ui.theme.SignalAmber
import com.example.util.MediaUtils

fun Modifier.desktopEnterSubmit(onSubmit: () -> Unit): Modifier =
    this.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
            !event.isShiftPressed
        ) {
            onSubmit()
            true
        } else {
            false
        }
    }

@Composable
fun UserAvatar(
    displayName: String,
    colorHex: String = "#00E5A3",
    size: Dp = 44.dp,
    isLive: Boolean = false,
    isBot: Boolean = false,
    isVerifiedOwner: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val accent = remember(colorHex, isVerifiedOwner, isBot) {
        when {
            isVerifiedOwner -> SignalAmber
            isBot -> PulseEmerald
            else -> MediaUtils.parseHexColor(colorHex, PulseEmerald)
        }
    }
    val initials = remember(displayName) {
        displayName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "P" }
    }

    val ringModifier = when {
        isLive -> Modifier.border(2.5.dp, LiveCoral, CircleShape)
        isVerifiedOwner -> Modifier.border(2.dp, SignalAmber, CircleShape)
        else -> Modifier.border(1.dp, accent.copy(alpha = 0.45f), CircleShape)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.2f))
                .then(ringModifier)
        ) {
            if (isBot) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = "System Bot",
                    tint = PulseEmerald,
                    modifier = Modifier.size(size * 0.54f)
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(LiveCoral)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        } else if (isVerifiedOwner || isBot) {
            Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = if (isBot) "Verified Bot" else "Verified Owner",
                tint = if (isBot) PulseEmerald else SignalAmber,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.34f).coerceAtLeast(14.dp))
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun VerifiedBadgeIcon(
    isBot: Boolean = false,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.Rounded.Verified,
        contentDescription = if (isBot) "Verified System Bot" else "Verified Official Owner",
        tint = if (isBot) PulseEmerald else SignalAmber,
        modifier = modifier.size(size)
    )
}

@Composable
fun RoleBadgePill(
    role: String,
    isVerified: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVerified && role == UserPublicProfile.ROLE_MEMBER) return
    val isBot = role == UserPublicProfile.ROLE_BOT
    val isOwner = role == UserPublicProfile.ROLE_OWNER
    val container = when {
        isOwner -> SignalAmber.copy(alpha = 0.18f)
        isBot -> PulseEmerald.copy(alpha = 0.18f)
        else -> SignalAmber.copy(alpha = 0.14f)
    }
    val contentColor = if (isBot) PulseEmerald else SignalAmber
    val label = when {
        isOwner -> "CEO • OWNER"
        isBot -> "SYSTEM BOT"
        else -> "VERIFIED"
    }

    Surface(
        color = container,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, contentColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = if (isBot) Icons.Rounded.SmartToy else Icons.Rounded.Verified,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Surface(
        color = LiveCoral,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun Base64ImageCard(
    base64: String,
    contentDescription: String,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(base64) { MediaUtils.decodeBase64ToImageBitmap(base64) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

@Composable
fun RemoteImageCard(
    imageUrl: String,
    contentDescription: String,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = remember(imageUrl) {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build()
        },
        contentDescription = contentDescription,
        contentScale = contentScale,
        loading = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        error = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Unable to load image",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = modifier
    )
}

@Composable
fun PostMediaImage(
    imageUrl: String,
    imageBase64: String,
    contentDescription: String,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier
) {
    when {
        imageBase64.isNotBlank() -> Base64ImageCard(
            base64 = imageBase64,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
        imageUrl.isNotBlank() -> RemoteImageCard(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsSheet(
    profile: UserPublicProfile,
    onDismiss: () -> Unit,
    onSaveProfile: (displayName: String, bio: String, statusText: String) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var statusText by remember(profile) { mutableStateOf(profile.statusText) }
    var bio by remember(profile) { mutableStateOf(profile.bio) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                UserAvatar(
                    displayName = displayName.ifBlank { profile.displayName },
                    colorHex = profile.avatarColorHex,
                    size = 56.dp,
                    isLive = profile.isLive,
                    isBot = profile.isBot,
                    isVerifiedOwner = profile.isOwner || profile.isVerified
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName.ifBlank { "Creator" },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (profile.isVerified || profile.isOwner) {
                            Spacer(modifier = Modifier.width(6.dp))
                            VerifiedBadgeIcon(isBot = profile.isBot, size = 20.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    if (profile.isOwner || profile.isVerified) {
                        RoleBadgePill(role = profile.role, isVerified = profile.isVerified)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = statusText.ifBlank { "Available on PulseLive" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (profile.isOwner) {
                Surface(
                    color = SignalAmber.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SignalAmber.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                ) {
                    Text(
                        text = "Verified CEO & Creator (OFFICIAL) — You can moderate broadcasts/posts and use /announce in PulseLive Bot to publish official updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = if (profile.isOwner) UserPublicProfile.OWNER_DISPLAY_NAME else displayName,
                onValueChange = { if (!profile.isOwner && it.length <= 50) displayName = it },
                enabled = !profile.isOwner,
                label = {
                    Text(
                        if (profile.isOwner) "Verified Owner Handle (Locked)" else "Display Name"
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_name_input")
            )

            OutlinedTextField(
                value = statusText,
                onValueChange = { if (it.length <= 80) statusText = it },
                label = { Text("Status Line (WhatsApp-style)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 180) bio = it },
                label = { Text("Creator Bio") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out")
                }

                Button(
                    onClick = {
                        onSaveProfile(displayName, bio, statusText)
                        onDismiss()
                    },
                    enabled = displayName.isNotBlank() || profile.isOwner,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("save_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save")
                }
            }
        }
    }
}
