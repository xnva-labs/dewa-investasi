package com.xnvalabs.investmenttracker.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnvalabs.investmenttracker.data.mockNews
import com.xnvalabs.investmenttracker.model.NewsItem
import com.xnvalabs.investmenttracker.model.Sentiment

// ============================================================================
// TAB 2 — MARKET SENTIMENT FEED (mock, ala feed X/Twitter)
// ============================================================================

@Composable
fun SentimentScreen() {
    val news = remember { mockNews() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(news) { item -> NewsCard(item) }
        item { Spacer(Modifier.height(24.dp)) }
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
