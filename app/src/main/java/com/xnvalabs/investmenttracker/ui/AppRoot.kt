package com.xnvalabs.investmenttracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xnvalabs.investmenttracker.viewmodel.PortfolioViewModel

// ============================================================================
// NAVIGASI — 4 TAB
// ============================================================================

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Portfolio : Tab("portfolio", "Portfolio", Icons.Filled.AccountBalanceWallet)
    object Sentiment : Tab("sentiment", "Sentiment", Icons.Filled.Article)
    object Valuation : Tab("valuation", "Valuasi", Icons.Filled.Calculate)
    object Dictionary : Tab("dictionary", "Kamus", Icons.Filled.MenuBook)
}

val tabs = listOf(Tab.Portfolio, Tab.Sentiment, Tab.Valuation, Tab.Dictionary)

/**
 * Shell utama aplikasi: TopAppBar + NavigationBar + konten tab aktif.
 * State portfolio dipegang PortfolioViewModel (Room-backed, persisten).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var selectedTab by remember { mutableStateOf<Tab>(Tab.Portfolio) }
    val viewModel: PortfolioViewModel = viewModel()
    val assets by viewModel.assets.collectAsState()
    val usdIdrRate by viewModel.usdIdrRate.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            Tab.Portfolio -> "Portfolio & Tracker"
                            Tab.Sentiment -> "Market Sentiment"
                            Tab.Valuation -> "Valuation Calculator"
                            Tab.Dictionary -> "Kamus Investasi & Trading"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab.route == tab.route,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                Tab.Portfolio -> PortfolioScreen(
                    assets = assets,
                    usdIdrRate = usdIdrRate,
                    isRefreshing = isRefreshing,
                    errorMessage = errorMessage,
                    onDismissError = viewModel::clearError,
                    onAddAsset = viewModel::addAsset,
                    onDeleteAsset = viewModel::deleteAsset,
                    onSimulate = viewModel::simulateRandomPriceChange,
                    onRefreshCrypto = { viewModel.refreshLivePrices() }
                )
                Tab.Sentiment -> SentimentScreen()
                Tab.Valuation -> ValuationScreen()
                Tab.Dictionary -> DictionaryScreen()
            }
        }
    }
}
