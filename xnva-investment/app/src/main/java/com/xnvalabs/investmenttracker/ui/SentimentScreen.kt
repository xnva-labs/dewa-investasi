package com.xnvalabs.investmenttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xnvalabs.investmenttracker.data.mockNews
import com.xnvalabs.investmenttracker.data.remote.NewsAnalysisResult
import com.xnvalabs.investmenttracker.model.NewsItem
import com.xnvalabs.investmenttracker.model.Sentiment
import com.xnvalabs.investmenttracker.viewmodel.NewsAnalysisViewModel

// ============================================================================
// TAB 2 — MARKET SENTIMENT
// Bagian atas: "Tanya AI" — cari berita real-time (Google News RSS, ID +
// global sekaligus) soal 1 aset, lalu Gemini merumuskan faktor pendukung
// naik/turun HANYA dari berita itu (grounded, bukan prediksi pasti harga).
// Bagian bawah: feed sentiment contoh (mock, ala X/Twitter) — tetap ada
// sebagai demo, tidak diubah.
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentimentScreen(portfolioTickers: List<String> = emptyList()) {
    val news = remember { mockNews() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { AiNewsAnalysisSection(portfolioTickers = portfolioTickers) }
        item {
            Text(
                "Feed Sentiment (Contoh)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        items(news) { item -> NewsCard(item) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiNewsAnalysisSection(portfolioTickers: List<String>) {
    val viewModel: NewsAnalysisViewModel = viewModel()
    val isLoading by viewModel.isLoading.collectAsState()
    val result by viewModel.result.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var query by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tanya AI soal Saham/Crypto", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Cari berita terbaru (3 hari terakhir, semua sumber ID + global) lalu dirumuskan AI jadi faktor pendukung naik/turun. Bukan prediksi harga pasti.",
                fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp
            )
            Spacer(Modifier.height(10.dp))

            if (!viewModel.isConfigured) {
                Text(
                    "GEMINI_API_KEY belum diset di build ini — fitur AI belum aktif. Lihat PANDUAN-GITHUB-BUILD.md.",
                    fontSize = 11.sp, color = Color(0xFFF59E0B)
                )
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it.uppercase() },
                label = { Text("Nama saham/ticker (mis. BBCA, NVDA, BTC)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (portfolioTickers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    portfolioTickers.distinct().forEach { ticker ->
                        AssistChip(onClick = { query = ticker }, label = { Text(ticker, fontSize = 11.sp) })
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { viewModel.analyze(query) },
                enabled = !isLoading && query.isNotBlank() && viewModel.isConfigured,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Menganalisis berita...", fontSize = 12.sp)
                } else {
                    Text("Tanya AI", fontSize = 12.sp)
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(errorMessage!!, color = Color(0xFFEF4444), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (result != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                AiResultView(result!!)
            }
        }
    }
}

@Composable
fun AiResultView(result: NewsAnalysisResult) {
    Column {
        if (result.headlines.isNotEmpty()) {
            Text(
                "Berdasarkan ${result.headlines.size} berita terbaru:",
                fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(result.aiSummary, fontSize = 13.sp, lineHeight = 19.sp)

        if (result.headlines.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Sumber berita:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            result.headlines.forEach { h ->
                Text(
                    "\u2022 [${h.source}] ${h.title}",
                    fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun NewsCard(item: NewsItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (item.verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Verified, contentDescription = "Verified",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text("${item.handle} \u00B7 ${item.time}", color = Color.Gray, fontSize = 11.sp)
                }
                SentimentBadge(item.sentiment)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.body, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            AssistChip(onClick = {}, label = { Text("#${item.tag}", fontSize = 11.sp) })
        }
    }
}

@Composable
fun SentimentBadge(sentiment: Sentiment) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(sentiment.color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(sentiment.label, color = sentiment.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
