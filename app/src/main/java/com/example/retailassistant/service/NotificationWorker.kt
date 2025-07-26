package com.example.retailassistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.retailassistant.R
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.data.InvoiceStatus
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val repository: InvoiceRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            checkOverdueInvoices()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun checkOverdueInvoices() {
        val invoices = repository.getInvoicesStream().first()
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val overdueInvoices = invoices.filter { invoice ->
            invoice.status == InvoiceStatus.UNPAID &&
            try {
                val issueDate = LocalDate.parse(invoice.issueDate, formatter)
                issueDate.isBefore(today.minusDays(30)) // Consider 30+ days as overdue
            } catch (e: Exception) {
                false
            }
        }

        if (overdueInvoices.isNotEmpty()) {
            showOverdueNotification(overdueInvoices.size)
        }
    }

    private fun showOverdueNotification(count: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android 8.0+
        val channelId = "overdue_invoices"
        val channel = NotificationChannel(
            channelId,
            "Overdue Invoices",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for overdue invoices"
        }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Overdue Invoices")
            .setContentText("You have $count overdue invoice${if (count > 1) "s" else ""}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }
}