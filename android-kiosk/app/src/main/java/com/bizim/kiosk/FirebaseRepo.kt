package com.bizim.kiosk

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

private const val TAG = "FirebaseRepo"

/** Thin wrapper around the Firestore calls a single kiosk device needs. */
class FirebaseRepo(deviceId: String, private val authUid: String) {
    private val db = Firebase.firestore
    private val deviceRef = db.collection("devices").document(deviceId)

    suspend fun ensureDeviceDocument() {
        val snap = deviceRef.get().await()
        if (!snap.exists()) {
            val data = hashMapOf(
                "authUid" to authUid,
                "label" to "",
                "targetPackage" to "",
                "webUrl" to "",
                "locked" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastSeenAt" to FieldValue.serverTimestamp(),
            )
            deviceRef.set(data).await()
        }
    }

    fun listenDevice(onChange: (DocumentSnapshot) -> Unit): ListenerRegistration =
        deviceRef.addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "listenDevice error", error)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) onChange(snap)
        }

    /** Fires once for every command that is currently (or becomes) pending. */
    fun listenPendingCommands(
        onCommand: (id: String, type: String, payload: Map<String, Any?>) -> Unit,
    ): ListenerRegistration =
        deviceRef.collection("commands")
            .whereEqualTo("status", "pending")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w(TAG, "listenPendingCommands error", error)
                    return@addSnapshotListener
                }
                snap?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        @Suppress("UNCHECKED_CAST")
                        val payload = doc.get("payload") as? Map<String, Any?> ?: emptyMap()
                        onCommand(doc.id, doc.getString("type").orEmpty(), payload)
                    }
                }
            }

    suspend fun markCommandDone(commandId: String) {
        deviceRef.collection("commands").document(commandId)
            .update("status", "done", "doneAt", FieldValue.serverTimestamp())
            .await()
    }

    suspend fun logEvent(type: String, message: String? = null) {
        deviceRef.collection("events").add(
            hashMapOf(
                "type" to type,
                "message" to message,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Written immediately on unlock/relock, not just on the next heartbeat -- the panel
     *  needs to reflect this the moment it happens, not up to 45s late. */
    suspend fun setLocked(locked: Boolean) {
        deviceRef.update("locked", locked).await()
    }

    suspend fun heartbeat(fields: Map<String, Any?>) {
        val data = fields + ("lastSeenAt" to FieldValue.serverTimestamp())
        deviceRef.update(data).await()
    }
}
