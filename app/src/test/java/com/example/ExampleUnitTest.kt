package com.example

import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentEntryEntity
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun invoiceTotalCalculation_isCorrect() {
        val doc = DocumentEntity(
            docNumber = "0000337",
            customerName = "مؤسسة الأمل",
            docType = DocumentType.SALES_INVOICE
        )
        val entries = listOf(
            DocumentEntryEntity(description = "أرز بسمتي", quantity = 5.0, unitPrice = 160.0, totalAmount = 800.0),
            DocumentEntryEntity(description = "زيت طبخ", quantity = 10.0, unitPrice = 45.0, totalAmount = 450.0),
            DocumentEntryEntity(description = "سكر أبيض", quantity = 2.0, unitPrice = 110.0, totalAmount = 220.0)
        )
        val docWithEntries = DocumentWithEntries(doc, entries)

        assertEquals(1470.0, docWithEntries.invoiceTotal, 0.001)
    }

    @Test
    fun customerLedgerNetBalance_isCorrect() {
        val doc = DocumentEntity(
            docNumber = "ACC-101",
            customerName = "الشيخ عبد الله",
            docType = DocumentType.CUSTOMER_LEDGER
        )
        val entries = listOf(
            DocumentEntryEntity(description = "بضاعة آجل", debit = 1500.0, credit = 0.0),
            DocumentEntryEntity(description = "دفعة مسددة", debit = 0.0, credit = 500.0),
            DocumentEntryEntity(description = "بضاعة إضافية", debit = 700.0, credit = 0.0)
        )
        val docWithEntries = DocumentWithEntries(doc, entries)

        assertEquals(2200.0, docWithEntries.totalDebit, 0.001)
        assertEquals(500.0, docWithEntries.totalCredit, 0.001)
        assertEquals(1700.0, docWithEntries.netBalance, 0.001)
    }
}
