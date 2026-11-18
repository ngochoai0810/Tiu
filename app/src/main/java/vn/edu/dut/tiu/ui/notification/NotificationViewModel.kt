package vn.edu.dut.tiu.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.edu.dut.tiu.data.model.Notification
import vn.edu.dut.tiu.data.repository.NotificationRepository

class NotificationViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    private val _filterTab = MutableStateFlow(0) // 0: All, 1: Budget, 2: Reminder

    val state: StateFlow<NotificationUiState> = combine(_notifications, _filterTab) { list, tab ->
        val filtered = when (tab) {
            1 -> list.filter { it.type == "BUDGET" }
            2 -> list.filter { it.type == "REMINDER" }
            else -> list
        }
        val unreadCount = list.count { !it.isRead }
        NotificationUiState(filtered, unreadCount, tab, list.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationUiState())

    init {
        viewModelScope.launch {
            notificationRepository.observeNotifications()
                .catch { e -> e.printStackTrace() }
                .collect { list ->
                    _notifications.value = list
                }
        }
    }

    fun setFilterTab(index: Int) {
        _filterTab.value = index
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                notificationRepository.markAllAsRead()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

data class NotificationUiState(
    val list: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val filterTab: Int = 0,
    val totalCount: Int = 0,
)