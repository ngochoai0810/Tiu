package vn.edu.dut.tiu.data.repository

import kotlinx.coroutines.flow.Flow
import vn.edu.dut.tiu.data.model.Notification

interface NotificationRepository {
    /** Lấy danh sách thông báo của người dùng hiện tại theo thời gian thực */
    fun observeNotifications(): Flow<List<Notification>>
    
    /** Đánh dấu một thông báo đã đọc */
    suspend fun markAsRead(notificationId: String)
    
    /** Đánh dấu tất cả đã đọc */
    suspend fun markAllAsRead()
    
    /** Xóa thông báo */
    suspend fun deleteNotification(notificationId: String)
    
    /** (Nội bộ hệ thống) Thêm thông báo mới */
    suspend fun addNotification(notification: Notification)
}
