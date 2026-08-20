package com.damsan.green.data.repository

import android.util.Base64
import com.damsan.green.BuildConfig
import com.damsan.green.data.model.WasteAiReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.round

class WasteAiReviewService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(18, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeTrashPhotos(beforeFile: File, afterFile: File): WasteAiReview = withContext(Dispatchers.IO) {
        if (!hasGeminiApiKey()) {
            return@withContext WasteAiReview(
                reviewStatus = STATUS_NEEDS_REVIEW,
                wasteType = "demo",
                detectedItems = 0,
                estimatedKg = 0.0,
                confidence = 0,
                reason = "Chưa cấu hình GEMINI_API_KEY nên cần admin duyệt thủ công.",
                warnings = "AI chưa bật",
                autoApproved = false
            )
        }

        runCatching {
            val beforeBase64 = Base64.encodeToString(beforeFile.readBytes(), Base64.NO_WRAP)
            val afterBase64 = Base64.encodeToString(afterFile.readBytes(), Base64.NO_WRAP)
            val requestBody = buildRequestBody(beforeBase64, afterBase64)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${BuildConfig.GEMINI_MODEL}:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    return@use failedReview("Gemini chưa phản hồi hợp lệ (${response.code}).")
                }

                parseGeminiResponse(body)
            }
        }.getOrElse { error ->
            failedReview("Không phân tích được ảnh: ${error.message.orEmpty().ifBlank { "lỗi kết nối AI" }}")
        }
    }

    private fun buildRequestBody(beforeBase64: String, afterBase64: String): String {
        val prompt = """
            Bạn là chuyên gia môi trường học đường, chuyên nhận diện rác từ ảnh hiện trường.
            Ảnh 1 là hiện trạng trước; ảnh 2 là minh chứng sau khi người dùng tới thùng rác.
            Chỉ phân tích vật thể nhìn thấy rõ trong ảnh, không suy đoán vật bị che khuất.
            Dùng ẢNH 1 làm nguồn chính để nhận dạng tên và nhóm rác; chỉ dùng ẢNH 2 để kiểm tra hành động bỏ rác.
            Không được trả "Không xác định" chỉ vì vật rác không còn nhìn rõ trong ẢNH 2.

            Quy tắc phân loại bắt buộc:
            - RECYCLABLE: chai nhựa, lon kim loại/nhôm, giấy vụn sạch, bìa carton sạch.
            - NON_RECYCLABLE: khăn giấy/giấy ăn đã dùng hoặc bẩn, túi nilon bẩn, hộp xốp, vỏ kẹo, thức ăn thừa.
            - Giấy hoặc khăn giấy nhỏ nhưng nhìn thấy rõ vẫn phải is_trash=true và gọi tên cụ thể, không trả "Không xác định" chỉ vì vật thể nhỏ.
            - Nếu có nhiều loại rác, chọn category theo vật thể rác chiếm diện tích lớn nhất.
            - Nếu ảnh không có rác, ảnh mờ, chỉ có người/cảnh vật hoặc không đủ bằng chứng: is_trash=false,
              trash_name="Không xác định", category="NON_RECYCLABLE", estimated_kg=0.
            - after_is_disposed=true nếu ảnh 2 nhìn rõ thùng và rác đã nằm trong thùng, đang ở miệng thùng,
              hoặc bàn tay đang đưa chính vật rác đó ngay trên miệng thùng trong hành động bỏ rác.
              Không chấp nhận ảnh chỉ có bàn tay, chỉ có thùng, hoặc rác đứng xa thùng.
            - confidence là độ tin cậy 0-100 của toàn bộ cặp ảnh; nếu không chắc thì dưới 70.
            - trash_name phải là tên cụ thể bằng tiếng Việt, ví dụ "Lon nước Bò Húc", "Chai nhựa PET".
            - estimated_kg là tổng khối lượng rác nhìn thấy trong ảnh, tính bằng kg và phải ước lượng thận trọng.
              Tham chiếu: mảnh giấy/vỏ kẹo 0.005-0.02 kg; lon rỗng khoảng 0.015 kg;
              chai nhựa 500 ml khoảng 0.015-0.03 kg; chai 1.5 lít khoảng 0.03-0.05 kg;
              hộp xốp khoảng 0.01-0.03 kg. Nếu có nhiều vật, cộng khối lượng các vật nhìn thấy.

            Chỉ trả về đúng một JSON thuần túy, không markdown, không khối code, không giải thích,
            không thêm khóa ngoài bốn khóa sau:
            {"is_trash":boolean,"trash_name":"Tên vật thể","category":"RECYCLABLE hoặc NON_RECYCLABLE","estimated_kg":number,"after_is_disposed":boolean,"confidence":number,"reason":"ngắn gọn"}
        """.trimIndent()

        val parts = JSONArray()
            .put(JSONObject().put("text", "ẢNH 1 - HIỆN TRẠNG TRƯỚC:"))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", beforeBase64)
                )
            )
            .put(JSONObject().put("text", "ẢNH 2 - MINH CHỨNG SAU:"))
            .put(
                JSONObject().put(
                    "inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", afterBase64)
                )
            )
            .put(JSONObject().put("text", prompt))

        val contents = JSONArray()
            .put(JSONObject().put("role", "user").put("parts", parts))

        return JSONObject()
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.15)
                    .put("maxOutputTokens", 220)
                    .put("responseMimeType", "application/json")
                    .put(
                        "responseSchema",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put(
                                "properties",
                                JSONObject()
                                    .put("is_trash", JSONObject().put("type", "BOOLEAN"))
                                    .put("trash_name", JSONObject().put("type", "STRING"))
                                    .put(
                                        "category",
                                        JSONObject()
                                            .put("type", "STRING")
                                            .put("enum", JSONArray().put(CATEGORY_RECYCLABLE).put(CATEGORY_NON_RECYCLABLE))
                                    )
                                    .put("estimated_kg", JSONObject().put("type", "NUMBER"))
                                    .put("after_is_disposed", JSONObject().put("type", "BOOLEAN"))
                                    .put("confidence", JSONObject().put("type", "NUMBER"))
                                    .put("reason", JSONObject().put("type", "STRING"))
                            )
                            .put(
                                "required",
                                JSONArray().put("is_trash").put("trash_name").put("category").put("estimated_kg").put("after_is_disposed").put("confidence").put("reason")
                            )
                    )
            )
            .toString()
    }

    private fun parseGeminiResponse(body: String): WasteAiReview {
        val text = JSONObject(body)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            .orEmpty()

        if (text.isBlank()) return failedReview("Gemini trả lời rỗng.")

        val payload = JSONObject(extractJson(text))
        val isTrash = payload.optBoolean("is_trash", false)
        val trashName = payload.optString("trash_name", "").trim().ifBlank { "Không xác định" }
        val rawCategory = payload.optString("category", "").uppercase(Locale.US)
        val category = rawCategory.takeIf { it in VALID_CATEGORIES }.orEmpty()
        val estimatedKg = payload.optDouble("estimated_kg", 0.0).sanitizeEstimatedKg(isTrash)
        val afterIsDisposed = payload.optBoolean("after_is_disposed", false)
        val confidence = payload.optInt("confidence", 0).coerceIn(0, 100)
        val classificationValid = isTrash && trashName != "Không xác định" && category.isNotBlank() && afterIsDisposed && confidence >= 70
        val wasteType = when (category) {
            CATEGORY_RECYCLABLE -> "recyclable"
            CATEGORY_NON_RECYCLABLE -> "non_recyclable"
            else -> "unclear"
        }

        return WasteAiReview(
            isTrash = isTrash,
            trashName = trashName,
            category = category,
            reviewStatus = if (classificationValid) STATUS_AUTO_APPROVED else STATUS_NEEDS_REVIEW,
            wasteType = wasteType,
            detectedItems = if (isTrash) 1 else 0,
            estimatedKg = estimatedKg,
            confidence = confidence,
            reason = payload.optString("reason").ifBlank { if (classificationValid) "Đã nhận diện $trashName và xác thực ảnh sau." else "Ảnh chưa đủ bằng chứng." },
            warnings = if (classificationValid) "" else "Cần Ban thi đua kiểm tra thủ công",
            autoApproved = classificationValid,
            afterIsDisposed = afterIsDisposed
        )
    }

    private fun extractJson(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return "{}"
    }

    private fun Double.sanitizeEstimatedKg(isTrash: Boolean): Double {
        if (!isTrash || !isFinite() || this <= 0.0) return 0.0
        return round(coerceAtMost(MAX_ESTIMATED_KG) * 1000.0) / 1000.0
    }

    private fun failedReview(reason: String) = WasteAiReview(
        reviewStatus = STATUS_FAILED,
        wasteType = "unclear",
        detectedItems = 0,
        estimatedKg = 0.0,
        confidence = 0,
        reason = reason,
        warnings = "Admin cần duyệt thủ công",
        autoApproved = false
    )

    private fun hasGeminiApiKey(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        return key.isNotBlank() &&
            !key.equals("YOUR_GEMINI_KEY", ignoreCase = true) &&
            !key.lowercase(Locale.US).contains("your")
    }

    private companion object {
        const val STATUS_AUTO_APPROVED = "auto_approved"
        const val STATUS_NEEDS_REVIEW = "needs_review"
        const val STATUS_FAILED = "failed"
        const val CATEGORY_RECYCLABLE = "RECYCLABLE"
        const val CATEGORY_NON_RECYCLABLE = "NON_RECYCLABLE"
        const val MAX_ESTIMATED_KG = 5.0
        val VALID_CATEGORIES = setOf(CATEGORY_RECYCLABLE, CATEGORY_NON_RECYCLABLE)
    }
}
