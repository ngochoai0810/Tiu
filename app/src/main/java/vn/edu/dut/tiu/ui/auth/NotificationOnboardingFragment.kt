package vn.edu.dut.tiu.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.databinding.FragmentNotificationOnboardingBinding
import vn.edu.dut.tiu.worker.ReminderManager

class NotificationOnboardingFragment : Fragment(R.layout.fragment_notification_onboarding) {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        navigateToRegister()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentNotificationOnboardingBinding.bind(view)

        // Hiển thị giờ nhắc hiện tại
        updateTimeDisplay(binding)

        // Mở bộ chọn giờ nhắc nhở
        binding.btnChangeReminderTime.setOnClickListener {
            val (h, m) = ReminderManager.getReminderTime(requireContext())
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(h)
                .setMinute(m)
                .setTitleText("Chọn giờ nhắc nhở")
                .build()

            picker.addOnPositiveButtonClickListener {
                ReminderManager.setReminderTime(requireContext(), picker.hour, picker.minute)
                updateTimeDisplay(binding)
            }
            picker.show(childFragmentManager, "ONBOARDING_TIME_PICKER")
        }

        binding.btnEnableNotification.setOnClickListener {
            ReminderManager.setReminderEnabled(requireContext(), true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    navigateToRegister()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                navigateToRegister()
            }
        }

        binding.linkSkip.setOnClickListener {
            ReminderManager.setReminderEnabled(requireContext(), false)
            navigateToRegister()
        }
    }

    private fun updateTimeDisplay(binding: FragmentNotificationOnboardingBinding) {
        val (h, m) = ReminderManager.getReminderTime(requireContext())
        binding.textReminderTime.text = getString(R.string.tiu_notification_time_format, h, m)
    }

    private fun navigateToRegister() {
        findNavController().navigate(R.id.registerFragment)
    }
}
