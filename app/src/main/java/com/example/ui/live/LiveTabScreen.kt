package com.example.ui.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PulseLiveRepository
import com.example.model.Livestream
import com.example.model.UserPublicProfile
import com.example.ui.components.LiveBadge
import com.example.ui.components.RoleBadgePill
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PulseEmerald
import com.example.util.MediaUtils
import com.example.viewmodel.UiState

@Composable
fun LiveTabScreen(
    livestreamsState: UiState<List<Livestream>>,
    userDirectory: Map<String, UserPublicProfile>,
    isVerifiedOwner: Boolean,
    showGoLiveSheet: Boolean,
    onShowGoLiveSheet: (Boolean) -> Unit,
    onStartBroadcast: (title: String, category: String) -> Unit,
    onJoinStream: (Livestream) -> Unit,
    onModeratorEndStream: (Livestream) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember { listOf("All") + PulseLiveRepository.STREAM_CATEGORIES }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Go Live Banner
            item(key = "live_hero_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(172.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, PulseEmerald.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_live_stage),
                        contentDescription = stringResource(R.string.cd_hero_backdrop),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        ObsidianBg.copy(alpha = 0.92f),
                                        ObsidianBg.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    tint = LiveCoral,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE STUDIO",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PulseEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Stream straight to your community",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = { onShowGoLiveSheet(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiveCoral,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("hero_go_live_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Go Live Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Category Filter Chips
            item(key = "live_categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories, key = { it }) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            when (livestreamsState) {
                is UiState.Loading -> {
                    item(key = "live_loading") {
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
                    item(key = "live_error") {
                        Text(
                            text = livestreamsState.message,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val filtered = livestreamsState.data.filter {
                        selectedCategory == "All" || it.category == selectedCategory
                    }
                    val liveNow = filtered.filter { it.isLiveNow }
                    val ended = filtered.filter { !it.isLiveNow }

                    if (liveNow.isEmpty() && ended.isEmpty()) {
                        item(key = "live_empty") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp)
                            ) {
                                Text(
                                    text = "No broadcasts in this category yet",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap Go Live Now to launch the first broadcast room.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        if (liveNow.isNotEmpty()) {
                            item(key = "header_live_now") {
                                Text(
                                    text = "On Air Now (${liveNow.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(liveNow, key = { "live_${it.id}" }) { stream ->
                                LivestreamCard(
                                    stream = stream,
                                    hostProfile = userDirectory[stream.hostId],
                                    canModerateEnd = isVerifiedOwner && stream.isLiveNow,
                                    onClick = { onJoinStream(stream) },
                                    onModeratorEnd = { onModeratorEndStream(stream) }
                                )
                            }
                        }
                        if (ended.isNotEmpty()) {
                            item(key = "header_recent") {
                                Text(
                                    text = "Recent Broadcasts",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            items(ended.take(10), key = { "ended_${it.id}" }) { stream ->
                                LivestreamCard(
                                    stream = stream,
                                    hostProfile = userDirectory[stream.hostId],
                                    canModerateEnd = false,
                                    onClick = { onJoinStream(stream) },
                                    onModeratorEnd = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onShowGoLiveSheet(true) },
            containerColor = LiveCoral,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .minimumInteractiveComponentSize()
                .testTag("go_live_fab")
        ) {
            Icon(imageVector = Icons.Rounded.Videocam, contentDescription = "Go Live")
        }
    }

    if (showGoLiveSheet) {
        GoLiveSetupSheet(
            onDismiss = { onShowGoLiveSheet(false) },
            onStartBroadcast = { title, category ->
                onShowGoLiveSheet(false)
                onStartBroadcast(title, category)
            }
        )
    }
}

@Composable
private fun LivestreamCard(
    stream: Livestream,
    hostProfile: UserPublicProfile?,
    canModerateEnd: Boolean,
    onClick: () -> Unit,
    onModeratorEnd: () -> Unit
) {
    val isHostVerifiedOwner = hostProfile?.isOwner == true || hostProfile?.isVerified == true
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (stream.isLiveNow) 1.5.dp else 1.dp,
                color = if (stream.isLiveNow) LiveCoral.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("livestream_card_${stream.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    UserAvatar(
                        displayName = stream.hostName,
                        colorHex = if (stream.isLiveNow) "#FF4D6D" else "#00E5A3",
                        size = 44.dp,
                        isLive = stream.isLiveNow,
                        isVerifiedOwner = isHostVerifiedOwner
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stream.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stream.hostName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isHostVerifiedOwner) {
                                Spacer(modifier = Modifier.width(4.dp))
                                VerifiedBadgeIcon(size = 13.dp)
                            }
                            if (hostProfile?.isOwner == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                RoleBadgePill(role = UserPublicProfile.ROLE_OWNER, isVerified = true)
                            }
                            Text(
                                text = " • ${stream.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canModerateEnd) {
                        IconButton(
                            onClick = onModeratorEnd,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.StopCircle,
                                contentDescription = "End stream as Owner",
                                tint = LiveCoral
                            )
                        }
                    }
                    if (stream.isLiveNow) {
                        LiveBadge()
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ENDED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = "Viewers",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${stream.viewerCount}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "Hearts",
                            tint = LiveCoral,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${stream.likeCount}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(
                        text = MediaUtils.formatRelativeTime(stream.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (stream.isLiveNow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = PulseEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Tune In",
                            style = MaterialTheme.typography.labelLarge,
                            color = PulseEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoLiveSetupSheet(
    onDismiss: () -> Unit,
    onStartBroadcast: (title: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Chill") }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Sensors,
                    contentDescription = null,
                    tint = LiveCoral,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Launch Livestream",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 100) title = it },
                label = { Text("Broadcast Title") },
                placeholder = { Text("Late night Q&A, studio session...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("go_live_title_input")
            )

            Text(
                text = "Topic Tag",
                style = MaterialTheme.typography.titleSmall
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PulseLiveRepository.STREAM_CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Button(
                onClick = { onStartBroadcast(title.ifBlank { "Live Studio Session" }, selectedCategory) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiveCoral,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("start_broadcast_confirm_button")
            ) {
                Icon(imageVector = Icons.Rounded.Videocam, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Live Broadcast", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
