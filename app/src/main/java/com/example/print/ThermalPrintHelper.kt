package com.example.print

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import com.example.data.model.PaymentType
import java.util.Locale

object ThermalPrintHelper {

    /**
     * Generate 80mm / 58mm plain text formatted thermal receipt with Arabic alignment
     */
    fun generateThermalTextReceipt(docWithEntries: DocumentWithEntries): String {
        val doc = docWithEntries.document
        val entries = docWithEntries.entries
        val sb = StringBuilder()

        val divider = "------------------------------------------\n"
        val doubleDivider = "==========================================\n"

        sb.append(doubleDivider)
        sb.append(centerText(doc.storeName.ifEmpty { "فاتورة تجارية" }, 42)).append("\n")
        if (doc.storeAddress.isNotEmpty()) {
            sb.append(centerText(doc.storeAddress, 42)).append("\n")
        }
        if (doc.storePhone.isNotEmpty()) {
            sb.append(centerText("هاتف: ${doc.storePhone}", 42)).append("\n")
        }
        if (doc.commercialReg.isNotEmpty()) {
            sb.append(centerText("س.ت: ${doc.commercialReg}  |  ص.ب: ${doc.poBox}", 42)).append("\n")
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
                    val desc = item.description.take(18)
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
                sb.append(formatLedgerRow("التفاصيل", "له", "عليه", "الرصيد"))
                sb.append(divider)
                for (item in entries) {
                    val desc = item.description.take(14)
                    val credit = if (item.credit > 0) String.format(Locale.US, "%.0f", item.credit) else "-"
                    val debit = if (item.debit > 0) String.format(Locale.US, "%.0f", item.debit) else "-"
                    val bal = String.format(Locale.US, "%.0f", item.runningBalance)
                    sb.append(formatLedgerRow(desc, credit, debit, bal))
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

    private fun formatLedgerRow(col1: String, col2: String, col3: String, col4: String): String {
        return String.format(Locale.US, "%-14s %-8s %-8s %-10s\n", col1, col2, col3, col4)
    }

    /**
     * Print via Android PrintManager (HTML preview / Thermal page format)
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

    /**
     * Share formatted receipt via WhatsApp or system share dialog
     */
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

    /**
     * HTML formatted thermal receipt for print
     */
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
                        <td class="col-desc">${item.description}</td>
                        <td class="col-date">${item.entryDate}</td>
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
                <div>هاتف: ${doc.storePhone} | س.ت: ${doc.commercialReg}</div>
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
                            <th class="text-right">التفاصيل</th>
                            <th>التاريخ</th>
                            <th>مدين</th>
                            <th>دائن</th>
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
