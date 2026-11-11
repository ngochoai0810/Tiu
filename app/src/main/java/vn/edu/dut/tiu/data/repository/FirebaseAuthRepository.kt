package vn.edu.dut.tiu.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import vn.edu.dut.tiu.data.model.DefaultCategories
import vn.edu.dut.tiu.data.model.UserProfile
import vn.edu.dut.tiu.data.remote.FirestoreMapper
import vn.edu.dut.tiu.data.remote.FirestorePaths

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : AuthRepository {

    override val currentUserId: String? get() = auth.currentUser?.uid
    override val currentDisplayName: String? get() = auth.currentUser?.displayName
    override val currentEmail: String? get() = auth.currentUser?.email

    override suspend fun signIn(email: String, password: String) {
        val user = auth.signInWithEmailAndPassword(email, password).await().user
            ?: throw NotSignedInException()
        ensureUserDocument(user.uid, user.displayName.orEmpty(), user.email.orEmpty())
    }

    override suspend fun register(displayName: String, email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: throw NotSignedInException()
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
        ).await()
        ensureUserDocument(user.uid, displayName, email)
    }

    override suspend fun currentProfile(): UserProfile? {
        val uid = currentUserId ?: return null
        val doc = db.collection(FirestorePaths.USERS).document(uid).get().await()
        return FirestoreMapper.toUser(doc)
    }

    override fun signOut() = auth.signOut()

    /**
     * Lần đầu đăng nhập: tạo document `users/{uid}` và các danh mục mặc định
     * trong cùng một WriteBatch (thành công hết hoặc không ghi gì).
     */
    private suspend fun ensureUserDocument(uid: String, displayName: String, email: String) {
        val userRef = db.collection(FirestorePaths.USERS).document(uid)
        if (userRef.get().await().exists()) return

        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.set(
            userRef,
            FirestoreMapper.toMap(UserProfile(uid = uid, displayName = displayName, email = email, createdAt = now))
        )
        val categories = userRef.collection(FirestorePaths.CATEGORIES)
        DefaultCategories.all.forEach { c ->
            batch.set(categories.document(c.id), FirestoreMapper.toMap(c.copy(createdAt = now)))
        }
        batch.commit().await()
    }
}
