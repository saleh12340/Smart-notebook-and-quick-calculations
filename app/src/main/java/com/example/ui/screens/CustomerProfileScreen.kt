package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import com.example.data.model.PaymentType
import com.example.ui.viewmodel.CurrentScreen
import com.example.ui.viewmodel.DaftarViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileScreen(
    viewModel: DaftarViewModel,
    customerName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allDocs by viewModel.allDocuments.collectAsState()

    val customerDocs = remember(allDocs, customerName) {
        allDocs.filter { it.document.customerName.trim().equals(customerName.trim(), ignoreCase = true) }
    }

    var documentToDelete by remember { mutableStateOf<DocumentWithEntries?>(null) }

    // حساب إجماليات العميل
    var totalDebit = 0.0
    var totalCredit = 0.0
    var totalInvoices = 0.0

    customerDocs.forEach { docWithEntries ->
        when (docWithEntries.document.docType) {
            DocumentType.SALES_INVOICE -> {
                val invTotal = docWithEntries.invoiceTotal
                totalInvoices += invTotal
                if (docWithEntries.document.paymentType == PaymentType.CREDIT) {
                    totalDebit += invTotal
                }
            }
            DocumentType.CUSTOMER_LEDGER -> {
                totalDebit += docWithEntries.totalDebit
                totalCredit += docWithEntries.totalCredit
            }
            DocumentType.LINED_NOTE -> {}
        }
    }

    val netBalance = totalDebit - totalCredit

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E3A8A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "حساب: $customerName",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${customerDocs.size} مستندات وحركات مسجلة",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // مشاركة كشف حساب واتساب
                    IconButton(
                        onClick = {
                            val msg = StringBuilder()
                            msg.append("📄 *كشف حساب العميل لدى بقالة العزي*\n")
                            msg.append("👤 العميل: $customerName\n")
                            msg.append("----------------------------\n")
                            customerDocs.forEach { item ->
                                val typeLabel = when (item.document.docType) {
                                    DocumentType.SALES_INVOICE -> "فاتورة بيع"
                                    DocumentType.CUSTOMER_LEDGER -> "كشف حساب"
                                    DocumentType.LINED_NOTE -> "ملاحظة"
                                }
                                val amountStr = if (item.document.docType == DocumentType.SALES_INVOICE) {
                                    "${String.format(Locale.US, "%.0f", item.invoiceTotal)} ريال"
                                } else {
                                    "مدين: ${String.format(Locale.US, "%.0f", item.totalDebit)} | دائن: ${String.format(Locale.US, "%.0f", item.totalCredit)}"
                                }
                                msg.append("• ${item.document.dateString} | $typeLabel (${item.document.docNumber}): $amountStr\n")
                            }
                            msg.append("----------------------------\n")
                            val balLabel = if (netBalance >= 0) "المتبقي عليكم (مدين)" else "رصيدكم الفائض (دائن)"
                            msg.append("💰 *صافي الرصيد الحالي:* ${String.format(Locale.US, "%.2f", Math.abs(netBalance))} ريال ($balLabel)\n\n")
                            msg.append("شاكرين حسن تعاملكم معنا — بقالة العزي (776425052)")

                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, msg.toString())
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "إرسال كشف الحساب عبر"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F5F9))
                .padding(innerPadding)
        ) {
            // بطاقة ملخص الرصيد المالي للعميل
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (netBalance > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (netBalance > 0) "إجمالي المبلغ المطلوب من العميل (دين عليه):" else if (netBalance < 0) "رصيد دائن للعميل (فائض له):" else "الحساب مصفى تماماً:",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.2f", Math.abs(netBalance))} ريال",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (netBalance > 0) Color(0xFFDC2626) else if (netBalance < 0) Color(0xFF16A34A) else Color.DarkGray
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (netBalance > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                        ) {
                            Text(
                                text = if (netBalance > 0) "عليه (مدين)" else if (netBalance < 0) "له (دائن)" else "مصفى",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "إجمالي له (دائن): ${String.format(Locale.US, "%.0f", totalCredit)} ريال", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                        Text(text = "إجمالي عليه (مدين): ${String.format(Locale.US, "%.0f", totalDebit)} ريال", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // شريط أزرار الإضافة لحساب العميل
            Text(
                text = "إضافة مستند جديد باسم العميل:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. إضافة فاتورة
                Button(
                    onClick = {
                        viewModel.loadDocument(0L, DocumentType.SALES_INVOICE, customerName)
                        viewModel.navigateTo(CurrentScreen.InvoiceEditor(0L, customerName))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("فاتورة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // 2. إضافة كشف حساب
                Button(
                    onClick = {
                        viewModel.loadDocument(0L, DocumentType.CUSTOMER_LEDGER, customerName)
                        viewModel.navigateTo(CurrentScreen.CustomerLedger(0L, customerName))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("كشف حساب", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // 3. إضافة ملاحظة
                Button(
                    onClick = {
                        viewModel.loadDocument(0L, DocumentType.LINED_NOTE, customerName)
                        viewModel.navigateTo(CurrentScreen.LinedNote(0L, customerName))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ملاحظة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // قائمة مستندات العميل
            if (customerDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("لا توجد فواتير أو حركات مسجلة باسم هذا العميل بعد", color = Color.Gray, fontSize = 13.sp)
                        Text("اضغط على أحد الأزرار بالأعلى لإنشاء أول فاتورة أو كشف حساب", color = Color.DarkGray, fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(customerDocs, key = { it.document.id }) { docWithEntries ->
                        CustomerDocItemCard(
                            docWithEntries = docWithEntries,
                            onClick = {
                                viewModel.loadDocument(docWithEntries.document.id, docWithEntries.document.docType)
                                when (docWithEntries.document.docType) {
                                    DocumentType.SALES_INVOICE -> viewModel.navigateTo(CurrentScreen.InvoiceEditor(docWithEntries.document.id))
                                    DocumentType.CUSTOMER_LEDGER -> viewModel.navigateTo(CurrentScreen.CustomerLedger(docWithEntries.document.id))
                                    DocumentType.LINED_NOTE -> viewModel.navigateTo(CurrentScreen.LinedNote(docWithEntries.document.id))
                                }
                            },
                            onPrint = {
                                viewModel.navigateTo(CurrentScreen.ThermalPrint(docWithEntries.document.id))
                            },
                            onDelete = {
                                documentToDelete = docWithEntries
                            }
                        )
                    }
                }
            }
        }
    }

    if (documentToDelete != null) {
        val doc = documentToDelete!!.document
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("حذف المستند", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف ${doc.title} رقم (${doc.docNumber})؟ لا يمكن التراجع بعد الحذف.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc.id)
                        documentToDelete = null
                        Toast.makeText(context, "تم حذف المستند", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun CustomerDocItemCard(
    docWithEntries: DocumentWithEntries,
    onClick: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit
) {
    val doc = docWithEntries.document
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                    .background(
                        when (doc.docType) {
                            DocumentType.SALES_INVOICE -> Color(0xFF1E3A8A)
                            DocumentType.CUSTOMER_LEDGER -> Color(0xFF047857)
                            DocumentType.LINED_NOTE -> Color(0xFF475569)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (doc.docType) {
                        DocumentType.SALES_INVOICE -> Icons.Default.ReceiptLong
                        DocumentType.CUSTOMER_LEDGER -> Icons.AutoMirrored.Filled.MenuBook
                        DocumentType.LINED_NOTE -> Icons.Default.EditNote
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = doc.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "#${doc.docNumber}", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    text = "${doc.dateString} | ${docWithEntries.entries.size} بنود",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            // المبلغ الإجمالي
            Column(horizontalAlignment = Alignment.End) {
                if (doc.docType == DocumentType.SALES_INVOICE) {
                    Text(
                        text = "${String.format(Locale.US, "%.0f", docWithEntries.invoiceTotal)} ريال",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF1E40AF)
                    )
                    Text(
                        text = if (doc.paymentType == PaymentType.CASH) "نقداً" else "آجل",
                        fontSize = 10.sp,
                        color = if (doc.paymentType == PaymentType.CASH) Color(0xFF16A34A) else Color(0xFFDC2626)
                    )
                } else if (doc.docType == DocumentType.CUSTOMER_LEDGER) {
                    val bal = docWithEntries.netBalance
                    Text(
                        text = "${String.format(Locale.US, "%.0f", Math.abs(bal))} ريال",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (bal >= 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                    )
                    Text(
                        text = if (bal >= 0) "عليه" else "له",
                        fontSize = 10.sp,
                        color = if (bal >= 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onPrint, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Print, contentDescription = "طباعة", tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
            }
        }
    }
}
