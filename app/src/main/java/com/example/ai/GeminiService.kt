package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class GeminiService(private val context: Context) {

    private val tag = "GeminiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Native Android TTS as instant fallback / complementary audio engine
    private var nativeTts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        try {
            nativeTts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val langResult = nativeTts?.setLanguage(Locale("ar"))
                    isTtsReady = langResult != TextToSpeech.LANG_MISSING_DATA && langResult != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize native TTS", e)
        }
    }

    private fun getApiKey(): String {
        return BuildConfig.GEMINI_API_KEY
    }

    /**
     * Multi-turn chat using specified model:
     * - gemini-3.5-flash (general tasks)
     * - gemini-3.1-pro-preview (complex tasks)
     * - gemini-3.1-flash-lite (fast responses)
     */
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        model: String = "gemini-3.5-flash",
        systemInstruction: String = "أنت خبير محاسبي عربي ومساعد ذكي في تطبيق دفاتر الملاحظات والفواتير. تساعد المستخدم في تنظيم الحسابات ومراجعة الدائن والمدين وحسابات الأصناف والتسعير وصياغة الفواتير بأسلوب مهني ومختصر وواضح."
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("يرجى إدخال مفتاح GEMINI_API_KEY في لوحة Secrets للذكاء الاصطناعي."))
        }

        try {
            val root = JSONObject()

            // System instruction
            val sysInstObj = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().put("text", systemInstruction))
            sysInstObj.put("parts", sysParts)
            root.put("systemInstruction", sysInstObj)

            // Contents array (Conversation history)
            val contentsArray = JSONArray()
            for (msg in messages) {
                val contentObj = JSONObject()
                contentObj.put("role", if (msg.role == "model") "model" else "user")
                val parts = JSONArray()
                parts.put(JSONObject().put("text", msg.content))
                contentObj.put("parts", parts)
                contentsArray.put(contentObj)
            }
            root.put("contents", contentsArray)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val body = root.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "Error ${response.code}"
                } catch (e: Exception) {
                    "Error ${response.code}: $responseBody"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(responseBody)
            val text = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")

            if (!text.isNullOrEmpty()) {
                Result.success(text)
            } else {
                Result.failure(Exception("لم يتم استلام نص من النموذج."))
            }
        } catch (e: Exception) {
            Log.e(tag, "Chat error", e)
            Result.failure(e)
        }
    }

    /**
     * Image Generation using gemini-3-pro-image-preview
     * User can specify image size: "1K", "2K", "4K"
     */
    suspend fun generateImage(
        prompt: String,
        imageSize: String = "1K",
        aspectRatio: String = "1:1"
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("مفتاح GEMINI_API_KEY غير مهيأ."))
        }

        try {
            val root = JSONObject()
            val contents = JSONArray()
            val contentObj = JSONObject()
            val parts = JSONArray()
            parts.put(JSONObject().put("text", prompt))
            contentObj.put("parts", parts)
            contents.put(contentObj)
            root.put("contents", contents)

            val genConfig = JSONObject()
            val responseModalities = JSONArray()
            responseModalities.put("TEXT")
            responseModalities.put("IMAGE")
            genConfig.put("responseModalities", responseModalities)

            val imgConfig = JSONObject()
            imgConfig.put("aspectRatio", aspectRatio)
            imgConfig.put("imageSize", imageSize) // 1K, 2K, 4K
            genConfig.put("imageConfig", imgConfig)
            root.put("generationConfig", genConfig)

            // As mandated in the prompt and skill
            val model = "gemini-3-pro-image-preview"
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val body = root.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("فشل إنشاء الصورة: ${response.code}"))
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val candidateParts = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")

            var foundBitmap: Bitmap? = null
            if (candidateParts != null) {
                for (i in 0 until candidateParts.length()) {
                    val part = candidateParts.getJSONObject(i)
                    val inlineData = part.optJSONObject("inlineData")
                    if (inlineData != null) {
                        val base64Data = inlineData.optString("data")
                        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        foundBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        break
                    }
                }
            }

            if (foundBitmap != null) {
                Result.success(foundBitmap)
            } else {
                Result.failure(Exception("لم يرجع النموذج صورة صالحة."))
            }
        } catch (e: Exception) {
            Log.e(tag, "Image generation error", e)
            Result.failure(e)
        }
    }

    /**
     * Text to Speech using model gemini-3.8-flash-tts
     * With automatic fallback to native Android TTS if API call fails or key is missing.
     */
    suspend fun speakText(text: String, onComplete: () -> Unit = {}): Result<Boolean> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val root = JSONObject()
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray()
                parts.put(JSONObject().put("text", "اقرأ بصوت واضح ومريح باللغة العربية: $text"))
                contentObj.put("parts", parts)
                contents.put(contentObj)
                root.put("contents", contents)

                val genConfig = JSONObject()
                val responseModalities = JSONArray()
                responseModalities.put("AUDIO")
                genConfig.put("responseModalities", responseModalities)

                val speechConfig = JSONObject()
                val voiceConfig = JSONObject()
                voiceConfig.put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Kore"))
                speechConfig.put("voiceConfig", voiceConfig)
                genConfig.put("speechConfig", speechConfig)
                root.put("generationConfig", genConfig)

                // As required by prompt: gemini-3.8-flash-tts
                val model = "gemini-3.8-flash-tts"
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val body = root.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder().url(url).post(body).build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val candidateParts = json.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")

                    if (candidateParts != null) {
                        for (i in 0 until candidateParts.length()) {
                            val part = candidateParts.getJSONObject(i)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val audioBase64 = inlineData.optString("data")
                                val mimeType = inlineData.optString("mimeType")
                                val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                                playAudioBytes(audioBytes, mimeType, onComplete)
                                return@withContext Result.success(true)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Gemini TTS remote call failed, falling back to Android TTS", e)
            }
        }

        // Fallback to Android Native TTS engine
        withContext(Dispatchers.Main) {
            try {
                nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "daftartts")
                onComplete()
            } catch (e: Exception) {
                Log.e(tag, "Native TTS playback error", e)
            }
        }
        Result.success(true)
    }

    private fun playAudioBytes(bytes: ByteArray, mimeType: String, onComplete: () -> Unit) {
        try {
            val tempFile = File.createTempFile("tts_gemini_", ".audio", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }

            val mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    it.release()
                    tempFile.delete()
                    onComplete()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error playing audio with MediaPlayer, trying PCM AudioTrack", e)
            try {
                // If raw PCM 24kHz
                val sampleRate = 24000
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bytes.size)
                    .build()
                track.play()
                track.write(bytes, 0, bytes.size)
                track.stop()
                track.release()
                onComplete()
            } catch (pEx: Exception) {
                Log.e(tag, "AudioTrack also failed", pEx)
                onComplete()
            }
        }
    }

    fun stopAudio() {
        try {
            nativeTts?.stop()
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop TTS", e)
        }
    }
}
