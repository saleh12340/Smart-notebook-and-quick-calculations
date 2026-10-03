package com.example.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import com.example.data.model.PaymentType
import com.example.ui.components.StampRed
import com.example.ui.viewmodel.CurrentScreen
import com.example.ui.viewmodel.CustomerSummary
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

    var selectedTab by remember { mutableIntStateOf(0) } // 0: حسابات العملاء, 1: الملاحظات الدفترية
    var showFabMenu by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerInitialDebit by remember { mutableStateOf("") }
    var docToDelete by remember { mutableStateOf<DocumentWithEntries?>(null) }

    // تجميع حسابات الأشخاص والعملاء
    val customerMap = remember(allDocs) {
        val map = mutableMapOf<String, MutableList<DocumentWithEntries>>()
        allDocs.forEach { docWithEntries ->
            val cName = docWithEntries.document.customerName.trim()
            if (cName.isNotEmpty()) {
                map.getOrPut(cName) { mutableListOf() }.add(docWithEntries)
            }
        }
        map
    }

    val customerSummaries = remember(customerMap) {
        customerMap.map { (name, docs) ->
            var totalDebit = 0.0
            var totalCredit = 0.0
            var totalInvoices = 0.0
            var latestDate = ""
            var latestUpdated = 0L

            docs.forEach { d ->
                if (d.document.updatedAt > latestUpdated) {
                    latestUpdated = d.document.updatedAt
                    latestDate = d.document.dateString
                }
                when (d.document.docType) {
                    DocumentType.SALES_INVOICE -> {
                        val invTotal = d.invoiceTotal
                        totalInvoices += invTotal
                        if (d.document.paymentType == PaymentType.CREDIT) {
                            totalDebit += invTotal
                        }
                    }
                    DocumentType.CUSTOMER_LEDGER -> {
                        totalDebit += d.totalDebit
                        totalCredit += d.totalCredit
                    }
                    DocumentType.LINED_NOTE -> {}
                }
            }

            CustomerSummary(
                customerName = name,
                totalDebit = totalDebit,
                totalCredit = totalCredit,
                netBalance = totalDebit - totalCredit,
                totalInvoicesAmount = totalInvoices,
                documentCount = docs.size,
                lastDate = latestDate,
                lastUpdatedAt = latestUpdated
            )
        }.sortedByDescending { it.lastUpdatedAt }
    }

    // فلترة العملاء بالبحث
    val filteredCustomers = remember(customerSummaries, searchQuery) {
        if (searchQuery.isBlank()) {
            customerSummaries
        } else {
            val q = searchQuery.trim().lowercase()
            customerSummaries.filter { it.customerName.lowercase().contains(q) }
        }
    }

    // فلترة الملاحظات العامة
    val generalNotes = remember(allDocs, searchQuery) {
        allDocs.filter { it.document.docType == DocumentType.LINED_NOTE }.filter { noteDoc ->
            if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                noteDoc.document.title.lowercase().contains(q) ||
                noteDoc.document.notes.lowercase().contains(q) ||
                noteDoc.document.customerName.lowercase().contains(q)
            }
        }
    }

    // إجماليات المحل العامة
    val grandTotalDebt = customerSummaries.filter { it.netBalance > 0 }.sumOf { it.netBalance }
    val grandTotalCredit = customerSummaries.filter { it.netBalance < 0 }.sumOf { Math.abs(it.netBalance) }

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
                                text = "بقالة العزي — إدارة حسابات العملاء",
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

                    // زر التقارير والحركات — الأحدث أولاً
                    IconButton(
                        onClick = { viewModel.navigateTo(CurrentScreen.Reports) },
                        modifier = Modifier.testTag("reports_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "التقارير",
                            tint = Color.White
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
                    modifier = Modifier.testTag("home_add_fab")
                )

                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    // 1. إضافة عميل جديد
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF1E3A8A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إضافة عميل جديد (حساب شخص)", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = {
                            showFabMenu = false
                            showAddCustomerDialog = true
                        },
                        modifier = Modifier.testTag("menu_add_customer")
                    )

                    // 2. إضافة ملاحظة دفترية
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFF475569))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إضافة ملاحظة دفترية", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = {
                            showFabMenu = false
                            viewModel.loadDocument(0L, DocumentType.LINED_NOTE)
                            viewModel.navigateTo(CurrentScreen.LinedNote(0L))
                        },
                        modifier = Modifier.testTag("menu_add_note")
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F5F9))
                .padding(innerPadding)
        ) {
            // بطاقة الملخص المالي الإجمالي
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "إجمالي الديون في السوق (لنا):", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                        Text(
                            text = "${String.format(Locale.US, "%.0f", grandTotalDebt)} ريال",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFF87171)
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color(0xFF475569)))

                    Column {
                        Text(text = "فائض أرصدة العملاء (لهم):", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                        Text(
                            text = "${String.format(Locale.US, "%.0f", grandTotalCredit)} ريال",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4ADE80)
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color(0xFF475569)))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "العملاء", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                        Text(
                            text = "${customerSummaries.size}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }

            // شريط البحث
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("بحث عن اسم عميل أو ملاحظة...", fontSize = 13.sp) },
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
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF1E3A8A)
                )
            )

            // علامات التبويب الرئيسية (حسابات العملاء / الملاحظات الدفترية)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFF1E3A8A),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("حسابات العملاء (${filteredCustomers.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("الملاحظات الدفترية (${generalNotes.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // محتوى التبويب المختار
            when (selectedTab) {
                0 -> {
                    // قائمة حسابات العملاء
                    if (filteredCustomers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("لا توجد حسابات عملاء مسجلة حالياً", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { showAddCustomerDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إضافة أول عميل الآن")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(filteredCustomers, key = { it.customerName }) { customer ->
                                CustomerAccountCard(
                                    customer = customer,
                                    onClick = {
                                        viewModel.navigateTo(CurrentScreen.CustomerProfile(customer.customerName))
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // قائمة الملاحظات الدفترية
                    if (generalNotes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("لا توجد ملاحظات دفترية مسجلة", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        viewModel.loadDocument(0L, DocumentType.LINED_NOTE)
                                        viewModel.navigateTo(CurrentScreen.LinedNote(0L))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إضافة ملاحظة جديدة")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(generalNotes, key = { it.document.id }) { noteDoc ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.loadDocument(noteDoc.document.id, DocumentType.LINED_NOTE)
                                            viewModel.navigateTo(CurrentScreen.LinedNote(noteDoc.document.id))
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF475569)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.White)
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = noteDoc.document.title.ifEmpty { "ملاحظة دفترية" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = noteDoc.document.notes.take(60).ifEmpty { "بدون تفاصيل إضافية..." },
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = noteDoc.document.dateString,
                                                fontSize = 10.sp,
                                                color = Color.DarkGray
                                            )
                                        }

                                        IconButton(onClick = {
                                            viewModel.navigateTo(CurrentScreen.ThermalPrint(noteDoc.document.id))
                                        }) {
                                            Icon(Icons.Default.Print, contentDescription = "طباعة", tint = Color(0xFF0284C7))
                                        }

                                        IconButton(onClick = { docToDelete = noteDoc }) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFDC2626))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // نافذة إضافة عميل جديد
    if (showAddCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            title = { Text("إضافة حساب عميل جديد", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "أدخل اسم العميل لإنشاء دفتر حساب مخصص له:",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newCustomerName,
                        onValueChange = { newCustomerName = it },
                        label = { Text("اسم العميل أو الشخص *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCustomerInitialDebit,
                        onValueChange = { newCustomerInitialDebit = it },
                        label = { Text("رصيد سابق / دين ابتدائي (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCustomerName.isNotBlank()) {
                            val initDebit = newCustomerInitialDebit.toDoubleOrNull() ?: 0.0
                            viewModel.addNewCustomer(newCustomerName.trim(), initDebit)
                            showAddCustomerDialog = false
                            newCustomerName = ""
                            newCustomerInitialDebit = ""
                            Toast.makeText(context, "تم إنشاء حساب العميل بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
                ) {
                    Text("إنشاء الحساب")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomerDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // نافذة تأكيد الحذف للملاحظات
    if (docToDelete != null) {
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("تأكيد الحذف", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف هذه الملاحظة نهائياً؟") },
            confirmButton = {
                Button(
                    onClick = {
                        docToDelete?.let { viewModel.deleteDocument(it.document.id) }
                        docToDelete = null
                        Toast.makeText(context, "تم الحذف", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("حذف")
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

/**
 * بطاقة حساب العميل في الشاشة الرئيسية
 */
@Composable
fun CustomerAccountCard(
    customer: CustomerSummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E3A8A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.customerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "${customer.documentCount} حركات مسجلة ${if (customer.lastDate.isNotEmpty()) "• ${customer.lastDate}" else ""}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format(Locale.US, "%.0f", Math.abs(customer.netBalance))} ريال",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = if (customer.netBalance > 0) Color(0xFFDC2626) else if (customer.netBalance < 0) Color(0xFF16A34A) else Color.DarkGray
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (customer.netBalance > 0) Color(0xFFFEE2E2) else if (customer.netBalance < 0) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = if (customer.netBalance > 0) "عليه (مدين)" else if (customer.netBalance < 0) "له (دائن)" else "مصفى",
                        color = if (customer.netBalance > 0) Color(0xFFDC2626) else if (customer.netBalance < 0) Color(0xFF16A34A) else Color.DarkGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
