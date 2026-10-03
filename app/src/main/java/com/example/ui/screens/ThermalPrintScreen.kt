package com.example.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentType
import com.example.data.model.DocumentWithEntries
import com.example.print.ThermalPrintHelper
import com.example.ui.viewmodel.DaftarViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalPrintScreen(
    viewModel: DaftarViewModel,
    docId: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val activeDoc by viewModel.activeDocument.collectAsState()
    val activeEntries by viewModel.activeEntries.collectAsState()

    var showBluetoothDialog by remember { mutableStateOf(false) }
    var bluetoothDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isPrintingBluetooth by remember { mutableStateOf(false) }
    var is80mm by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.navigateBack()
    }

    val docWithEntries = DocumentWithEntries(activeDoc, activeEntries)
    val thermalText = ThermalPrintHelper.generateThermalTextReceipt(docWithEntries)

    // لون الترويسة حسب نوع الوثيقة (أزرق للفاتورة، أخضر للحساب، عسلي للملاحظة)
    val headerThemeColor = when (activeDoc.docType) {
        DocumentType.SALES_INVOICE -> Color(0xFF1E40AF)
        DocumentType.CUSTOMER_LEDGER -> Color(0xFF047857)
        DocumentType.LINED_NOTE -> Color(0xFFB45309)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "معاينة وطباعة حرارية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "بلوتوث وطابعات النظام (${if (is80mm) "80mm" else "58mm"})",
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
                    IconButton(onClick = {
                        ThermalPrintHelper.shareReceiptText(context, docWithEntries)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerThemeColor
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
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // شريط اختيار مقاس الورق (58mm أو 80mm)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "عرض الإيصال الحراري:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !is80mm,
                        onClick = { is80mm = false },
                        label = { Text("58mm (شائع)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = headerThemeColor,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = is80mm,
                        onClick = { is80mm = true },
                        label = { Text("80mm (عريض)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = headerThemeColor,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // أزرار الطباعة الرئيسية: بلوتوث + طباعة النظام + نسخ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // زر طباعة البلوتوث المباشرة للطابعات الحرارية (ESC/POS)
                Button(
                    onClick = {
                        val devices = ThermalPrintHelper.getPairedBluetoothPrinters(context)
                        bluetoothDevices = devices
                        showBluetoothDialog = true
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("bluetooth_print_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isPrintingBluetooth) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جاري الطباعة...", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("طباعة بلوتوث", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                // زر طباعة النظام PrintManager (يدعم أي طابعة أو تصدير PDF)
                Button(
                    onClick = {
                        ThermalPrintHelper.printDocument(context, docWithEntries)
                    },
                    modifier = Modifier.weight(1.1f),
                    colors = ButtonDefaults.buttonColors(containerColor = headerThemeColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("طباعة نظام", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                // زر نسخ النص
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(thermalText))
                        Toast.makeText(context, "تم نسخ نص الإيصال", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("نسخ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ورقة الإيصال الحراري محاكية للواقع بدقة
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (is80mm) 1f else 0.88f)
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Text(
                        text = thermalText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.Black,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    ThermalPrintHelper.shareReceiptText(context, docWithEntries)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("مشاركة الفاتورة عبر واتساب والتطبيقات", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }

    // نافذة اختيار طابعة البلوتوث
    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اختر طابعة البلوتوث", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                if (bluetoothDevices.isEmpty()) {
                    Column {
                        Text(
                            text = "لم يتم العثور على طابعات بلوتوث مقترنة.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "يرجى التوجه إلى إعدادات الهاتف > البلوتوث > واقتران طابعتك الحرارية (مثل POS-58 أو MTP-II أو Bluetooth Printer) أولاً، ثم العودة واختيارها.",
                            fontSize = 11.sp,
                            color = Color.DarkGray,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(bluetoothDevices) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showBluetoothDialog = false
                                        isPrintingBluetooth = true
                                        scope.launch {
                                            val result = ThermalPrintHelper.printReceiptViaBluetooth(
                                                device = device,
                                                docWithEntries = docWithEntries,
                                                is80mm = is80mm
                                            )
                                            isPrintingBluetooth = false
                                            result.onSuccess { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "خطأ في الطباعة", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF0284C7))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        @SuppressLint("MissingPermission")
                                        val devName = try { device.name } catch (e: Exception) { null } ?: "طابعة حرارية"
                                        Text(devName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(device.address, fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBluetoothDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}
