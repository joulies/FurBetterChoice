package com.example.furbetterchoice.api

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Service for automatically uploading product photos to Cloudinary image storage.
 * Uses unsigned uploads via Cloudinary's REST API endpoint.
 */
object CloudinaryUploadService {

    private const val TAG = "CloudinaryUpload"

    // Cloudinary configuration
    var cloudName: String = "mrxubhv4"
    var uploadPreset: String = "ml_default"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun setConfig(cloudName: String, uploadPreset: String) {
        if (cloudName.isNotBlank()) this.cloudName = cloudName.trim()
        if (uploadPreset.isNotBlank()) this.uploadPreset = uploadPreset.trim()
    }

    private const val MAX_INPUT_STREAM_BYTES = 10 * 1024 * 1024 // 10 MB hard upload safety guard

    /**
     * Uploads an Image Uri (from Gallery or Camera) to Cloudinary.
     * Returns the secure HTTPS URL on success.
     */
    suspend fun uploadImageUri(
        context: Context,
        uri: Uri,
        customCloudName: String = cloudName,
        customPreset: String = uploadPreset
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            if (mimeType.isNotEmpty() && !mimeType.startsWith("image/")) {
                return@withContext Result.failure(IllegalArgumentException("Unsupported file type: $mimeType"))
            }
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Unable to open image stream from Uri"))
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Selected image is empty"))
            }
            if (bytes.size > MAX_INPUT_STREAM_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Selected image exceeds 10 MB safety limit"))
            }
            uploadImageBytes(bytes, customCloudName, customPreset)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read image Uri for Cloudinary upload: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads a Bitmap image to Cloudinary.
     * Returns the secure HTTPS URL on success.
     */
    suspend fun uploadBitmap(
        bitmap: Bitmap,
        customCloudName: String = cloudName,
        customPreset: String = uploadPreset
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, outputStream)
            val bytes = outputStream.toByteArray()
            uploadImageBytes(bytes, customCloudName, customPreset)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress bitmap for Cloudinary upload: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Compresses and/or resizes image byte array so that file size is strictly <= 1 MB (1,048,576 bytes).
     */
    fun compressBytesToUnder1Mb(bytes: ByteArray): ByteArray {
        if (bytes.size <= 1048576) return bytes
        return runCatching {
            var bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            var quality = 85
            var stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            var compressed = stream.toByteArray()

            while (compressed.size > 1048576 && quality > 20) {
                quality -= 15
                stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                compressed = stream.toByteArray()
            }

            if (compressed.size > 1048576) {
                val matrix = android.graphics.Matrix().apply { postScale(0.75f, 0.75f) }
                while (compressed.size > 1048576 && bmp.width > 200) {
                    val scaled = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                    if (scaled != bmp) bmp.recycle()
                    bmp = scaled
                    stream = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    compressed = stream.toByteArray()
                }
            }
            compressed
        }.getOrDefault(bytes)
    }

    /**
     * Uploads raw JPEG/PNG bytes to Cloudinary unsigned upload endpoint (or signed if [signature]
     * and [apiKey] are supplied by an auth backend). Enforces <= 1 MB compression and valid image bounds.
     */
    suspend fun uploadImageBytes(
        bytes: ByteArray,
        customCloudName: String = cloudName,
        customPreset: String = uploadPreset,
        apiKey: String? = null,
        timestampSeconds: Long? = null,
        signature: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetCloud = customCloudName.ifBlank { "mrxubhv4" }
        val targetPreset = customPreset.ifBlank { "ml_default" }
        val url = "https://api.cloudinary.com/v1_1/$targetCloud/image/upload"

        try {
            if (bytes.isEmpty() || bytes.size > MAX_INPUT_STREAM_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Image payload size out of bounds"))
            }
            val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return@withContext Result.failure(IllegalArgumentException("File is not a valid image"))
            }

            val finalBytes = compressBytesToUnder1Mb(bytes)
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", targetPreset)
                .addFormDataPart(
                    "file",
                    "product_${System.currentTimeMillis()}.jpg",
                    finalBytes.toRequestBody("image/jpeg".toMediaType())
                )

            if (!apiKey.isNullOrBlank() && timestampSeconds != null && !signature.isNullOrBlank()) {
                bodyBuilder.addFormDataPart("api_key", apiKey)
                bodyBuilder.addFormDataPart("timestamp", timestampSeconds.toString())
                bodyBuilder.addFormDataPart("signature", signature)
            } else {
                Log.d(TAG, "Using unsigned Cloudinary preset '$targetPreset' with strict client size/mime guards.")
            }

            val requestBody = bodyBuilder.build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful || responseBody.isBlank()) {
                Log.e(TAG, "Cloudinary REST upload failed (HTTP ${response.code}): $responseBody")
                return@withContext Result.failure(
                    IllegalStateException("Cloudinary HTTP ${response.code}: $responseBody")
                )
            }

            val json = JSONObject(responseBody)
            val secureUrl = json.optString("secure_url").ifBlank { json.optString("url") }

            if (secureUrl.isNotBlank()) {
                Log.i(TAG, "Successfully uploaded image to Cloudinary: $secureUrl")
                Result.success(secureUrl)
            } else {
                Log.e(TAG, "Cloudinary JSON response missing 'secure_url': $responseBody")
                Result.failure(IllegalStateException("Cloudinary response missing image URL"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloudinary network request failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
