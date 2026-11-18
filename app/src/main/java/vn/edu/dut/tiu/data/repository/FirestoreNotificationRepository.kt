package vn.edu.dut.tiu.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import vn.edu.dut.tiu.data.model.Notification
import java.util.UUID

class FirestoreNotificationRepository(
    private val db: FirebaseFirestore,
    private val uidProvider: () -> String?
) : NotificationRepository {

    private fun collection() = uidProvider()?.let { uid ->
        db.collection("users").document(uid).collection("notifications")
    } ?: throw IllegalStateException("User not logged in")

    override fun observeNotifications(): Flow<List<Notification>> = callbackFlow {
        val listener = collection()
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val notifications = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Notification::class.java)?.copy(
                            id = doc.id,
                            isRead = doc.getBoolean("isRead") ?: doc.getBoolean("read") ?: false
                        )
                    }
                    trySend(notifications)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun markAsRead(notificationId: String) {
        collection().document(notificationId).update("isRead", true).await()
    }

    override suspend fun markAllAsRead() {
        val unreadDocs = collection().get().await().documents.filter { doc ->
            !(doc.getBoolean("isRead") ?: doc.getBoolean("read") ?: false)
        }
        db.runBatch { batch ->
            for (doc in unreadDocs) {
                batch.update(doc.reference, "isRead", true)
            }
        }.await()
    }

    override suspend fun deleteNotification(notificationId: String) {
        collection().document(notificationId).delete().await()
    }

    override suspend fun addNotification(notification: Notification) {
        val id = notification.id.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        val notifToSave = notification.copy(id = id)
        collection().document(id).set(notifToSave).await()
    }
}
