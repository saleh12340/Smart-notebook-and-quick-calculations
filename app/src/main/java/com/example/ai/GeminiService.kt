package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import java.text.SimpleDateFormat
import java.util.Date
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

    /**
     * جلب مفتاح Gemini API:
     * 1. من التفضيلات المحفوظة (المفتاح المخصص المثبت من قبل المستخدم)
     * 2. من BuildConfig إذا كان ممرراً
     */
    fun getApiKey(): String {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val customKey = prefs.getString("gemini_api_key", "")?.trim() ?: ""
        if (customKey.isNotEmpty()) {
            return customKey
        }
        val buildKey = try { BuildConfig.GEMINI_API_KEY.trim() } catch (e: Exception) { "" }
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    /**
     * حفظ وتثبيت مفتاح Gemini API في التفضيلات الدائمة بالجهاز
     */
    fun saveCustomApiKey(key: String) {
        val cleanKey = key.trim()
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", cleanKey).apply()
    }

    fun isApiKeyConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    private fun resolveModelName(model: String): String {
        return when (model) {
            "gemini-3.1-flash-lite", "gemini-flash-lite" -> "gemini-3.1-flash-lite-preview"
            "gemini-3.1-pro", "gemini-pro" -> "gemini-3.1-pro-preview"
            "gemini-flash" -> "gemini-flash-latest"
            "gemini-3.5-flash" -> "gemini-3.5-flash"
            else -> if (model.isBlank()) "gemini-3.5-flash" else model
        }
    }

    /**
     * فحص واختبار صحة مفتاح Gemini API
     */
    suspend fun testApiKey(apiKeyToTest: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val key = (apiKeyToTest ?: getApiKey()).trim()
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("لم يتم إدخال مفتاح API. يرجى لصق المفتاح أولاً."))
        }

        try {
            val root = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            contentObj.put("role", "user")
            val parts = JSONArray()
            parts.put(JSONObject().put("text", "مرحبا، تأكيد اتصال سريع."))
            contentObj.put("parts", parts)
            contentsArray.put(contentObj)
            root.put("contents", contentsArray)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$key"
            val body = root.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", key)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Result.success("تم التحقق بنجاح! المفتاح صحيح والذكاء الاصطناعي (Gemini) جاهز للعمل.")
            } else {
                val errMessage = parseErrorMessage(responseBody, response.code)
                Result.failure(Exception(errMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("خطأ في الاتصال بالإنترنت: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * إرسال رسالة للمساعد الذكي
     */
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        model: String = "gemini-3.5-flash",
        systemInstruction: String = "أنت خبير محاسبي عربي ومساعد ذكي في تطبيق دفاتر الملاحظات وفواتير البيع لبقالة العزي. تساعد في تنظيم الحسابات ومراجعة الدائن والمدين وحسابات الأصناف والتسعير وصياغة الفواتير ورسائل المطالبات بأسلوب مهني وواضح ودقيق."
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""

        // إذا لم يتوفر مفتاح صالح، تقديم إجابة ذكية فورية من المحرك المحاسبي المحلي المدمج
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            val localResponse = generateSmartAccountingFallback(lastUserMessage)
            return@withContext Result.success(localResponse)
        }

        try {
            val root = JSONObject()

            // System instruction
            if (systemInstruction.isNotBlank()) {
                val sysInstObj = JSONObject()
                val sysParts = JSONArray()
                sysParts.put(JSONObject().put("text", systemInstruction))
                sysInstObj.put("parts", sysParts)
                root.put("systemInstruction", sysInstObj)
            }

            // إعداد ومعالجة سجل المحادثة وفق شروط Gemini API
            val validContents = buildValidContentsArray(messages)
            if (validContents.length() == 0) {
                val localResponse = generateSmartAccountingFallback(lastUserMessage)
                return@withContext Result.success(localResponse)
            }
            root.put("contents", validContents)

            val targetModel = resolveModelName(model)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"
            val body = root.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errDetails = parseErrorMessage(responseBody, response.code)
                val fallbackText = generateSmartAccountingFallback(lastUserMessage)
                val note = "\n\n⚠️ (تنبيه الاتصال: تعذر الربط مع نموذج [$targetModel] - $errDetails. تم تقديم الإجابة عبر المحرك المحاسبي المحلي)."
                return@withContext Result.success(fallbackText + note)
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
                val fallback = generateSmartAccountingFallback(lastUserMessage)
                Result.success(fallback)
            }
        } catch (e: Exception) {
            Log.e(tag, "Chat error, switching to local engine", e)
            val fallback = generateSmartAccountingFallback(lastUserMessage)
            Result.success(fallback + "\n\nℹ️ (تمت الإجابة عبر المحرك الداخلي بدون إنترنت: ${e.localizedMessage ?: e.message}).")
        }
    }

    /**
     * بناء مصفوفة contents متوافقة 100% مع معايير Gemini API:
     * 1. إزالة الرسائل الفارغة
     * 2. ضمان أن تكون الرسالة الأولى دائماً من دور 'user'
     * 3. دمج الأدوار المتكررة المتتالية
     */
    private fun buildValidContentsArray(messages: List<ChatMessage>): JSONArray {
        val contentsArray = JSONArray()
        val meaningful = messages.filter { it.content.isNotBlank() }
        if (meaningful.isEmpty()) return contentsArray

        // العثور على أول رسالة user لبدء المحادثة
        val firstUserIndex = meaningful.indexOfFirst { it.role == "user" }
        if (firstUserIndex == -1) {
            // لا يوجد رسائل user، أضف الأخيرة كطلب
            val last = meaningful.last()
            val contentObj = JSONObject().put("role", "user")
            val parts = JSONArray().put(JSONObject().put("text", last.content))
            contentObj.put("parts", parts)
            contentsArray.put(contentObj)
            return contentsArray
        }

        var currentRole: String? = null
        var currentText = StringBuilder()

        for (i in firstUserIndex until meaningful.size) {
            val msg = meaningful[i]
            val role = if (msg.role == "model") "model" else "user"

            if (currentRole == null) {
                currentRole = role
                currentText.append(msg.content)
            } else if (currentRole == role) {
                currentText.append("\n").append(msg.content)
            } else {
                // حفظ الدور السابق
                val contentObj = JSONObject().put("role", currentRole)
                val parts = JSONArray().put(JSONObject().put("text", currentText.toString()))
                contentObj.put("parts", parts)
                contentsArray.put(contentObj)

                // بدء دور جديد
                currentRole = role
                currentText = StringBuilder(msg.content)
            }
        }

        if (currentRole != null && currentText.isNotEmpty()) {
            val contentObj = JSONObject().put("role", currentRole)
            val parts = JSONArray().put(JSONObject().put("text", currentText.toString()))
            contentObj.put("parts", parts)
            contentsArray.put(contentObj)
        }

        return contentsArray
    }

    private fun parseErrorMessage(responseBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message")
            if (!message.isNullOrBlank()) {
                "HTTP $statusCode: $message"
            } else {
                "HTTP $statusCode: خطأ في الخادم"
            }
        } catch (e: Exception) {
            "HTTP $statusCode"
        }
    }

    /**
     * محرك محاسبي ذكي محلي يعمل في كافة الظروف ويقدم إجابات وصيغ احترافية
     */
    private fun generateSmartAccountingFallback(prompt: String): String {
        val p = prompt.lowercase(Locale("ar"))
        return when {
            p.contains("مطالبة") || p.contains("رسالة") || p.contains("تحصيل") || p.contains("ودية") -> {
                """
                📨 **نموذج رسالة مطالبة ودية لسداد حساب آجل (جاهزة للنسخ والمشاركة عبر واتساب):**

                "السلام عليكم ورحمة الله وبركاته،
                الأخ العزيز / [اسم العميل] المحترم،
                تحية طيبة وبعد،،
                نحيطكم علماً بأن رصيد حسابكم المتبقي لدى **بقالة العزي** هو: [المبلغ] ريال.
                نرجو منكم التكرم بمراجعة الحساب وتأكيد السداد في أقرب فرصة مناسبة.
                شاكرين لكم حسن تعاملكم وثقتكم بنا دائماً.
                — إدارة بقالة العزي (هاتف: 776425052)"
                """.trimIndent()
            }
            p.contains("مدين") || p.contains("دائن") || p.contains("الفرق") || p.contains("قيد") || p.contains("عليه") || p.contains("له") -> {
                """
                ⚖️ **قاعدة المحاسبة الذهبية في دفتر الحسابات:**

                1. **عليه (مدين / Debit):**
                   - أي مبلغ أو بضاعة أخذها العميل وتعتبر ديناً عليه لصالح المحل.
                   - مثال: العميل أخذ أرز وسكر بقيمة 300 ريال ⬅️ نسجلها في خانة **«عليه»**.

                2. **له (دائن / Credit):**
                   - أي دفعة نقدية سددها العميل للمحل أو بضاعة مرتجعة منه.
                   - مثال: العميل سدد 200 ريال نقداً ⬅️ نسجلها في خانة **«له»**.

                3. **الرصيد التراكمي:**
                   - `الرصيد = المتبقي السابق + عليه - له`.
                   - إذا كان الرصيد موجباً: المبلغ مستحق على العميل (دين عليه).
                   - إذا كان الرصيد سالباً: العميل دفع أكثر من حسابه (مستحق له).
                """.trimIndent()
            }
            p.contains("تسعير") || p.contains("ربح") || p.contains("أرباح") -> {
                """
                📊 **نصائح تسعير البضائع لرفع أرباح البقالة:**

                1. **الأصناف الأساسية (سكر، رز، زيت):**
                   - حافظ على هامش ربح تنافسي معتدل (5% إلى 8%) لضمان سرعة دوران البضاعة وجذب الزبائن.
                2. **الأصناف التكميلية والحلويات والمنظفات:**
                   - يمكن رفع هامش الربح فيها إلى (15% إلى 25%) لأن الزبون لا يدقق في أسعارها كثيراً.
                3. **حساب سعر البيع تلقائياً:**
                   - سعر البيع = سعر الشراء الكلي ÷ العدد الكلي + هامش الربح المطلوب.
                """.trimIndent()
            }
            p.contains("جرد") || p.contains("تنظيم") || p.contains("نصائح") -> {
                """
                📋 **إرشادات الإدارة المالية الناجحة للبقالة:**

                1. **فصل حسابات البيت عن البقالة:** لا تأخذ أي صنف للاستخدام الشخصي دون تسجيله في الدفتر.
                2. **تصفية الحسابات الدورية:** حدد موعداً أسبوعياً أو شهرياً لسداد حسابات الآجل مع العملاء.
                3. **النسخ الاحتياطي:** استخدم زر الإعدادات ⚙️ لعمل نسخة احتياطية دورية وحفظها في Google Drive أو الواتساب لحماية حساباتك من الضياع.
                """.trimIndent()
            }
            else -> {
                """
                💡 **المساعد المحاسبي لبقالة العزي:**

                لقد تم استلام استفسارك: "$prompt".
                - يسعدني مساعدتك في صياغة الفواتير، تدقيق أرصدة العملاء، مراجعة العمليات الحسابية، وصياغة رسائل المطالبات والمتابعة.
                
                📌 *ملاحظة:* لتفعيل التوليد المتقدم عبر خوادم Google الذكية، يمكنك إدخال مفتاح Gemini API الخاص بك من زر المفتاح 🔑 في أعلى الشاشة أو من الإعدادات ⚙️.
                """.trimIndent()
            }
        }
    }

    /**
     * تفسير أمر محاسبي بالذكاء الاصطناعي
     */
    suspend fun interpretAccountingCommand(prompt: String, model: String = "gemini-3.5-flash"): Result<JSONObject> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext Result.success(localCommandParser(prompt))
        try {
            val targetModel = resolveModelName(model)
            val system = "أنت محرك أوامر محاسبية لتطبيق أندرويد عربي. أعد JSON فقط بدون Markdown. العمليات: create_invoice {action,customer,paymentType:CASH|CREDIT,items:[{description,quantity,unitPrice,totalAmount}]}; create_account {action,customer,openingBalance}; add_account_entry {action,customer,description,debit,credit}; create_note {action,title,text}; repair_data {action}; unknown {action,message}. لا تخترع أرقاماً غير موجودة في طلب المستخدم."
            val root = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"
            val response = client.newCall(Request.Builder().url(url).addHeader("x-goog-api-key", apiKey).post(root.toString().toRequestBody(jsonMediaType)).build()).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("تعذر تنفيذ الأمر الذكي: HTTP " + response.code))
            val text = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty().trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            Result.success(JSONObject(text))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun localCommandParser(prompt: String): JSONObject {
        val p = prompt.trim()
        val lower = p.lowercase(Locale("ar"))
        val customerRegex = Regex("(?:للعميل|حساب|العميل)\\s*[:：]?\\s*([^،,:؛\\n]+)", RegexOption.IGNORE_CASE)
        val customer = customerRegex.find(p)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        return when {
            lower.contains("أنشئ حساب") || lower.contains("انشئ حساب") || lower.contains("ابدأ حساب") -> {
                val amount = Regex("(?<!\\d)(\\d+(?:[.,]\\d+)?)").find(p)?.value?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                JSONObject().put("action","create_account").put("customer",customer).put("openingBalance",amount)
            }
            lower.contains("أنشئ فاتورة") || lower.contains("انشئ فاتورة") || lower.contains("فاتورة بيع") -> {
                val nums = Regex("(?<!\\d)(\\d+(?:[.,]\\d+)?)").findAll(p).map { it.value.replace(",", "").toDoubleOrNull() ?: 0.0 }.toList()
                val quantity = nums.getOrNull(0) ?: 1.0
                val price = nums.getOrNull(1) ?: 0.0
                val item = p.substringAfter("الصنف", "").substringAfter("صنف", "").substringBefore("الكمية").trim()
                JSONObject().put("action","create_invoice").put("customer",customer).put("paymentType",if (lower.contains("آجل") || lower.contains("اجل")) "CREDIT" else "CASH").put("items",JSONArray().put(JSONObject().put("description",if (item.isBlank()) "صنف" else item).put("quantity",quantity).put("unitPrice",price).put("totalAmount",quantity*price)))
            }
            lower.contains("اصلح") || lower.contains("إصلاح") || lower.contains("دقق") -> JSONObject().put("action","repair_data")
            else -> JSONObject().put("action","unknown").put("message","لم أفهم أمراً تنفيذياً واضحاً. استخدم: أنشئ فاتورة، أنشئ حساب، أضف حركة، أصلح الحسابات.")
        }
    }

    /**
     * Image Generation using gemini-2.5-flash-image
     */
    suspend fun generateImage(
        prompt: String,
        imageSize: String = "1K",
        aspectRatio: String = "1:1"
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            val localStamp = generateLocalStoreStampBitmap(prompt)
            return@withContext Result.success(localStamp)
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
            imgConfig.put("imageSize", imageSize)
            genConfig.put("imageConfig", imgConfig)
            root.put("generationConfig", genConfig)

            val model = "gemini-2.5-flash-image"
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val body = root.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).addHeader("x-goog-api-key", apiKey).post(body).build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val localStamp = generateLocalStoreStampBitmap(prompt)
                return@withContext Result.success(localStamp)
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
                val localStamp = generateLocalStoreStampBitmap(prompt)
                Result.success(localStamp)
            }
        } catch (e: Exception) {
            Log.e(tag, "Image generation error, using local stamp", e)
            val localStamp = generateLocalStoreStampBitmap(prompt)
            Result.success(localStamp)
        }
    }

    /**
     * توليد ختم تجاري دائري رسمي عالي الدقة محلياً
     */
    private fun generateLocalStoreStampBitmap(title: String): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val stampRed = Color.rgb(220, 38, 38)
        val borderPaint = Paint().apply {
            color = stampRed
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
        }

        val innerBorderPaint = Paint().apply {
            color = stampRed
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = stampRed
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val center = size / 2f
        canvas.drawCircle(center, center, center - 20f, borderPaint)
        canvas.drawCircle(center, center, center - 35f, innerBorderPaint)

        val storeName = if (title.isNotBlank() && title.length < 25) title else "بقالة العزي للتجارة"
        canvas.drawText(storeName, center, center - 60f, textPaint)

        textPaint.textSize = 28f
        canvas.drawText("★ معتمد وموثق ★", center, center + 10f, textPaint)

        textPaint.textSize = 22f
        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
        canvas.drawText("هاتف: 776425052", center, center + 70f, textPaint)
        canvas.drawText("التاريخ: $dateStr", center, center + 110f, textPaint)

        return bitmap
    }

    suspend fun textToSpeech(text: String): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            if (isTtsReady && nativeTts != null) {
                nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gemini_tts_${System.currentTimeMillis()}")
                Result.success(Unit)
            } else {
                Result.failure(Exception("محرك النطق الصوتي غير متاح في جهازك حالياً."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun speakText(text: String, onComplete: () -> Unit = {}) {
        try {
            if (isTtsReady && nativeTts != null) {
                nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gemini_tts_${System.currentTimeMillis()}")
            }
        } catch (ignored: Exception) {}
        onComplete()
    }

    fun stopAudio() {
        try {
            nativeTts?.stop()
        } catch (ignored: Exception) {}
    }

    fun shutdown() {
        try {
            nativeTts?.stop()
            nativeTts?.shutdown()
        } catch (ignored: Exception) {}
    }
}
