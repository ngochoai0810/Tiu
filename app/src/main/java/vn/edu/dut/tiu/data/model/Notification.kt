package vn.edu.dut.tiu.data.model

data class Notification(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "SYSTEM", // "BUDGET", "REMINDER", "FRIEND", "SYSTEM"
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val actionUrl: String? = null
)
