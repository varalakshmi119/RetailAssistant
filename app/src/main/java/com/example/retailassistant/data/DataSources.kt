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
import com.example.retailassistant.BuildConfig
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
    entities = [Customer::class, Invoice::class, InteractionLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun invoiceDao(): InvoiceDao
    abstract fun customerDao(): CustomerDao
    abstract fun interactionLogDao(): InteractionLogDao
}
@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoices(invoices: List<Invoice>)
    @Query("SELECT * FROM invoices WHERE userId = :userId ORDER BY createdAt DESC")
    fun getInvoicesStreamForUser(userId: String): Flow<List<Invoice>>
    @Query("SELECT * FROM invoices WHERE id = :invoiceId")
    fun getInvoiceById(invoiceId: String): Flow<Invoice?>
    @Query("DELETE FROM invoices WHERE userId = :userId")
    suspend fun clearUserInvoices(userId: String)
}
@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(customers: List<Customer>)
    @Query("SELECT * FROM customers WHERE userId = :userId ORDER BY name ASC")
    fun getCustomersStreamForUser(userId: String): Flow<List<Customer>>
    @Query("SELECT * FROM customers WHERE id = :customerId")
    fun getCustomerById(customerId: String): Flow<Customer?>
    @Query("DELETE FROM customers WHERE userId = :userId")
    suspend fun clearUserCustomers(userId: String)
}
@Dao
interface InteractionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLogs(logs: List<InteractionLog>)
    @Query("SELECT * FROM interaction_logs WHERE invoiceId = :invoiceId ORDER BY createdAt DESC")
    fun getLogsForInvoice(invoiceId: String): Flow<List<InteractionLog>>
    @Query("DELETE FROM interaction_logs WHERE userId = :userId")
    suspend fun clearUserLogs(userId: String)
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
                    val reqWidth = 1080
                    val reqHeight = 1080
                    if (height > reqHeight || width > reqWidth) {
                        val halfHeight: Int = height / 2
                        val halfWidth: Int = width / 2
                        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                            inSampleSize *= 2
                        }
                    }
                    val finalOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    val bitmap = BitmapFactory.decodeStream(bitmapStream, null, finalOptions)
                    ByteArrayOutputStream().use { baos ->
                        bitmap?.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                        baos.toByteArray()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
// --- Gemini API Client ---
object GeminiApiClient {
    private const val MODEL_ID = "gemini-2.5-flash"
    private const val API_KEY = BuildConfig.GEMINI_API_KEY
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_ID:generateContent?key=$API_KEY"
    private val client by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
            engine {
                connectTimeout = 60_000
                socketTimeout = 60_000
            }
        }
    }
    suspend fun extractInvoiceData(imageBytes: ByteArray): Result<ExtractedInvoiceData> {
        return try {
            if (API_KEY.isBlank()) {
                throw Exception("Gemini API key is not configured. Please see instructions in DataSources.kt.")
            }
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val request = buildRequest(base64Image)
            val responseString: String = client.post(API_URL) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
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
                try {
                    val errorResponse = lenientJson.decodeFromString<GeminiErrorResponse>(responseString)
                    throw Exception("API Error: ${errorResponse.error.message}")
                } catch (e: Exception) {
                    throw Exception("API Error: $responseString")
                }
            }
            val cleanJsonText = extractJsonFromText(jsonText)
            val extractedData = lenientJson.decodeFromString<ExtractedInvoiceData>(cleanJsonText)
            Result.success(extractedData)
        } catch (e: Exception) {
            Result.failure(Exception("AI extraction failed: ${e.message ?: "An unknown error occurred."}"))
        }
    }
    private fun extractJsonFromText(text: String): String {
        return text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
    private fun buildRequest(base64ImageData: String): GeminiRequest {
        val prompt = """
            You are an expert AI for the "Retail Assistant" app, designed for small business owners. Your task is to analyze the provided invoice image and extract key information into a single, valid JSON object.
            Extract these fields:
            - "customer_name": The person or company being billed.
            - "date": The invoice issue date in "YYYY-MM-DD" format.
            - "due_date": The payment due date in "YYYY-MM-DD" format. If none, use the issue date.
            - "phone_number": The customer's phone number.
            - "email": The customer's email address.
            - "total_amount": The final total amount as a numeric value (e.g., 123.45).
            **CRITICAL RULES:**
            - Your output MUST be ONLY the JSON object. No extra text, comments, or markdown formatting like ```json.
            - If a value is not found, use `null` for that field.
            - The JSON must be perfectly valid.
            - `total_amount` must be a number, not a string.
            Example of a perfect response:
            {
              "customer_name": "John Appleseed",
              "date": "2025-07-26",
              "due_date": "2025-08-25",
              "phone_number": "555-123-4567",
              "email": "john.appleseed@example.com",
              "total_amount": 199.99
            }
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
        // Forcing JSON output is a powerful feature of newer Gemini models
        val generationConfig = GenerationConfig(responseMimeType = "application/json")
        return GeminiRequest(contents, generationConfig)
    }
}