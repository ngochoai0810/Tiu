package vn.edu.dut.tiu.ui.home

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
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.data.repository.CategoryRepository
import vn.edu.dut.tiu.data.repository.TransactionRepository
import vn.edu.dut.tiu.domain.BudgetCalculator
import vn.edu.dut.tiu.ui.common.toUserMessage
import vn.edu.dut.tiu.util.DateUtils
import java.time.LocalDate
import java.time.YearMonth

/** Một dòng trong danh sách Nhật ký: tiêu đề ngày hoặc một giao dịch. */
sealed interface HomeItem {
    data class Header(val date: LocalDate, val label: String, val net: Long) : HomeItem
    data class Row(val transaction: Transaction, val icon: String) : HomeItem
}

data class HomeUiState(
    val monthLabel: String = "",
    val canGoNext: Boolean = false,
    val income: Long = 0L,
    val expense: Long = 0L,
    val items: List<HomeItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val balance: Long get() = income - expense
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val today: () -> LocalDate = { LocalDate.now(DateUtils.zone) },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val state: StateFlow<HomeUiState> = month
        .flatMapLatest { m ->
            combine(transactionRepo.observeMonth(m), categoryRepo.observeCategories()) { txs, cats ->
                buildState(m, txs, cats)
            }.catch { e ->
                emit(HomeUiState(monthLabel = DateUtils.monthLabel(m), loading = false, error = e.toUserMessage()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun previousMonth() { month.value = month.value.minusMonths(1) }

    fun nextMonth() {
        if (month.value < YearMonth.from(today())) month.value = month.value.plusMonths(1)
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            try {
                transactionRepo.delete(transaction.id)
                _messages.emit("Đã xóa giao dịch")
            } catch (e: Exception) {
                _messages.emit(e.toUserMessage())
            }
        }
    }

    private fun buildState(m: YearMonth, txs: List<Transaction>, cats: List<Category>) = HomeUiState(
        monthLabel = DateUtils.monthLabel(m),
        canGoNext = m < YearMonth.from(today()),
        income = BudgetCalculator.totalIncome(txs),
        expense = BudgetCalculator.totalExpense(txs),
        items = groupByDay(txs, cats.associate { it.id to it.icon }, today()),
        loading = false,
    )

    companion object {
        /** Nhóm giao dịch theo ngày (mới nhất trước), mỗi nhóm có tiêu đề + tổng thu-chi của ngày. */
        fun groupByDay(txs: List<Transaction>, icons: Map<String, String>, today: LocalDate): List<HomeItem> =
            txs.groupBy { DateUtils.toLocalDate(it.date) }
                .toSortedMap(compareByDescending { it })
                .flatMap { (date, list) ->
                    val net = list.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
                    listOf<HomeItem>(HomeItem.Header(date, DateUtils.dayLabel(date, today), net)) +
                        list.sortedByDescending { it.date }.map { HomeItem.Row(it, icons[it.categoryId] ?: "•") }
                }
    }
}
