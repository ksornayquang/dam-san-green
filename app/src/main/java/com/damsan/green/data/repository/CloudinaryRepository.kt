package com.damsan.green.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.damsan.green.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class CloudinaryRepository(private val context: Context) {

    private val client = OkHttpClient()
    private val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
    private val apiKey = BuildConfig.CLOUDINARY_API_KEY
    private val apiSecret = BuildConfig.CLOUDINARY_API_SECRET

    /**
     * Upload ảnh lên Cloudinary, trả về secure_url
     * @param imageFile File ảnh đã nén
     * @param onProgress callback % tiến trình (0-100)
     */
    suspend fun uploadImage(
        imageFile: File,
        publicIdPrefix: String = "trash",
        onProgress: ((Int) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val folder = "damsan_green"
            val publicId = "${publicIdPrefix}_${System.currentTimeMillis()}"

            // Tạo chữ ký SHA1 để xác thực
            val signatureStr = "folder=$folder&public_id=$publicId&timestamp=$timestamp${apiSecret}"
            val signature = sha1(signatureStr)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imageFile.name,
                    imageFile.asRequestBody("image/jpeg".toMediaType()))
                .addFormDataPart("api_key", apiKey)
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("signature", signature)
                .addFormDataPart("folder", folder)
                .addFormDataPart("public_id", publicId)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                throw Exception("Upload thất bại: ${response.code} - $body")
            }

            val json = JSONObject(body)
            val secureUrl = json.getString("secure_url")

            onProgress?.invoke(100)
            Result.success(secureUrl)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Nén ảnh trước khi upload để tiết kiệm băng thông
     */
    fun compressImage(sourceUri: Uri, quality: Int = 80): File {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw Exception("Không thể đọc ảnh")

        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            ?: throw Exception("Không thể giải mã ảnh")
        inputStream.close()

        // Scale xuống nếu quá lớn (max 1920px)
        val maxSize = 1920
        val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
            android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap

        val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(outputFile)
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream)
        outputStream.close()

        return outputFile
    }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val result = md.digest(input.toByteArray())
        return result.joinToString("") { "%02x".format(it) }
    }
}
