package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.CadenceViewModel
import com.example.ui.components.NavigationTab
import com.example.ui.components.PacePulseBottomNav
import com.example.ui.screens.CadenceGuideScreen
import com.example.ui.screens.MainPulseScreen
import com.example.ui.screens.StatsHistoryScreen
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PacePulseTheme

import com.example.ui.theme.ThemeManager
import com.example.ui.language.LanguageManager
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    private val cadenceViewModel: CadenceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ThemeManager.init(this)
        LanguageManager.init(this)

        enableEdgeToEdge()
        setContent {
            PacePulseTheme {
                PacePulseApp(viewModel = cadenceViewModel)
            }
        }
    }
}

@Composable
fun PacePulseApp(viewModel: CadenceViewModel) {
    var selectedTab by remember { mutableStateOf(NavigationTab.PULSE) }

    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        viewModel.trackScreenView(selectedTab.name.lowercase() + "_screen")
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PacePulseBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            NavigationTab.PULSE -> MainPulseScreen(viewModel = viewModel, modifier = modifier)
            NavigationTab.STATS -> StatsHistoryScreen(viewModel = viewModel, modifier = modifier)
            NavigationTab.GUIDE -> CadenceGuideScreen(modifier = modifier)
        }
    }
}

