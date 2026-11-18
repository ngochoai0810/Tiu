package vn.edu.dut.tiu.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import vn.edu.dut.tiu.R
import vn.edu.dut.tiu.ui.MainActivity

class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        showNotification()
        return Result.success()
    }

    private fun showNotification() {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "tiu_daily_reminder"
        val channel = NotificationChannel(
            channelId,
            "Nhắc nhở ghi chép",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Nhắc nhở bạn ghi lại chi tiêu hàng ngày"
        }
        notificationManager.createNotificationChannel(channel)

        // Tạo Intent để khi bấm vào Notification sẽ mở MainActivity
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Có thể truyền thêm dữ liệu để MainActivity biết là mở từ Notification
            putExtra("OPEN_FROM_NOTIFICATION", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val customText = ReminderManager.getReminderText(applicationContext).takeIf { it.isNotBlank() } ?: "Chạm để thêm hoặc chụp món đồ / hóa đơn."

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // TODO: Tạo icon trắng đen nhỏ cho notification sau
            .setContentTitle("Ghi lại chi tiêu hôm nay nhé")
            .setContentText(customText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(1, notification)
            }
        } else {
            notificationManager.notify(1, notification)
        }
    }
}
