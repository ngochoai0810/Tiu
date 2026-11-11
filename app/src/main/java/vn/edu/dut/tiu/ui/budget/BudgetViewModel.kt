package vn.edu.dut.tiu.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.edu.dut.tiu.data.model.Budget
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.data.repository.BudgetRepository
import vn.edu.dut.tiu.data.repository.CategoryRepository
import vn.edu.dut.tiu.data.repository.TransactionRepository
import vn.edu.dut.tiu.domain.BudgetCalculator
import vn.edu.dut.tiu.domain.BudgetProgress
import vn.edu.dut.tiu.ui.common.toUserMessage
import vn.edu.dut.tiu.util.DateUtils
import vn.edu.dut.tiu.util.MoneyFormat
import java.time.LocalDate
import java.time.YearMonth

data class CategoryBudgetItem(
    val budget: Budget,
    val name: String,
    val icon: String,
    val progress: BudgetProgress,
)

data class BudgetUiState(
    val monthInfo: String = "",
    /** Ngân sách tổng của tháng (null = chưa đặt). */
    val totalBudget: Budget? = null,
    val totalProgress: BudgetProgress = BudgetProgress(0, 0),
    val dailyAllowance: Long = 0L,
    val items: List<CategoryBudgetItem> = emptyList(),
    /** Danh mục chi tiêu chưa có ngân sách – để chọn khi tạo mới. */
    val categoriesWithoutBudget: List<Category> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    private val budgetRepo: BudgetRepository,
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val today: () -> LocalDate = { LocalDate.now(DateUtils.zone) },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val state: StateFlow<BudgetUiState> = month
        .flatMapLatest { m ->
            combine(
                budgetRepo.observeMonth(m),
                transactionRepo.observeMonth(m),
                categoryRepo.observeCategories(),
            ) { budgets, txs, cats -> buildState(m, budgets, txs, cats, today()) }
                .catch { e -> emit(BudgetUiState(loading = false, error = e.toUserMessage())) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    /** Trả về thông báo lỗi nếu hạn mức không hợp lệ, null nếu đã gửi lệnh lưu. */
    fun saveBudget(categoryId: String?, amountText: String): String? {
        val limit = MoneyFormat.parse(amountText)
        val error = when {
            limit == null -> "Nhập hạn mức"
            limit <= 0 -> "Hạn mức phải lớn hơn 0"
            limit > 1_000_000_000L -> "Hạn mức quá lớn"
            else -> null
        }
        if (error != null) return error
        val existing = state.value.let { s ->
            if (categoryId == null) s.totalBudget else s.items.firstOrNull { it.budget.categoryId == categoryId }?.budget
        }
        val budget = Budget(
            month = DateUtils.monthKey(month.value),
            categoryId = categoryId,
            limitAmount = limit!!,
            createdAt = existing?.createdAt ?: 0L,
        )
        viewModelScope.launch {
            try {
                budgetRepo.upsert(budget)
                _messages.emit(if (existing == null) "Đã tạo ngân sách" else "Đã cập nhật ngân sách")
            } catch (e: Exception) {
                _messages.emit(e.toUserMessage())
            }
        }
        return null
    }

    fun delete(budget: Budget) {
        viewModelScope.launch {
            try {
                budgetRepo.delete(budget.id)
                _messages.emit("Đã xóa ngân sách")
            } catch (e: Exception) {
                _messages.emit(e.toUserMessage())
            }
        }
    }

    companion object {
        fun buildState(
            m: YearMonth,
            budgets: List<Budget>,
            txs: List<Transaction>,
            cats: List<Category>,
            today: LocalDate,
        ): BudgetUiState {
            val spentByCategory = BudgetCalculator.expenseByCategory(txs)
            val totalBudget = budgets.firstOrNull { it.isTotal }
            val totalProgress = BudgetProgress(totalBudget?.limitAmount ?: 0L, BudgetCalculator.totalExpense(txs))
            val catById = cats.associateBy { it.id }

            val items = budgets.filter { !it.isTotal }.map { b ->
                val c = catById[b.categoryId]
                CategoryBudgetItem(
                    budget = b,
                    name = c?.name ?: "(Danh mục đã xóa)",
                    icon = c?.icon.orEmpty(),
                    progress = BudgetProgress(b.limitAmount, spentByCategory[b.categoryId] ?: 0L),
                )
            }.sortedByDescending { it.progress.spent }

            val budgeted = items.map { it.budget.categoryId }.toSet()
            val daysLeft = BudgetCalculator.daysLeft(m, today)
            return BudgetUiState(
                monthInfo = "Tháng ${m.monthValue} · " + if (daysLeft > 0) "còn $daysLeft ngày" else "đã kết thúc",
                totalBudget = totalBudget,
                totalProgress = totalProgress,
                dailyAllowance = if (totalBudget != null) BudgetCalculator.dailyAllowance(totalProgress, m, today) else 0L,
                items = items,
                categoriesWithoutBudget = cats.filter { it.type == TransactionType.EXPENSE && it.id !in budgeted },
                loading = false,
            )
        }
    }
}
