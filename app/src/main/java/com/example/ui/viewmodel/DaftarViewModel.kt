package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ChatMessage
import com.example.ai.GeminiService
import com.example.data.backup.BackupHelper
import com.example.data.db.DaftarDatabase
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentEntryEntity
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import com.example.data.model.PaymentType
import com.example.data.repository.DaftarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class CustomerSummary(
    val customerName: String,
    val totalDebit: Double,
    val totalCredit: Double,
    val netBalance: Double,
    val totalInvoicesAmount: Double,
    val documentCount: Int,
    val lastDate: String,
    val lastUpdatedAt: Long
)

sealed class CurrentScreen {
    data object Home : CurrentScreen()
    data class CustomerProfile(val customerName: String) : CurrentScreen()
    data class InvoiceEditor(val docId: Long = 0, val prefilledCustomer: String = "") : CurrentScreen()
    data class CustomerLedger(val docId: Long = 0, val prefilledCustomer: String = "") : CurrentScreen()
    data class LinedNote(val docId: Long = 0, val prefilledCustomer: String = "") : CurrentScreen()
    data class ThermalPrint(val docId: Long) : CurrentScreen()
    data object AiAssistant : CurrentScreen()
    data object Settings : CurrentScreen()
}

class DaftarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DaftarRepository
    val geminiService: GeminiService = GeminiService(application)

    init {
        val database = DaftarDatabase.getDatabase(application, viewModelScope)
        repository = DaftarRepository(database.daftarDao())
    }

    // Navigation State
    private val _currentScreen = MutableStateFlow<CurrentScreen>(CurrentScreen.Home)
    val currentScreen: StateFlow<CurrentScreen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: CurrentScreen) {
        _currentScreen.value = screen
    }

    fun navigateBack() {
        _currentScreen.value = CurrentScreen.Home
    }

    // All documents flow
    val allDocuments: StateFlow<List<DocumentWithEntries>> = repository.allDocuments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Search and filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<DocumentType?>(null)
    val selectedFilter = _selectedFilter.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFilter(filter: DocumentType?) {
        _selectedFilter.value = filter
    }

    // Active document being edited
    private val _activeDocument = MutableStateFlow<DocumentEntity>(createDefaultDocument(DocumentType.SALES_INVOICE))
    val activeDocument: StateFlow<DocumentEntity> = _activeDocument.asStateFlow()

    private val _activeEntries = MutableStateFlow<List<DocumentEntryEntity>>(emptyList())
    val activeEntries: StateFlow<List<DocumentEntryEntity>> = _activeEntries.asStateFlow()

    fun loadDocument(docId: Long, defaultType: DocumentType = DocumentType.SALES_INVOICE, prefilledCustomer: String = "") {
        if (docId == 0L) {
            _activeDocument.value = createDefaultDocument(defaultType, prefilledCustomer)
            _activeEntries.value = createInitialEntries(defaultType)
        } else {
            viewModelScope.launch {
                val found = allDocuments.value.find { it.document.id == docId }
                if (found != null) {
                    _activeDocument.value = found.document
                    val entries = found.entries.toMutableList()
                    // عند فتح مستند محفوظ: نعرض البيانات الموجودة فقط + سطر إدخال واحد فارغ.
                    // لا ننشئ 5 أو 6 أسطر فارغة عند البداية.
                    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
                    val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
                    val now = Date()
                    val hasTrailingEmpty = entries.lastOrNull()?.let {
                        it.description.isBlank() &&
                        it.totalAmount == 0.0 &&
                        it.debit == 0.0 &&
                        it.credit == 0.0
                    } == true
                    if (!hasTrailingEmpty) {
                        entries.add(
                            if (found.document.docType == DocumentType.CUSTOMER_LEDGER) {
                                DocumentEntryEntity(
                                    runningBalance = 0.0,
                                    credit = 0.0,
                                    debit = 0.0,
                                    entryDate = dateFormat.format(now),
                                    entryDay = dayFormat.format(now),
                                    description = ""
                                )
                            } else {
                                DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0)
                            }
                        )
                    }
                    _activeEntries.value = if (found.document.docType == DocumentType.CUSTOMER_LEDGER) recalculateLedgerBalances(entries) else entries
                }
            }
        }
    }

    private fun getNextSequentialDocNumber(): String {
        val count = allDocuments.value.size
        val maxExisting = allDocuments.value.mapNotNull { it.document.docNumber.toLongOrNull() }.maxOrNull() ?: 0L
        val next = (maxOf(count.toLong(), maxExisting) + 1L)
        return String.format(Locale.US, "%07d", next)
    }

    fun createDefaultDocument(type: DocumentType, customerName: String = ""): DocumentEntity {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
        val now = Date()
        val nextDocNumber = getNextSequentialDocNumber()
        val prefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val sName = prefs.getString("store_name", "بقالة العزي") ?: "بقالة العزي"
        val sPhone = prefs.getString("store_phone", "776425052") ?: "776425052"
        val sAddress = prefs.getString("store_address", "السوق العام") ?: "السوق العام"
        val sReg = prefs.getString("commercial_reg", "101000") ?: "101000"
        val sSeller = prefs.getString("seller_name", "المحاسب") ?: "المحاسب"

        return DocumentEntity(
            docNumber = nextDocNumber,
            title = when (type) {
                DocumentType.SALES_INVOICE -> "فاتورة بيع"
                DocumentType.CUSTOMER_LEDGER -> "كشف حساب عميل"
                DocumentType.LINED_NOTE -> "ملاحظة دفترية"
            },
            customerName = customerName,
            docType = type,
            paymentType = PaymentType.CASH,
            storeName = sName,
            storeAddress = sAddress,
            storePhone = sPhone,
            commercialReg = sReg,
            poBox = "302",
            fax = "",
            dateString = dateFormat.format(now),
            dayString = dayFormat.format(now),
            notes = "",
            sellerSignature = sSeller
        )
    }

    fun addNewCustomer(customerName: String, initialDebit: Double = 0.0) {
        if (customerName.isBlank()) return
        viewModelScope.launch {
            val doc = createDefaultDocument(DocumentType.CUSTOMER_LEDGER, customerName.trim())
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
            val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
            val now = Date()
            val initialEntry = DocumentEntryEntity(
                description = if (initialDebit > 0) "رصيد سابق / افتتاح حساب" else "افتتاح حساب عميل",
                debit = initialDebit,
                credit = 0.0,
                runningBalance = initialDebit,
                entryDate = dateFormat.format(now),
                entryDay = dayFormat.format(now)
            )
            val id = repository.saveDocument(doc, listOf(initialEntry))
            _activeDocument.value = doc.copy(id = id)
            _activeEntries.value = listOf(initialEntry)
            navigateTo(CurrentScreen.CustomerProfile(customerName.trim()))
        }
    }

    private fun createInitialEntries(type: DocumentType): List<DocumentEntryEntity> {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
        val now = Date()
        val dStr = dateFormat.format(now)
        val dayStr = dayFormat.format(now)

        return when (type) {
            DocumentType.SALES_INVOICE -> listOf(
                DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0)
            )
            DocumentType.CUSTOMER_LEDGER -> listOf(
                DocumentEntryEntity(
                    runningBalance = 0.0,
                    credit = 0.0,
                    debit = 0.0,
                    entryDate = dStr,
                    entryDay = dayStr,
                    description = ""
                )
            )
            DocumentType.LINED_NOTE -> emptyList()
        }
    }

    fun updateDocumentHeader(updated: DocumentEntity) {
        _activeDocument.value = updated
    }

    fun updateDocumentNoteText(text: String) {
        _activeDocument.value = _activeDocument.value.copy(notes = text)
    }

    // --- Invoice Item Management: Total Amount is the primary driver ---

    // 1. تعديل القيمة الإجمالية مباشرة (الأساس المحاسبي للفاتورة)
    fun updateInvoiceRowTotal(index: Int, totalAmount: Double) {
        val current = _activeEntries.value.toMutableList()
        if (index in current.indices) {
            val cur = current[index]
            val q = if (cur.quantity <= 0.0) 1.0 else cur.quantity
            val unitPrice = if (totalAmount > 0.0 && q > 0.0) totalAmount / q else 0.0
            current[index] = cur.copy(
                quantity = q,
                unitPrice = unitPrice,
                totalAmount = totalAmount
            )
            // التوسع التلقائي إذا بدأ المستخدم بالكتابة في السطر الأخير
            if (index == current.lastIndex && (cur.description.isNotBlank() || totalAmount > 0.0)) {
                current.add(DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0))
            }
            _activeEntries.value = current
        }
    }

    // 2. تعديل اسم الصنف / التفاصيل
    fun updateInvoiceRowDescription(index: Int, description: String) {
        val current = _activeEntries.value.toMutableList()
        if (index in current.indices) {
            val cur = current[index]
            current[index] = cur.copy(description = description)
            if (index == current.lastIndex && (description.isNotBlank() || cur.totalAmount > 0.0)) {
                current.add(DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0))
            }
            _activeEntries.value = current
        }
    }

    // 3. تعديل الكمية / العدد
    fun updateInvoiceRowQuantity(index: Int, quantity: Double) {
        val current = _activeEntries.value.toMutableList()
        if (index in current.indices) {
            val q = if (quantity <= 0.0) 1.0 else quantity
            val cur = current[index]
            val newTotal = if (cur.unitPrice > 0.0) q * cur.unitPrice else cur.totalAmount
            val newPrice = if (newTotal > 0.0 && q > 0.0) newTotal / q else cur.unitPrice
            current[index] = cur.copy(
                quantity = q,
                unitPrice = newPrice,
                totalAmount = newTotal
            )
            if (index == current.lastIndex && (cur.description.isNotBlank() || newTotal > 0.0)) {
                current.add(DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0))
            }
            _activeEntries.value = current
        }
    }

    // 4. تعديل سعر الوحدة (اختياري، يحدّث الإجمالي تلقائياً)
    fun updateInvoiceRowUnitPrice(index: Int, unitPrice: Double) {
        val current = _activeEntries.value.toMutableList()
        if (index in current.indices) {
            val cur = current[index]
            val q = if (cur.quantity <= 0.0) 1.0 else cur.quantity
            val total = q * unitPrice
            current[index] = cur.copy(
                unitPrice = unitPrice,
                totalAmount = total
            )
            if (index == current.lastIndex && (cur.description.isNotBlank() || total > 0.0)) {
                current.add(DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0))
            }
            _activeEntries.value = current
        }
    }

    fun updateInvoiceEntry(index: Int, description: String, quantity: Double, unitPrice: Double) {
        updateInvoiceRowUnitPrice(index, unitPrice)
        updateInvoiceRowDescription(index, description)
    }

    fun updateInvoiceEntryByTotal(index: Int, description: String, quantity: Double, totalAmount: Double) {
        updateInvoiceRowTotal(index, totalAmount)
        updateInvoiceRowDescription(index, description)
    }

    fun addInvoiceEntry() {
        val current = _activeEntries.value.toMutableList()
        current.add(DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0))
        _activeEntries.value = current
    }

    fun removeEntry(index: Int) {
        val current = _activeEntries.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            // التأكد من بقاء سطر واحد على الأقل متاحاً للكتابة
            if (current.isEmpty()) {
                val now = Date()
                val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
                val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
                if (_activeDocument.value.docType == DocumentType.SALES_INVOICE) {
                    current.add(DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0))
                } else if (_activeDocument.value.docType == DocumentType.CUSTOMER_LEDGER) {
                    current.add(DocumentEntryEntity(runningBalance = 0.0, credit = 0.0, debit = 0.0, entryDate = dateFormat.format(now), entryDay = dayFormat.format(now), description = ""))
                }
            }
            if (_activeDocument.value.docType == DocumentType.CUSTOMER_LEDGER) {
                _activeEntries.value = recalculateLedgerBalances(current)
            } else {
                _activeEntries.value = current
            }
        }
    }

    // --- Ledger Account Entry Management ---
    fun updateLedgerEntry(index: Int, description: String, debit: Double, credit: Double, dateStr: String, dayStr: String) {
        val current = _activeEntries.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(
                description = description,
                debit = debit,
                credit = credit,
                entryDate = dateStr,
                entryDay = dayStr
            )
            // التوسع التلقائي لكشف الحساب: إذا تم إدخال حركة في السطر الأخير، يضاف سطر جديد فوراً
            if (index == current.lastIndex && (description.isNotBlank() || debit > 0.0 || credit > 0.0)) {
                val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
                val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
                val now = Date()
                current.add(
                    DocumentEntryEntity(
                        runningBalance = 0.0,
                        credit = 0.0,
                        debit = 0.0,
                        entryDate = dateFormat.format(now),
                        entryDay = dayFormat.format(now),
                        description = ""
                    )
                )
            }
            _activeEntries.value = recalculateLedgerBalances(current)
        }
    }

    fun addLedgerEntry() {
        val current = _activeEntries.value.toMutableList()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
        val now = Date()
        current.add(
            DocumentEntryEntity(
                runningBalance = 0.0,
                credit = 0.0,
                debit = 0.0,
                entryDate = dateFormat.format(now),
                entryDay = dayFormat.format(now),
                description = ""
            )
        )
        _activeEntries.value = recalculateLedgerBalances(current)
    }

    private fun recalculateLedgerBalances(entries: List<DocumentEntryEntity>): List<DocumentEntryEntity> {
        var running = 0.0
        return entries.map { entry ->
            // الرصيد = السابق + عليه (مدين) - له (دائن)
            running += (entry.debit - entry.credit)
            entry.copy(runningBalance = running)
        }
    }

    fun saveActiveDocument(onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val doc = _activeDocument.value.copy(updatedAt = System.currentTimeMillis())
            // تنظيف الأسطر الفارغة الزائدة عند الحفظ
            val entriesToSave = _activeEntries.value.filter { entry ->
                when (doc.docType) {
                    DocumentType.SALES_INVOICE -> entry.description.isNotBlank() || entry.totalAmount > 0.0
                    DocumentType.CUSTOMER_LEDGER -> entry.description.isNotBlank() || entry.debit > 0.0 || entry.credit > 0.0
                    DocumentType.LINED_NOTE -> true
                }
            }.ifEmpty { _activeEntries.value.take(1) }

            val savedId = repository.saveDocument(doc, entriesToSave)
            _activeDocument.value = doc.copy(id = savedId)
            // حفظ نسخة احتياطية تلقائية فورية عند الحفظ
            BackupHelper.performAutoBackupIfEnabled(getApplication(), repository.dao, doc.storeName)
            onSaved(savedId)
        }
    }

    // --- Backup & Restore Methods ---
    fun triggerAutoBackup() {
        viewModelScope.launch {
            BackupHelper.performAutoBackupIfEnabled(getApplication(), repository.dao, _activeDocument.value.storeName)
        }
    }

    suspend fun exportManualBackup(context: Context) {
        BackupHelper.exportManualBackup(context, repository.dao, _activeDocument.value.storeName)
    }

    suspend fun restoreBackupFromJson(context: Context, json: String, onComplete: (Result<Int>) -> Unit) {
        val res = BackupHelper.restoreFromJson(context, repository.dao, json)
        onComplete(res)
    }

    suspend fun restoreLatestAutoBackup(context: Context, onComplete: (Result<Int>) -> Unit) {
        val res = BackupHelper.restoreLatestAutoBackup(context, repository.dao)
        onComplete(res)
    }

    fun updateDefaultStoreProfile(name: String, phone: String, address: String) {
        val cur = _activeDocument.value
        _activeDocument.value = cur.copy(
            storeName = name,
            storePhone = phone,
            storeAddress = address
        )
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            if (_currentScreen.value !is CurrentScreen.Home) {
                _currentScreen.value = CurrentScreen.Home
            }
        }
    }

    // --- AI Chatbot & Models ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                role = "model",
                content = "أهلاً بك في المساعد المحاسبي الذكي! أنا هنا لمساعدتك في تدقيق الحسابات، مراجعة فواتير البيع، صياغة القيود اليومية أو رسائل متابعة ديون العملاء."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _selectedAiModel = MutableStateFlow("gemini-3.5-flash")
    val selectedAiModel: StateFlow<String> = _selectedAiModel.asStateFlow()

    fun setSelectedAiModel(model: String) {
        _selectedAiModel.value = model
    }    
    private val _aiWriteEnabled = MutableStateFlow(
        application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("ai_write_enabled", false)
    )
    val aiWriteEnabled: StateFlow<Boolean> = _aiWriteEnabled.asStateFlow()

    fun setAiWriteEnabled(enabled: Boolean) {
        _aiWriteEnabled.value = enabled
        getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("ai_write_enabled", enabled).apply()
    }

    fun executeAiCommand(prompt: String, onComplete: (String) -> Unit = {}) {
        if (!_aiWriteEnabled.value) {
            onComplete("فعّل «تفويض تنفيذ أوامر الذكاء الاصطناعي» أولاً من شاشة المساعد.")
            return
        }
        viewModelScope.launch {
            val result = geminiService.interpretAccountingCommand(prompt, _selectedAiModel.value)
            val message = result.fold(
                onSuccess = { command -> executeParsedAiCommand(command) },
                onFailure = { "تعذر تفسير الأمر الذكي: §{it.message ?: "خطأ غير معروف"}" }
            )
            _chatMessages.value = _chatMessages.value + ChatMessage(role = "model", content = message)
            onComplete(message)
        }
    }

    private suspend fun executeParsedAiCommand(command: JSONObject): String {
        return when (command.optString("action")) {
            "create_invoice" -> {
                val customer = command.optString("customer").trim()
                val payment = if (command.optString("paymentType") == "CREDIT") PaymentType.CREDIT else PaymentType.CASH
                val itemsJson = command.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<DocumentEntryEntity>()
                for (i in 0 until itemsJson.length()) {
                    val item = itemsJson.optJSONObject(i) ?: continue
                    val q = item.optDouble("quantity", 1.0).coerceAtLeast(0.0)
                    val total = item.optDouble("totalAmount", 0.0)
                    val price = if (item.has("unitPrice")) item.optDouble("unitPrice", 0.0) else if (q > 0) total / q else 0.0
                    items.add(DocumentEntryEntity(
                        description = item.optString("description", "صنف").trim().ifEmpty { "صنف" },
                        quantity = q, unitPrice = price,
                        totalAmount = if (total > 0.0) total else q * price
                    ))
                }
                if (items.isEmpty()) return "لم يتم إنشاء الفاتورة: لم يحدد الذكاء الاصطناعي أي صنف."
                val doc = createDefaultDocument(DocumentType.SALES_INVOICE, customer).copy(paymentType = payment)
                val id = repository.saveDocument(doc, items)
                _activeDocument.value = doc.copy(id = id)
                _activeEntries.value = items + DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0)
                BackupHelper.performAutoBackupIfEnabled(getApplication(), repository.dao, doc.storeName)
                navigateTo(CurrentScreen.InvoiceEditor(id))
                "تم إنشاء الفاتورة رقم §{doc.docNumber} للعميل §{customer.ifEmpty { "غير محدد" }}."
            }
            "create_account" -> {
                val customer = command.optString("customer").trim()
                if (customer.isBlank()) return "لم يتم إنشاء الحساب: اذكر اسم العميل."
                val opening = command.optDouble("openingBalance", 0.0).coerceAtLeast(0.0)
                val doc = createDefaultDocument(DocumentType.CUSTOMER_LEDGER, customer)
                val now = Date()
                val entry = DocumentEntryEntity(
                    description = if (opening > 0) "رصيد افتتاحي" else "افتتاح حساب",
                    debit = opening, credit = 0.0, runningBalance = opening,
                    entryDate = SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(now),
                    entryDay = SimpleDateFormat("EEEE", Locale("ar")).format(now)
                )
                val id = repository.saveDocument(doc, listOf(entry))
                _activeDocument.value = doc.copy(id = id)
                _activeEntries.value = listOf(entry)
                BackupHelper.performAutoBackupIfEnabled(getApplication(), repository.dao, doc.storeName)
                navigateTo(CurrentScreen.CustomerProfile(customer))
                "تم إنشاء حساب العميل §{customer} برصيد افتتاحي §{String.format(Locale.US, "%.0f", opening)} ريال."
            }
            "add_account_entry" -> {
                val customer = command.optString("customer").trim()
                if (customer.isBlank()) return "لم تتم إضافة الحركة: اذكر اسم العميل."
                val existing = allDocuments.value.firstOrNull {
                    it.document.docType == DocumentType.CUSTOMER_LEDGER && it.document.customerName.trim() == customer
                }
                val doc = existing?.document ?: createDefaultDocument(DocumentType.CUSTOMER_LEDGER, customer)
                val entries = existing?.entries?.filter {
                    it.description.isNotBlank() || it.debit != 0.0 || it.credit != 0.0
                }?.toMutableList() ?: mutableListOf()
                val now = Date()
                entries.add(DocumentEntryEntity(
                    description = command.optString("description", "حركة حساب"),
                    debit = command.optDouble("debit", 0.0).coerceAtLeast(0.0),
                    credit = command.optDouble("credit", 0.0).coerceAtLeast(0.0),
                    entryDate = SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(now),
                    entryDay = SimpleDateFormat("EEEE", Locale("ar")).format(now)
                ))
                val normalized = recalculateLedgerBalances(entries)
                val id = repository.saveDocument(doc, normalized)
                _activeDocument.value = doc.copy(id = id)
                _activeEntries.value = normalized + DocumentEntryEntity(
                    entryDate = SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(now),
                    entryDay = SimpleDateFormat("EEEE", Locale("ar")).format(now)
                )
                BackupHelper.performAutoBackupIfEnabled(getApplication(), repository.dao, doc.storeName)
                navigateTo(CurrentScreen.CustomerProfile(customer))
                "تمت إضافة الحركة إلى حساب §{customer} وتحديث الرصيد."
            }
            "create_note" -> {
                val doc = createDefaultDocument(DocumentType.LINED_NOTE).copy(
                    title = command.optString("title", "ملاحظة"),
                    notes = command.optString("text", "")
                )
                val id = repository.saveDocument(doc, emptyList())
                _activeDocument.value = doc.copy(id = id)
                _activeEntries.value = emptyList()
                BackupHelper.performAutoBackupIfEnabled(getApplication(), repository.dao, doc.storeName)
                navigateTo(CurrentScreen.LinedNote(id))
                "تم إنشاء الملاحظة وحفظها."
            }
            "repair_data" -> repairData()
            else -> command.optString("message", "لم يتم تنفيذ أمر واضح.")
        }
    }

    private suspend fun repairData(): String {
        var repaired = 0
        for (docWithEntries in allDocuments.value) {
            val doc = docWithEntries.document
            val meaningful = docWithEntries.entries.filter {
                when (doc.docType) {
                    DocumentType.SALES_INVOICE -> it.description.isNotBlank() || it.totalAmount > 0.0
                    DocumentType.CUSTOMER_LEDGER -> it.description.isNotBlank() || it.debit != 0.0 || it.credit != 0.0
                    DocumentType.LINED_NOTE -> true
                }
            }
            val normalized = if (doc.docType == DocumentType.CUSTOMER_LEDGER) recalculateLedgerBalances(meaningful) else meaningful
            repository.saveDocument(doc.copy(updatedAt = System.currentTimeMillis()), normalized)
            repaired++
        }
        BackupHelper.performAutoBackupIfEnabled(getApplication(), repository.dao, _activeDocument.value.storeName)
        "تم فحص وإصلاح §{repaired} سجلًا: حُذفت الأسطر الفارغة الزائدة وأعيد حساب أرصدة الحسابات."
    }


    fun sendChatMessage(userText: String) {
        if (userText.isBlank()) return
        val currentList = _chatMessages.value.toMutableList()
        val userMsg = ChatMessage(role = "user", content = userText)
        currentList.add(userMsg)
        _chatMessages.value = currentList
        _isAiLoading.value = true

        viewModelScope.launch {
            val result = geminiService.sendChatMessage(
                messages = currentList,
                model = _selectedAiModel.value
            )
            _isAiLoading.value = false
            result.onSuccess { reply ->
                _chatMessages.value = _chatMessages.value + ChatMessage(role = "model", content = reply)
            }.onFailure { err ->
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    role = "model",
                    content = "حدث خطأ أثناء التواصل مع النموذج: ${err.localizedMessage ?: err.message}"
                )
            }
        }
    }

    // --- Image Generation (gemini-3-pro-image-preview with 1K, 2K, 4K affordance) ---
    private val _generatedImage = MutableStateFlow<Bitmap?>(null)
    val generatedImage: StateFlow<Bitmap?> = _generatedImage.asStateFlow()

    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    private val _selectedImageResolution = MutableStateFlow("1K") // 1K, 2K, 4K
    val selectedImageResolution: StateFlow<String> = _selectedImageResolution.asStateFlow()

    fun setSelectedImageResolution(res: String) {
        _selectedImageResolution.value = res
    }

    fun generateStampOrLogo(prompt: String) {
        if (prompt.isBlank()) return
        _isGeneratingImage.value = true
        viewModelScope.launch {
            val result = geminiService.generateImage(
                prompt = prompt,
                imageSize = _selectedImageResolution.value
            )
            _isGeneratingImage.value = false
            result.onSuccess { bitmap ->
                _generatedImage.value = bitmap
            }.onFailure {
                // Keep image state
            }
        }
    }

    // --- Text To Speech (TTS) ---
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    fun speakDocument(text: String) {
        if (_isSpeaking.value) {
            geminiService.stopAudio()
            _isSpeaking.value = false
            return
        }

        _isSpeaking.value = true
        viewModelScope.launch {
            geminiService.speakText(text) {
                _isSpeaking.value = false
            }
        }
    }

    fun stopSpeaking() {
        geminiService.stopAudio()
        _isSpeaking.value = false
    }

    override fun onCleared() {
        super.onCleared()
        geminiService.stopAudio()
    }
}
