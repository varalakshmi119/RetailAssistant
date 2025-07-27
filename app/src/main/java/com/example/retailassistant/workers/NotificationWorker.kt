package com.example.retailassistant.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.retailassistant.MainActivity
import com.example.retailassistant.R
import com.example.retailassistant.data.repository.RetailRepository
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
        const val NOTIFICATION_ID = 101
        const val WORK_NAME = "OverdueInvoiceNotifier"
        const val WORK_TAG = "NotificationWorker"
        private const val CHANNEL_ID = "overdue_invoices_channel"
        private const val TAG = "NotificationWorker"
    }

    override suspend fun doWork(): Result {
        // Only run the worker if a user is logged in.
        if (supabase.auth.currentUserOrNull() == null) {
            Log.i(TAG, "No user logged in. Skipping work.")
            return Result.success() // Not an error, just nothing to do.
        }

        return try {
            // First, sync data to ensure we are checking against the latest information.
            repository.syncAllUserData().getOrThrow()
            checkOverdueInvoices()
            Log.i(TAG, "Work finished successfully.")
            Result.success()
        } catch (e: Exception) {
            // If sync fails (e.g., no internet), it's better to retry later
            // than to send a potentially incorrect notification based on stale data.
            Log.e(TAG, "Work failed, will retry.", e)
            Result.retry()
        }
    }

    private suspend fun checkOverdueInvoices() {
        val overdueInvoices = repository.getInvoicesStream().first().filter { it.isOverdue }
        if (overdueInvoices.isNotEmpty()) {
            showOverdueNotification(overdueInvoices.size)
        } else {
            Log.i(TAG, "No overdue invoices found.")
        }
    }

    private fun showOverdueNotification(count: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelName = "Overdue Invoices"

        // Create the notification channel (idempotent)
        val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
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
