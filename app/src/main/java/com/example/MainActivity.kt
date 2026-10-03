package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.CustomerLedgerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InvoiceEditorScreen
import com.example.ui.screens.LinedNoteScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.ThermalPrintScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CurrentScreen
import com.example.ui.viewmodel.DaftarViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // فرض الاتجاه من اليمين إلى اليسار (RTL) للتطبيق بالكامل
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val viewModel: DaftarViewModel = viewModel()
                        val currentScreen by viewModel.currentScreen.collectAsState()

                        when (val screen = currentScreen) {
                            is CurrentScreen.Home -> {
                                HomeScreen(viewModel = viewModel)
                            }
                            is CurrentScreen.InvoiceEditor -> {
                                InvoiceEditorScreen(viewModel = viewModel, docId = screen.docId)
                            }
                            is CurrentScreen.CustomerLedger -> {
                                CustomerLedgerScreen(viewModel = viewModel, docId = screen.docId)
                            }
                            is CurrentScreen.LinedNote -> {
                                LinedNoteScreen(viewModel = viewModel, docId = screen.docId)
                            }
                            is CurrentScreen.ThermalPrint -> {
                                ThermalPrintScreen(viewModel = viewModel, docId = screen.docId)
                            }
                            is CurrentScreen.CustomerProfile -> {
                                com.example.ui.screens.CustomerProfileScreen(viewModel = viewModel, customerName = screen.customerName)
                            }
                            is CurrentScreen.AiAssistant -> {
                                AiAssistantScreen(viewModel = viewModel)
                            }
                            is CurrentScreen.Reports -> {
                                ReportsScreen(viewModel = viewModel, onBack = { viewModel.navigateBack() })
                            }
                            is CurrentScreen.Settings -> {
                                com.example.ui.screens.SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
