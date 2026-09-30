package com.xnvalabs.investmenttracker.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnvalabs.investmenttracker.data.dictionaryData
import com.xnvalabs.investmenttracker.model.DictionaryTerm

// ============================================================================
// TAB 4 — KAMUS ISTILAH INVESTASI & TRADING
// Bisa dicari, dikelompokkan otomatis per kategori dari DictionaryData.kt.
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen() {
    val allTerms = remember { dictionaryData() }
    var query by remember { mutableStateOf("") }
    var expandedTerm by remember { mutableStateOf<String?>(null) }

    val filtered = remember(query) {
        if (query.isBlank()) allTerms
        else allTerms.filter {
            it.term.contains(query, ignoreCase = true) ||
                it.definition.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
        }
    }
    val grouped = filtered.groupBy { it.category }.toSortedMap()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cari istilah (mis. RSI, EPS, leverage)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 4.dp)
        )
        Text(
            "${filtered.size} istilah ditemukan",
            color = Color.Gray, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            grouped.forEach { (category, terms) ->
                item {
                    Text(
                        category,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                    )
                }
                items(terms) { term ->
                    DictionaryTermCard(
                        term = term,
                        expanded = expandedTerm == term.term,
                        onToggle = {
                            expandedTerm = if (expandedTerm == term.term) null else term.term
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun DictionaryTermCard(term: DictionaryTerm, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(term.term, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(term.definition, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("Contoh:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(2.dp))
                        Text(term.example, fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(2.dp))
                Text(
                    term.definition,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
