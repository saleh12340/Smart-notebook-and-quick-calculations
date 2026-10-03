package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import com.example.data.model.PaymentType
import com.example.print.ThermalPrintHelper
import com.example.ui.components.StampRed
import com.example.ui.viewmodel.CurrentScreen
import com.example.ui.viewmodel.DaftarViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DaftarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allDocs by viewModel.allDocuments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    var showFabMenu by remember { mutableStateOf(false) }
    var docToDelete by remember { mutableStateOf<DocumentWithEntries?>(null) }

    // Filter documents
    val filteredDocs = allDocs.filter { docWithEntries ->
        val matchesFilter = selectedFilter == null || docWithEntries.document.docType == selectedFilter
        val matchesSearch = if (searchQuery.isBlank()) true else {
            val q = searchQuery.trim().lowercase()
            docWithEntries.document.customerName.lowercase().contains(q) ||
            docWithEntries.document.docNumber.lowercase().contains(q) ||
            docWithEntries.document.title.lowercase().contains(q) ||
            docWithEntries.entries.any { it.description.lowercase().contains(q) }
        }
        matchesFilter && matchesSearch
    }

    // Summary calculations
    val totalInvoicesSum = allDocs.filter { it.document.docType == DocumentType.SALES_INVOICE }.sumOf { it.invoiceTotal }
    val totalLedgerDebit = allDocs.filter { it.document.docType == DocumentType.CUSTOMER_LEDGER }.sumOf { it.totalDebit }
    val totalLedgerCredit = allDocs.filter { it.document.docType == DocumentType.CUSTOMER_LEDGER }.sumOf { it.totalCredit }
    val notesCount = allDocs.count { it.document.docType == DocumentType.LINED_NOTE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E3A8A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "دفتر الفواتير والحسابات",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = "كتابة بين الأسطر وطباعة حرارية",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                actions = {
                    // زر المساعد المحاسبي الذكي
                    IconButton(
                        onClick = { viewModel.navigateTo(CurrentScreen.AiAssistant) },
                        modifier = Modifier.testTag("ai_assistant_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "المساعد الذكي",
                            tint = Color(0xFFFDE047)
                        )
                    }

                    // زر الإعدادات والنسخ الاحتياطي
                    IconButton(
                        onClick = { viewModel.navigateTo(CurrentScreen.Settings) },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات والنسخ الاحتياطي",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { showFabMenu = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("إضافة جديدة", fontWeight = FontWeight.Bold) },
                    containerColor = Color(0xFF1E3A8A),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_new_fab")
                )

                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF1E3A8A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("فاتورة أصناف (كما في الصورة)", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = {
                            showFabMenu = false
                            viewModel.loadDocument(0L, DocumentType.SALES_INVOICE)
                            viewModel.navigateTo(CurrentScreen.InvoiceEditor(0L))
                        },
                        modifier = Modifier.testTag("new_sales_invoice_menu")
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color(0xFF16A34A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("كشف حساب عميل (دفتر الحسابات)", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = {
                            showFabMenu = false
                            viewModel.loadDocument(0L, DocumentType.CUSTOMER_LEDGER)
                            viewModel.navigateTo(CurrentScreen.CustomerLedger(0L))
                        },
                        modifier = Modifier.testTag("new_customer_ledger_menu")
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFFD97706))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ملاحظة مسطرة جديدة", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = {
                            showFabMenu = false
                            viewModel.loadDocument(0L, DocumentType.LINED_NOTE)
                            viewModel.navigateTo(CurrentScreen.LinedNote(0L))
                        },
                        modifier = Modifier.testTag("new_lined_note_menu")
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding)
        ) {
            // شريط الإحصائيات السريعة
            QuickStatsBar(
                invoicesSum = totalInvoicesSum,
                ledgerDebit = totalLedgerDebit,
                ledgerCredit = totalLedgerCredit,
                notesCount = notesCount
            )

            // شريط البحث
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("بحث باسم العميل أو رقم الفاتورة...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.Gray)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("home_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF1E3A8A),
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                )
            )

            // أزرار التصفية (الكل، فواتير أصناف، حسابات عملاء، ملاحظات)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { viewModel.setSelectedFilter(null) },
                        label = { Text("الكل (${allDocs.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0F172A),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == DocumentType.SALES_INVOICE,
                        onClick = { viewModel.setSelectedFilter(DocumentType.SALES_INVOICE) },
                        label = { Text("فواتير الأصناف", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E3A8A),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == DocumentType.CUSTOMER_LEDGER,
                        onClick = { viewModel.setSelectedFilter(DocumentType.CUSTOMER_LEDGER) },
                        label = { Text("حسابات العملاء", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF16A34A),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == DocumentType.LINED_NOTE,
                        onClick = { viewModel.setSelectedFilter(DocumentType.LINED_NOTE) },
                        label = { Text("الملاحظات", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD97706),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // قائمة البطاقات
            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا توجد سجلات مطابقة",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "اضغط على زر (إضافة جديدة) لإنشاء فاتورة أو كشف حساب",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDocs, key = { it.document.id }) { docWithEntries ->
                        DocumentCardItem(
                            docWithEntries = docWithEntries,
                            onClick = {
                                viewModel.loadDocument(docWithEntries.document.id, docWithEntries.document.docType)
                                when (docWithEntries.document.docType) {
                                    DocumentType.SALES_INVOICE -> viewModel.navigateTo(CurrentScreen.InvoiceEditor(docWithEntries.document.id))
                                    DocumentType.CUSTOMER_LEDGER -> viewModel.navigateTo(CurrentScreen.CustomerLedger(docWithEntries.document.id))
                                    DocumentType.LINED_NOTE -> viewModel.navigateTo(CurrentScreen.LinedNote(docWithEntries.document.id))
                                }
                            },
                            onPrintClick = {
                                viewModel.loadDocument(docWithEntries.document.id, docWithEntries.document.docType)
                                viewModel.navigateTo(CurrentScreen.ThermalPrint(docWithEntries.document.id))
                            },
                            onSpeakClick = {
                                val textToRead = when (docWithEntries.document.docType) {
                                    DocumentType.SALES_INVOICE -> {
                                        "فاتورة مبيعات رقم ${docWithEntries.document.docNumber} للعميل ${docWithEntries.document.customerName.ifEmpty { "المحترم" }}، الإجمالي الكلي ${docWithEntries.invoiceTotal} ريال."
                                    }
                                    DocumentType.CUSTOMER_LEDGER -> {
                                        "كشف حساب ${docWithEntries.document.customerName.ifEmpty { "العميل" }}، إجمالي عليه ${docWithEntries.totalDebit} ريال، وإجمالي له ${docWithEntries.totalCredit} ريال، وصافي الرصيد ${Math.abs(docWithEntries.netBalance)} ريال."
                                    }
                                    DocumentType.LINED_NOTE -> {
                                        "${docWithEntries.document.title}: ${docWithEntries.document.notes}"
                                    }
                                }
                                viewModel.speakDocument(textToRead)
                            },
                            onDeleteClick = { docToDelete = docWithEntries }
                        )
                    }
                }
            }
        }
    }

    // تأكيد الحذف
    docToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("تأكيد الحذف", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف السجل رقم ${target.document.docNumber} (${target.document.customerName})؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(target.document.id)
                        docToDelete = null
                    }
                ) {
                    Text("حذف", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun QuickStatsBar(
    invoicesSum: Double,
    ledgerDebit: Double,
    ledgerCredit: Double,
    notesCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // فواتير المبيعات (أزرق)
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Text("المبيعات", fontSize = 9.sp, color = Color(0xFF1E40AF), fontWeight = FontWeight.Bold)
                Text(
                    text = String.format(Locale.US, "%.0f ر.ي", invoicesSum),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E40AF)
                )
            }
        }

        // أرصدة مدينة - عليهم (أحمر)
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Text("عليهم (مدين)", fontSize = 9.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                Text(
                    text = String.format(Locale.US, "%.0f ر.ي", ledgerDebit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFDC2626)
                )
            }
        }

        // أرصدة دائنة - لهم (أخضر)
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Text("لهم (دائن)", fontSize = 9.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                Text(
                    text = String.format(Locale.US, "%.0f ر.ي", ledgerCredit),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF047857)
                )
            }
        }

        // ملاحظات دفترية (عسلي/ذهبي)
        Card(
            modifier = Modifier.weight(0.9f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Text("الملاحظات", fontSize = 9.sp, color = Color(0xFFB45309), fontWeight = FontWeight.Bold)
                Text(
                    text = "$notesCount ملاحظة",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFB45309)
                )
            }
        }
    }
}

@Composable
fun DocumentCardItem(
    docWithEntries: DocumentWithEntries,
    onClick: () -> Unit,
    onPrintClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val doc = docWithEntries.document
    val entries = docWithEntries.entries

    val themeColor = when (doc.docType) {
        DocumentType.SALES_INVOICE -> Color(0xFF1E40AF)
        DocumentType.CUSTOMER_LEDGER -> Color(0xFF047857)
        DocumentType.LINED_NOTE -> Color(0xFFB45309)
    }

    val cardBg = when (doc.docType) {
        DocumentType.SALES_INVOICE -> Color(0xFFF8FAFC)
        DocumentType.CUSTOMER_LEDGER -> Color(0xFFF0FDF4)
        DocumentType.LINED_NOTE -> Color(0xFFFFFBEB)
    }

    val cardBorder = when (doc.docType) {
        DocumentType.SALES_INVOICE -> Color(0xFF93C5FD)
        DocumentType.CUSTOMER_LEDGER -> Color(0xFFA7F3D0)
        DocumentType.LINED_NOTE -> Color(0xFFFDE68A)
    }

    val typeIcon = when (doc.docType) {
        DocumentType.SALES_INVOICE -> Icons.Default.ReceiptLong
        DocumentType.CUSTOMER_LEDGER -> Icons.AutoMirrored.Filled.MenuBook
        DocumentType.LINED_NOTE -> Icons.Default.EditNote
    }

    val typeLabel = when (doc.docType) {
        DocumentType.SALES_INVOICE -> if (doc.paymentType == PaymentType.CASH) "فاتورة أصناف (نقداً)" else "فاتورة أصناف (آجل)"
        DocumentType.CUSTOMER_LEDGER -> "كشف حساب عميل"
        DocumentType.LINED_NOTE -> "ملاحظة دفترية"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("document_card_${doc.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // الصف العلوي: الشارة والأيقونة ورقم الفاتورة والتاريخ
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = themeColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = typeLabel,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "№ ${doc.docNumber}",
                    color = StampRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${doc.dateString} (${doc.dayString})",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // اسم العميل أو العنوان
            Text(
                text = doc.customerName.ifEmpty { doc.title.ifEmpty { "سجل بدون اسم" } },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // معاينة سريعة للأصناف أو الملاحظة
            when (doc.docType) {
                DocumentType.SALES_INVOICE -> {
                    val previewItems = entries.take(2).joinToString(" • ") { it.description.ifEmpty { "صنف" } }
                    Text(
                        text = if (previewItems.isNotEmpty()) "$previewItems..." else "لا توجد أصناف مدخلة",
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DocumentType.CUSTOMER_LEDGER -> {
                    Text(
                        text = "عدد العمليات: ${entries.size} | له: ${docWithEntries.totalCredit} | عليه: ${docWithEntries.totalDebit}",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
                DocumentType.LINED_NOTE -> {
                    Text(
                        text = doc.notes.ifEmpty { "لا توجد تفاصيل" },
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شريط المعلومات السفلي والأزرار
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // المبلغ
                when (doc.docType) {
                    DocumentType.SALES_INVOICE -> {
                        Text(
                            text = "الإجمالي: ${String.format(Locale.US, "%.2f ريال", docWithEntries.invoiceTotal)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E3A8A)
                        )
                    }
                    DocumentType.CUSTOMER_LEDGER -> {
                        val net = docWithEntries.netBalance
                        val netColor = if (net >= 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                        val netLabel = if (net >= 0) "عليه (مدين)" else "له (دائن)"
                        Text(
                            text = "الصافي: ${String.format(Locale.US, "%.2f ريال", Math.abs(net))} ($netLabel)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = netColor
                        )
                    }
                    DocumentType.LINED_NOTE -> {
                        Text(text = "ملاحظة دفترية", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // زر الاستماع بالصوت (Gemini TTS)
                IconButton(
                    onClick = onSpeakClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "قراءة صوتية",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // زر الطباعة الحرارية
                IconButton(
                    onClick = onPrintClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "طباعة حرارية",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // زر الحذف
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
