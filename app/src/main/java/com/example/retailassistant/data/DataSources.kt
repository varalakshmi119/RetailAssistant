package com.example.retailassistant.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException

// --- Room Database & DAOs ---
@Database(
    entities = [Customer::class, Invoice::class], 
    version = 1, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun invoiceDao(): InvoiceDao
    abstract fun customerDao(): CustomerDao
}

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoices(invoices: List<Invoice>)

    @Query("SELECT * FROM invoices WHERE userId = :userId ORDER BY createdAt DESC")
    fun getInvoicesStreamForUser(userId: String): Flow<List<Invoice>>

    @Query("DELETE FROM invoices WHERE userId = :userId")
    suspend fun clearUserInvoices(userId: String)
}

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(customers: List<Customer>)

    @Query("SELECT * FROM customers WHERE userId = :userId")
    fun getCustomersStreamForUser(userId: String): Flow<List<Customer>>

    @Query("DELETE FROM customers WHERE userId = :userId")
    suspend fun clearUserCustomers(userId: String)
}

// --- Image Processing Utility ---
object ImageUtils {
    suspend fun compressImage(context: Context, imageUri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                
                context.contentResolver.openInputStream(imageUri)?.use { bitmapStream ->
                    var inSampleSize = 1
                    val (height: Int, width: Int) = options.outHeight to options.outWidth
                    
                    if (height > 1080 || width > 1080) {
                        val halfHeight: Int = height / 2
                        val halfWidth: Int = width / 2
                        
                        while (halfHeight / inSampleSize >= 1080 && halfWidth / inSampleSize >= 1080) {
                            inSampleSize *= 2
                        }
                    }
                    
                    val finalOptions = BitmapFactory.Options().apply { 
                        this.inSampleSize = inSampleSize 
                    }
                    val bitmap = BitmapFactory.decodeStream(bitmapStream, null, finalOptions)
                    
                    ByteArrayOutputStream().use { baos ->
                        bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        baos.toByteArray()
                    }
                }
            }
        } catch (e: IOException) { 
            null 
        }
    }
}
// --- Gemini API Client ---
object GeminiApiClient {
    private const val MODEL_ID = "gemini-2.5-flash"
    private const val API_KEY = "AIzaSyClNieoNdf3oTfWz1ZmI6fTsrjkp9ak7pg"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_ID:generateContent?key=$API_KEY"

    private val client by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { 
                    ignoreUnknownKeys = true
                    isLenient = true 
                })
            }
        }
    }

    suspend fun extractInvoiceData(imageBytes: ByteArray): Result<ExtractedInvoiceData> {
        return try {
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val request = buildRequest(base64Image)
            
            val responseString: String = client.post(API_URL) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            // Create a more lenient JSON parser for Gemini responses
            val lenientJson = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }

            val jsonText = if (responseString.contains("\"candidates\"")) {
                val response = lenientJson.decodeFromString<GeminiResponse>(responseString)
                response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("AI response was empty.")
            } else {
                // Try to parse as error response, but if it fails, return the raw response
                try {
                    val errorResponse = lenientJson.decodeFromString<GeminiErrorResponse>(responseString)
                    throw Exception(errorResponse.error.message)
                } catch (e: Exception) {
                    throw Exception("API Error: $responseString")
                }
            }

            // Clean and extract JSON from the response
            val cleanJsonText = extractJsonFromText(jsonText)

            val extractedData = lenientJson.decodeFromString<ExtractedInvoiceData>(cleanJsonText)
            Result.success(extractedData)
        } catch (e: Exception) {
            Result.failure(Exception("AI extraction failed: ${e.message}"))
        }
    }

    private fun extractJsonFromText(text: String): String {
        // Remove markdown code blocks
        var cleaned = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Find JSON object boundaries
        val startIndex = cleaned.indexOf('{')
        val endIndex = cleaned.lastIndexOf('}')
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            cleaned = cleaned.substring(startIndex, endIndex + 1)
        }

        return cleaned
    }

    private fun buildRequest(base64ImageData: String): GeminiRequest {
        val prompt = """
            You are an expert at extracting data from invoice images. Analyze this invoice image and extract the following information:
            
            1. Customer Name (the person/company being billed)
            2. Invoice Date (convert to YYYY-MM-DD format)
            3. Phone Number (10-digit format if available)
            
            Return ONLY a valid JSON object with these exact keys:
            {"customer_name": "extracted name or null", "date": "YYYY-MM-DD or null", "phone_number": "phone number or null"}
            
            Rules:
            - Use null for missing information.
            - Date must be in YYYY-MM-DD format.
            - No additional text or explanations.
            - Ensure valid JSON syntax.
        """.trimIndent()

        val contents = listOf(
            Content(
                "user", 
                listOf(
                    Part(text = prompt),
                    Part(inlineData = InlineData("image/jpeg", base64ImageData))
                )
            )
        )
        val generationConfig = GenerationConfig(responseMimeType = "application/json")
        return GeminiRequest(contents, generationConfig)
    }
}