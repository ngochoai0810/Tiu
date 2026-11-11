package vn.edu.dut.tiu.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.Transaction
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.data.repository.CategoryRepository
import vn.edu.dut.tiu.data.repository.TransactionRepository
import vn.edu.dut.tiu.domain.TransactionValidator
import vn.edu.dut.tiu.ui.common.toUserMessage

/**
 * ViewModel của form Thêm (Create) / Sửa (Update) / Xóa (Delete) giao dịch.
 * [transactionId] = null -> thêm mới; khác null -> sửa giao dịch có sẵn.
 */
class TransactionEditorViewModel(
    private val transactionRepo: TransactionRepository,
    categoryRepo: CategoryRepository,
    private val transactionId: String?,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    data class UiState(
        val isEditing: Boolean = false,
        val type: TransactionType = TransactionType.EXPENSE,
        /** Danh mục cùng loại với [type] để người dùng chọn. */
        val categories: List<Category> = emptyList(),
        val selectedCategory: Category? = null,
        val dateMillis: Long = 0L,
        /** Giao dịch gốc khi sửa – Fragment dùng để điền sẵn số tiền / ghi chú. */
        val original: Transaction? = null,
        val errors: Map<TransactionValidator.Field, String> = emptyMap(),
        val isSaving: Boolean = false,
    )

    sealed interface Event {
        data object Saved : Event
        data object Deleted : Event
        data class Error(val message: String) : Event
    }

    private var allCategories: List<Category> = emptyList()
    private var selectedCategoryId: String? = null

    /** Fragment chỉ điền sẵn form một lần (kể cả khi xoay màn hình). */
    var prefillConsumed = false

    private val _state = MutableStateFlow(UiState(isEditing = transactionId != null, dateMillis = now()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            categoryRepo.observeCategories()
                .catch { e -> _events.send(Event.Error(e.toUserMessage())) }
                .collect { cats ->
                    allCategories = cats
                    refreshCategories()
                }
        }
        if (transactionId != null) loadExisting(transactionId)
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val tx = try {
                transactionRepo.get(id)
            } catch (e: Exception) {
                _events.send(Event.Error(e.toUserMessage())); return@launch
            }
            if (tx == null) {
                _events.send(Event.Error("Không tìm thấy giao dịch (có thể đã bị xóa)"))
                return@launch
            }
            selectedCategoryId = tx.categoryId
            _state.update { it.copy(type = tx.type, dateMillis = tx.date, original = tx) }
            refreshCategories()
        }
    }

    private fun refreshCategories() {
        _state.update { s ->
            val ofType = allCategories.filter { it.type == s.type }
            // Danh mục của giao dịch cũ có thể đã bị xóa -> vẫn hiển thị tên đã lưu.
            val selected = ofType.firstOrNull { it.id == selectedCategoryId }
                ?: s.original?.takeIf { it.categoryId == selectedCategoryId && it.type == s.type }
                    ?.let { Category(id = it.categoryId, name = it.categoryName, type = it.type) }
            s.copy(categories = ofType, selectedCategory = selected)
        }
    }

    fun setType(type: TransactionType) {
        if (type == _state.value.type) return
        selectedCategoryId = null
        _state.update { it.copy(type = type, errors = it.errors - TransactionValidator.Field.CATEGORY) }
        refreshCategories()
    }

    fun selectCategory(category: Category) {
        selectedCategoryId = category.id
        _state.update { it.copy(errors = it.errors - TransactionValidator.Field.CATEGORY) }
        refreshCategories()
    }

    fun setDate(millis: Long) {
        _state.update { it.copy(dateMillis = millis, errors = it.errors - TransactionValidator.Field.DATE) }
    }

    fun save(amountText: String, note: String) {
        val s = _state.value
        if (s.isSaving) return
        val cleanNote = note.trim()
        val result = TransactionValidator.validate(amountText, s.selectedCategory?.id, cleanNote, s.dateMillis, now())
        _state.update { it.copy(errors = result.errors) }
        if (!result.isValid) return

        val category = s.selectedCategory!!
        val tx = Transaction(
            id = transactionId.orEmpty(),
            type = s.type,
            amount = result.amount!!,
            categoryId = category.id,
            categoryName = category.name,
            note = cleanNote,
            date = s.dateMillis,
            imageUrl = s.original?.imageUrl,
            recurringId = s.original?.recurringId,
            createdAt = s.original?.createdAt ?: 0L,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                if (transactionId == null) transactionRepo.add(tx) else transactionRepo.update(tx)
                _events.send(Event.Saved)
            } catch (e: Exception) {
                _events.send(Event.Error(e.toUserMessage()))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun delete() {
        val id = transactionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                transactionRepo.delete(id)
                _events.send(Event.Deleted)
            } catch (e: Exception) {
                _events.send(Event.Error(e.toUserMessage()))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}
