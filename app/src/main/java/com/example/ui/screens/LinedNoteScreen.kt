package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LedgerBorderColor
import com.example.ui.components.LinedPaperSheet
import com.example.ui.components.PaperCreamWhite
import com.example.ui.components.PaperLineColor
import com.example.ui.components.StampRed
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

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ملاحظة دفترية مسطرة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = activeDoc.dateString,
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

                    IconButton(
                        onClick = {
                            viewModel.saveActiveDocument {
                                Toast.makeText(context, "تم حفظ الملاحظة بنجاح", Toast.LENGTH_SHORT).show()
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
                    containerColor = Color(0xFFD97706)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            // دفتر الملاحظات المسطر
            LinedPaperSheet(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // ترويسة الورقة
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LedgerBorderColor, RoundedCornerShape(6.dp))
                            .background(PaperCreamWhite)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = activeDoc.title,
                                onValueChange = { t -> viewModel.updateDocumentHeader(activeDoc.copy(title = t)) },
                                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                                decorationBox = { inner ->
                                    if (activeDoc.title.isEmpty()) Text("عنوان الملاحظة...", color = Color.Gray, fontSize = 14.sp)
                                    inner()
                                }
                            )
                        }

                        Text(
                            text = "№ ${activeDoc.docNumber}",
                            color = StampRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // كتابة الملاحظات بين الأسطر على ورق أبيض مسطر
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .drawBehind {
                                val lineSpacing = 34.dp.toPx()
                                val totalLines = (size.height / lineSpacing).toInt()
                                for (i in 1..totalLines) {
                                    val y = i * lineSpacing
                                    drawLine(
                                        color = PaperLineColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                    )
                                }
                            }
                            .background(Color.White)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        BasicTextField(
                            value = activeDoc.notes,
                            onValueChange = { txt -> viewModel.updateDocumentNoteText(txt) },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = Color.Black,
                                lineHeight = 34.sp
                            ),
                            decorationBox = { inner ->
                                if (activeDoc.notes.isEmpty()) {
                                    Text(
                                        text = "اكتب هنا بين السطور في المساحة البيضاء بوضوح...",
                                        color = Color.LightGray,
                                        fontSize = 14.sp,
                                        lineHeight = 34.sp
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.saveActiveDocument {
                        Toast.makeText(context, "تم حفظ الملاحظة بنجاح", Toast.LENGTH_SHORT).show()
                        viewModel.navigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("حفظ الملاحظة المسطرة", fontWeight = FontWeight.Bold)
            }
        }
    }
}
