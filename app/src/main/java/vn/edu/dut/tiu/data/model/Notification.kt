package vn.edu.dut.tiu.data.model

import com.google.firebase.firestore.PropertyName

data class Notification(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "SYSTEM", // "BUDGET", "REMINDER", "FRIEND", "SYSTEM"
    val timestamp: Long = 0L,
    @get:PropertyName("isRead")
    val isRead: Boolean = false,
    val actionUrl: String? = null
)
