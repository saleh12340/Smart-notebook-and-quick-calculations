package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ChatMessage
import com.example.ai.GeminiService
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

sealed class CurrentScreen {
    data object Home : CurrentScreen()
    data class InvoiceEditor(val docId: Long = 0) : CurrentScreen()
    data class CustomerLedger(val docId: Long = 0) : CurrentScreen()
    data class LinedNote(val docId: Long = 0) : CurrentScreen()
    data class ThermalPrint(val docId: Long) : CurrentScreen()
    data object AiAssistant : CurrentScreen()
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

    fun loadDocument(docId: Long, defaultType: DocumentType = DocumentType.SALES_INVOICE) {
        if (docId == 0L) {
            _activeDocument.value = createDefaultDocument(defaultType)
            _activeEntries.value = createInitialEntries(defaultType)
        } else {
            viewModelScope.launch {
                val found = allDocuments.value.find { it.document.id == docId }
                if (found != null) {
                    _activeDocument.value = found.document
                    _activeEntries.value = found.entries
                }
            }
        }
    }

    private fun createDefaultDocument(type: DocumentType): DocumentEntity {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
        val now = Date()
        val randomNum = String.format(Locale.US, "%07d", (100..9999).random())

        return DocumentEntity(
            docNumber = randomNum,
            title = when (type) {
                DocumentType.SALES_INVOICE -> "فاتورة بيع"
                DocumentType.CUSTOMER_LEDGER -> "كشف حساب عميل"
                DocumentType.LINED_NOTE -> "ملاحظة دفترية"
            },
            customerName = "",
            docType = type,
            paymentType = PaymentType.CASH,
            storeName = "مركز التجارة العام",
            storeAddress = "شارع السوق الرئيسي",
            storePhone = "770000000",
            commercialReg = "45120",
            poBox = "302",
            fax = "",
            dateString = dateFormat.format(now),
            dayString = dayFormat.format(now),
            notes = "",
            sellerSignature = "المسؤول"
        )
    }

    private fun createInitialEntries(type: DocumentType): List<DocumentEntryEntity> {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val dayFormat = SimpleDateFormat("EEEE", Locale("ar"))
        val now = Date()
        val dStr = dateFormat.format(now)
        val dayStr = dayFormat.format(now)

        return when (type) {
            DocumentType.SALES_INVOICE -> listOf(
                DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0),
                DocumentEntryEntity(description = "", quantity = 1.0, unitPrice = 0.0, totalAmount = 0.0),
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

    // --- Invoice Item Management ---
    fun updateInvoiceEntry(index: Int, description: String, quantity: Double, unitPrice: Double) {
        val current = _activeEntries.value.toMutableList()
        if (index in current.indices) {
            val total = quantity * unitPrice
            current[index] = current[index].copy(
                description = description,
                quantity = quantity,
                unitPrice = unitPrice,
                totalAmount = total
            )
            _activeEntries.value = current
        }
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
            val savedId = repository.saveDocument(doc, _activeEntries.value)
            _activeDocument.value = doc.copy(id = savedId)
            onSaved(savedId)
        }
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
