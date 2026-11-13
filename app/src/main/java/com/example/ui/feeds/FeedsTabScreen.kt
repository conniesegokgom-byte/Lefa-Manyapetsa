package com.example.ui.feeds

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PulseLiveRepository
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.UserPublicProfile
import com.example.ui.components.PostMediaImage
import com.example.ui.components.RoleBadgePill
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadgeIcon
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.PulseEmerald
import com.example.ui.theme.SignalAmber
import com.example.util.MediaUtils
import com.example.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsTabScreen(
    currentUserId: String,
    currentUserProfile: UserPublicProfile,
    isVerifiedOwner: Boolean,
    userDirectory: Map<String, UserPublicProfile>,
    feedPostsState: UiState<List<FeedPost>>,
    likedPostIds: Set<String>,
    activeCommentsState: UiState<List<FeedComment>>,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onCreatePost: (caption: String, category: String, imageBase64: String, imageUrl: String) -> Unit,
    onPublishOfficialAnnouncement: (String) -> Unit,
    onToggleLike: (FeedPost, Boolean) -> Unit,
    onOpenComments: (String?) -> Unit,
    onAddComment: (postId: String, text: String) -> Unit,
    onDeletePost: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showComposerSheet by remember { mutableStateOf(false) }
    var commentSheetPost by remember { mutableStateOf<FeedPost?>(null) }
    var lightboxPost by remember { mutableStateOf<FeedPost?>(null) }
    val categories = remember { listOf("All") + PulseLiveRepository.FEED_CATEGORIES }
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                color = PulseEmerald,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("feeds_pull_to_refresh")
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxSize()
                    .testTag("feeds_lazy_column"),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Composer Bar
                item(key = "feed_quick_composer") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showComposerSheet = true }
                            .testTag("feed_quick_composer")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            UserAvatar(
                                displayName = currentUserProfile.displayName,
                                colorHex = currentUserProfile.avatarColorHex,
                                size = 42.dp,
                                isVerifiedOwner = isVerifiedOwner || currentUserProfile.isVerified
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isVerifiedOwner) {
                                    "Share an OFFICIAL update or photo..."
                                } else {
                                    "Share a feed update or photo..."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isVerifiedOwner) Icons.Rounded.Campaign else Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Create post",
                                tint = if (isVerifiedOwner) SignalAmber else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Search Posts & Accessible Refresh Action (for desktop/mouse users)
                item(key = "feed_search_bar") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search posts, authors, or topics...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("feeds_search_input")
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            IconButton(
                                onClick = onRefresh,
                                enabled = !isRefreshing,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("feeds_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh feeds",
                                    tint = if (isRefreshing) MaterialTheme.colorScheme.onSurfaceVariant else PulseEmerald
                                )
                            }
                        }
                    }
                }

                // Category Filter Chips
                item(key = "feed_categories") {
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

                when (feedPostsState) {
                    is UiState.Loading -> {
                        item(key = "feed_loading") {
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
                        item(key = "feed_error") {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(20.dp)
                                ) {
                                    Text(
                                        text = feedPostsState.message,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(onClick = onRefresh) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                    is UiState.Success -> {
                        val filtered = feedPostsState.data.filter { post ->
                            val matchesCat = selectedCategory == "All" || post.category == selectedCategory
                            val matchesQuery = searchQuery.isBlank() ||
                                post.caption.contains(searchQuery, ignoreCase = true) ||
                                post.authorName.contains(searchQuery, ignoreCase = true) ||
                                post.category.contains(searchQuery, ignoreCase = true)
                            matchesCat && matchesQuery
                        }
                        if (filtered.isEmpty()) {
                            item(key = "feed_empty") {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_empty_feed_art),
                                        contentDescription = stringResource(R.string.cd_empty_feed),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No matching posts" else "No feed posts yet",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Publish a text update, photo, or image post to the community feed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = { showComposerSheet = true }) {
                                        Text("Create First Post")
                                    }
                                }
                            }
                        } else {
                            items(filtered, key = { it.id }) { post ->
                                val isLiked = post.id in likedPostIds
                                val authorProfile = userDirectory[post.authorId]
                                FeedPostCard(
                                    post = post,
                                    authorProfile = authorProfile,
                                    isLiked = isLiked,
                                    canDelete = post.authorId == currentUserId || isVerifiedOwner,
                                    onToggleLike = { onToggleLike(post, isLiked) },
                                    onOpenComments = {
                                        onOpenComments(post.id)
                                        commentSheetPost = post
                                    },
                                    onImageClick = { lightboxPost = post },
                                    onDelete = { onDeletePost(post.id) }
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showComposerSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("new_post_fab")
            ) {
                Icon(imageVector = Icons.Rounded.EditNote, contentDescription = "New Post")
            }
        }
    }

    if (showComposerSheet) {
        CreatePostSheet(
            isVerifiedOwner = isVerifiedOwner,
            onDismiss = { showComposerSheet = false },
            onPublish = { caption, category, imageBase64, imageUrl, asSystemBot ->
                showComposerSheet = false
                if (asSystemBot && isVerifiedOwner && imageBase64.isBlank() && imageUrl.isBlank()) {
                    onPublishOfficialAnnouncement(caption)
                } else {
                    onCreatePost(caption, category, imageBase64, imageUrl)
                }
            }
        )
    }

    commentSheetPost?.let { post ->
        FeedCommentsSheet(
            post = post,
            userDirectory = userDirectory,
            commentsState = activeCommentsState,
            onDismiss = {
                commentSheetPost = null
                onOpenComments(null)
            },
            onSendComment = { text -> onAddComment(post.id, text) }
        )
    }

    lightboxPost?.let { post ->
        FeedImageLightboxDialog(
            post = post,
            onDismiss = { lightboxPost = null }
        )
    }
}

@Composable
private fun FeedPostCard(
    post: FeedPost,
    authorProfile: UserPublicProfile?,
    isLiked: Boolean,
    canDelete: Boolean,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onImageClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isBotAuthor = post.authorId == UserPublicProfile.SYSTEM_BOT_ID || authorProfile?.isBot == true
    val isOwnerAuthor = authorProfile?.isOwner == true
    val isVerifiedAuthor = isBotAuthor || isOwnerAuthor || authorProfile?.isVerified == true
    val isAnnouncement = post.category == "Announcement" || isBotAuthor

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(
                width = if (isAnnouncement) 1.5.dp else 1.dp,
                color = if (isAnnouncement) SignalAmber.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("feed_post_card_${post.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    UserAvatar(
                        displayName = post.authorName,
                        colorHex = if (isOwnerAuthor) "#FFB703" else "#00E5A3",
                        size = 42.dp,
                        isBot = isBotAuthor,
                        isVerifiedOwner = isVerifiedAuthor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (isVerifiedAuthor) {
                                Spacer(modifier = Modifier.width(5.dp))
                                VerifiedBadgeIcon(isBot = isBotAuthor, size = 15.dp)
                            }
                            if (isOwnerAuthor) {
                                Spacer(modifier = Modifier.width(6.dp))
                                RoleBadgePill(role = UserPublicProfile.ROLE_OWNER, isVerified = true)
                            } else if (isBotAuthor) {
                                Spacer(modifier = Modifier.width(6.dp))
                                RoleBadgePill(role = UserPublicProfile.ROLE_BOT, isVerified = true)
                            }
                        }
                        Text(
                            text = "${post.category} • ${MediaUtils.formatRelativeTime(post.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete post",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (post.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (post.hasImage) {
                Spacer(modifier = Modifier.height(12.dp))
                PostMediaImage(
                    imageUrl = post.imageUrl,
                    imageBase64 = post.imageBase64,
                    contentDescription = "Photo posted by ${post.authorName}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onImageClick)
                        .testTag("feed_post_image_${post.id}")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleLike)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("feed_like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike post" else "Like post",
                        tint = if (isLiked) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${post.likeCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isLiked) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenComments)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("feed_comment_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${post.commentCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
