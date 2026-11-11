package vn.edu.dut.tiu.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.edu.dut.tiu.data.model.Category
import vn.edu.dut.tiu.data.model.TransactionType
import vn.edu.dut.tiu.data.repository.CategoryRepository
import vn.edu.dut.tiu.ui.common.toUserMessage

data class CategoryUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val categories: List<Category> = emptyList(),
    /** Toàn bộ danh mục (mọi loại) – dùng để kiểm tra trùng tên. */
    val all: List<Category> = emptyList(),
    val loading: Boolean = true,
)

class CategoryViewModel(private val repo: CategoryRepository) : ViewModel() {

    private val type = MutableStateFlow(TransactionType.EXPENSE)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val state: StateFlow<CategoryUiState> =
        combine(repo.observeCategories().catch { e -> _messages.emit(e.toUserMessage()); emit(emptyList()) }, type) { all, t ->
            CategoryUiState(type = t, categories = all.filter { it.type == t }, all = all, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState())

    fun setType(t: TransactionType) { type.value = t }

    /** Thêm (editing = null) hoặc sửa danh mục. Trả lỗi kiểm tra dữ liệu, null nếu đã gửi lệnh lưu. */
    fun save(editing: Category?, name: String, icon: String): String? {
        val cleanName = name.trim()
        val error = validateName(cleanName, type.value, editing?.id, state.value.all)
        if (error != null) return error
        val cleanIcon = icon.trim().ifEmpty { "•" }
        viewModelScope.launch {
            try {
                if (editing == null) {
                    repo.add(Category(name = cleanName, icon = cleanIcon, type = type.value))
                    _messages.emit("Đã thêm danh mục")
                } else {
                    repo.update(editing.copy(name = cleanName, icon = cleanIcon))
                    _messages.emit("Đã cập nhật danh mục")
                }
            } catch (e: Exception) {
                _messages.emit(e.toUserMessage())
            }
        }
        return null
    }

    /** Không cho xóa danh mục còn giao dịch tham chiếu (bảo toàn quan hệ CATEGORY 1–n TRANSACTION). */
    fun delete(category: Category) {
        viewModelScope.launch {
            try {
                if (repo.isInUse(category.id)) {
                    _messages.emit("Không thể xóa: \"${category.name}\" đang được dùng trong giao dịch")
                } else {
                    repo.delete(category.id)
                    _messages.emit("Đã xóa danh mục")
                }
            } catch (e: Exception) {
                _messages.emit(e.toUserMessage())
            }
        }
    }

    companion object {
        const val MAX_NAME = 30

        fun validateName(name: String, type: TransactionType, excludeId: String?, all: List<Category>): String? = when {
            name.isBlank() -> "Nhập tên danh mục"
            name.length > MAX_NAME -> "Tên tối đa $MAX_NAME ký tự"
            all.any { it.type == type && it.id != excludeId && it.name.equals(name, ignoreCase = true) } ->
                "Đã có danh mục \"$name\""
            else -> null
        }
    }
}
