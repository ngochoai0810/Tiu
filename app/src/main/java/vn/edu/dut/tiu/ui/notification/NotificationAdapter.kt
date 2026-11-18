package vn.edu.dut.tiu.ui.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.data.model.Notification
import vn.edu.dut.tiu.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val onItemClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(
        private val binding: ItemNotificationBinding,
        private val onItemClick: (Notification) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        fun bind(notification: Notification) {
            binding.textTitle.text = notification.title
            binding.textBody.text = notification.body
            
            // Format time
            val date = Date(notification.timestamp)
            binding.textTime.text = dateFormat.format(date)
            
            // Icon dựa trên Type
            val (icon, bgRes) = when (notification.type) {
                "BUDGET" -> "⚠️" to R.drawable.bg_doodle_card_warning
                "REMINDER" -> "📷" to R.drawable.bg_doodle_card_orange
                else -> "🔔" to R.drawable.bg_doodle_card
            }
            binding.iconNotification.text = icon
            binding.iconNotification.setBackgroundResource(bgRes)
            
            // Chưa đọc (Unread badge)
            binding.badgeUnread.visibility = if (notification.isRead) View.INVISIBLE else View.VISIBLE
            
            binding.root.setOnClickListener {
                onItemClick(notification)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Notification>() {
            override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
                return oldItem == newItem
            }
        }
    }
}
