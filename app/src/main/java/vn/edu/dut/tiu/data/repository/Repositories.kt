package vn.edu.dut.tiu.data.repository

import kotlinx.coroutines.flow.Flow
import vn.edu.dut.tiu.data.model.Budget
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.UserProfile
import java.time.YearMonth

/*
 * Tầng Repository: ViewModel chỉ biết các interface này, không biết Firestore.
 * Nhờ vậy có thể thay nguồn dữ liệu (Room, API...) hoặc dùng bản giả (fake) khi unit test.
 */

interface AuthRepository {
    val currentUserId: String?
    val currentDisplayName: String?
    val currentEmail: String?
    suspend fun signIn(email: String, password: String)
    suspend fun register(displayName: String, email: String, password: String)
    suspend fun currentProfile(): UserProfile?
    fun signOut()
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun add(category: Category): String
    /** Đổi tên danh mục và cập nhật tên phi chuẩn hóa trong các giao dịch liên quan. */
    suspend fun update(category: Category)
    suspend fun delete(categoryId: String)
    suspend fun isInUse(categoryId: String): Boolean
}

interface TransactionRepository {
    /** Giao dịch trong một tháng, mới nhất trước, cập nhật realtime. */
    fun observeMonth(month: YearMonth): Flow<List<Transaction>>
    suspend fun get(id: String): Transaction?
    suspend fun add(transaction: Transaction): String
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: String)
}

interface BudgetRepository {
    fun observeMonth(month: YearMonth): Flow<List<Budget>>
    /** Thêm mới hoặc sửa (id cố định theo tháng + danh mục nên không bị trùng). */
    suspend fun upsert(budget: Budget)
    suspend fun delete(id: String)
}

class NotSignedInException : IllegalStateException("Bạn chưa đăng nhập")
