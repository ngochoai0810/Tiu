package vn.edu.dut.tiu.data.remote

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Tên collection Firestore – ánh xạ trực tiếp từ ERD. */
object FirestorePaths {
    const val USERS = "users"
    const val CATEGORIES = "categories"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val NOTIFICATIONS = "notifications"
    const val RECURRING = "recurring"
    const val FRIENDSHIPS = "friendships"
}

/** Biến snapshot listener của Firestore thành Flow (tự gỡ listener khi UI ngừng lắng nghe). */
fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}
