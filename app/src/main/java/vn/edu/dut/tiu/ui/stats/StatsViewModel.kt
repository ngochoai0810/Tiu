package vn.edu.dut.tiu.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.data.repository.TransactionRepository
import vn.edu.dut.tiu.util.DateUtils
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class StatsFilter { WEEK, MONTH, YEAR }

data class StatsUiState(
    val filter: StatsFilter = StatsFilter.MONTH,
    val title: String = "",
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val expensesByCategory: List<Pair<String, Long>> = emptyList(),
    val insightTitle: String = "",
    val insightBody: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val transactionRepo: TransactionRepository,
    private val today: () -> LocalDate = { LocalDate.now(DateUtils.zone) }
) : ViewModel() {

    private val _filter = MutableStateFlow(StatsFilter.MONTH)

    val state: StateFlow<StatsUiState> = _filter
        .flatMapLatest { filter ->
            val (start, end) = getRangeForFilter(filter)
            val title = getTitleForFilter(filter)
            
            // Lấy tất cả giao dịch trong khoảng thời gian
            transactionRepo.observeRange(
                start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
                .map { filteredTxs ->
                    buildState(filter, title, filteredTxs)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setFilter(filter: StatsFilter) {
        _filter.update { filter }
    }

    private fun getRangeForFilter(filter: StatsFilter): Pair<LocalDate, LocalDate> {
        val now = today()
        return when (filter) {
            StatsFilter.WEEK -> {
                val field = WeekFields.of(Locale.getDefault()).dayOfWeek()
                val start = now.with(field, 1)
                val end = now.with(field, 7)
                start to end
            }
            StatsFilter.MONTH -> {
                val start = now.withDayOfMonth(1)
                val end = now.with(TemporalAdjusters.lastDayOfMonth())
                start to end
            }
            StatsFilter.YEAR -> {
                val start = now.withDayOfYear(1)
                val end = now.with(TemporalAdjusters.lastDayOfYear())
                start to end
            }
        }
    }

    private fun getTitleForFilter(filter: StatsFilter): String {
        val now = today()
        return when (filter) {
            StatsFilter.WEEK -> "Chi tiêu tuần này"
            StatsFilter.MONTH -> "Chi tiêu tháng ${now.monthValue}"
            StatsFilter.YEAR -> "Chi tiêu năm ${now.year}"
        }
    }

    private fun buildState(filter: StatsFilter, title: String, txs: List<Transaction>): StatsUiState {
        val expenses = txs.filter { it.type == TransactionType.EXPENSE }
        val incomes = txs.filter { it.type == TransactionType.INCOME }

        val totalExpense = expenses.sumOf { it.amount }
        val totalIncome = incomes.sumOf { it.amount }

        val byCategory = expenses.groupBy { it.categoryName.ifBlank { "Khác" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val insightTitle: String
        val insightBody: String

        if (totalIncome > totalExpense && totalExpense > 0) {
            insightTitle = "Tuyệt vời, bạn đã tiết kiệm được tiền!"
            insightBody = "Tiếp tục duy trì phong độ giữ thu nhập cao hơn chi tiêu nhé."
        } else if (totalExpense == 0L) {
            insightTitle = "Chưa có dữ liệu"
            insightBody = "Hãy ghi lại khoản chi đầu tiên để Tiu phân tích giúp bạn."
        } else {
            insightTitle = "Hãy chú ý nhịp chi tiêu"
            insightBody = "Chi tiêu đang chạm gần hoặc vượt mức thu nhập của bạn."
        }

        return StatsUiState(
            filter = filter,
            title = title,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            expensesByCategory = byCategory,
            insightTitle = insightTitle,
            insightBody = insightBody
        )
    }
}