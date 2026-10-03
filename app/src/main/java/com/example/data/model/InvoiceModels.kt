package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.Embedded

enum class DocumentType {
    SALES_INVOICE,    // النوع الثاني: فاتورة بيع أصناف نقداً / آجل (كما في الصورة)
    CUSTOMER_LEDGER,  // النوع الأول: دفتر حسابات العميل (رصيد تراكمي، له، عليه، التاريخ، التفاصيل)
    LINED_NOTE        // ملاحظات عادية بين السطور
}

enum class PaymentType {
    CASH, // نقداً
    CREDIT // آجل
}

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val docNumber: String = "0000337", // رقم الفاتورة أو القيد المميز
    val title: String = "",            // عنوان الملاحظة أو اسم الحساب
    val customerName: String = "",     // المطلوب من الأخ / المحترم
    val docType: DocumentType = DocumentType.SALES_INVOICE,
    val paymentType: PaymentType = PaymentType.CASH,
    val storeName: String = "مؤسسة التجارة والخدمات",
    val storeAddress: String = "الشارع العام - بجوار السوق",
    val storePhone: String = "777000000",
    val commercialReg: String = "101000", // س.ت
    val poBox: String = "123",           // ص.ب
    val fax: String = "",                // فاكس
    val dateString: String = "",         // 2026/10/03
    val dayString: String = "",          // السبت
    val notes: String = "",              // ملاحظات عامة أو شروط إضافية
    val stampImageUrl: String? = null,   // ختم أو صورة الشعار المولد بالذكاء الاصطناعي
    val buyerSignature: String = "",     // توقيع المشتري
    val sellerSignature: String = "المحاسب",    // توقيع البائع
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "document_entries",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class DocumentEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long = 0,
    val sortOrder: Int = 0,
    
    // للحسابات (النوع الأول):
    val debit: Double = 0.0,       // عليه / مدين
    val credit: Double = 0.0,      // له / دائن
    val runningBalance: Double = 0.0, // الرصيد التراكمي
    val entryDate: String = "",    // التاريخ واليوم
    val entryDay: String = "",     // اسم اليوم
    
    // للأصناف والفواتير (النوع الثاني):
    val description: String = "",  // التفاصيل / Description
    val quantity: Double = 1.0,    // العدد / Qty
    val unitPrice: Double = 0.0,   // سعر الوحدة / Unit Price (ريال)
    val totalAmount: Double = 0.0  // القيمة الإجمالية / Total Amount (ريال)
)

data class DocumentWithEntries(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "documentId"
    )
    val entries: List<DocumentEntryEntity>
) {
    // إجمالي الفاتورة للأصناف
    val invoiceTotal: Double
        get() = entries.sumOf { it.totalAmount }

    // إجمالي له (دائن) للحسابات
    val totalCredit: Double
        get() = entries.sumOf { it.credit }

    // إجمالي عليه (مدين) للحسابات
    val totalDebit: Double
        get() = entries.sumOf { it.debit }

    // الرصيد الصافي للحسابات: موجب = عليه (مدين)، سالب = له (دائن)
    val netBalance: Double
        get() = totalDebit - totalCredit
}
