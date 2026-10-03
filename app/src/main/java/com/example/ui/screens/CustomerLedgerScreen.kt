package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CustomerInputLine
import com.example.ui.components.LedgerBorderColor
import com.example.ui.components.LedgerTableHeader
import com.example.ui.components.LedgerTableItemRow
import com.example.ui.components.LinedPaperSheet
import com.example.ui.components.PaperCreamWhite
import com.example.ui.components.StampRed
import com.example.ui.viewmodel.CurrentScreen
import com.example.ui.viewmodel.DaftarViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLedgerScreen(
    viewModel: DaftarViewModel,
    docId: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeDoc by viewModel.activeDocument.collectAsState()
    val activeEntries by viewModel.activeEntries.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    BackHandler {
        viewModel.navigateBack()
    }

    val totalDebit = activeEntries.sumOf { it.debit }
    val totalCredit = activeEntries.sumOf { it.credit }
    val netBalance = totalDebit - totalCredit

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "دفتر حسابات العميل",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "رقم الحساب: ${activeDoc.docNumber}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
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
                    // قراءة صوتية لملخص الحساب
                    IconButton(
                        onClick = {
                            val status = if (netBalance >= 0) "عليه رصيد مدين بقيمة" else "له رصيد دائن بقيمة"
                            val text = "كشف حساب ${activeDoc.customerName.ifEmpty { "العميل" }}. إجمالي عليه مدين ${String.format(Locale.US, "%.2f", totalDebit)} ريال، وإجمالي له دائن ${String.format(Locale.US, "%.2f", totalCredit)} ريال، والصافي $status ${String.format(Locale.US, "%.2f", Math.abs(netBalance))} ريال."
                            viewModel.speakDocument(text)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "قراءة صوتية",
                            tint = if (isSpeaking) Color(0xFFFDE047) else Color.White
                        )
                    }

                    // طباعة حرارية
                    IconButton(
                        onClick = {
                            viewModel.saveActiveDocument { savedId ->
                                viewModel.navigateTo(CurrentScreen.ThermalPrint(savedId))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "طباعة حرارية",
                            tint = Color.White
                        )
                    }

                    // حفظ
                    IconButton(
                        onClick = {
                            viewModel.saveActiveDocument {
                                Toast.makeText(context, "تم حفظ كشف الحساب بنجاح", Toast.LENGTH_SHORT).show()
                                viewModel.navigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "حفظ",
                            tint = Color(0xFF4ADE80)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF16A34A)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F5F9))
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            // شريط إحصائي سريع لأرصدة الحساب
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text("إجمالي عليه (مدين)", fontSize = 10.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        Text(String.format(Locale.US, "%.1f ريال", totalDebit), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text("إجمالي له (دائن)", fontSize = 10.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                        Text(String.format(Locale.US, "%.1f ريال", totalCredit), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                }

                Card(
                    modifier = Modifier.weight(1.2f),
                    colors = CardDefaults.cardColors(containerColor = if (netBalance >= 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(if (netBalance >= 0) "صافي الرصيد (عليه)" else "صافي الرصيد (له)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = String.format(Locale.US, "%.1f ريال", Math.abs(netBalance)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (netBalance >= 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                        )
                    }
                }
            }

            // لوح دفتر الحسابات المسطر (كتابة بين الأسطر)
            LinedPaperSheet(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // ترويسة دفتر الحسابات
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, LedgerBorderColor, RoundedCornerShape(6.dp))
                            .background(PaperCreamWhite)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(text = activeDoc.storeName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "دفتر حسابات العملاء والديون", fontSize = 10.sp, color = Color.DarkGray)
                            Text(text = "التاريخ: ${activeDoc.dateString}", fontSize = 10.sp, color = Color.Gray)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.5.dp, LedgerBorderColor, RoundedCornerShape(16.dp))
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("كشف حساب", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(text = "رقم القيد", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = activeDoc.docNumber,
                                color = StampRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // سطر العميل: المطلوب من الأخ / المحترم
                    CustomerInputLine(
                        customerName = activeDoc.customerName,
                        onCustomerNameChange = { name ->
                            viewModel.updateDocumentHeader(activeDoc.copy(customerName = name))
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // ترويسة جدول العمليات (الرصيد التراكمي | له | عليه | التاريخ واليوم | التفاصيل)
                    LedgerTableHeader()

                    // أسطر العمليات المكتوبة بين الأسطر
                    activeEntries.forEachIndexed { index, entry ->
                        LedgerTableItemRow(
                            index = index,
                            runningBalance = entry.runningBalance,
                            credit = entry.credit,
                            debit = entry.debit,
                            dateStr = entry.entryDate,
                            dayStr = entry.entryDay,
                            description = entry.description,
                            onDescriptionChange = { desc ->
                                viewModel.updateLedgerEntry(index, desc, entry.debit, entry.credit, entry.entryDate, entry.entryDay)
                            },
                            onDebitChange = { d ->
                                viewModel.updateLedgerEntry(index, entry.description, d, entry.credit, entry.entryDate, entry.entryDay)
                            },
                            onCreditChange = { c ->
                                viewModel.updateLedgerEntry(index, entry.description, entry.debit, c, entry.entryDate, entry.entryDay)
                            },
                            onDateChange = { dt ->
                                viewModel.updateLedgerEntry(index, entry.description, entry.debit, entry.credit, dt, entry.entryDay)
                            },
                            onDelete = {
                                viewModel.removeEntry(index)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // زر إضافة قيد / عملية جديدة
                    Button(
                        onClick = { viewModel.addLedgerEntry() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_ledger_row_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة عملية جديدة (قيد)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // التجميع السفلي للعمليات
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LedgerBorderColor, RoundedCornerShape(4.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "إجمالي له (دائن): ${String.format(Locale.US, "%.2f", totalCredit)} ريال",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                            Text(
                                text = "إجمالي عليه (مدين): ${String.format(Locale.US, "%.2f", totalDebit)} ريال",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val netLabel = if (netBalance >= 0) "رصيد عليه (مدين للمحل)" else "رصيد له (دائن على المحل)"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                                .background(Color.White)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "الرصيد الصافي المتبقي:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${String.format(Locale.US, "%.2f", Math.abs(netBalance))} ريال ($netLabel)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = if (netBalance >= 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // أزرار الحفظ والطباعة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.saveActiveDocument {
                            Toast.makeText(context, "تم حفظ كشف الحساب بنجاح", Toast.LENGTH_SHORT).show()
                            viewModel.navigateBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ الكشف", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        viewModel.saveActiveDocument { savedId ->
                            viewModel.navigateTo(CurrentScreen.ThermalPrint(savedId))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("طباعة حرارية", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
