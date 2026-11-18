package vn.edu.dut.tiu.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import vn.edu.dut.tiu.data.model.Notification

class NotificationViewModel : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    private val _filterTab = MutableStateFlow(0) // 0: All, 1: Budget, 2: Reminder

    val state: StateFlow<NotificationUiState> = combine(_notifications, _filterTab) { list, tab ->
        val filtered = when (tab) {
            1 -> list.filter { it.type == "BUDGET" }
            2 -> list.filter { it.type == "REMINDER" }
            else -> list
        }
        val unreadCount = list.count { !it.isRead }
        NotificationUiState(filtered, unreadCount, tab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationUiState())

    init {
        // Mock data cho giao diện, sẽ thay bằng dữ liệu từ Repository sau
        val mockData = listOf(
            Notification("1", "Ăn uống sắp chạm ngân sách", "Đã dùng 90% (540.000 / 600.000đ). Còn 17 ngày trong tháng.", "BUDGET", System.currentTimeMillis() - 3600000, false),
            Notification("2", "Hôm nay bạn chưa chụp khoản nào", "Ghi lại một tấm trước khi quên nhé.", "REMINDER", System.currentTimeMillis() - 7200000, false),
            Notification("3", "Minh muốn kết bạn trên tiu", "Hai bạn có thể xem nhật ký được chia sẻ.", "FRIEND", System.currentTimeMillis() - 86400000, true)
        )
        _notifications.value = mockData
    }

    fun setFilterTab(index: Int) {
        _filterTab.value = index
    }

    fun markAllAsRead() {
        val current = _notifications.value.map { it.copy(isRead = true) }
        _notifications.value = current
    }
}

data class NotificationUiState(
    val list: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val filterTab: Int = 0
)