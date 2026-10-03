package com.example.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
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

            // 2. إنشاء صورة إيصال نقية بالأبيض والأسود مع نصوص عربية واضحة
            val paperWidth = if (is80mm) 576 else 384
            val bitmap = generateReceiptBitmap(docWithEntries, paperWidth)

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
     * رسم الإيصال كصورة Canvas مخصصة للطباعة الحرارية بدقة وخطوط عربية واضحة
     */
    private fun generateReceiptBitmap(docWithEntries: DocumentWithEntries, width: Int): Bitmap {
        val doc = docWithEntries.document
        val entries = docWithEntries.entries

        // تقدير الارتفاع بناء على عدد الأصناف
        val estimatedHeight = 350 + (entries.size * 35) + 200
        val bitmap = Bitmap.createBitmap(width, estimatedHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val regularPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textSize = 17f
            textAlign = Paint.Align.RIGHT
        }

        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        var y = 35f

        // اسم المحل
        textPaint.textSize = 24f
        canvas.drawText(doc.storeName.ifEmpty { "بقالة العزي" }, width / 2f, y, textPaint)
        y += 28f

        // تفاصيل المتجر
        regularPaint.textAlign = Paint.Align.CENTER
        regularPaint.textSize = 15f
        if (doc.storeAddress.isNotEmpty()) {
            canvas.drawText(doc.storeAddress, width / 2f, y, regularPaint)
            y += 22f
        }
        if (doc.storePhone.isNotEmpty()) {
            canvas.drawText("هاتف: ${doc.storePhone}", width / 2f, y, regularPaint)
            y += 22f
        }

        // خط فاصل مزدوج
        canvas.drawLine(10f, y, width - 10f, y, linePaint)
        y += 4f
        canvas.drawLine(10f, y, width - 10f, y, linePaint)
        y += 24f

        // عنوان الفاتورة ورقمها
        textPaint.textSize = 19f
        val docTitle = when (doc.docType) {
            DocumentType.SALES_INVOICE -> "فاتورة بيع " + if (doc.paymentType == PaymentType.CASH) "نقداً" else "آجل"
            DocumentType.CUSTOMER_LEDGER -> "كشف حساب عميل"
            DocumentType.LINED_NOTE -> "ملاحظة دفترية"
        }
        canvas.drawText("[ $docTitle ]", width / 2f, y, textPaint)
        y += 26f

        regularPaint.textAlign = Paint.Align.RIGHT
        regularPaint.textSize = 16f
        canvas.drawText("رقم السند: ${doc.docNumber}", width - 15f, y, regularPaint)
        y += 22f
        canvas.drawText("التاريخ: ${doc.dateString} (${doc.dayString})", width - 15f, y, regularPaint)
        y += 22f
        if (doc.customerName.isNotEmpty()) {
            canvas.drawText("المطلوب من: ${doc.customerName}", width - 15f, y, regularPaint)
            y += 24f
        }

        // خط فاصل
        canvas.drawLine(10f, y, width - 10f, y, linePaint)
        y += 22f

        // ترويسة الجدول
        regularPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        if (doc.docType == DocumentType.SALES_INVOICE) {
            canvas.drawText("الإجمالي", width - 15f, y, regularPaint)
            canvas.drawText("العدد", width * 0.70f, y, regularPaint)
            canvas.drawText("السعر", width * 0.48f, y, regularPaint)
            canvas.drawText("البيان", 60f, y, regularPaint)
            y += 8f
            canvas.drawLine(10f, y, width - 10f, y, linePaint)
            y += 24f

            regularPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            for (item in entries) {
                canvas.drawText(String.format(Locale.US, "%.1f", item.totalAmount), width - 15f, y, regularPaint)
                canvas.drawText(String.format(Locale.US, "%.1f", item.quantity), width * 0.70f, y, regularPaint)
                canvas.drawText(String.format(Locale.US, "%.1f", item.unitPrice), width * 0.48f, y, regularPaint)
                canvas.drawText(item.description.take(14), 60f, y, regularPaint)
                y += 24f
            }

            y += 4f
            canvas.drawLine(10f, y, width - 10f, y, linePaint)
            y += 26f

            textPaint.textSize = 21f
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("الإجمالي الكلي: ${String.format(Locale.US, "%.2f", docWithEntries.invoiceTotal)} ريال", width - 15f, y, textPaint)
            y += 28f
        } else if (doc.docType == DocumentType.CUSTOMER_LEDGER) {
            canvas.drawText("التاريخ", width - 15f, y, regularPaint)
            canvas.drawText("البيان", width * 0.68f, y, regularPaint)
            canvas.drawText("عليه", width * 0.38f, y, regularPaint)
            canvas.drawText("له", width * 0.23f, y, regularPaint)
            canvas.drawText("الرصيد", 50f, y, regularPaint)
            y += 8f
            canvas.drawLine(10f, y, width - 10f, y, linePaint)
            y += 24f

            regularPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            for (item in entries) {
                val dt = item.entryDate.takeLast(5) // MM/dd
                canvas.drawText(dt, width - 15f, y, regularPaint)
                canvas.drawText(item.description.take(10), width * 0.68f, y, regularPaint)
                canvas.drawText(if (item.debit > 0) String.format(Locale.US, "%.0f", item.debit) else "-", width * 0.38f, y, regularPaint)
                canvas.drawText(if (item.credit > 0) String.format(Locale.US, "%.0f", item.credit) else "-", width * 0.23f, y, regularPaint)
                canvas.drawText(String.format(Locale.US, "%.0f", item.runningBalance), 50f, y, regularPaint)
                y += 24f
            }

            y += 4f
            canvas.drawLine(10f, y, width - 10f, y, linePaint)
            y += 24f

            textPaint.textSize = 18f
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("إجمالي له (دائن): ${String.format(Locale.US, "%.2f", docWithEntries.totalCredit)} ريال", width - 15f, y, textPaint)
            y += 24f
            canvas.drawText("إجمالي عليه (مدين): ${String.format(Locale.US, "%.2f", docWithEntries.totalDebit)} ريال", width - 15f, y, textPaint)
            y += 26f
            val netLabel = if (docWithEntries.netBalance >= 0) "عليه (مدين)" else "له (دائن)"
            canvas.drawText("صافي الرصيد: ${String.format(Locale.US, "%.2f", Math.abs(docWithEntries.netBalance))} ريال ($netLabel)", width - 15f, y, textPaint)
            y += 28f
        }

        // الشروط والتذييل
        regularPaint.textAlign = Paint.Align.CENTER
        regularPaint.textSize = 13f
        canvas.drawText("* البضاعة المباعة لا ترد ولا تستبدل إلا في حال الخطأ *", width / 2f, y, regularPaint)
        y += 22f
        canvas.drawText("*** شكراً لتعاملكم معنا ***", width / 2f, y, regularPaint)

        // اقتصاص الارتفاع الحقيقي للصورة
        val actualHeight = (y + 30).toInt().coerceAtLeast(100)
        return Bitmap.createBitmap(bitmap, 0, 0, width, actualHeight.coerceAtMost(bitmap.height))
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
