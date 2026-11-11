package vn.edu.dut.tiu.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.repository.CategoryRepository
import vn.edu.dut.tiu.data.repository.TransactionRepository
import java.time.YearMonth

/** Thay Dispatchers.Main (Android) bằng dispatcher test để chạy ViewModel trên JVM. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Repository giả lưu trong bộ nhớ – thay cho Firestore khi test. */
class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    val items = MutableStateFlow(initial)
    private var nextId = 1

    override fun observeMonth(month: YearMonth): Flow<List<Transaction>> = items.map { it }
    override suspend fun get(id: String) = items.value.firstOrNull { it.id == id }
    override suspend fun add(transaction: Transaction): String {
        val id = "tx${nextId++}"
        items.value = items.value + transaction.copy(id = id)
        return id
    }
    override suspend fun update(transaction: Transaction) {
        items.value = items.value.map { if (it.id == transaction.id) transaction else it }
    }
    override suspend fun delete(id: String) {
        items.value = items.value.filterNot { it.id == id }
    }
}

class FakeCategoryRepository(initial: List<Category>) : CategoryRepository {
    val items = MutableStateFlow(initial)
    override fun observeCategories(): Flow<List<Category>> = items
    override suspend fun add(category: Category): String {
        val id = "cat${items.value.size + 1}"
        items.value = items.value + category.copy(id = id)
        return id
    }
    override suspend fun update(category: Category) {
        items.value = items.value.map { if (it.id == category.id) category else it }
    }
    override suspend fun delete(categoryId: String) {
        items.value = items.value.filterNot { it.id == categoryId }
    }
    override suspend fun isInUse(categoryId: String) = false
}
