package com.retailassistant.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.Invoice
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.core.net.toUri

object SharingUtils {
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun sendPaymentReminderViaWhatsApp(
        context: Context,
        invoice: Invoice,
        customer: Customer?,
        imageBytes: ByteArray? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit
    ) {
        try {
            val customerPhone = customer?.phone
            if (customerPhone.isNullOrBlank()) {
                onError("Customer phone number is required for WhatsApp reminder")
                return
            }
            val cleanPhone = cleanIndianPhoneNumber(customerPhone)
            if (cleanPhone.isEmpty()) {
                onError("Invalid customer phone number format")
                return
            }

            val message = buildPaymentReminderMessage(invoice, customer)
            if (imageBytes != null) {
                sendReminderWithImageAndText(context, message, imageBytes, cleanPhone, onSuccess, onError)
            } else {
                sendReminderTextOnly(context, message, cleanPhone, onSuccess, onError)
            }
        } catch (e: Exception) {
            onError("Failed to send payment reminder: ${e.message}")
        }
    }

    fun canSendWhatsAppReminder(context: Context, customer: Customer?): Boolean {
        val hasPhone = !customer?.phone.isNullOrBlank()
        val cleanPhone = cleanIndianPhoneNumber(customer?.phone ?: "")
        val hasValidPhone = cleanPhone.isNotEmpty()
        val hasWhatsApp = isWhatsAppInstalled(context)
        return hasPhone && hasValidPhone && hasWhatsApp
    }

    fun getWhatsAppStatus(context: Context, customer: Customer?): WhatsAppStatus {
        val phone = customer?.phone
        return when {
            phone.isNullOrBlank() -> WhatsAppStatus.NO_PHONE
            cleanIndianPhoneNumber(phone).isEmpty() -> WhatsAppStatus.INVALID_PHONE
            isWhatsAppInstalled(context) -> WhatsAppStatus.APP_AVAILABLE
            else -> WhatsAppStatus.WEB_ONLY
        }
    }

    enum class WhatsAppStatus {
        APP_AVAILABLE,
        WEB_ONLY,
        NO_PHONE,
        INVALID_PHONE
    }

    private fun sendReminderWithImageAndText(
        context: Context,
        message: String,
        imageBytes: ByteArray,
        phoneNumber: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val imageUri = saveImageToCache(context, imageBytes, "payment_reminder_${System.currentTimeMillis()}")
            if (isWhatsAppInstalled(context)) {
                val success = sendImageWithTextViaWhatsAppApp(context, imageUri, message, phoneNumber)
                if (success) {
                    onSuccess()
                    return
                }
                val successAlt = sendViaWhatsAppAppAlternative(context, imageUri, message, phoneNumber)
                if (successAlt) {
                    onSuccess()
                    return
                }
            }
            openWhatsAppChatWithInstructions(context, phoneNumber, message, imageUri, onSuccess, onError)
        } catch (e: Exception) {
            onError("Failed to send reminder with image: ${e.message}")
        }
    }

    private fun sendImageWithTextViaWhatsAppApp(
        context: Context,
        imageUri: Uri,
        message: String,
        phoneNumber: String
    ): Boolean {
        val packages = listOf(WHATSAPP_BUSINESS_PACKAGE, WHATSAPP_PACKAGE)
        for (packageName in packages) {
            if (isPackageInstalled(context, packageName)) {
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        putExtra(Intent.EXTRA_TEXT, message)
                        putExtra("jid", "${phoneNumber}@s.whatsapp.net")
                        setPackage(packageName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return true
                    }
                } catch (e: Exception) {
                    // Fail silently and try next method
                }
            }
        }
        return false
    }

    private fun sendViaWhatsAppAppAlternative(
        context: Context,
        imageUri: Uri,
        message: String,
        phoneNumber: String
    ): Boolean {
        val packages = listOf(WHATSAPP_BUSINESS_PACKAGE, WHATSAPP_PACKAGE)
        for (packageName in packages) {
            if (isPackageInstalled(context, packageName)) {
                try {
                    val chatIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = "https://wa.me/$phoneNumber".toUri()
                        setPackage(packageName)
                    }
                    if (chatIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(chatIntent)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, imageUri)
                                    putExtra(Intent.EXTRA_TEXT, message)
                                    setPackage(packageName)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                // Fail silently
                            }
                        }, 1000)
                        return true
                    }
                } catch (e: Exception) {
                    // Fail silently and try next method
                }
            }
        }
        return false
    }

    private fun openWhatsAppChatWithInstructions(
        context: Context,
        phoneNumber: String,
        message: String,
        imageUri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val chatUrl = "https://wa.me/$phoneNumber?text=${Uri.encode(message)}"
            val chatIntent = Intent(Intent.ACTION_VIEW, chatUrl.toUri())
            val packages = listOf(WHATSAPP_BUSINESS_PACKAGE, WHATSAPP_PACKAGE)
            var opened = false
            for (packageName in packages) {
                if (isPackageInstalled(context, packageName)) {
                    chatIntent.setPackage(packageName)
                    if (chatIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(chatIntent)
                        opened = true
                        break
                    }
                }
            }
            if (!opened) {
                val webIntent = Intent(Intent.ACTION_VIEW, chatUrl.toUri())
                if (webIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(webIntent)
                    opened = true
                }
            }
            if (opened) {
                Toast.makeText(
                    context,
                    "WhatsApp opened. Invoice image is ready to share manually if needed.",
                    Toast.LENGTH_LONG
                ).show()
                onSuccess()
            } else {
                onError("Unable to open WhatsApp")
            }
        } catch (e: Exception) {
            onError("Failed to open WhatsApp chat: ${e.message}")
        }
    }

    private fun sendReminderTextOnly(
        context: Context,
        message: String,
        phoneNumber: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val chatUrl = "https://wa.me/$phoneNumber?text=${Uri.encode(message)}"
            if (isWhatsAppInstalled(context)) {
                val packages = listOf(WHATSAPP_BUSINESS_PACKAGE, WHATSAPP_PACKAGE)
                for (packageName in packages) {
                    if (isPackageInstalled(context, packageName)) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = chatUrl.toUri()
                                setPackage(packageName)
                            }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                                onSuccess()
                                return
                            }
                        } catch (e: Exception) {
                            continue
                        }
                    }
                }
            }
            val webIntent = Intent(Intent.ACTION_VIEW, chatUrl.toUri())
            if (webIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(webIntent)
                onSuccess()
            } else {
                onError("No browser available to open WhatsApp Web")
            }
        } catch (e: Exception) {
            onError("Failed to send text reminder: ${e.message}")
        }
    }

    private fun buildPaymentReminderMessage(invoice: Invoice, customer: Customer?): String {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        val customerName = customer?.name ?: "Valued Customer"
        val balanceDue = invoice.balanceDue
        return buildString {
            // English Version
            appendLine("💳 *Payment Reminder*")
            appendLine()
            appendLine("Hi $customerName,")
            appendLine()
            appendLine("Please find your invoice details in the attached image. Kindly review and process payment by the due date.")
            appendLine()
            when {
                invoice.isOverdue -> {
                    appendLine("🔴 *OVERDUE NOTICE*")
                    appendLine("This invoice is past due. Please settle immediately.")
                }
                balanceDue > 0 -> {
                    val daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), invoice.dueDate)
                    when {
                        daysUntilDue <= 3 -> appendLine("⚠️ Due in $daysUntilDue day(s). Please process payment soon.")
                        daysUntilDue <= 7 -> appendLine("📅 Payment due in $daysUntilDue days.")
                        else -> appendLine("📅 Kindly ensure payment by due date.")
                    }
                }
                else -> {
                    appendLine("✅ This invoice has been fully paid — thank you!")
                }
            }
            appendLine()
            appendLine("Thank you for your business! 🙏")
            appendLine()
            appendLine("---")
            appendLine()
            // Telugu Version
            appendLine("💳 *చెల్లింపు రిమైండర్*")
            appendLine()
            appendLine("హాయ్ $customerName గారు,")
            appendLine()
            appendLine("దయచేసి మీ ఇన్వాయిస్ వివరాలను చిత్రంలో చూసి, నిర్ణీత తేదీలోగా చెల్లింపు చేయండి.")
            appendLine()
            when {
                invoice.isOverdue -> {
                    appendLine("🔴 *గడువు దాటిన నోటీస్*")
                    appendLine("ఈ ఇన్వాయిస్ గడువు దాటింది. దయచేసి వెంటనే చెల్లించండి.")
                }
                balanceDue > 0 -> {
                    val daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), invoice.dueDate)
                    when {
                        daysUntilDue <= 3 -> appendLine("⚠️ $daysUntilDue రోజుల్లో గడువు. దయచేసి త్వరగా చెల్లించండి.")
                        daysUntilDue <= 7 -> appendLine("📅 $daysUntilDue రోజుల్లో చెల్లింపు గడువు.")
                        else -> appendLine("📅 దయచేసి గడువు లోపు చెల్లించండి.")
                    }
                }
                else -> {
                    appendLine("✅ ఈ ఇన్వాయిస్ పూర్తిగా చెల్లించబడింది — ధన్యవాదాలు!")
                }
            }
            appendLine()
            appendLine("మీ వ్యాపారానికి ధన్యవాదాలు! 🙏")
            appendLine()
            appendLine("Best regards,")
            appendLine("Your Business Team")
        }
    }

    private fun saveImageToCache(context: Context, imageBytes: ByteArray, fileName: String): Uri {
        val cacheDir = File(context.cacheDir, "whatsapp_reminders")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val imageFile = File(cacheDir, "$fileName.jpg")
        cleanOldCacheFiles(cacheDir)
        FileOutputStream(imageFile).use { fos ->
            fos.write(imageBytes)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    private fun cleanOldCacheFiles(cacheDir: File) {
        try {
            val files = cacheDir.listFiles() ?: return
            val currentTime = System.currentTimeMillis()
            val oneHourAgo = currentTime - (60 * 60 * 1000)
            files.filter { it.lastModified() < oneHourAgo }
                .forEach { it.delete() }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun cleanIndianPhoneNumber(phoneNumber: String): String {
        var cleaned = phoneNumber.replace(Regex("[^\\d+]"), "")
        cleaned = when {
            cleaned.startsWith("+91") && cleaned.length == 13 -> cleaned
            cleaned.startsWith("91") && cleaned.length == 12 -> "+$cleaned"
            cleaned.length == 10 && cleaned.first() in '6'..'9' -> "+91$cleaned"
            cleaned.startsWith("+9191") -> "+91${cleaned.substring(5)}"
            cleaned.startsWith("9191") -> "+91${cleaned.substring(4)}"
            else -> ""
        }
        return if (cleaned.matches(Regex("\\+91[6-9]\\d{9}"))) {
            cleaned.removePrefix("+")
        } else {
            ""
        }
    }

    private fun isWhatsAppInstalled(context: Context): Boolean {
        return isPackageInstalled(context, WHATSAPP_PACKAGE) ||
                isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}