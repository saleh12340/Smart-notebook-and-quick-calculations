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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CustomerInputLine
import com.example.ui.components.LedgerBorderColor
import com.example.ui.components.LinedPaperSheet
import com.example.ui.components.PaperCreamWhite
import com.example.ui.components.PaperHeaderBackground
import com.example.ui.components.PaperLineColor
import com.example.ui.components.PaperRedMargin
import com.example.ui.components.StampRed
import com.example.data.storage.AppStorageHelper
import com.example.ui.viewmodel.CurrentScreen
import com.example.ui.viewmodel.DaftarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinedNoteScreen(
    viewModel: DaftarViewModel,
    docId: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeDoc by viewModel.activeDocument.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val copied = AppStorageHelper.copyUriToDownloads(
                context = context,
                sourceUri = uri,
                fileName = "note_image_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg"
            )
            if (copied != null) {
                viewModel.updateDocumentHeader(activeDoc.copy(stampImageUrl = copied.toString()))
                Toast.makeText(context, "تم استيراد الصورة وحفظ نسخة منها داخل مجلد التطبيق في Downloads", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "تعذر استيراد الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                                .size(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFB45309)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ملاحظة دفترية مسطرة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${activeDoc.dateString} (${activeDoc.dayString})",
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
                    // استيراد صورة
                    IconButton(
                        onClick = { imagePicker.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "استيراد صورة",
                            tint = Color.White
                        )
                    }

                    // قراءة صوتية
                    IconButton(
                        onClick = {
                            val text = "${activeDoc.title.ifEmpty { "ملاحظة" }}. ${activeDoc.notes}"
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
                                Toast.makeText(context, "تم حفظ الملاحظة الدفترية بنجاح", Toast.LENGTH_SHORT).show()
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
                    containerColor = Color(0xFFB45309)
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
            // دفتر الملاحظات الواقعي المسطر طبق الأصل
            LinedPaperSheet(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. الترويسة الدفترية الرسمية للملاحظة
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, LedgerBorderColor, RoundedCornerShape(6.dp))
                            .background(PaperCreamWhite)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // القسم الأيمن: بيانات المحل والتاريخ
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(
                                    text = "محل: ${activeDoc.storeName.ifEmpty { "بقالة العزي للتجارة" }}",
                                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                                )
                                Text(
                                    text = "هاتف: ${activeDoc.storePhone.ifEmpty { "776425052" }}",
                                    style = TextStyle(fontSize = 10.sp, color = Color.DarkGray)
                                )
                                Text(
                                    text = "التاريخ: ${activeDoc.dateString}",
                                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                )
                            }

                            // القسم الأوسط: شارة [ مذكرة / ملاحظة دفترية ]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.5.dp, LedgerBorderColor, RoundedCornerShape(16.dp))
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ملاحظة دفترية",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFB45309)
                                )
                            }

                            // القسم الأيسر: رقم السند بالأحمر
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(text = "رقم الملاحظة", fontSize = 10.sp, color = Color.Gray)
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

                        // سطر عنوان الملاحظة الرئيسي
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, LedgerBorderColor, RoundedCornerShape(4.dp))
                                .background(PaperHeaderBackground)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "الموضوع / العنوان:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            BasicTextField(
                                value = activeDoc.title,
                                onValueChange = { t -> viewModel.updateDocumentHeader(activeDoc.copy(title = t)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("note_title_input"),
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                ),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (activeDoc.title.isEmpty()) {
                                        Text("اكتب موضوع أو عنوان الملاحظة هنا...", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    inner()
                                }
                            )
                        }
                    }

                    if (!activeDoc.stampImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = activeDoc.stampImageUrl,
                            contentDescription = "الصورة المستوردة",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .border(1.dp, LedgerBorderColor, RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // 2. سطر اسم العميل أو الشخص المعني بالملاحظة (المطلوب من الأخ / المحترم)
                    CustomerInputLine(
                        customerName = activeDoc.customerName,
                        onCustomerNameChange = { name ->
                            viewModel.updateDocumentHeader(activeDoc.copy(customerName = name))
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. ورقة الدفتر المسطرة طبق الأصل (Ruled Lined Page) مع الهامش العمودي الأيمن
                    val lineSpacingDp = 34.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                            .border(1.dp, LedgerBorderColor, RoundedCornerShape(4.dp))
                            .drawBehind {
                                val lineSpacingPx = lineSpacingDp.toPx()
                                val totalLines = (size.height / lineSpacingPx).toInt()

                                // رسم الخطوط الأفقية الزرقاء/الرمادية المنقطة المتوازية (خطوط الدفتر)
                                for (i in 1..totalLines) {
                                    val lineY = i * lineSpacingPx
                                    drawLine(
                                        color = PaperLineColor,
                                        start = Offset(0f, lineY),
                                        end = Offset(size.width, lineY),
                                        strokeWidth = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                    )
                                }

                                // رسم خط الهامش العمودي الأحمر الأيمن المميز للدفاتر المسطرة
                                val rightMarginX = size.width - 32.dp.toPx()
                                drawLine(
                                    color = PaperRedMargin.copy(alpha = 0.6f),
                                    start = Offset(rightMarginX, 0f),
                                    end = Offset(rightMarginX, size.height),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                            .background(Color.White)
                            .padding(top = 4.dp, bottom = 4.dp, start = 8.dp, end = 38.dp)
                    ) {
                        BasicTextField(
                            value = activeDoc.notes,
                            onValueChange = { txt -> viewModel.updateDocumentNoteText(txt) },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("note_text_input"),
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = Color.Black,
                                lineHeight = 34.sp,
                                textAlign = TextAlign.Start
                            ),
                            decorationBox = { inner ->
                                if (activeDoc.notes.isEmpty()) {
                                    Text(
                                        text = "اكتب هنا بين السطور في ورقة الدفتر المسطرة...\nاكتب أي ملاحظة أو حسابات أو تنبيهات تجارية...",
                                        color = Color.LightGray,
                                        fontSize = 13.sp,
                                        lineHeight = 34.sp
                                    )
                                }
                                inner()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. تذييل الورقة الدفترية مع التواقيع والختم المعتمد
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LedgerBorderColor, RoundedCornerShape(4.dp))
                            .background(PaperCreamWhite)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "توقيع المحرر:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                BasicTextField(
                                    value = activeDoc.sellerSignature,
                                    onValueChange = { sig -> viewModel.updateDocumentHeader(activeDoc.copy(sellerSignature = sig)) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF)),
                                    singleLine = true,
                                    decorationBox = { inner ->
                                        if (activeDoc.sellerSignature.isEmpty()) Text("المحاسب", color = Color.LightGray, fontSize = 10.sp)
                                        inner()
                                    }
                                )
                            }
                        }

                        // الختم الرسمي الأحمر
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StampRed.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StampRed)
                        ) {
                            Text(
                                text = "ختم وسجل بقالة العزي",
                                color = StampRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
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
                            Toast.makeText(context, "تم حفظ الملاحظة الدفترية بنجاح", Toast.LENGTH_SHORT).show()
                            viewModel.navigateBack()
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ الملاحظة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.saveActiveDocument { savedId ->
                            viewModel.navigateTo(CurrentScreen.ThermalPrint(savedId))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("طباعة حرارية", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
