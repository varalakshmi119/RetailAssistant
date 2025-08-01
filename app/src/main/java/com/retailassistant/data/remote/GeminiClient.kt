package com.retailassistant.data.remote
import android.util.Base64
import com.retailassistant.BuildConfig
import com.retailassistant.data.db.ExtractedInvoiceData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
// Data classes for Gemini API serialization
@Serializable private data class GeminiRequest(val contents: List<Content>, val generationConfig: GenerationConfig)
@Serializable private data class Content(val parts: List<Part>)
@Serializable private data class Part(val text: String? = null, val inlineData: InlineData? = null)
@Serializable private data class InlineData(val mimeType: String, val data: String)
@Serializable private data class GenerationConfig(val responseMimeType: String)
@Serializable private data class GeminiResponse(val candidates: List<Candidate>)
@Serializable private data class Candidate(val content: Content)
@Serializable private data class GeminiErrorResponse(val error: ErrorBody)
@Serializable private data class ErrorBody(val message: String)
/**
 * A robust client for interacting with the Google Gemini API. It handles request
 * building, JSON response parsing, and error handling gracefully.
 */
class GeminiClient(private val httpClient: HttpClient) {
    private val modelId = "gemini-2.5-flash"
    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    suspend fun extractInvoiceData(imageBytes: ByteArray): Result<ExtractedInvoiceData> {
        return runCatching {
            if (apiKey.isBlank() || apiKey == "YOUR_API_KEY") {
                throw IllegalStateException("Gemini API key is not configured in local.properties.")
            }
            require(imageBytes.isNotEmpty()) { "Image data cannot be empty." }
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val request = buildRequest(base64Image)
            val responseString: String = httpClient.post(apiUrl) {
                contentType(ContentType.Application.Json)
                // IMPROVEMENT (Security): Sending API key in a header is more secure than a query parameter.
                header("x-goog-api-key", apiKey)
                setBody(request)
            }.body()
            parseAndDecodeResponse(responseString)
        }
    }
    private fun parseAndDecodeResponse(responseString: String): ExtractedInvoiceData {
        // IMPROVEMENT: Replaced brittle string check with a robust try-catch block.
        return try {
            val response = jsonParser.decodeFromString<GeminiResponse>(responseString)
            val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("AI response was empty or malformed.")
            // Clean the response from markdown code block markers
            val cleanedJsonText = jsonText.removeSurrounding("```json\n", "\n```").trim()
            jsonParser.decodeFromString<ExtractedInvoiceData>(cleanedJsonText)
        } catch (e: SerializationException) {
            // If decoding the success response fails, try decoding the error response.
            try {
                val errorResponse = jsonParser.decodeFromString<GeminiErrorResponse>(responseString)
                throw Exception("Gemini API Error: ${errorResponse.error.message}")
            } catch (e2: SerializationException) {
                // If both fail, the response is truly unknown.
                throw Exception("Failed to parse Gemini API response: $responseString")
            }
        }
    }
    private fun buildRequest(base64ImageData: String): GeminiRequest {
        val prompt = """
            Your task is to analyze the provided invoice image and extract key information into a single, valid JSON object.
            Extract these fields:
            - "customer_name": The person or company name being billed.
            - "date": The invoice issue date in "YYYY-MM-DD" format.
            - "due_date": The payment due date in "YYYY-MM-DD" format. If no due date is found, use the issue date.
            - "phone_number": The customer's phone number.
            - "email": The customer's email address.
            - "total_amount": The final total amount as a numeric value (e.g., 123.45 or 15000).
            **CRITICAL RULES:**
            1. Your entire output MUST BE ONLY the JSON object. No extra text, comments, markdown, or explanations.
            2. If a specific value is not found, use `null` for that field in the JSON.
            3. The JSON must be perfectly valid. `total_amount` MUST be a number, not a string. Do not include currency symbols.
        """.trimIndent()
        val contents = listOf(Content(listOf(
            Part(text = prompt),
            Part(inlineData = InlineData("image/jpeg", base64ImageData))
        )))
        val generationConfig = GenerationConfig(responseMimeType = "application/json")
        return GeminiRequest(contents, generationConfig)
    }
}
