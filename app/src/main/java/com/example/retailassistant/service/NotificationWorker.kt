package com.example.retailassistant.service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.retailassistant.MainActivity
import com.example.retailassistant.R
import com.example.retailassistant.data.InvoiceRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {
    private val repository: InvoiceRepository by inject()
    override suspend fun doWork(): Result {
        return try {
            repository.syncUserData().getOrThrow() // Ensure data is fresh before checking
            checkOverdueInvoices()
            Result.success()
        } catch (e: Exception) {
            // If sync fails, it's better to not send a potentially incorrect notification
            Result.failure()
        }
    }
    private suspend fun checkOverdueInvoices() {
        val overdueInvoices = repository.getInvoicesStream().first().filter { it.isOverdue }
        if (overdueInvoices.isNotEmpty()) {
            showOverdueNotification(overdueInvoices.size)
        }
    }
    private fun showOverdueNotification(count: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "overdue_invoices_channel"
        val channelName = "Overdue Invoices"
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for invoices that are past their due date."
        }
        notificationManager.createNotificationChannel(channel)
        // Create an intent to open the app when the notification is tapped
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a proper icon
            .setContentTitle("Action Required: Overdue Invoices")
            .setContentText("You have $count overdue invoice${if (count > 1) "s" else ""} requiring attention.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // Set the PendingIntent
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    companion object {
        const val NOTIFICATION_ID = 101
        const val WORK_NAME = "OverdueInvoiceNotifier"
    }
}