package vn.edu.dut.tiu.ui.notification

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentNotificationBinding
import vn.edu.dut.tiu.databinding.FragmentNotificationSettingsBinding
import vn.edu.dut.tiu.ui.common.appContainer
import vn.edu.dut.tiu.worker.ReminderManager
import java.util.Locale

class NotificationFragment : Fragment(R.layout.fragment_notification) {

    private val vm: NotificationViewModel by viewModels {
        viewModelFactory {
            initializer { NotificationViewModel(appContainer.notificationRepository) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentNotificationBinding.bind(view)

        b.btnBack.setOnClickListener { findNavController().popBackStack() }
        b.btnSettings.setOnClickListener { findNavController().navigate(R.id.notificationSettingsFragment) }

        b.btnMarkAllRead.setOnClickListener {
            vm.markAllAsRead()
        }

        b.tabAll.setOnClickListener { vm.setFilterTab(0) }
        b.tabBudget.setOnClickListener { vm.setFilterTab(1) }
        b.tabReminder.setOnClickListener { vm.setFilterTab(2) }
        
        val adapter = NotificationAdapter { notification ->
            // TODO: Xử lý click item nếu cần (đánh dấu đã đọc 1 item, mở màn hình chi tiết, ...)
        }
        b.recyclerNotifications.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collectLatest { s ->
                selectTab(b, s.filterTab)
                
                val unreadText = if (s.unreadCount > 0) "${s.unreadCount} chưa đọc" else "0 chưa đọc"
                b.textUnreadCount.text = unreadText
                
                b.tabAll.text = "Tất cả · ${s.totalCount}"
                
                adapter.submitList(s.list)
            }
        }
    }

    private fun selectTab(b: FragmentNotificationBinding, index: Int) {
        val tabs = listOf(b.tabAll, b.tabBudget, b.tabReminder)
        tabs.forEachIndexed { i, textView ->
            if (i == index) {
                textView.setBackgroundResource(R.drawable.bg_toggle_selected)
                textView.setTextColor(resources.getColor(R.color.tiu_ink, null))
            } else {
                textView.setBackgroundResource(0)
                textView.setTextColor(resources.getColor(R.color.tiu_text_secondary, null))
            }
        }
    }
}

class NotificationSettingsFragment : Fragment(R.layout.fragment_notification_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentNotificationSettingsBinding.bind(view)
        val context = requireContext()

        b.btnBack.setOnClickListener { findNavController().popBackStack() }

        val isEnabled = ReminderManager.isReminderEnabled(context)
        b.switchDaily.isChecked = isEnabled

        val (hour, minute) = ReminderManager.getReminderTime(context)
        updateTimeText(b, hour, minute)

        b.switchDaily.setOnCheckedChangeListener { _, isChecked ->
            ReminderManager.setReminderEnabled(context, isChecked)
            if (isChecked) {
                ReminderManager.scheduleReminder(context)
            } else {
                ReminderManager.cancelReminder(context)
            }
        }

        b.rowDailyReminder.setOnClickListener {
            val (h, m) = ReminderManager.getReminderTime(context)
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(h)
                .setMinute(m)
                .setTitleText("Chọn giờ nhắc nhở")
                .build()

            picker.addOnPositiveButtonClickListener {
                ReminderManager.setReminderTime(context, picker.hour, picker.minute)
                updateTimeText(b, picker.hour, picker.minute)
                if (b.switchDaily.isChecked) {
                    ReminderManager.scheduleReminder(context)
                }
            }
            picker.show(childFragmentManager, "REMINDER_TIME_PICKER")
        }
    }

    private fun updateTimeText(b: FragmentNotificationSettingsBinding, hour: Int, minute: Int) {
        b.textReminderTime.text = String.format(Locale.getDefault(), "Mỗi ngày lúc %02d:%02d (Chạm để đổi giờ)", hour, minute)
    }
}
