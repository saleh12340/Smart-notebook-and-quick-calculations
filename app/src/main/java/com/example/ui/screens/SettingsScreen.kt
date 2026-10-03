package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.backup.BackupHelper
import com.example.ui.viewmodel.DaftarViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DaftarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    // Store settings state
    var storeName by remember { mutableStateOf(prefs.getString("store_name", "بقالة العزي") ?: "بقالة العزي") }
    var storePhone by remember { mutableStateOf(prefs.getString("store_phone", "776425052") ?: "776425052") }
    var storeAddress by remember { mutableStateOf(prefs.getString("store_address", "السوق العام") ?: "السوق العام") }
    var commercialReg by remember { mutableStateOf(prefs.getString("commercial_reg", "101000") ?: "101000") }
    var sellerName by remember { mutableStateOf(prefs.getString("seller_name", "المحاسب") ?: "المحاسب") }

    // Gemini API settings state
    var geminiApiKey by remember { mutableStateOf(viewModel.geminiService.getApiKey()) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isTestingAi by remember { mutableStateOf(false) }

    // Backup state
    var isAutoBackupEnabled by remember { mutableStateOf(BackupHelper.isAutoBackupEnabled(context)) }
    var lastBackupTime by remember { mutableStateOf(BackupHelper.getLastBackupTimeString(context)) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonText by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }

    // Thermal Printer state
    var is80mmDefault by remember { mutableStateOf(prefs.getBoolean("printer_80mm_default", false)) }

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإعدادات والنسخ الاحتياطي",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // 1. بطاقة بيانات المتجر الافتراضية
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFF1E40AF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("بيانات المتجر والفواتير", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("اسم المحل (افتراضي: بقالة العزي)") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_store_name"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF1E40AF))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = storePhone,
                        onValueChange = { storePhone = it },
                        label = { Text("رقم الهاتف (افتراضي: 776425052)") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_store_phone"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF1E40AF))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = storeAddress,
                        onValueChange = { storeAddress = it },
                        label = { Text("العنوان / الشارع") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = commercialReg,
                            onValueChange = { commercialReg = it },
                            label = { Text("السجل التجاري") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sellerName,
                            onValueChange = { sellerName = it },
                            label = { Text("اسم البائع / المحاسب") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            prefs.edit().apply {
                                putString("store_name", storeName.trim())
                                putString("store_phone", storePhone.trim())
                                putString("store_address", storeAddress.trim())
                                putString("commercial_reg", commercialReg.trim())
                                putString("seller_name", sellerName.trim())
                                apply()
                            }
                            viewModel.updateDefaultStoreProfile(storeName.trim(), storePhone.trim(), storeAddress.trim())
                            Toast.makeText(context, "تم حفظ بيانات المتجر بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ بيانات المتجر", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. بطاقة النسخ الاحتياطي التلقائي واليدوي والاسترجاع
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = Color(0xFF047857))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("النسخ الاحتياطي والاسترجاع", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // مفتاح النسخ الاحتياطي التلقائي
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("النسخ الاحتياطي التلقائي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("حفظ نسخة أوتوماتيكية دورية عند حفظ السجلات", fontSize = 11.sp, color = Color.Gray)
                            Text("آخر نسخة: $lastBackupTime", fontSize = 10.sp, color = Color(0xFF047857), fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = isAutoBackupEnabled,
                            onCheckedChange = { checked ->
                                isAutoBackupEnabled = checked
                                BackupHelper.setAutoBackupEnabled(context, checked)
                                if (checked) {
                                    scope.launch {
                                        viewModel.triggerAutoBackup()
                                        lastBackupTime = BackupHelper.getLastBackupTimeString(context)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF047857))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // زر النسخ الاحتياطي اليدوي والمشاركة
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.exportManualBackup(context)
                                lastBackupTime = BackupHelper.getLastBackupTimeString(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ احتياطي يدوي ومشاركة (تصدير ملف)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // استرجاع نسخة يدوية
                        OutlinedButton(
                            onClick = { showRestoreDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استرجاع نسخة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // استرجاع النسخة التلقائية الأخيرة
                        OutlinedButton(
                            onClick = {
                                isRestoring = true
                                scope.launch {
                                    viewModel.restoreLatestAutoBackup(context) { res ->
                                        isRestoring = false
                                        res.onSuccess { count ->
                                            Toast.makeText(context, "تم استرجاع $count سجل بنجاح!", Toast.LENGTH_LONG).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, err.message ?: "فشل الاسترجاع", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("استعادة التلقائية", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. بطاقة إعدادات الذكاء الاصطناعي (Gemini AI)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFD97706))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مفتاح الذكاء الاصطناعي (Gemini API Key)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "التطبيق يحتوي على محرك محاسبي ذكي محلي يعمل دائماً. يمكنك إدخال مفتاح Gemini الخاص بك لتفعيل التحليل السحابي المتطور وتوليد الصور بالذكاء الاصطناعي.",
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        label = { Text("أدخل مفتاح GEMINI_API_KEY") },
                        modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.geminiService.saveCustomApiKey(geminiApiKey.trim())
                                Toast.makeText(context, "تم حفظ وتثبيت مفتاح الذكاء الاصطناعي بنجاح", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حفظ وتثبيت المفتاح", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                isTestingAi = true
                                scope.launch {
                                    val testResult = viewModel.geminiService.testApiKey(geminiApiKey.trim())
                                    isTestingAi = false
                                    testResult.onSuccess { msg ->
                                        viewModel.geminiService.saveCustomApiKey(geminiApiKey.trim())
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, "فحص المفتاح: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isTestingAi) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فحص واختبار الاتصال", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 4. بطاقة إعدادات الطباعة
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF0284C7))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إعدادات الطابعة الحرارية", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("مقاس الورق الافتراضي:", fontSize = 12.sp, color = Color.Gray)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !is80mmDefault,
                            onClick = {
                                is80mmDefault = false
                                prefs.edit().putBoolean("printer_80mm_default", false).apply()
                            },
                            label = { Text("58mm (الأكثر شيوعاً)") }
                        )
                        FilterChip(
                            selected = is80mmDefault,
                            onClick = {
                                is80mmDefault = true
                                prefs.edit().putBoolean("printer_80mm_default", true).apply()
                            },
                            label = { Text("80mm (مقاس عريض)") }
                        )
                    }
                }
            }
        }
    }

    // نافذة استرجاع النسخة الاحتياطية
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("استرجاع قاعدة البيانات", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "الصق نص النسخة الاحتياطية (JSON) المستخرج سابقاً لاسترجاع كافة الفواتير والقيود:",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreJsonText,
                        onValueChange = { restoreJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("{ \"app\": \"دفتر الفواتير...\" }", fontSize = 11.sp) },
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreJsonText.isNotBlank()) {
                            isRestoring = true
                            scope.launch {
                                viewModel.restoreBackupFromJson(context, restoreJsonText) { res ->
                                    isRestoring = false
                                    res.onSuccess { count ->
                                        showRestoreDialog = false
                                        Toast.makeText(context, "تم بنجاح استرجاع $count سجل!", Toast.LENGTH_LONG).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "خطأ في ملف النسخة", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                ) {
                    Text("استرجاع الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
