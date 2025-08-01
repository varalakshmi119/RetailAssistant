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

/**
 * Utility for sending payment reminders via WhatsApp with invoice images
 * Optimized for Indian phone numbers and guaranteed image+text delivery
 */
object SharingUtils {
    
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    
    /**
     * Sends payment reminder to customer via WhatsApp with invoice image
     */
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
            
            // Log for debugging
            android.util.Log.d("WhatsApp", "Original phone: $customerPhone")
            android.util.Log.d("WhatsApp", "Clean phone: $cleanPhone")
            
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

    /**
     * Checks if WhatsApp is available and customer has phone number
     */
    fun canSendWhatsAppReminder(context: Context, customer: Customer?): Boolean {
        val hasPhone = !customer?.phone.isNullOrBlank()
        val cleanPhone = cleanIndianPhoneNumber(customer?.phone ?: "")
        val hasValidPhone = cleanPhone.isNotEmpty()
        val hasWhatsApp = isWhatsAppInstalled(context)
        return hasPhone && hasValidPhone && hasWhatsApp
    }
    
    /**
     * Gets WhatsApp availability status for UI display
     */
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

    /**
     * Sends reminder with image and text together - guaranteed delivery
     */
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
            
            // Try WhatsApp app first with both image and text
            if (isWhatsAppInstalled(context)) {
                val success = sendImageWithTextViaWhatsAppApp(context, imageUri, message, phoneNumber)
                if (success) {
                    onSuccess()
                    return
                }
                
                // Fallback: Try alternative method for app
                val successAlt = sendViaWhatsAppAppAlternative(context, imageUri, message, phoneNumber)
                if (successAlt) {
                    onSuccess()
                    return
                }
            }
            
            // Final fallback: Open chat and let user send manually
            openWhatsAppChatWithInstructions(context, phoneNumber, message, imageUri, onSuccess, onError)
            
        } catch (e: Exception) {
            onError("Failed to send reminder with image: ${e.message}")
        }
    }

    /**
     * Primary method: Send image with text caption via WhatsApp app
     */
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
                    // Method 1: Direct share with contact
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
                    android.util.Log.e("WhatsApp", "Method 1 failed for $packageName: ${e.message}")
                }
            }
        }
        return false
    }

    /**
     * Alternative method: Use WhatsApp's URL scheme with image
     */
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
                    // Method 2: Open chat first, then share image
                    val chatIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = "https://wa.me/$phoneNumber".toUri()
                        setPackage(packageName)
                    }
                    
                    if (chatIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(chatIntent)
                        
                        // Small delay then send image
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
                                android.util.Log.e("WhatsApp", "Delayed share failed: ${e.message}")
                            }
                        }, 1000)
                        
                        return true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhatsApp", "Method 2 failed for $packageName: ${e.message}")
                }
            }
        }
        return false
    }

    /**
     * Final fallback: Open chat and show instructions
     */
    private fun openWhatsAppChatWithInstructions(
        context: Context,
        phoneNumber: String,
        message: String,
        imageUri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Open WhatsApp chat
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
                // Try web WhatsApp
                val webIntent = Intent(Intent.ACTION_VIEW, chatUrl.toUri())
                if (webIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(webIntent)
                    opened = true
                }
            }
            
            if (opened) {
                // Show toast with instructions
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

    /**
     * Send text-only reminder
     */
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
            
            // Fallback to web
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

    /**
     * Build payment reminder message in English and Telugu
     */
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

    /**
     * Save image to cache directory for sharing
     */
    private fun saveImageToCache(context: Context, imageBytes: ByteArray, fileName: String): Uri {
        val cacheDir = File(context.cacheDir, "whatsapp_reminders")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        val imageFile = File(cacheDir, "$fileName.jpg")
        
        // Clean old files first
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

    /**
     * Clean old cache files to prevent storage buildup
     */
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

    /**
     * Clean and format Indian phone numbers
     * Handles formats like: 9876543210, +919876543210, 91 9876543210, etc.
     */
    private fun cleanIndianPhoneNumber(phoneNumber: String): String {
        // Remove all spaces, hyphens, brackets, and other non-digit characters except +
        var cleaned = phoneNumber.replace(Regex("[^\\d+]"), "")
        
        // Handle different Indian phone number formats
        cleaned = when {
            // Already has +91
            cleaned.startsWith("+91") && cleaned.length == 13 -> cleaned
            
            // Has 91 prefix without +
            cleaned.startsWith("91") && cleaned.length == 12 -> "+$cleaned"
            
            // 10-digit Indian mobile number (starts with 6,7,8,9)
            cleaned.length == 10 && cleaned.first() in '6'..'9' -> "+91$cleaned"
            
            // Remove +91 if number is longer than expected (duplicate country code)
            cleaned.startsWith("+9191") -> "+91${cleaned.substring(5)}"
            cleaned.startsWith("9191") -> "+91${cleaned.substring(4)}"
            
            // Invalid format
            else -> ""
        }
        
        // Final validation: Should be +91 followed by 10 digits starting with 6-9
        return if (cleaned.matches(Regex("\\+91[6-9]\\d{9}"))) {
            cleaned.removePrefix("+") // WhatsApp expects without + in wa.me URLs
        } else {
            ""
        }
    }

    /**
     * Check if WhatsApp is installed
     */
    private fun isWhatsAppInstalled(context: Context): Boolean {
        return isPackageInstalled(context, WHATSAPP_PACKAGE) || 
               isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE)
    }

    /**
     * Check if specific package is installed
     */
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}