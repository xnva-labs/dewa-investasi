@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package id.fajar.zahra

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.fajar.zahra.islamic.IslamicContentRepository
import kotlinx.coroutines.CancellationException

@Composable
fun IslamicContentScreen() {
    var mode by remember { mutableStateOf("hadith") }
    var selectedBook by remember { mutableStateOf(IslamicContentRepository.books.first().id) }
    var numberText by remember { mutableStateOf("1") }
    var selectedNumber by remember { mutableIntStateOf(1) }
    var duaSource by remember { mutableStateOf("harian") }
    var hadith by remember { mutableStateOf<IslamicContentRepository.TextItem?>(null) }
    var duas by remember { mutableStateOf<List<IslamicContentRepository.TextItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mode, selectedBook, selectedNumber, duaSource, reloadToken) {
        loading = true
        error = null
        try {
            if (mode == "hadith") {
                hadith = IslamicContentRepository.getHadith(selectedBook, selectedNumber)
            } else {
                duas = IslamicContentRepository.getDuas(duaSource)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: Exception) {
            error = problem.message ?: "Konten belum dapat dimuat. Periksa internet dan coba lagi."
        } finally {
            loading = false
        }
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Hadis & Doa") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "hadith", onClick = { mode = "hadith" }, label = { Text("Hadis") })
                FilterChip(selected = mode == "dua", onClick = { mode = "dua" }, label = { Text("Doa & dzikir") })
            }

            if (mode == "hadith") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IslamicContentRepository.books.forEach { book ->
                        FilterChip(
                            selected = selectedBook == book.id,
                            onClick = { selectedBook = book.id },
                            label = { Text(book.label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = numberText,
                        onValueChange = { numberText = it.filter(Char::isDigit).take(6) },
                        label = { Text("Nomor hadis") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { selectedNumber = numberText.toIntOrNull()?.coerceIn(1, 100_000) ?: 1; numberText = selectedNumber.toString() }) {
                        Text("Buka")
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IslamicContentRepository.duaSources.forEach { (source, label) ->
                        FilterChip(selected = duaSource == source, onClick = { duaSource = source }, label = { Text(label) })
                    }
                }
            }

            if (loading) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Mengambil konten…", style = MaterialTheme.typography.bodySmall)
                }
            }
            error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Konten belum tersedia", fontWeight = FontWeight.SemiBold)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { reloadToken += 1 }) { Text("Coba lagi") }
                    }
                }
            }

            if (mode == "hadith") {
                val entry = hadith
                if (entry != null && error == null) {
                    Card(
                        Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFEEF5F0))
                    ) {
                        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(entry.reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (entry.arabic.isNotBlank()) {
                                Text(entry.arabic, Modifier.fillMaxWidth(), fontSize = 25.sp, lineHeight = 42.sp, textAlign = TextAlign.End)
                            }
                            if (entry.translation.isNotBlank()) Text(entry.translation, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp)
                            Spacer(Modifier.weight(1f))
                            Text(entry.source, style = MaterialTheme.typography.labelSmall)
                            Text("Periksa kembali rujukan dan penjelasan ulama untuk kajian mendalam.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled = selectedNumber > 1,
                                    onClick = { selectedNumber -= 1; numberText = selectedNumber.toString() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Sebelumnya") }
                                Button(
                                    onClick = { selectedNumber = (selectedNumber + 1).coerceAtMost(100_000); numberText = selectedNumber.toString() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Hadis berikutnya") }
                            }
                        }
                    }
                }
            } else {
                if (!loading && error == null && duas.isEmpty()) Text("Belum ada doa pada kategori ini.")
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(duas) { dua ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFFFFFF))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(dua.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (dua.arabic.isNotBlank()) Text(dua.arabic, Modifier.fillMaxWidth(), fontSize = 23.sp, lineHeight = 38.sp, textAlign = TextAlign.End)
                                if (dua.translation.isNotBlank()) Text(dua.translation, style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp)
                                Text(dua.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
