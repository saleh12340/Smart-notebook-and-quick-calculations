package com.example.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import com.example.data.model.PaymentType
import com.example.data.storage.AppStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

object ThermalPrintHelper {

    // المعرف القياسي للاتصال بالطابعات الحرارية عبر البلوتوث (Serial Port Profile - SPP)
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * الحصول على قائمة الطابعات المقترنة بالجهاز عبر البلوتوث
     */
    @SuppressLint("MissingPermission")
    fun getPairedBluetoothPrinters(context: Context): List<BluetoothDevice> {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * طباعة الفاتورة عبر البلوتوث مباشرة باستخدام أمر الصورة النقطية (Raster ESC/POS Bitmap)
     * يضمن طباعة الحروف العربية مشبوكة وصحيحة بنسبة 100% على كافة الطابعات الحرارية الصينية والعالمية
     */
    @SuppressLint("MissingPermission")
    suspend fun printReceiptViaBluetooth(
        device: BluetoothDevice,
        docWithEntries: DocumentWithEntries,
        is80mm: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            outputStream = socket.outputStream

            // 1. تهيئة الطابعة ESC @
            outputStream.write(byteArrayOf(0x1B, 0x40))

            // 2. إنشاء صورة إيصال نقية مطبوعة كصورة
            val bitmap = generateReceiptBitmap(docWithEntries, is80mm)

            // 3. تحويل الصورة إلى أوامر نقطية ESC/POS (GS v 0)
            val escPosBytes = bitmapToEscPosRaster(bitmap)
            outputStream.write(escPosBytes)

            // 4. تغذية الورق 4 أسطر وقطع الورق
            outputStream.write(byteArrayOf(0x1B, 0x64, 0x04)) // تغذية 4 أسطر
            outputStream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // قطع الورق جزئياً

            outputStream.flush()
            Result.success("تم إرسال الفاتورة بنجاح إلى الطابعة: ${device.name ?: "طابعة حرارية"}")
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الاتصال بالطابعة: ${e.localizedMessage ?: e.message}"))
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * رسم الإيصال كصورة مطبوعة محاكية لأجهزة الكاشير ونظام البيان تماماً كما في الصورة
     */
    fun generateReceiptBitmap(docWithEntries: DocumentWithEntries, is80mm: Boolean = false): Bitmap {
        val doc = docWithEntries.document
        val entries = docWithEntries.entries.filter { it.description.isNotBlank() || it.totalAmount > 0.0 || it.debit > 0.0 || it.credit > 0.0 }
            .ifEmpty { docWithEntries.entries.take(1) }

        val width = if (is80mm) 576 else 384
        val pad = 12f

        // تقدير الارتفاع بناء على عدد الأصناف
        val estimatedHeight = 420 + (entries.size * 34) + 180
        val bitmap = Bitmap.createBitmap(width, estimatedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val boldPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textSize = if (is80mm) 26f else 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val regularPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textSize = if (is80mm) 17f else 14f
            textAlign = Paint.Align.CENTER
        }

        val linePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        val boxPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }

        var y = 32f

        // 1. الترويسة الرئيسية: اسم المتجر والشعار
        boldPaint.textSize = if (is80mm) 28f else 24f
        canvas.drawText(doc.storeName.ifEmpty { "بقالة العزي للتجارة" }, width / 2f, y, boldPaint)
        y += 24f

        regularPaint.textSize = if (is80mm) 16f else 13f
        regularPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("خدمات تجارية - مواد غذائية - جملة وتجزئة", width / 2f, y, regularPaint)
        y += 22f

        regularPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val phoneStr = if (doc.storePhone.isNotEmpty()) doc.storePhone else "776425052"
        val regStr = if (doc.commercialReg.isNotEmpty()) doc.commercialReg else "22510"
        canvas.drawText("س.ت: $regStr • هاتف: $phoneStr", width / 2f, y, regularPaint)
        y += 22f

        // 2. مربعات معلومات الفاتورة المؤطرة (كما في صورة الكاشير تماماً)
        val boxHeight = 28f
        val boxWidth3 = (width - 2 * pad - 8f) / 3f

        // السطر الأول من المربعات: [ زبون نقدي ] [ مبيع ] [ نقداً ]
        val cName = if (doc.customerName.isNotEmpty()) doc.customerName.take(12) else "زبون نقدي"
        val pType = if (doc.paymentType == PaymentType.CASH) "نقداً" else "آجل"
        val docTypeTitle = when (doc.docType) {
            DocumentType.SALES_INVOICE -> "مبيع"
            DocumentType.CUSTOMER_LEDGER -> "حساب"
            DocumentType.LINED_NOTE -> "ملاحظة"
        }

        // رسم المربعات الثلاثة بزوايا دائرية
        // المربع الأيمن (الزبون)
        val r1 = android.graphics.RectF(width - pad - boxWidth3, y, width - pad, y + boxHeight)
        canvas.drawRoundRect(r1, 8f, 8f, boxPaint)
        canvas.drawText(cName, r1.centerX(), r1.centerY() + 5f, regularPaint)

        // المربع الأوسط (نوع السند: مبيع)
        val r2 = android.graphics.RectF(r1.left - 4f - boxWidth3, y, r1.left - 4f, y + boxHeight)
        canvas.drawRoundRect(r2, 8f, 8f, boxPaint)
        canvas.drawText(docTypeTitle, r2.centerX(), r2.centerY() + 5f, regularPaint)

        // المربع الأيسر (طريقة الدفع: نقداً/آجل)
        val r3 = android.graphics.RectF(pad, y, pad + boxWidth3, y + boxHeight)
        canvas.drawRoundRect(r3, 8f, 8f, boxPaint)
        canvas.drawText(pType, r3.centerX(), r3.centerY() + 5f, regularPaint)

        y += boxHeight + 6f

        // السطر الثاني من المربعات: [ رقم : 1 ] [ 2026/10/03 ] [ الوقت ]
        val timeStr = java.text.SimpleDateFormat("hh:mm a", Locale.US).format(java.util.Date())
            .replace("AM", "ص").replace("PM", "م")
        val dateOnlyStr = if (doc.dateString.isNotEmpty()) doc.dateString else java.text.SimpleDateFormat("yyyy/MM/dd", Locale.US).format(java.util.Date())

        val r4 = android.graphics.RectF(width - pad - boxWidth3, y, width - pad, y + boxHeight)
        canvas.drawRoundRect(r4, 8f, 8f, boxPaint)
        canvas.drawText("رقم : ${doc.docNumber.takeLast(6)}", r4.centerX(), r4.centerY() + 5f, regularPaint)

        val r5 = android.graphics.RectF(r4.left - 4f - boxWidth3, y, r4.left - 4f, y + boxHeight)
        canvas.drawRoundRect(r5, 8f, 8f, boxPaint)
        canvas.drawText(dateOnlyStr, r5.centerX(), r5.centerY() + 5f, regularPaint)

        val r6 = android.graphics.RectF(pad, y, pad + boxWidth3, y + boxHeight)
        canvas.drawRoundRect(r6, 8f, 8f, boxPaint)
        canvas.drawText(timeStr, r6.centerX(), r6.centerY() + 5f, regularPaint)

        y += boxHeight + 10f

        // 3. جدول الأصناف الشبكي الكامل مع حدود الخلايا (Grid Table)
        val tableLeft = pad
        val tableRight = width - pad
        val tableTop = y
        val rowHeight = 28f

        if (doc.docType == DocumentType.SALES_INVOICE) {
            // توزيع أعمدة الجدول: م (أقصى اليمين) | المادة | الكمية | السعر | الإجمالي (أقصى اليسار)
            val colMWidth = 28f
            val colTotalWidth = if (is80mm) 95f else 75f
            val colPriceWidth = if (is80mm) 85f else 65f
            val colQtyWidth = if (is80mm) 65f else 50f

            val xM = tableRight - colMWidth
            val xQty = tableLeft + colTotalWidth + colPriceWidth
            val xPrice = tableLeft + colTotalWidth
            val xTotal = tableLeft

            // رسم ترويسة الجدول
            val headerRect = android.graphics.RectF(tableLeft, y, tableRight, y + rowHeight)
            canvas.drawRect(headerRect, boxPaint)

            boldPaint.textSize = if (is80mm) 14f else 12f
            canvas.drawText("م", tableRight - (colMWidth / 2f), y + 20f, boldPaint)
            canvas.drawText("المــــادة", (xM + xQty + colQtyWidth) / 2f, y + 20f, boldPaint)
            canvas.drawText("الكمية", xQty + (colQtyWidth / 2f), y + 20f, boldPaint)
            canvas.drawText("السعر", xPrice + (colPriceWidth / 2f), y + 20f, boldPaint)
            canvas.drawText("الإجمالي", xTotal + (colTotalWidth / 2f), y + 20f, boldPaint)

            // رسم الخطوط العمودية للترويسة
            canvas.drawLine(xM, y, xM, y + rowHeight, linePaint)
            canvas.drawLine(xQty + colQtyWidth, y, xQty + colQtyWidth, y + rowHeight, linePaint)
            canvas.drawLine(xPrice + colPriceWidth, y, xPrice + colPriceWidth, y + rowHeight, linePaint)
            canvas.drawLine(xPrice, y, xPrice, y + rowHeight, linePaint)

            y += rowHeight

            // أسطر الأصناف
            regularPaint.textSize = if (is80mm) 15f else 12.5f
            var totalQtyCount = 0.0

            entries.forEachIndexed { idx, item ->
                val rItem = android.graphics.RectF(tableLeft, y, tableRight, y + rowHeight)
                canvas.drawRect(rItem, boxPaint)

                // الخطوط العمودية للسطر
                canvas.drawLine(xM, y, xM, y + rowHeight, linePaint)
                canvas.drawLine(xQty + colQtyWidth, y, xQty + colQtyWidth, y + rowHeight, linePaint)
                canvas.drawLine(xPrice + colPriceWidth, y, xPrice + colPriceWidth, y + rowHeight, linePaint)
                canvas.drawLine(xPrice, y, xPrice, y + rowHeight, linePaint)

                // القيم
                canvas.drawText("${idx + 1}", tableRight - (colMWidth / 2f), y + 21f, regularPaint)
                val descStr = item.description.ifEmpty { "صنف عام" }.take(14)
                canvas.drawText(descStr, (xM + xQty + colQtyWidth) / 2f, y + 21f, regularPaint)

                val qStr = if (item.quantity % 1 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.1f", item.quantity)
                canvas.drawText(qStr, xQty + (colQtyWidth / 2f), y + 21f, regularPaint)
                totalQtyCount += item.quantity

                val pStr = if (item.unitPrice % 1 == 0.0) item.unitPrice.toInt().toString() else String.format(Locale.US, "%.1f", item.unitPrice)
                canvas.drawText(pStr, xPrice + (colPriceWidth / 2f), y + 21f, regularPaint)

                val tStr = if (item.totalAmount % 1 == 0.0) item.totalAmount.toInt().toString() else String.format(Locale.US, "%.1f", item.totalAmount)
                boldPaint.textSize = if (is80mm) 15f else 12.5f
                canvas.drawText(tStr, xTotal + (colTotalWidth / 2f), y + 21f, boldPaint)

                y += rowHeight
            }

            // سطر مجاميع الجدول
            val totalRowRect = android.graphics.RectF(tableLeft, y, tableRight, y + rowHeight)
            canvas.drawRect(totalRowRect, boxPaint)
            canvas.drawLine(xQty + colQtyWidth, y, xQty + colQtyWidth, y + rowHeight, linePaint)
            canvas.drawLine(xPrice, y, xPrice, y + rowHeight, linePaint)

            boldPaint.textSize = if (is80mm) 14f else 12f
            canvas.drawText("المجموع", (tableRight + xQty + colQtyWidth) / 2f, y + 21f, boldPaint)

            val totalQStr = if (totalQtyCount % 1 == 0.0) totalQtyCount.toInt().toString() else String.format(Locale.US, "%.1f", totalQtyCount)
            canvas.drawText(totalQStr, xQty + (colQtyWidth / 2f), y + 21f, boldPaint)

            val totalInvStr = String.format(Locale.US, "%.0f", docWithEntries.invoiceTotal)
            boldPaint.textSize = if (is80mm) 16f else 13.5f
            canvas.drawText(totalInvStr, xTotal + (colTotalWidth / 2f), y + 21f, boldPaint)

            y += rowHeight + 12f

            // 4. صناديق الحسم والصافي للدفع (كما في الصورة تماماً)
            val halfBoxWidth = (width - 2 * pad - 6f) / 2f

            // [ الحسم : . ]
            val rDiscount = android.graphics.RectF(width - pad - halfBoxWidth, y, width - pad, y + 36f)
            canvas.drawRoundRect(rDiscount, 10f, 10f, boxPaint)
            regularPaint.textSize = if (is80mm) 16f else 13f
            canvas.drawText("الحســـم :  0.00", rDiscount.centerX(), rDiscount.centerY() + 5f, regularPaint)

            // [ الصافي للدفع : 7,000 ريال ]
            val rNet = android.graphics.RectF(pad, y, pad + halfBoxWidth, y + 36f)
            canvas.drawRoundRect(rNet, 10f, 10f, boxPaint)
            boldPaint.textSize = if (is80mm) 17f else 14f
            canvas.drawText("الصافي للدفع : $totalInvStr ريال", rNet.centerX(), rNet.centerY() + 5f, boldPaint)

            y += 42f

            // 5. صندوق التفقيط (المبلغ كتابة بالحروف)
            val tafqeetText = "فقط " + tafqeetArabic(docWithEntries.invoiceTotal) + " لا غير"
            val rTafqeet = android.graphics.RectF(pad, y, width - pad, y + 32f)
            canvas.drawRoundRect(rTafqeet, 10f, 10f, boxPaint)
            regularPaint.textSize = if (is80mm) 15f else 12.5f
            regularPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(tafqeetText, rTafqeet.centerX(), rTafqeet.centerY() + 5f, regularPaint)

            y += 40f

        } else {
            // كشف حساب عميل
            val colBalanceWidth = if (is80mm) 90f else 70f
            val colCreditWidth = if (is80mm) 75f else 60f
            val colDebitWidth = if (is80mm) 75f else 60f
            val colDateWidth = if (is80mm) 85f else 65f

            val headerRect = android.graphics.RectF(tableLeft, y, tableRight, y + rowHeight)
            canvas.drawRect(headerRect, boxPaint)

            boldPaint.textSize = if (is80mm) 13f else 11.5f
            canvas.drawText("التاريخ", tableRight - (colDateWidth / 2f), y + 20f, boldPaint)
            canvas.drawText("البيان", (tableRight - colDateWidth + tableLeft + colBalanceWidth + colCreditWidth + colDebitWidth) / 2f, y + 20f, boldPaint)
            canvas.drawText("عليه", tableLeft + colBalanceWidth + colCreditWidth + (colDebitWidth / 2f), y + 20f, boldPaint)
            canvas.drawText("له", tableLeft + colBalanceWidth + (colCreditWidth / 2f), y + 20f, boldPaint)
            canvas.drawText("الرصيد", tableLeft + (colBalanceWidth / 2f), y + 20f, boldPaint)

            y += rowHeight

            regularPaint.textSize = if (is80mm) 14f else 12f
            entries.forEach { item ->
                val rItem = android.graphics.RectF(tableLeft, y, tableRight, y + rowHeight)
                canvas.drawRect(rItem, boxPaint)

                val dt = if (item.entryDate.length >= 5) item.entryDate.takeLast(5) else item.entryDate
                canvas.drawText(dt, tableRight - (colDateWidth / 2f), y + 21f, regularPaint)
                canvas.drawText(item.description.take(10), (tableRight - colDateWidth + tableLeft + colBalanceWidth + colCreditWidth + colDebitWidth) / 2f, y + 21f, regularPaint)

                canvas.drawText(if (item.debit > 0) String.format(Locale.US, "%.0f", item.debit) else "-", tableLeft + colBalanceWidth + colCreditWidth + (colDebitWidth / 2f), y + 21f, regularPaint)
                canvas.drawText(if (item.credit > 0) String.format(Locale.US, "%.0f", item.credit) else "-", tableLeft + colBalanceWidth + (colCreditWidth / 2f), y + 21f, regularPaint)
                boldPaint.textSize = if (is80mm) 14f else 12f
                canvas.drawText(String.format(Locale.US, "%.0f", item.runningBalance), tableLeft + (colBalanceWidth / 2f), y + 21f, boldPaint)

                y += rowHeight
            }

            y += 10f

            // ملخص كشف الحساب
            val rBal = android.graphics.RectF(pad, y, width - pad, y + 36f)
            canvas.drawRoundRect(rBal, 10f, 10f, boxPaint)
            val netL = if (docWithEntries.netBalance >= 0) "عليه (مدين)" else "له (دائن)"
            boldPaint.textSize = if (is80mm) 17f else 14f
            canvas.drawText("صافي الرصيد: ${String.format(Locale.US, "%.2f", Math.abs(docWithEntries.netBalance))} ريال ($netL)", rBal.centerX(), rBal.centerY() + 5f, boldPaint)

            y += 44f
        }

        // 6. التذييل
        boldPaint.textSize = if (is80mm) 18f else 15f
        canvas.drawText("شكراً لاختياركم ${doc.storeName.ifEmpty { "بقالة العزي" }}", width / 2f, y, boldPaint)
        y += 24f

        regularPaint.textSize = if (is80mm) 14f else 11.5f
        regularPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("برنامج دفتر الفواتير والحسابات  •  صفحة 1 من 1", width / 2f, y, regularPaint)
        y += 24f

        // اقتصاص الارتفاع الحقيقي بدقة تامة
        val actualHeight = y.toInt().coerceAtLeast(150)
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(bitmap.height))
    }

    /**
     * تحويل الأرقام إلى نصوص باللغة العربية (تفقيط)
     */
    fun tafqeetArabic(amount: Double): String {
        val n = amount.toLong()
        if (n == 0L) return "صفر ريال"
        if (n < 0) return "سالب " + tafqeetArabic(Math.abs(amount))

        val ones = arrayOf("", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة",
            "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر")
        val tens = arrayOf("", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
        val hundreds = arrayOf("", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة")

        fun convertUnder1000(num: Long): String {
            if (num == 0L) return ""
            val h = (num / 100).toInt()
            val rem = (num % 100).toInt()
            val sb = StringBuilder()
            if (h > 0) {
                sb.append(hundreds[h])
            }
            if (rem > 0) {
                if (sb.isNotEmpty()) sb.append(" و")
                if (rem < 20) {
                    sb.append(ones[rem])
                } else {
                    val o = rem % 10
                    val t = rem / 10
                    if (o > 0) {
                        sb.append(ones[o]).append(" و").append(tens[t])
                    } else {
                        sb.append(tens[t])
                    }
                }
            }
            return sb.toString()
        }

        val billions = n / 1_000_000_000L
        val millions = (n % 1_000_000_000L) / 1_000_000L
        val thousands = (n % 1_000_000L) / 1_000L
        val remainder = n % 1_000L

        val parts = mutableListOf<String>()
        if (billions > 0) parts.add("${convertUnder1000(billions)} مليار")
        if (millions > 0) parts.add("${convertUnder1000(millions)} مليون")
        if (thousands > 0) {
            val tText = when (thousands) {
                1L -> "ألف"
                2L -> "ألفان"
                in 3..10 -> "${ones[thousands.toInt()]} آلاف"
                else -> "${convertUnder1000(thousands)} ألف"
            }
            parts.add(tText)
        }
        if (remainder > 0) parts.add(convertUnder1000(remainder))

        return parts.joinToString(" و ") + " ريال يمني"
    }

    /**
     * مشاركة صورة الإيصال كملف صورة حقيقي (PNG)
     */
    fun shareReceiptImage(context: Context, bitmap: Bitmap) {
        try {
            val cachePath = java.io.File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = java.io.File(cachePath, "receipt_${System.currentTimeMillis()}.png")
            val stream = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "📄 إيصال فاتورة مطبوعة من بقالة العزي")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة صورة الإيصال عبر"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "حدث خطأ أثناء تصدير الصورة: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * حفظ نفس صورة المعاينة/الطباعة داخل Downloads/دفتر الفواتير والحسابات/الصور.
     */
    fun saveReceiptImageToDownloads(context: Context, bitmap: Bitmap): Uri? {
        val fileName = "receipt_${System.currentTimeMillis()}.png"
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return AppStorageHelper.saveBytesToDownloads(
            context = context,
            bytes = stream.toByteArray(),
            fileName = fileName,
            mimeType = "image/png",
            subFolder = AppStorageHelper.IMAGES_FOLDER
        )
    }

    /**
     * تحويل الصورة النقطية (Bitmap) إلى أوامر ESC/POS Raster (GS v 0)
     */
    private fun bitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val output = ByteArrayOutputStream()
        // أمر GS v 0 0 xL xH yL yH
        output.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
        output.write(widthBytes and 0xFF)
        output.write((widthBytes shr 8) and 0xFF)
        output.write(height and 0xFF)
        output.write((height shr 8) and 0xFF)

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var byteVal = 0
                for (b in 0 until 8) {
                    val x = xByte * 8 + b
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val bl = pixel and 0xFF
                        val luminance = (r * 0.299 + g * 0.587 + bl * 0.114).toInt()
                        // 1 = أسود، 0 = أبيض
                        if (luminance < 128) {
                            byteVal = byteVal or (1 shl (7 - b))
                        }
                    }
                }
                output.write(byteVal)
            }
        }
        return output.toByteArray()
    }

    /**
     * توليد نص الإيصال الحراري العادي
     */
    fun generateThermalTextReceipt(docWithEntries: DocumentWithEntries): String {
        val doc = docWithEntries.document
        val entries = docWithEntries.entries
        val sb = StringBuilder()

        val divider = "------------------------------------------\n"
        val doubleDivider = "==========================================\n"

        sb.append(doubleDivider)
        sb.append(centerText(doc.storeName.ifEmpty { "بقالة العزي" }, 42)).append("\n")
        if (doc.storeAddress.isNotEmpty()) {
            sb.append(centerText(doc.storeAddress, 42)).append("\n")
        }
        if (doc.storePhone.isNotEmpty()) {
            sb.append(centerText("هاتف: ${doc.storePhone}", 42)).append("\n")
        }
        sb.append(doubleDivider)

        val docTitle = when (doc.docType) {
            DocumentType.SALES_INVOICE -> "فاتورة بيع " + if (doc.paymentType == PaymentType.CASH) "نقداً" else "آجل"
            DocumentType.CUSTOMER_LEDGER -> "كشف حساب عميل"
            DocumentType.LINED_NOTE -> "ملاحظات دفترية"
        }

        sb.append(centerText("[ $docTitle ]", 42)).append("\n")
        sb.append("رقم السند: ${doc.docNumber}\n")
        sb.append("التاريخ: ${doc.dateString} - ${doc.dayString}\n")
        if (doc.customerName.isNotEmpty()) {
            sb.append("المطلوب من: ${doc.customerName}\n")
        }
        sb.append(divider)

        when (doc.docType) {
            DocumentType.SALES_INVOICE -> {
                sb.append(formatRow("البيان", "العدد", "السعر", "الإجمالي"))
                sb.append(divider)
                for (item in entries) {
                    val desc = item.description.take(16)
                    val qty = String.format(Locale.US, "%.1f", item.quantity)
                    val price = String.format(Locale.US, "%.1f", item.unitPrice)
                    val total = String.format(Locale.US, "%.2f", item.totalAmount)
                    sb.append(formatRow(desc, qty, price, total))
                }
                sb.append(divider)
                val totalStr = String.format(Locale.US, "%.2f ريال", docWithEntries.invoiceTotal)
                sb.append("الإجمالي الكلي: $totalStr\n")
            }
            DocumentType.CUSTOMER_LEDGER -> {
                sb.append(formatLedgerRow("التاريخ", "البيان", "عليه", "له", "الرصيد"))
                sb.append(divider)
                for (item in entries) {
                    val dt = item.entryDate.takeLast(5)
                    val desc = item.description.take(12)
                    val debit = if (item.debit > 0) String.format(Locale.US, "%.0f", item.debit) else "-"
                    val credit = if (item.credit > 0) String.format(Locale.US, "%.0f", item.credit) else "-"
                    val bal = String.format(Locale.US, "%.0f", item.runningBalance)
                    sb.append(formatLedgerRow(dt, desc, debit, credit, bal))
                }
                sb.append(divider)
                sb.append("إجمالي له (دائن): ${String.format(Locale.US, "%.2f", docWithEntries.totalCredit)} ريال\n")
                sb.append("إجمالي عليه (مدين): ${String.format(Locale.US, "%.2f", docWithEntries.totalDebit)} ريال\n")
                val netLabel = if (docWithEntries.netBalance >= 0) "رصيد عليه (مدين)" else "رصيد له (دائن)"
                val netVal = Math.abs(docWithEntries.netBalance)
                sb.append("الرصيد الصافي: ${String.format(Locale.US, "%.2f", netVal)} ريال ($netLabel)\n")
            }
            DocumentType.LINED_NOTE -> {
                sb.append(doc.notes).append("\n")
            }
        }

        sb.append(divider)
        sb.append("* البضاعة المباعة لا ترد ولا تستبدل\n  بعد خروجها إلا في حال الخطأ والسهو\n")
        sb.append("توقيع المستلم: ............  البائع: ${doc.sellerSignature}\n")
        sb.append(doubleDivider)
        sb.append(centerText("*** شكراً لتعاملكم معنا ***", 42)).append("\n")

        return sb.toString()
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text
        val pad = (width - text.length) / 2
        return " ".repeat(pad) + text
    }

    private fun formatRow(col1: String, col2: String, col3: String, col4: String): String {
        return String.format(Locale.US, "%-16s %-6s %-8s %-9s\n", col1, col2, col3, col4)
    }

    private fun formatLedgerRow(col1: String, col2: String, col3: String, col4: String, col5: String): String {
        return String.format(Locale.US, "%-6s %-12s %-6s %-6s %-8s\n", col1, col2, col3, col4, col5)
    }

    /**
     * الطباعة عبر نظام أندرويد PrintManager
     */
    fun printDocument(context: Context, docWithEntries: DocumentWithEntries) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "فاتورة_${docWithEntries.document.docNumber}"

        val htmlContent = generatePrintHtml(docWithEntries)

        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printAdapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(jobName)
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                    .setResolution(PrintAttributes.Resolution("thermal", "Thermal", 203, 203))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(jobName, printAdapter, attributes)
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    fun shareReceiptText(context: Context, docWithEntries: DocumentWithEntries) {
        val text = generateThermalTextReceipt(docWithEntries)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, "فاتورة رقم ${docWithEntries.document.docNumber}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "مشاركة الفاتورة عبر")
        context.startActivity(shareIntent)
    }

    private fun generatePrintHtml(docWithEntries: DocumentWithEntries): String {
        val doc = docWithEntries.document
        val entries = docWithEntries.entries

        val itemsHtml = StringBuilder()
        if (doc.docType == DocumentType.SALES_INVOICE) {
            for (item in entries) {
                itemsHtml.append("""
                    <tr class="line-row">
                        <td class="col-desc">${item.description}</td>
                        <td class="col-qty">${item.quantity}</td>
                        <td class="col-price">${String.format(Locale.US, "%.2f", item.unitPrice)}</td>
                        <td class="col-total">${String.format(Locale.US, "%.2f", item.totalAmount)}</td>
                    </tr>
                """.trimIndent())
            }
        } else if (doc.docType == DocumentType.CUSTOMER_LEDGER) {
            for (item in entries) {
                itemsHtml.append("""
                    <tr class="line-row">
                        <td class="col-date">${item.entryDate}</td>
                        <td class="col-desc">${item.description}</td>
                        <td class="col-price">${if (item.debit > 0) String.format(Locale.US, "%.2f", item.debit) else "-"}</td>
                        <td class="col-price">${if (item.credit > 0) String.format(Locale.US, "%.2f", item.credit) else "-"}</td>
                        <td class="col-total">${String.format(Locale.US, "%.2f", item.runningBalance)}</td>
                    </tr>
                """.trimIndent())
            }
        }

        return """
        <!DOCTYPE html>
        <html dir="rtl" lang="ar">
        <head>
            <meta charset="utf-8">
            <style>
                body {
                    font-family: 'Courier New', Courier, monospace, Tahoma;
                    width: 78mm;
                    margin: 0 auto;
                    padding: 8px;
                    color: #000;
                    font-size: 12px;
                    background: #fff;
                }
                .text-center { text-align: center; }
                .text-left { text-align: left; }
                .text-right { text-align: right; }
                .store-title { font-size: 17px; font-weight: bold; margin-bottom: 2px; }
                .badge-invoice {
                    border: 2px solid #000;
                    border-radius: 6px;
                    padding: 4px 10px;
                    display: inline-block;
                    font-weight: bold;
                    margin: 6px 0;
                }
                .doc-num { color: #d00; font-size: 15px; font-weight: bold; }
                .dashed-divider { border-top: 1px dashed #444; margin: 8px 0; }
                .double-divider { border-top: 2px solid #000; margin: 8px 0; }
                table { width: 100%; border-collapse: collapse; margin: 6px 0; }
                th { border-bottom: 1px solid #000; padding: 4px; font-size: 11px; }
                td { padding: 5px 2px; font-size: 11px; border-bottom: 1px dashed #bbb; }
                .total-box {
                    font-size: 14px;
                    font-weight: bold;
                    padding: 6px;
                    background: #f2f2f2;
                    border: 1px solid #000;
                    margin-top: 8px;
                    display: flex;
                    justify-content: space-between;
                }
                .footer-notice {
                    font-size: 9px;
                    text-align: center;
                    margin-top: 10px;
                    color: #333;
                }
                .signatures {
                    margin-top: 15px;
                    display: flex;
                    justify-content: space-between;
                    font-size: 10px;
                }
            </style>
        </head>
        <body>
            <div class="text-center">
                <div class="store-title">${doc.storeName}</div>
                <div>${doc.storeAddress}</div>
                <div>هاتف: ${doc.storePhone}</div>
                <div class="dashed-divider"></div>
                <div class="badge-invoice">فاتورة بيع ${if (doc.paymentType == PaymentType.CASH) "نقداً" else "آجل"}</div>
                <div>رقم الفاتورة: <span class="doc-num">${doc.docNumber}</span></div>
                <div>التاريخ: ${doc.dateString} (${doc.dayString})</div>
            </div>
            
            <div class="dashed-divider"></div>
            <div><strong>المطلوب من: </strong> ${doc.customerName.ifEmpty { "العميل المحترم" }}</div>
            
            <table>
                <thead>
                    ${if (doc.docType == DocumentType.SALES_INVOICE) """
                        <tr>
                            <th class="text-right">التفاصيل</th>
                            <th>العدد</th>
                            <th>السعر</th>
                            <th>الإجمالي</th>
                        </tr>
                    """ else """
                        <tr>
                            <th class="text-right">التاريخ</th>
                            <th class="text-right">البيان</th>
                            <th>عليه (مدين)</th>
                            <th>له (دائن)</th>
                            <th>الرصيد</th>
                        </tr>
                    """}
                </thead>
                <tbody>
                    $itemsHtml
                </tbody>
            </table>
            
            ${if (doc.docType == DocumentType.SALES_INVOICE) """
                <div class="total-box">
                    <span>الإجمالي الكلي (TOTAL):</span>
                    <span>${String.format(Locale.US, "%.2f", docWithEntries.invoiceTotal)} ريال</span>
                </div>
            """ else """
                <div class="total-box">
                    <span>صافي الرصيد:</span>
                    <span>${String.format(Locale.US, "%.2f", Math.abs(docWithEntries.netBalance))} ريال (${if (docWithEntries.netBalance >= 0) "عليه" else "له"})</span>
                </div>
            """}
            
            <div class="footer-notice">
                * البضاعة المباعة لا ترد ولا تستبدل بعد خروجها من المحل ما عدا السهو والخطأ
            </div>
            
            <div class="signatures">
                <div>توقيع المشتري: ................</div>
                <div>توقيع البائع: ${doc.sellerSignature}</div>
            </div>
            
            <div class="text-center" style="margin-top: 14px; font-size: 10px;">
                *** نسعد دائماً بخدمتكم ***
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}
