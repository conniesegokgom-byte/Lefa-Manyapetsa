package com.example.data

import com.example.model.CallSession
import com.example.model.ChatRoom
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PulseCallRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing calls.")
    }

    fun observeUserCalls(): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        val path = "calls"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CallSession::class.java, ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = error.unwrapFirestoreException()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeCall(callId: String): Flow<CallSession?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("calls").document(callId)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(CallSession::class.java, ESTIMATE) }
                .catch { error ->
                    val fsError = error.unwrapFirestoreException()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun startCall(
        callerName: String,
        calleeId: String,
        calleeName: String,
        participantUids: List<String>,
        callType: String,
        roomId: String = ""
    ): Result<CallSession> = runCatching {
        val uid = requireUserId()
        val cleanParticipants = (listOf(uid, calleeId) + participantUids)
            .filter { it.isNotBlank() }
            .distinct()
            .take(30)
        require(cleanParticipants.size >= 2) { "Call requires at least 2 participants" }

        val validType = if (callType == CallSession.TYPE_VIDEO) CallSession.TYPE_VIDEO else CallSession.TYPE_VOICE
        val isVideo = validType == CallSession.TYPE_VIDEO
        val cleanCaller = callerName.trim().ifBlank { "Creator" }.take(50)
        val cleanCallee = calleeName.trim().ifBlank { "Member" }.take(60)
        val docRef = db.collection("calls").document()

        val payload = buildMap<String, Any> {
            put("callerId", uid)
            put("callerName", cleanCaller)
            put("calleeId", calleeId)
            put("calleeName", cleanCallee)
            put("participantUids", cleanParticipants)
            if (roomId.isNotBlank()) put("roomId", roomId)
            put("callType", validType)
            put("status", CallSession.STATUS_RINGING)
            put("isVideoEnabledByCaller", isVideo)
            put("isVideoEnabledByCallee", false)
            put("durationSeconds", 0L)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }

        CallSession(
            id = docRef.id,
            callerId = uid,
            callerName = cleanCaller,
            calleeId = calleeId,
            calleeName = cleanCallee,
            participantUids = cleanParticipants,
            roomId = roomId,
            callType = validType,
            status = CallSession.STATUS_RINGING,
            isVideoEnabledByCaller = isVideo,
            isVideoEnabledByCallee = false,
            durationSeconds = 0L
        )
    }

    suspend fun answerCall(call: CallSession): Result<Unit> = runCatching {
        val uid = requireUserId()
        val isCaller = call.callerId == uid
        val docRef = db.collection("calls").document(call.id)
        val payload = buildMap<String, Any> {
            put("status", CallSession.STATUS_CONNECTED)
            if (!isCaller && call.isVideoCall) {
                put("isVideoEnabledByCallee", true)
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun toggleCallVideo(call: CallSession, enabled: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val field = if (call.callerId == uid) "isVideoEnabledByCaller" else "isVideoEnabledByCallee"
        val docRef = db.collection("calls").document(call.id)
        val payload = buildMap<String, Any> {
            put(field, enabled)
            if (enabled && call.callType != CallSession.TYPE_VIDEO) {
                put("callType", CallSession.TYPE_VIDEO)
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun endOrDeclineCall(
        call: CallSession,
        finalStatus: String,
        durationSeconds: Long,
        actorName: String,
        linkedRoom: ChatRoom? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeStatus = when (finalStatus) {
            CallSession.STATUS_DECLINED,
            CallSession.STATUS_MISSED,
            CallSession.STATUS_ENDED -> finalStatus
            else -> CallSession.STATUS_ENDED
        }
        val safeDuration = durationSeconds.coerceAtLeast(0L)
        val docRef = db.collection("calls").document(call.id)
        val payload = mapOf(
            "status" to safeStatus,
            "durationSeconds" to safeDuration,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }

        // Write a call summary log to the linked chat room if available
        if (linkedRoom != null) {
            val label = if (call.isVideoCall) "Video Call" else "Voice Call"
            val summary = when (safeStatus) {
                CallSession.STATUS_DECLINED -> "$label declined"
                CallSession.STATUS_MISSED -> "Missed $label"
                else -> if (safeDuration > 0L) "$label • ${formatDuration(safeDuration)}" else "$label ended"
            }
            val roomRef = db.collection("chat_rooms").document(linkedRoom.id)
            val msgRef = roomRef.collection("messages").document()
            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "roomId" to linkedRoom.id,
                    "senderId" to uid,
                    "senderName" to actorName.trim().ifBlank { "Creator" }.take(50),
                    "text" to summary,
                    "messageType" to "call_log",
                    "participantUids" to linkedRoom.participantUids.ifEmpty { listOf(uid) },
                    "isPublic" to linkedRoom.isPublic,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                roomRef,
                mapOf(
                    "lastMessageText" to summary,
                    "lastMessageSenderId" to uid,
                    "lastMessageSenderName" to actorName.trim().ifBlank { "Creator" }.take(50),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            runCatching { batch.commit().await() }
        }
    }

    companion object {
        private val ESTIMATE = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE

        fun formatDuration(seconds: Long): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return "%02d:%02d".format(mins, secs)
        }
    }
}
