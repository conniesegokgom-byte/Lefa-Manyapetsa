package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.UserPublicProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class PulseLiveRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun profileAndCommunityDirectory_createAndObserve_succeedsForSignedInUser() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = PulseLiveRepository(firestore, auth)

        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Alice") }
        assertTrue("Profile creation failed: ${profileRes.exceptionOrNull()}", profileRes.isSuccess)
        val profile = profileRes.getOrThrow()
        assertEquals(aliceUid, profile.userId)
        assertEquals(UserPublicProfile.ROLE_MEMBER, profile.role)
        assertFalse(profile.isVerified)

        val users = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCommunityUsers().first { list -> list.any { it.userId == aliceUid } }
        }
        assertTrue(users.any { it.userId == aliceUid && it.displayName == "Alice" })
    }

    @Test
    fun systemBotRoom_ensureAndSlashCommands_repliesInsidePersonalBotRoom() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = PulseLiveRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Alice").getOrThrow() }

        val botRoomRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.botRepository.ensurePersonalBotRoom("Alice") }
        assertTrue("Bot room creation failed: ${botRoomRes.exceptionOrNull()}", botRoomRes.isSuccess)
        val botRoom = botRoomRes.getOrThrow()
        assertEquals("bot_$aliceUid", botRoom.id)
        assertEquals("bot", botRoom.roomType)

        val replyRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.botRepository.sendBotCommandAndReply(botRoom, "Alice", "/help")
        }
        assertTrue("Bot command reply failed: ${replyRes.exceptionOrNull()}", replyRes.isSuccess)

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMessages(botRoom.id, isPublic = false).first { list ->
                list.any { it.senderId == UserPublicProfile.SYSTEM_BOT_ID && it.messageType == "bot_reply" }
            }
        }
        assertTrue(messages.any { it.text == "/help" && it.senderId == aliceUid })
        assertTrue(messages.any { it.isBotMessage && it.text.contains("/stats") })
    }

    @Test
    fun directChatAndMessages_privateIsolation_enforcesParticipantRules() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = PulseLiveRepository(firestore, auth)
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.ensureUserProfile("Alice").getOrThrow() }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = PulseLiveRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.ensureUserProfile("Bob").getOrThrow() }

        val roomRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.createOrOpenDirectChat("Bob", aliceProfile)
        }
        assertTrue("Create DM failed: ${roomRes.exceptionOrNull()}", roomRes.isSuccess)
        val room = roomRes.getOrThrow()

        val msgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.sendMessage(room, "Bob", "Hey Alice, stream starting!")
        }
        assertTrue("Send message failed: ${msgRes.exceptionOrNull()}", msgRes.isSuccess)

        val bobMessages = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeMessages(room.id, room.isPublic).first { it.isNotEmpty() }
        }
        assertTrue(bobMessages.any { it.text == "Hey Alice, stream starting!" && it.senderId == bobUid })

        // Charlie (non-participant) cannot read Alice & Bob's private room
        signInTestUser(CHARLIE_EMAIL)
        val charlieRepo = PulseLiveRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                charlieRepo.observeChatRoom(room.id).first()
            }
            fail("Expected PERMISSION_DENIED for non-participant")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    @Test
    fun livestreamAndFeed_lifecycleAndAtomicLikes_workEndToEnd() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = PulseLiveRepository(firestore, auth)

        val streamTitle = "Live Jam ${UUID.randomUUID().toString().take(6)}"
        val stream = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.startLivestream("Alice", streamTitle, "Music").getOrThrow()
        }
        assertEquals("live", stream.status)

        // Bob joins stream, reacts with heart, and posts live comment
        signInTestUser(BOB_EMAIL)
        val bobRepo = PulseLiveRepository(firestore, auth)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.sendStreamHeart(stream.id) }.isSuccess)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.postStreamComment(stream.id, "Bob", "Fire set!").isSuccess })

        val liveComments = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeStreamComments(stream.id).first { it.isNotEmpty() }
        }
        assertTrue(liveComments.any { it.text == "Fire set!" })

        // Alice ends stream
        signInTestUser(ALICE_EMAIL)
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.endLivestream(stream.id) }.isSuccess)

        // Alice publishes feed post & Bob likes/comments
        val caption = "Post ${UUID.randomUUID().toString().take(6)}"
        val postId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createFeedPost("Alice", caption, "Update").getOrThrow()
        }
        val feedPosts = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeFeedPosts().first { list -> list.any { it.id == postId } }
        }
        val createdPost = feedPosts.first { it.id == postId }

        signInTestUser(BOB_EMAIL)
        val likeRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.toggleFeedLike(createdPost, currentlyLiked = false)
        }
        assertTrue("Toggle like failed: ${likeRes.exceptionOrNull()}", likeRes.isSuccess)
        val commentRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.addFeedComment(postId, "Bob", "Great post!")
        }
        assertTrue("Add comment failed: ${commentRes.exceptionOrNull()}", commentRes.isSuccess)

        val imgPostId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.createFeedPost(
                authorName = "Bob",
                caption = "Studio snapshot",
                category = "Photo",
                imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4"
            ).getOrThrow()
        }
        val refreshed = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.refreshFeedPosts().getOrThrow() }
        assertTrue(refreshed.any { it.id == imgPostId && it.hasImage && it.caption == "Studio snapshot" })
    }

    @Test
    fun unauthenticatedCaller_observeStreamsAndFeed_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = PulseLiveRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeFeedPosts().first() }
            fail("Expected IllegalStateException or PERMISSION_DENIED when signed out")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException || e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
    }

    @Test
    fun calls_startAnswerEnd_andParticipantIsolation() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PulseLiveRepository(firestore, auth)
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = PulseLiveRepository(firestore, auth)

        signInTestUser(ALICE_EMAIL)
        val callRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.callRepository.startCall(
                callerName = "Alice",
                calleeId = bobUid,
                calleeName = "Bob",
                participantUids = listOf(aliceUid, bobUid),
                callType = com.example.model.CallSession.TYPE_VIDEO
            )
        }
        assertTrue("Start call failed: ${callRes.exceptionOrNull()}", callRes.isSuccess)
        val call = callRes.getOrThrow()

        // Bob answers call and ends it cleanly
        signInTestUser(BOB_EMAIL)
        val bobCalls = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.callRepository.observeUserCalls().first { list -> list.any { it.id == call.id } }
        }
        assertTrue(bobCalls.any { it.id == call.id && it.isRinging })

        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.callRepository.answerCall(call) }.isSuccess)
        assertTrue(
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.callRepository.endOrDeclineCall(
                    call = call,
                    finalStatus = com.example.model.CallSession.STATUS_ENDED,
                    durationSeconds = 18L,
                    actorName = "Bob"
                )
            }.isSuccess
        )

        // Charlie (non-participant) cannot read Alice & Bob's call
        signInTestUser(CHARLIE_EMAIL)
        val charlieRepo = PulseLiveRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                charlieRepo.callRepository.observeCall(call.id).first()
            }
            fail("Expected PERMISSION_DENIED for non-participant observing call")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_rule@test.com"
        const val BOB_EMAIL = "bob_rule@test.com"
        const val CHARLIE_EMAIL = "charlie_rule@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
