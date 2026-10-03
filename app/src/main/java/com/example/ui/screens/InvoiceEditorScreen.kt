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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PaymentType
import com.example.ui.components.CustomerInputLine
import com.example.ui.components.InvoiceOfficialFooter
import com.example.ui.components.InvoiceOfficialHeader
import com.example.ui.components.InvoiceTableHeader
import com.example.ui.components.InvoiceTableItemRow
import com.example.ui.components.LinedPaperSheet
import com.example.ui.components.PaperCreamWhite
import com.example.ui.viewmodel.CurrentScreen
import com.example.ui.viewmodel.DaftarViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceEditorScreen(
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

    val totalAmount = activeEntries.sumOf { it.totalAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "فاتورة بيع أصناف",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "رقم: ${activeDoc.docNumber}",
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
                    // قراءة صوتية بالذكاء الاصطناعي
                    IconButton(
                        onClick = {
                            val text = "فاتورة بيع رقم ${activeDoc.docNumber} للمشتري ${activeDoc.customerName.ifEmpty { "المحترم" }}. عدد الأصناف ${activeEntries.size}، والإجمالي الكلي ${String.format(Locale.US, "%.2f", totalAmount)} ريال."
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
                        },
                        modifier = Modifier.testTag("invoice_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "طباعة حرارية",
                            tint = Color.White
                        )
                    }

                    // حفظ الفاتورة
                    IconButton(
                        onClick = {
                            viewModel.saveActiveDocument {
                                Toast.makeText(context, "تم حفظ الفاتورة بنجاح", Toast.LENGTH_SHORT).show()
                                viewModel.navigateBack()
                            }
                        },
                        modifier = Modifier.testTag("save_invoice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "حفظ",
                            tint = Color(0xFF4ADE80)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E3A8A)
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
            // شريط خيارات سريع: نوع الفاتورة نقداً / آجل
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (activeDoc.paymentType == PaymentType.CASH) "طريقة البيع: نقداً" else "طريقة البيع: آجل",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF1E3A8A)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = activeDoc.paymentType == PaymentType.CASH,
                        onCheckedChange = { isCash ->
                            viewModel.updateDocumentHeader(
                                activeDoc.copy(paymentType = if (isCash) PaymentType.CASH else PaymentType.CREDIT)
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1E3A8A),
                            checkedTrackColor = Color(0xFF93C5FD)
                        )
                    )
                }

                // زر استدعاء المساعد المحاسبي الذكي
                OutlinedButton(
                    onClick = { viewModel.navigateTo(CurrentScreen.AiAssistant) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تدقيق ذكي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // لوح الدفتر المسطر الواقعي (كما في الصورة تماماً)
            LinedPaperSheet(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. الترويسة الرسمية للفاتورة
                    InvoiceOfficialHeader(
                        storeName = activeDoc.storeName,
                        storeAddress = activeDoc.storeAddress,
                        storePhone = activeDoc.storePhone,
                        commercialReg = activeDoc.commercialReg,
                        poBox = activeDoc.poBox,
                        fax = activeDoc.fax,
                        docNumber = activeDoc.docNumber,
                        isCash = activeDoc.paymentType == PaymentType.CASH,
                        dateString = activeDoc.dateString
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. سطر اسم العميل: المطلوب من الأخ / المحترم
                    CustomerInputLine(
                        customerName = activeDoc.customerName,
                        onCustomerNameChange = { name ->
                            viewModel.updateDocumentHeader(activeDoc.copy(customerName = name))
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. ترويسة جدول الأصناف (التفاصيل | العدد | سعر الوحدة | القيمة الإجمالية)
                    InvoiceTableHeader()

                    // 4. أسطر الأصناف المكتوبة بين الأسطر
                    activeEntries.forEachIndexed { index, entry ->
                        InvoiceTableItemRow(
                            index = index,
                            description = entry.description,
                            quantity = entry.quantity,
                            unitPrice = entry.unitPrice,
                            totalAmount = entry.totalAmount,
                            onDescriptionChange = { desc ->
                                viewModel.updateInvoiceEntry(index, desc, entry.quantity, entry.unitPrice)
                            },
                            onQuantityChange = { q ->
                                viewModel.updateInvoiceEntry(index, entry.description, q, entry.unitPrice)
                            },
                            onUnitPriceChange = { price ->
                                viewModel.updateInvoiceEntry(index, entry.description, entry.quantity, price)
                            },
                            onTotalAmountChange = { total ->
                                viewModel.updateInvoiceEntryByTotal(index, entry.description, entry.quantity, total)
                            },
                            onDelete = {
                                viewModel.removeEntry(index)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 5. تذييل الفاتورة: TOTAL الإجمالي + التحذير المطبوع + التواقيع
                    InvoiceOfficialFooter(
                        totalAmount = totalAmount,
                        sellerSignature = activeDoc.sellerSignature,
                        buyerSignature = activeDoc.buyerSignature,
                        onSellerSignatureChange = { sig ->
                            viewModel.updateDocumentHeader(activeDoc.copy(sellerSignature = sig))
                        },
                        onBuyerSignatureChange = { sig ->
                            viewModel.updateDocumentHeader(activeDoc.copy(buyerSignature = sig))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // أزرار الحفظ والطباعة في الأسفل
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.saveActiveDocument {
                            Toast.makeText(context, "تم حفظ الفاتورة بنجاح", Toast.LENGTH_SHORT).show()
                            viewModel.navigateBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ الفاتورة", fontWeight = FontWeight.Bold)
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
