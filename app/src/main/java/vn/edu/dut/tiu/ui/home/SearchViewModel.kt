package vn.edu.dut.tiu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import vn.edu.dut.tiu.data.repository.CategoryRepository
import vn.edu.dut.tiu.data.repository.TransactionRepository
import vn.edu.dut.tiu.util.DateUtils
import java.time.LocalDate

@kotlinx.coroutines.ExperimentalCoroutinesApi
class SearchViewModel(
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val today: () -> LocalDate = { LocalDate.now(DateUtils.zone) }
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    val searchResults: StateFlow<List<HomeItem>> = queryFlow
        .flatMapLatest { query ->
            if (query.isBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                val lowerQuery = fold(query.trim())
                
                // Get month range (can be optimized to query multiple months or all)
                val currentMonth = java.time.YearMonth.from(today())
                
                combine(
                    transactionRepo.observeMonth(currentMonth),
                    categoryRepo.observeCategories()
                ) { txs, cats ->
                    val filteredTxs = txs.filter {
                        fold(it.note).contains(lowerQuery) ||
                        fold(it.categoryName).contains(lowerQuery)
                    }

                    HomeViewModel.groupByDay(
                        txs = filteredTxs,
                        icons = cats.associate { it.id to it.icon },
                        today = today()
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun search(query: String) {
        queryFlow.value = query
    }

    companion object {
        /** "Cà Phê Đá" -> "ca phe da": tìm kiếm không phân biệt hoa/thường và dấu tiếng Việt. */
        fun fold(text: String): String = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('đ', 'd').replace('Đ', 'D')
            .lowercase()
    }
}