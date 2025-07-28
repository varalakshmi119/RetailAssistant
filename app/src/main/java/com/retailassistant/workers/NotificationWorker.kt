package com.retailassistant.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.retailassistant.R
import com.retailassistant.MainActivity
import com.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val repository: RetailRepository by inject()
    private val supabase: SupabaseClient by inject()

    companion object {
        const val WORK_NAME = "OverdueInvoiceNotifier"
        private const val CHANNEL_ID = "overdue_invoices_channel"
        private const val NOTIFICATION_ID = 101
    }

    override suspend fun doWork(): Result {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return Result.success()

        return try {
            // Ensure we have the latest data before checking.
            repository.syncAllUserData(userId).getOrThrow()
            val overdueInvoices = repository.getInvoicesStream(userId).first().filter { it.isOverdue }

            if (overdueInvoices.isNotEmpty()) {
                showOverdueNotification(overdueInvoices.size)
            }
            Result.success()
        } catch (e: Exception) {
            // If sync fails (e.g., no internet), retry later as per WorkManager policy.
            Result.retry()
        }
    }

    private fun showOverdueNotification(count: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(CHANNEL_ID, "Overdue Invoices", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifications for invoices that are past their due date."
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Ensure this is a monochrome icon
            .setContentTitle("Action Required: Overdue Invoices")
            .setContentText("You have $count overdue invoice${if (count > 1) "s" else ""} requiring attention.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
