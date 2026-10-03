package com.example.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.db.DaftarDao
import com.example.data.storage.AppStorageHelper
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentEntryEntity
import com.example.data.model.DocumentType
import com.example.data.model.PaymentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupHelper {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
    private const val KEY_LAST_BACKUP_TIME = "last_backup_timestamp"

    fun isAutoBackupEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_BACKUP, true) // مفعل افتراضياً لحماية بيانات المحل
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    fun getLastBackupTimeString(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
        if (time == 0L) return "لا توجد نسخة سابقة"
        val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))
        return sdf.format(Date(time))
    }

    private fun updateLastBackupTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis()).apply()
        AppStorageHelper.ensureFolders(context)
    }

    /**
     * تصدير كافة الفواتير والقيود من قاعدة البيانات إلى نص بتنسيق JSON
     */
    suspend fun createBackupJson(dao: DaftarDao, storeName: String): String = withContext(Dispatchers.IO) {
        val docs = dao.getAllDocumentsDirect()
        val root = JSONObject()
        root.put("app", "دفتر الفواتير والحسابات")
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())
        root.put("storeName", storeName)

        val docsArray = JSONArray()
        for (docWithEntries in docs) {
            val doc = docWithEntries.document
            val docObj = JSONObject().apply {
                put("id", doc.id)
                put("docNumber", doc.docNumber)
                put("title", doc.title)
                put("customerName", doc.customerName)
                put("docType", doc.docType.name)
                put("paymentType", doc.paymentType.name)
                put("storeName", doc.storeName)
                put("storeAddress", doc.storeAddress)
                put("storePhone", doc.storePhone)
                put("commercialReg", doc.commercialReg)
                put("poBox", doc.poBox)
                put("fax", doc.fax)
                put("dateString", doc.dateString)
                put("dayString", doc.dayString)
                put("notes", doc.notes)
                put("stampImageUrl", doc.stampImageUrl ?: "")
                put("buyerSignature", doc.buyerSignature)
                put("sellerSignature", doc.sellerSignature)
                put("createdAt", doc.createdAt)
                put("updatedAt", doc.updatedAt)

                val entriesArray = JSONArray()
                for (entry in docWithEntries.entries) {
                    val entryObj = JSONObject().apply {
                        put("description", entry.description)
                        put("quantity", entry.quantity)
                        put("unitPrice", entry.unitPrice)
                        put("totalAmount", entry.totalAmount)
                        put("debit", entry.debit)
                        put("credit", entry.credit)
                        put("runningBalance", entry.runningBalance)
                        put("entryDate", entry.entryDate)
                        put("entryDay", entry.entryDay)
                    }
                    entriesArray.put(entryObj)
                }
                put("entries", entriesArray)
            }
            docsArray.put(docObj)
        }

        root.put("documents", docsArray)
        root.toString(2)
    }

    /**
     * تنفيذ النسخ الاحتياطي التلقائي وحفظه في مساحة التخزين الداخلية الآمنة
     */
    suspend fun performAutoBackupIfEnabled(context: Context, dao: DaftarDao, storeName: String) = withContext(Dispatchers.IO) {
        if (!isAutoBackupEnabled(context)) return@withContext
        try {
            val json = createBackupJson(dao, storeName)
            val dir = File(context.filesDir, "backups")
            if (!dir.exists()) dir.mkdirs()
            val backupFile = File(dir, "auto_backup_latest.json")
            backupFile.writeText(json)

            AppStorageHelper.saveBytesToDownloads(
                context = context,
                bytes = json.toByteArray(Charsets.UTF_8),
                fileName = "auto_backup_latest.json",
                mimeType = "application/json",
                subFolder = AppStorageHelper.BACKUPS_FOLDER
            )
            updateLastBackupTime(context)
        } catch (ignored: Exception) {}
    }

    /**
     * مشاركة ملف النسخة الاحتياطية اليدوية عبر واتساب، جوجل درايف، أو حفظها في الملفات
     */
    suspend fun exportManualBackup(context: Context, dao: DaftarDao, storeName: String) = withContext(Dispatchers.IO) {
        val json = createBackupJson(dao, storeName)
        val timeStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val fileName = "Daftar_Backup_$timeStr.json"
        val publicUri = AppStorageHelper.saveBytesToDownloads(
            context = context,
            bytes = json.toByteArray(Charsets.UTF_8),
            fileName = fileName,
            mimeType = "application/json",
            subFolder = AppStorageHelper.BACKUPS_FOLDER
        ) ?: return@withContext
        updateLastBackupTime(context)

        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, publicUri)
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - $storeName")
                putExtra(Intent.EXTRA_TEXT, "نسخة احتياطية محفوظة في مجلد التطبيق داخل Downloads بتاريخ: $timeStr")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "مشاركة النسخة الاحتياطية")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    /**
     * استرجاع البيانات من نص أو ملف JSON
     */
    suspend fun restoreFromJson(context: Context, dao: DaftarDao, jsonString: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val docsArray = root.getJSONArray("documents")
            var restoredCount = 0

            for (i in 0 until docsArray.length()) {
                val docObj = docsArray.getJSONObject(i)
                val docEntity = DocumentEntity(
                    docNumber = docObj.optString("docNumber", "0000001"),
                    title = docObj.optString("title", ""),
                    customerName = docObj.optString("customerName", ""),
                    docType = try { DocumentType.valueOf(docObj.getString("docType")) } catch (e: Exception) { DocumentType.SALES_INVOICE },
                    paymentType = try { PaymentType.valueOf(docObj.getString("paymentType")) } catch (e: Exception) { PaymentType.CASH },
                    storeName = docObj.optString("storeName", "بقالة العزي"),
                    storeAddress = docObj.optString("storeAddress", "السوق العام"),
                    storePhone = docObj.optString("storePhone", "776425052"),
                    commercialReg = docObj.optString("commercialReg", ""),
                    poBox = docObj.optString("poBox", ""),
                    fax = docObj.optString("fax", ""),
                    dateString = docObj.optString("dateString", ""),
                    dayString = docObj.optString("dayString", ""),
                    notes = docObj.optString("notes", ""),
                    stampImageUrl = docObj.optString("stampImageUrl").takeIf { it.isNotEmpty() },
                    buyerSignature = docObj.optString("buyerSignature", ""),
                    sellerSignature = docObj.optString("sellerSignature", "المحاسب"),
                    createdAt = docObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = docObj.optLong("updatedAt", System.currentTimeMillis())
                )

                val entriesArray = docObj.optJSONArray("entries") ?: JSONArray()
                val entriesList = mutableListOf<DocumentEntryEntity>()

                for (j in 0 until entriesArray.length()) {
                    val entryObj = entriesArray.getJSONObject(j)
                    entriesList.add(
                        DocumentEntryEntity(
                            description = entryObj.optString("description", ""),
                            quantity = entryObj.optDouble("quantity", 1.0),
                            unitPrice = entryObj.optDouble("unitPrice", 0.0),
                            totalAmount = entryObj.optDouble("totalAmount", 0.0),
                            debit = entryObj.optDouble("debit", 0.0),
                            credit = entryObj.optDouble("credit", 0.0),
                            runningBalance = entryObj.optDouble("runningBalance", 0.0),
                            entryDate = entryObj.optString("entryDate", ""),
                            entryDay = entryObj.optString("entryDay", "")
                        )
                    )
                }

                dao.saveDocumentWithEntries(docEntity, entriesList)
                restoredCount++
            }

            updateLastBackupTime(context)
            Result.success(restoredCount)
        } catch (e: Exception) {
            Result.failure(Exception("الملف غير صالح أو تالف: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * استرجاع النسخة الاحتياطية التلقائية الأخيرة المحفوظة داخلياً
     */
    suspend fun restoreLatestAutoBackup(context: Context, dao: DaftarDao): Result<Int> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups")
        val backupFile = File(dir, "auto_backup_latest.json")
        if (!backupFile.exists()) {
            return@withContext Result.failure(Exception("لم يتم العثور على نسخة احتياطية تلقائية سابقة."))
        }
        val json = backupFile.readText()
        restoreFromJson(context, dao, json)
    }
}
