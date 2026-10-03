package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentEntryEntity
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import com.example.ui.viewmodel.DaftarViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MovementRow(
    val document: DocumentWithEntries,
    val entry: DocumentEntryEntity?,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: DaftarViewModel,
    onBack: () -> Unit
) {
    val allDocuments by viewModel.allDocuments.collectAsState()
    var selectedFilter by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }

    val movements = remember(allDocuments, selectedFilter, search) {
        val flattened = allDocuments.flatMap { doc ->
            val meaningfulEntries = doc.entries.filter {
                it.description.isNotBlank() ||
                    it.totalAmount != 0.0 ||
                    it.debit != 0.0 ||
                    it.credit != 0.0
            }

            if (doc.document.docType == DocumentType.CUSTOMER_LEDGER && meaningfulEntries.isNotEmpty()) {
                meaningfulEntries.map { entry -> MovementRow(doc, entry, doc.document.updatedAt) }
            } else {
                listOf(MovementRow(doc, null, doc.document.updatedAt))
            }
        }

        flattened
            .filter { row ->
                val typeOk = when (selectedFilter) {
                    1 -> row.document.document.docType == DocumentType.SALES_INVOICE
                    2 -> row.document.document.docType == DocumentType.CUSTOMER_LEDGER
                    3 -> row.document.document.docType == DocumentType.LINED_NOTE
                    else -> true
                }
                val q = search.trim()
                val searchOk = q.isBlank() ||
                    row.document.document.customerName.contains(q, ignoreCase = true) ||
                    row.document.document.title.contains(q, ignoreCase = true) ||
                    row.document.document.docNumber.contains(q, ignoreCase = true) ||
                    (row.entry?.description?.contains(q, ignoreCase = true) == true)
                typeOk && searchOk
            }
            .sortedWith(compareByDescending<MovementRow> { it.timestamp }
                .thenByDescending { it.entry?.id ?: 0L })
    }

    val totalInvoices = allDocuments.count { it.document.docType == DocumentType.SALES_INVOICE }
    val totalAccounts = allDocuments.count { it.document.docType == DocumentType.CUSTOMER_LEDGER }
    val totalNotes = allDocuments.count { it.document.docType == DocumentType.LINED_NOTE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("التقارير", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("جميع الحركات — الأحدث أولاً", fontSize = 11.sp, color = Color.White.copy(alpha = .82f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F5F9))
                .padding(padding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryItem("الفواتير", totalInvoices, Modifier.weight(1f))
                SummaryItem("الحسابات", totalAccounts, Modifier.weight(1f))
                SummaryItem("الملاحظات", totalNotes, Modifier.weight(1f))
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("بحث في الحركات...") }
            )

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("الكل", "الفواتير", "الحسابات", "الملاحظات")
                items(filters.size) { index ->
                    FilterChip(
                        selected = selectedFilter == index,
                        onClick = { selectedFilter = index },
                        label = { Text(filters[index], fontWeight = FontWeight.Bold) },
                        leadingIcon = if (index == 0) {
                            { Icon(Icons.Default.Assessment, contentDescription = null) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E3A8A),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }

            if (movements.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد حركات مسجلة", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    items(movements, key = { row ->
                        "\${row.document.document.id}-\${row.entry?.id ?: 0L}"
                    }) { row ->
                        MovementCard(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(title: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = Color.Gray)
            Text(value.toString(), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun MovementCard(row: MovementRow) {
    val doc = row.document.document
    val entry = row.entry
    val typeText = when (doc.docType) {
        DocumentType.SALES_INVOICE -> if (doc.paymentType.name == "CREDIT") "فاتورة بيع — آجل" else "فاتورة بيع — نقداً"
        DocumentType.CUSTOMER_LEDGER -> "حركة حساب"
        DocumentType.LINED_NOTE -> "ملاحظة"
    }
    val icon = when (doc.docType) {
        DocumentType.SALES_INVOICE -> Icons.Default.ReceiptLong
        DocumentType.CUSTOMER_LEDGER -> Icons.Default.Description
        DocumentType.LINED_NOTE -> Icons.Default.EditNote
    }
    val date = if (entry?.entryDate?.isNotBlank() == true) entry.entryDate else doc.dateString
    val detail = entry?.description?.takeIf { it.isNotBlank() } ?: doc.title.ifBlank { "حركة" }
    val amount = when {
        entry != null && entry.totalAmount != 0.0 -> entry.totalAmount
        entry != null && entry.debit != 0.0 -> entry.debit
        entry != null && entry.credit != 0.0 -> entry.credit
        else -> row.document.invoiceTotal
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1E3A8A))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(typeText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    text = if (doc.customerName.isNotBlank()) doc.customerName else detail,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (doc.customerName.isNotBlank()) detail else "رقم \${doc.docNumber}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "\${date.ifBlank { formatTimestamp(row.timestamp) }} • \${formatTimestamp(row.timestamp, true)}",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
            if (amount != 0.0) {
                Text(
                    text = String.format(Locale.US, "%,.0f", amount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long, timeOnly: Boolean = false): String {
    val pattern = if (timeOnly) "HH:mm" else "yyyy/MM/dd"
    return SimpleDateFormat(pattern, Locale("ar")).format(Date(timestamp))
}
