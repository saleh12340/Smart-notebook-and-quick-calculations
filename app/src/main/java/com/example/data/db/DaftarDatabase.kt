package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentEntryEntity
import com.example.data.model.DocumentType
import com.example.data.model.PaymentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [DocumentEntity::class, DocumentEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DaftarDatabase : RoomDatabase() {

    abstract fun daftarDao(): DaftarDao

    companion object {
        @Volatile
        private var INSTANCE: DaftarDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): DaftarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DaftarDatabase::class.java,
                    "daftar_database"
                )
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialSampleData(database.daftarDao())
                    }
                }
            }

            private suspend fun populateInitialSampleData(dao: DaftarDao) {
                val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
                val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
                val today = Date()
                val dateStr = dateFormat.format(today)
                val dayStr = dayFormat.format(today)

                // 1. فاتورة أصناف نموذجية طبق الأصل من الصورة المرفقة
                val invoiceDoc = DocumentEntity(
                    docNumber = "0000337",
                    title = "فاتورة بيع",
                    customerName = "مؤسسة الأمل التجارية",
                    docType = DocumentType.SALES_INVOICE,
                    paymentType = PaymentType.CASH,
                    storeName = "مركز البركة للتجارة",
                    storeAddress = "شارع التحرير - العاصمة",
                    storePhone = "01-445566 / 771234567",
                    commercialReg = "84920",
                    poBox = "514",
                    fax = "01-445567",
                    dateString = dateStr,
                    dayString = dayStr,
                    sellerSignature = "أحمد التاجر",
                    buyerSignature = "سالم باسلامة"
                )
                val invoiceEntries = listOf(
                    DocumentEntryEntity(
                        description = "أرز بسمتي فاخر كيس 40 كجم",
                        quantity = 5.0,
                        unitPrice = 160.0,
                        totalAmount = 800.0
                    ),
                    DocumentEntryEntity(
                        description = "زيت طبخ نقي كرتون 4 حبات",
                        quantity = 10.0,
                        unitPrice = 45.0,
                        totalAmount = 450.0
                    ),
                    DocumentEntryEntity(
                        description = "سكر أبيض ناعم شوال 50 كجم",
                        quantity = 2.0,
                        unitPrice = 110.0,
                        totalAmount = 220.0
                    ),
                    DocumentEntryEntity(
                        description = "حليب بودرة مجفف عبوة عائلية",
                        quantity = 6.0,
                        unitPrice = 35.0,
                        totalAmount = 210.0
                    )
                )
                dao.saveDocumentWithEntries(invoiceDoc, invoiceEntries)

                // 2. دفتر حسابات عميل (النوع الأول: رصيد تراكمي، له، عليه، التاريخ، التفاصيل)
                val ledgerDoc = DocumentEntity(
                    docNumber = "ACC-104",
                    title = "كشف حساب عميل",
                    customerName = "الشيخ عبد الله بن محمد",
                    docType = DocumentType.CUSTOMER_LEDGER,
                    paymentType = PaymentType.CREDIT,
                    storeName = "مركز البركة للتجارة",
                    storeAddress = "شارع التحرير",
                    storePhone = "771234567",
                    dateString = dateStr,
                    dayString = dayStr
                )
                val ledgerEntries = listOf(
                    DocumentEntryEntity(
                        runningBalance = 1500.0,
                        credit = 0.0,
                        debit = 1500.0,
                        entryDate = dateStr,
                        entryDay = dayStr,
                        description = "رصيد سابق مرحل من الشهر الماضي"
                    ),
                    DocumentEntryEntity(
                        runningBalance = 500.0,
                        credit = 1000.0,
                        debit = 0.0,
                        entryDate = dateStr,
                        entryDay = dayStr,
                        description = "دفعة نقدية مسددة سند قبض #14"
                    ),
                    DocumentEntryEntity(
                        runningBalance = 2300.0,
                        credit = 0.0,
                        debit = 1800.0,
                        entryDate = dateStr,
                        entryDay = dayStr,
                        description = "بضاعة بموجب فاتورة مبيعات #332"
                    )
                )
                dao.saveDocumentWithEntries(ledgerDoc, ledgerEntries)

                // 3. ملاحظة مسطرة عربية
                val noteDoc = DocumentEntity(
                    docNumber = "NOTE-01",
                    title = "ملاحظات جرد المستودع",
                    customerName = "إدارة المخازن",
                    docType = DocumentType.LINED_NOTE,
                    notes = "تم مطابقة الأصناف مع سجلات المبيعات اليومية.\nالأصناف المسجلة مستوفية المعايير ولا يوجد نقص في الكميات.\nيرجى اعتماد طلب التوريد الجديد في بداية الأسبوع القادم.",
                    dateString = dateStr,
                    dayString = dayStr
                )
                dao.saveDocumentWithEntries(noteDoc, emptyList())
            }
        }
    }
}
