package com.retailassistant.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.Invoice
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

/**
 * Utility for sharing invoice details and images via various platforms
 */
object SharingUtils {

    /**
     * Shares invoice details via WhatsApp with optional image attachment
     */
    fun shareInvoiceViaWhatsApp(
        context: Context,
        invoice: Invoice,
        customer: Customer?,
        imageBytes: ByteArray? = null,
        onError: (String) -> Unit
    ) {
        try {
            val message = buildInvoiceMessage(invoice, customer)
            
            if (imageBytes != null) {
                shareWithImage(context, message, imageBytes, customer?.phone, onError)
            } else {
                shareTextOnly(context, message, customer?.phone, onError)
            }
        } catch (e: Exception) {
            onError("Failed to share invoice: ${e.message}")
        }
    }

    /**
     * Shares invoice details via general sharing (any app)
     */
    fun shareInvoiceGeneral(
        context: Context,
        invoice: Invoice,
        customer: Customer?,
        imageBytes: ByteArray? = null,
        onError: (String) -> Unit
    ) {
        try {
            val message = buildInvoiceMessage(invoice, customer)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (imageBytes != null) "image/*" else "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                
                if (imageBytes != null) {
                    val imageUri = saveImageToCache(context, imageBytes, invoice.id)
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            
            val chooser = Intent.createChooser(intent, "Share Invoice")
            context.startActivity(chooser)
        } catch (e: Exception) {
            onError("Failed to share invoice: ${e.message}")
        }
    }

    private fun shareWithImage(
        context: Context,
        message: String,
        imageBytes: ByteArray,
        phoneNumber: String?,
        onError: (String) -> Unit
    ) {
        try {
            val imageUri = saveImageToCache(context, imageBytes, "invoice_${System.currentTimeMillis()}")
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                // Try to open WhatsApp specifically
                setPackage("com.whatsapp")
                
                // If phone number is provided, try to open specific chat
                phoneNumber?.let { phone ->
                    val cleanPhone = phone.replace(Regex("[^\\d+]"), "")
                    if (cleanPhone.isNotEmpty()) {
                        putExtra("jid", "$cleanPhone@s.whatsapp.net")
                    }
                }
            }
            
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // If WhatsApp is not available, fall back to general sharing
                shareInvoiceGeneral(context, createDummyInvoice(), null, imageBytes, onError)
            }
        } catch (e: Exception) {
            onError("Failed to share with image: ${e.message}")
        }
    }

    private fun shareTextOnly(
        context: Context,
        message: String,
        phoneNumber: String?,
        onError: (String) -> Unit
    ) {
        try {
            phoneNumber?.let { phone ->
                val cleanPhone = phone.replace(Regex("[^\\d+]"), "")
                if (cleanPhone.isNotEmpty()) {
                    // Try WhatsApp direct message
                    val whatsappIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}")
                    }
                    
                    try {
                        context.startActivity(whatsappIntent)
                        return
                    } catch (e: Exception) {
                        // Fall through to general sharing
                    }
                }
            }
            
            // General text sharing
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            
            val chooser = Intent.createChooser(intent, "Share Invoice Details")
            context.startActivity(chooser)
        } catch (e: Exception) {
            onError("Failed to share text: ${e.message}")
        }
    }

    private fun buildInvoiceMessage(invoice: Invoice, customer: Customer?): String {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        val customerName = customer?.name ?: "Valued Customer"
        
        return buildString {
            appendLine("📄 *Invoice Payment Reminder*")
            appendLine()
            appendLine("Dear $customerName,")
            appendLine()
            appendLine("This is a friendly reminder about your pending invoice:")
            appendLine()
            appendLine("💰 *Amount:* ${Utils.formatCurrency(invoice.totalAmount)}")
            appendLine("📅 *Issue Date:* ${invoice.issueDate.format(formatter)}")
            appendLine("⏰ *Due Date:* ${invoice.dueDate.format(formatter)}")
            appendLine()
            
            if (invoice.isOverdue) {
                appendLine("⚠️ *This invoice is overdue.* Please arrange payment at your earliest convenience.")
            } else {
                appendLine("Please ensure payment is made by the due date.")
            }
            
            appendLine()
            appendLine("Thank you for your business!")
            appendLine()
            appendLine("Best regards,")
            appendLine("Your Business Team")
        }
    }

    private fun saveImageToCache(context: Context, imageBytes: ByteArray, fileName: String): Uri {
        val cacheDir = File(context.cacheDir, "shared_images")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        val imageFile = File(cacheDir, "$fileName.jpg")
        FileOutputStream(imageFile).use { fos ->
            fos.write(imageBytes)
        }
        
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    // Helper function for fallback scenarios
    private fun createDummyInvoice(): Invoice {
        return Invoice(
            id = "",
            customerId = "",
            totalAmount = 0.0,
            issueDate = java.time.LocalDate.now(),
            dueDate = java.time.LocalDate.now(),
            originalScanUrl = "",
            createdAt = System.currentTimeMillis(),
            userId = ""
        )
    }
}