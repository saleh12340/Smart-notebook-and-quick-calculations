package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import com.example.ui.viewmodel.CurrentScreen
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ChatMessage
import com.example.ui.viewmodel.DaftarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: DaftarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Chat, 1: Image Gen

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFDE047))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "المساعد الذكي (Gemini AI)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "تدقيق محاسبي وتوليد أختام وقراءة صوتية",
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
                    IconButton(onClick = { viewModel.navigateTo(CurrentScreen.Settings) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات ومفتاح API",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
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
        ) {
            // علامات التبويب (المحادثة المحاسبية / توليد الأختام)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFF1E3A8A)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("المساعد المحاسبي", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("توليد الأختام والشعارات", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Image, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> ChatTabContent(viewModel = viewModel)
                1 -> ImageGenTabContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun ChatTabContent(
    viewModel: DaftarViewModel
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isAiLoading.collectAsState()
    val selectedModel by viewModel.selectedAiModel.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spokenText else "$inputText $spokenText"
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // شريط اختيار النموذج المطلوب مع التمرير الأفقي السلس
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F5F9))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("النموذج:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Spacer(modifier = Modifier.width(6.dp))

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedModel == "gemini-3.5-flash",
                        onClick = { viewModel.setSelectedAiModel("gemini-3.5-flash") },
                        label = { Text("3.5 Flash (شامل)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E3A8A),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedModel == "gemini-3.1-pro-preview",
                        onClick = { viewModel.setSelectedAiModel("gemini-3.1-pro-preview") },
                        label = { Text("3.1 Pro (تحليل متقدم)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E3A8A),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedModel == "gemini-3.1-flash-lite",
                        onClick = { viewModel.setSelectedAiModel("gemini-3.1-flash-lite") },
                        label = { Text("3.1 Flash-Lite (سريع)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E3A8A),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // أزرار الاقتراحات السريعة
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                SuggestionChipButton(text = "صياغة رسالة مطالبة ودية لعميل") {
                    inputText = "اكتب لي رسالة واتساب مهنية وودية لتذكير عميل بسداد رصيد دينه المتأخر."
                }
            }
            item {
                SuggestionChipButton(text = "طريقة قيد المبيعات الآجلة") {
                    inputText = "ما هو القيد المحاسبي الصحيح عند بيع بضاعة بالأجل للعميل؟"
                }
            }
            item {
                SuggestionChipButton(text = "نصائح تنظيم دفتر الحسابات") {
                    inputText = "أعطني 3 نصائح ذهبية لتاجر مبتدئ لضبط دفتر الحسابات وعدم ضياع الديون."
                }
            }
        }

        // قائمة رسائل المحادثة (Scrollable Thread)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    onSpeak = { viewModel.speakDocument(msg.content) }
                )
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("النموذج يفكر ويحلل البيانات...", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        // حقل إدخال الرسالة
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("اسأل المساعد المحاسبي أي سؤال...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1E3A8A),
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            // زر الإدخال الصوتي
            IconButton(
                onClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن للمساعد المحاسبي...")
                        }
                        speechLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "التعرف الصوتي غير متوفر في جهازك", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F9))
                    .testTag("chat_voice_input_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "تسجيل صوتي",
                    tint = Color(0xFF1E3A8A),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // زر الإرسال
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isLoading) {
                        val textToSend = inputText.trim()
                        inputText = ""
                        viewModel.sendChatMessage(textToSend)
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E3A8A))
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "إرسال",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun SuggestionChipButton(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFE2E8F0),
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color(0xFF1E293B),
            modifier = Modifier
                .border(0.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .clickable { onClick() }
        )
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF1E3A8A) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = message.content,
                    color = if (isUser) Color.White else Color(0xFF0F172A),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "قراءة بالصوت (TTS)",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Image Generation Tab:
 * Using model gemini-3-pro-image-preview
 * With explicit affordance to choose resolution: 1K, 2K, 4K
 */
@Composable
fun ImageGenTabContent(
    viewModel: DaftarViewModel
) {
    val context = LocalContext.current
    val generatedImage by viewModel.generatedImage.collectAsState()
    val isGenerating by viewModel.isGeneratingImage.collectAsState()
    val selectedResolution by viewModel.selectedImageResolution.collectAsState()

    var promptText by remember {
        mutableStateOf("ختم تجاري رسمي دائري باللون الأزرق الداكن يحتوي على عبارة معتمد ومدقق ورمز الميزان التجاري")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "توليد ختم أو شعار للفاتورة (gemini-3-pro-image-preview)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // اختيار دقة الصورة (1K, 2K, 4K) كما طلب النظام
                Text("اختر دقة وضوح الصورة:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1K", "2K", "4K").forEach { res ->
                        FilterChip(
                            selected = selectedResolution == res,
                            onClick = { viewModel.setSelectedImageResolution(res) },
                            label = { Text(res, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E3A8A),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("وصف الختم أو الشعار") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input"),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.generateStampOrLogo(promptText)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_image_button"),
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري توليد الصورة بدقة $selectedResolution...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("توليد الختم بالذكاء الاصطناعي ($selectedResolution)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // عرض الصورة المولدة
        if (generatedImage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("معاينة الختم المولد:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Image(
                        bitmap = generatedImage!!.asImageBitmap(),
                        contentDescription = "الختم المولد",
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "تم التوليد بنجاح بدقة $selectedResolution عبر نموذج gemini-3-pro-image-preview",
                        fontSize = 11.sp,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
