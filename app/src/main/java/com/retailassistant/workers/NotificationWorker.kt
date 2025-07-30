package com.retailassistant.workers
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.retailassistant.MainActivity
import com.retailassistant.R
import com.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
class NotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {
    private val repository: RetailRepository by inject()
    private val supabase: SupabaseClient by inject()
    companion object {
        const val WORK_NAME = "OverdueInvoiceNotifier"
        private const val CHANNEL_ID = "overdue_invoices_channel"
        private const val NOTIFICATION_ID = 101
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_COUNT_KEY = "retry_count"
    }
    override suspend fun doWork(): Result {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return Result.success() // No user, no work.
        val retryCount = inputData.getInt(RETRY_COUNT_KEY, 0)
        return try {
            // First, sync data to ensure we have the latest information
            repository.syncAllUserData(userId).getOrThrow()
            // Then, query the local database
            val overdueInvoices = repository.getInvoicesStream(userId).first().filter { it.isOverdue }
            if (overdueInvoices.isNotEmpty()) {
                showOverdueNotification(overdueInvoices.size)
            }
            Result.success()
        } catch (e: Exception) {
            // Implement retry limit to prevent infinite retries
            if (retryCount < MAX_RETRY_COUNT) {
                println("NotificationWorker failed (attempt ${retryCount + 1}/$MAX_RETRY_COUNT): ${e.message}")
                Result.retry()
            } else {
                println("NotificationWorker failed after $MAX_RETRY_COUNT attempts, giving up: ${e.message}")
                Result.failure()
            }
        }
    }
    private fun showOverdueNotification(count: Int) {
        createNotificationChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Ensure this drawable exists
            .setContentTitle("Action Required: Overdue Invoices")
            .setContentText("You have $count overdue invoice${if (count > 1) "s" else ""} requiring attention.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Cannot post notification. The app should request this permission from the user at an appropriate time.
                return
            }
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
    private fun createNotificationChannel() {
        val name = "Overdue Invoices"
        val descriptionText = "Notifications for invoices that are past their due date."
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
