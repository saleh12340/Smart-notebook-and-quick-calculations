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
                    if (found.document.docType == DocumentType.SALES_INVOICE) {
                        while (entries.size < 6 || (entries.lastOrNull()?.let { it.description.isNotBlank() || it.totalAmount > 0.0 } == true)) {
                            entries.add(DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0))
                            if (entries.size >= 6 && entries.last().description.isBlank() && entries.last().totalAmount == 0.0) break
                        }
                    } else if (found.document.docType == DocumentType.CUSTOMER_LEDGER) {
                        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
                        val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
                        val now = Date()
                        while (entries.size < 6 || (entries.lastOrNull()?.let { it.description.isNotBlank() || it.debit > 0.0 || it.credit > 0.0 } == true)) {
                            entries.add(DocumentEntryEntity(runningBalance = 0.0, credit = 0.0, debit = 0.0, entryDate = dateFormat.format(now), entryDay = dayFormat.format(now), description = ""))
                            if (entries.size >= 6 && entries.last().description.isBlank() && entries.last().debit == 0.0 && entries.last().credit == 0.0) break
                        }
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
            DocumentType.SALES_INVOICE -> List(6) {
                DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0)
            }
            DocumentType.CUSTOMER_LEDGER -> List(6) {
                DocumentEntryEntity(
                    runningBalance = 0.0,
                    credit = 0.0,
                    debit = 0.0,
                    entryDate = dStr,
                    entryDay = dayStr,
                    description = ""
                )
            }
            DocumentType.LINED_NOTE -> emptyList()
        }
    }

    fun updateDocumentHeader(updated: DocumentEntity) {
        _activeDocument.value = updated
    }

    fun updateDocumentNoteText(text: String) {
        _activeDocument.value = _activeDocument.value.copy(notes = text)
    }

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
            if (index == current.lastIndex && (cur.description.isNotBlank() || totalAmount > 0.0)) {
                current.add(DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0))
            }
            _activeEntries.value = current
        }
    }

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
            running += (entry.debit - entry.credit)
            entry.copy(runningBalance = running)
        }
    }

    fun saveActiveDocument(onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val doc = _activeDocument.value.copy(updatedAt = System.currentTimeMillis())
            val entriesToSave = _activeEntries.value.filter { entry ->
                when (doc.docType) {
                    DocumentType.SALES_INVOICE -> entry.description.isNotBlank() || entry.totalAmount > 0.0
                    DocumentType.CUSTOMER_LEDGER -> entry.description.isNotBlank() || entry.debit > 0.0 || entry.credit > 0.0
                    DocumentType.LINED_NOTE -> true
                }
            }.ifEmpty { _activeEntries.value.take(1) }

            val savedId = repository.saveDocument(doc, entriesToSave)
            _activeDocument.value = doc.copy(id = savedId)
            BackupHelper.performAutoBackupIfEnabled(getApplication(), repository.dao, doc.storeName)
            onSaved(savedId)
        }
    }

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

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                role = "model",
                content = "أهلاً بك في المساعد المحاسبي الذكي! أنا هنا لمساعدتك في تدقيق الحسابات، مراجعة فواتير البيع، واستفساراتك المحاسبية."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _selectedAiModel = MutableStateFlow("gemini-3.5-flash")
    val selectedAiModel: StateFlow<String> = _selectedAiModel.asStateFlow()

    private val _aiWriteEnabled = MutableStateFlow(false)
    val aiWriteEnabled: StateFlow<Boolean> = _aiWriteEnabled.asStateFlow()

    fun setSelectedAiModel(model: String) {
        _selectedAiModel.value = model
    }

    fun setAiWriteEnabled(enabled: Boolean) {
        _aiWriteEnabled.value = enabled
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

    fun executeAiCommand(command: String, onResult: (String) -> Unit) {
        if (command.isBlank()) return
        val currentList = _chatMessages.value.toMutableList()
        val userMsg = ChatMessage(role = "user", content = command)
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
                onResult("تم التنفيذ بنجاح")
            }.onFailure { err ->
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    role = "model",
                    content = "حدث خطأ أثناء التنفيذ: ${err.localizedMessage ?: err.message}"
                )
                onResult("فشل التنفيذ: ${err.localizedMessage ?: err.message}")
            }
        }
    }

    private val _generatedImage = MutableStateFlow<Bitmap?>(null)
    val generatedImage: StateFlow<Bitmap?> = _generatedImage.asStateFlow()

    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    private val _selectedImageResolution = MutableStateFlow("1K")
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

