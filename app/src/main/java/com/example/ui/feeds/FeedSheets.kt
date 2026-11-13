package com.example.ui.feeds

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.PulseLiveRepository
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.UserPublicProfile
import com.example.ui.components.PostMediaImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.components.desktopEnterSubmit
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PulseEmerald
import com.example.ui.theme.SignalAmber
import com.example.util.MediaUtils
import com.example.viewmodel.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePostSheet(
    isVerifiedOwner: Boolean,
    onDismiss: () -> Unit,
    onPublish: (caption: String, category: String, imageBase64: String, imageUrl: String, asSystemBot: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Update") }
    var imageBase64 by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var showUrlInput by remember { mutableStateOf(false) }
    var broadcastAsSystemBot by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    imageBase64 = MediaUtils.encodeUriToBase64(context, uri).orEmpty()
                    if (imageBase64.isNotBlank()) {
                        imageUrl = ""
                        if (selectedCategory == "Update") selectedCategory = "Photo"
                    }
                }
            }
        }
    )

    val trimmedUrl = imageUrl.trim()
    val hasValidRemoteUrl = trimmedUrl.startsWith("https://", ignoreCase = true) ||
        trimmedUrl.startsWith("http://", ignoreCase = true)
    val hasAttachedMedia = imageBase64.isNotBlank() || hasValidRemoteUrl

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
                text = "Create Feed Post",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 1000) caption = it },
                label = { Text("What's happening?") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("post_caption_input")
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PulseLiveRepository.FEED_CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            if (showUrlInput && imageBase64.isBlank()) {
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { if (it.length <= 2000) imageUrl = it },
                    label = { Text("Image URL (https://...)") },
                    placeholder = { Text("https://images.unsplash.com/...") },
                    singleLine = true,
                    trailingIcon = {
                        if (imageUrl.isNotBlank()) {
                            IconButton(onClick = { imageUrl = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear image URL")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_image_url_input")
                )
            }

            if (isVerifiedOwner && !hasAttachedMedia) {
                Surface(
                    color = SignalAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Broadcast via PulseLive Bot",
                                style = MaterialTheme.typography.titleSmall,
                                color = SignalAmber
                            )
                            Text(
                                text = "Posts to Feeds & PulseLive Lounge as an Official Announcement",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = broadcastAsSystemBot,
                            onCheckedChange = {
                                broadcastAsSystemBot = it
                                if (it) selectedCategory = "Announcement"
                            }
                        )
                    }
                }
            }

            if (hasAttachedMedia) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    PostMediaImage(
                        imageUrl = if (hasValidRemoteUrl) trimmedUrl else "",
                        imageBase64 = imageBase64,
                        contentDescription = "Selected post photo preview",
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = {
                            imageBase64 = ""
                            imageUrl = ""
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove photo", tint = Color.White)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("post_pick_photo_button")
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (imageBase64.isBlank()) "Photo" else "Change")
                }

                OutlinedButton(
                    onClick = { showUrlInput = !showUrlInput },
                    modifier = Modifier
                        .height(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("post_toggle_url_button")
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = "Attach image URL", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("URL")
                }

                Button(
                    onClick = {
                        onPublish(
                            caption,
                            selectedCategory,
                            imageBase64,
                            if (hasValidRemoteUrl) trimmedUrl else "",
                            broadcastAsSystemBot
                        )
                    },
                    enabled = (caption.isNotBlank() || (!broadcastAsSystemBot && hasAttachedMedia)) &&
                        (trimmedUrl.isEmpty() || hasValidRemoteUrl),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (broadcastAsSystemBot) SignalAmber else PulseEmerald,
                        contentColor = Color(0xFF002116)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("publish_post_button")
                ) {
                    Text(
                        text = if (broadcastAsSystemBot) "Broadcast" else "Publish",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedCommentsSheet(
    post: FeedPost,
    userDirectory: Map<String, UserPublicProfile>,
    commentsState: UiState<List<FeedComment>>,
    onDismiss: () -> Unit,
    onSendComment: (String) -> Unit
) {
    var commentInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Comments on ${post.authorName}'s post",
                style = MaterialTheme.typography.titleMedium
            )

            when (commentsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Text(commentsState.message, color = MaterialTheme.colorScheme.secondary)
                }
                is UiState.Success -> {
                    if (commentsState.data.isEmpty()) {
                        Text(
                            text = "No comments yet — start the conversation!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            items(commentsState.data, key = { it.id }) { comment ->
                                val commenter = userDirectory[comment.authorId]
                                val isVerified = commenter?.isOwner == true || commenter?.isVerified == true
                                Row(verticalAlignment = Alignment.Top) {
                                    UserAvatar(
                                        displayName = comment.authorName,
                                        size = 32.dp,
                                        isVerifiedOwner = isVerified
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = comment.authorName,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isVerified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                VerifiedBadgeIcon(size = 13.dp)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = MediaUtils.formatRelativeTime(comment.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = comment.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val sendFeedComment = {
                if (commentInput.isNotBlank()) {
                    onSendComment(commentInput)
                    commentInput = ""
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { if (it.length <= 500) commentInput = it },
                    placeholder = { Text("Add a comment...") },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .desktopEnterSubmit(sendFeedComment)
                        .testTag("feed_comment_input")
                )
                Button(
                    onClick = sendFeedComment,
                    enabled = commentInput.isNotBlank(),
                    modifier = Modifier
                        .height(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("feed_send_comment_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Post comment")
                }
            }
        }
    }
}

@Composable
fun FeedImageLightboxDialog(
    post: FeedPost,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg.copy(alpha = 0.96f))
                .padding(16.dp)
        ) {
            PostMediaImage(
                imageUrl = post.imageUrl,
                imageBase64 = post.imageBase64,
                contentDescription = "Full photo from ${post.authorName}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .minimumInteractiveComponentSize()
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close photo preview",
                    tint = Color.White
                )
            }
            if (post.caption.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.labelLarge,
                            color = PulseEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = post.caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
