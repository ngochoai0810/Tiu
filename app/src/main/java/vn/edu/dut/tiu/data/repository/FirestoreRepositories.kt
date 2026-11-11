package vn.edu.dut.tiu.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import vn.edu.dut.tiu.data.model.Budget
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.remote.FirestoreMapper
import vn.edu.dut.tiu.data.remote.FirestorePaths
import vn.edu.dut.tiu.data.remote.snapshotFlow
import vn.edu.dut.tiu.util.DateUtils
import java.time.YearMonth
import java.util.Date

/** Lớp cha: lấy collection con của người dùng đang đăng nhập (`users/{uid}/...`). */
abstract class UserScopedFirestoreRepository(
    protected val db: FirebaseFirestore,
    private val uidProvider: () -> String?,
) {
    protected fun userCollection(name: String): CollectionReference {
        val uid = uidProvider() ?: throw NotSignedInException()
        return db.collection(FirestorePaths.USERS).document(uid).collection(name)
    }
}

// ======================= CATEGORY =======================
class FirestoreCategoryRepository(
    db: FirebaseFirestore,
    uidProvider: () -> String?,
) : UserScopedFirestoreRepository(db, uidProvider), CategoryRepository {

    private val col get() = userCollection(FirestorePaths.CATEGORIES)

    override fun observeCategories(): Flow<List<Category>> =
        col.orderBy("createdAt").snapshotFlow()
            .map { snap -> snap.documents.mapNotNull(FirestoreMapper::toCategory) }

    override suspend fun add(category: Category): String {
        val ref = col.document()
        ref.set(FirestoreMapper.toMap(category.copy(createdAt = System.currentTimeMillis()))).await()
        return ref.id
    }

    override suspend fun update(category: Category) {
        val batch = db.batch()
        batch.update(
            col.document(category.id),
            mapOf("name" to category.name, "icon" to category.icon, "colorHex" to category.colorHex)
        )
        // Giữ nhất quán dữ liệu phi chuẩn hóa: đổi categoryName trong các giao dịch dùng danh mục này.
        val related = userCollection(FirestorePaths.TRANSACTIONS)
            .whereEqualTo("categoryId", category.id).get().await()
        related.documents.take(MAX_BATCH - 1).forEach { doc ->
            batch.update(doc.reference, "categoryName", category.name)
        }
        batch.commit().await()
    }

    override suspend fun delete(categoryId: String) {
        col.document(categoryId).delete().await()
    }

    override suspend fun isInUse(categoryId: String): Boolean =
        !userCollection(FirestorePaths.TRANSACTIONS)
            .whereEqualTo("categoryId", categoryId).limit(1).get().await().isEmpty

    private companion object { const val MAX_BATCH = 500 }
}

// ======================= TRANSACTION =======================
class FirestoreTransactionRepository(
    db: FirebaseFirestore,
    uidProvider: () -> String?,
) : UserScopedFirestoreRepository(db, uidProvider), TransactionRepository {

    private val col get() = userCollection(FirestorePaths.TRANSACTIONS)

    override fun observeMonth(month: YearMonth): Flow<List<Transaction>> {
        val (start, end) = DateUtils.monthRange(month)
        return col
            .whereGreaterThanOrEqualTo("date", Timestamp(Date(start)))
            .whereLessThan("date", Timestamp(Date(end)))
            .orderBy("date", Query.Direction.DESCENDING)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull(FirestoreMapper::toTransaction) }
    }

    override suspend fun get(id: String): Transaction? =
        FirestoreMapper.toTransaction(col.document(id).get().await())

    override suspend fun add(transaction: Transaction): String {
        val now = System.currentTimeMillis()
        val ref = col.document()
        ref.set(FirestoreMapper.toMap(transaction.copy(createdAt = now, updatedAt = now))).await()
        return ref.id
    }

    override suspend fun update(transaction: Transaction) {
        val data = FirestoreMapper.toMap(transaction.copy(updatedAt = System.currentTimeMillis()))
            .minus("createdAt") // không ghi đè thời điểm tạo
        col.document(transaction.id).update(data).await()
    }

    override suspend fun delete(id: String) {
        col.document(id).delete().await()
    }
}

// ======================= BUDGET =======================
class FirestoreBudgetRepository(
    db: FirebaseFirestore,
    uidProvider: () -> String?,
) : UserScopedFirestoreRepository(db, uidProvider), BudgetRepository {

    private val col get() = userCollection(FirestorePaths.BUDGETS)

    override fun observeMonth(month: YearMonth): Flow<List<Budget>> =
        col.whereEqualTo("month", DateUtils.monthKey(month)).snapshotFlow()
            .map { snap -> snap.documents.mapNotNull(FirestoreMapper::toBudget) }

    override suspend fun upsert(budget: Budget) {
        val now = System.currentTimeMillis()
        val id = Budget.idFor(budget.month, budget.categoryId)
        val toSave = budget.copy(
            id = id,
            createdAt = if (budget.createdAt == 0L) now else budget.createdAt,
            updatedAt = now,
        )
        col.document(id).set(FirestoreMapper.toMap(toSave)).await()
    }

    override suspend fun delete(id: String) {
        col.document(id).delete().await()
    }
}
