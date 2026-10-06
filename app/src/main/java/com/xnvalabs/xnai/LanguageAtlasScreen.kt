package com.xnvalabs.xnai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun LanguageAtlasScreen() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFamily by rememberSaveable { mutableStateOf("Semua") }
    val context = LocalContext.current
    var lexiconCount by remember { mutableStateOf(RecursiveCodebook(context = context.applicationContext).wordCount()) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val families = listOf("Semua") + KnowledgeCatalog.languages.map { it.family }.distinct().sorted()
    val languages = KnowledgeCatalog.languages.filter {
        (selectedFamily == "Semua" || it.family == selectedFamily) &&
            (query.isBlank() || (it.name + " " + it.family + " " + it.strengths + " " + it.commonUses).contains(query, ignoreCase = true))
    }
    val algorithms = KnowledgeCatalog.algorithms.filter {
        query.isBlank() || (it.name + " " + it.domain + " " + it.idea + " " + it.uses).contains(query, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("LANGUAGE + ALGORITHM ATLAS", color = XNACyan, fontWeight = FontWeight.Bold)
            Text("Kosakata XNAI memakai nomor sederhana 1, 2, 3...; nomor lama tidak diubah.")
            Text("Lexicon tersimpan: $lexiconCount kata", color = XNACyan)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Cari bahasa, algoritma, domain, atau use-case") })
        }
        item {
            Text("Sumber Bahasa Indonesia", fontWeight = FontWeight.Bold)
            Text("Stack bahasa Indonesia: kamus, ejaan, morfologi, semantik, korpus, terjemahan, speech, dan sumber resmi. Data besar tetap streaming/eksternal.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    status = "Mengimpor semua sumber yang eligible..."
                    scope.launch {
                        val result = XnaiLanguageResourceManager(context).importAllImportableResources { msg -> status = msg }
                        lexiconCount = RecursiveCodebook(context = context.applicationContext).wordCount()
                        val ok = result.count { it.value >= 0 }
                        status = "Stack selesai: $ok/${result.size} sumber eligible diproses. Lexicon: $lexiconCount kata."
                    }
                }) { Text("Masukkan semua sumber terbuka") }
                TextButton(onClick = { status = "KBBI/Sipebi tetap jalur resmi/berizin; sumber berhak cipta tidak disalin massal." }) {
                    Text("Status KBBI")
                }
            }
        }
        items(XnaiLanguageResourceCatalog.resources) { resource ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(resource.name, fontWeight = FontWeight.Bold)
                    Text("${resource.category} · ${resource.format} · ${resource.license}", color = XNACyan)
                    Text(resource.notes)
                    Text("Mode: ${resource.acquisition}")
                    Text("URL: ${resource.url}")
                    Spacer(Modifier.height(6.dp))
                    if (resource.importableInApp) {
                        Button(onClick = {
                            status = "Mengambil ${resource.name}..."
                            scope.launch {
                                status = try {
                                    val count = XnaiLanguageResourceManager(context).importResource(resource.id)
                                    lexiconCount = RecursiveCodebook(context = context.applicationContext).wordCount()
                                    "Selesai: $count record/token diproses dari ${resource.id}."
                                } catch (t: Throwable) {
                                    "Gagal: ${t.message ?: t.javaClass.simpleName}"
                                }
                            }
                        }) { Text("Ambil + masukkan") }
                    } else {
                        Text("Dataset eksternal besar: akses melalui sumber resmi.")
                    }
                }
            }
        }
        item {
            Text(status)
            HorizontalDivider()
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Bahasa") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Algoritma") })
            }
        }
        if (tab == 0) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    families.take(8).forEach { family ->
                        FilterChip(selected = selectedFamily == family, onClick = { selectedFamily = family }, label = { Text(family) })
                    }
                }
                Text("${languages.size} bahasa terindeks")
            }
            items(languages) { lang ->
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(lang.name, fontWeight = FontWeight.Bold)
                        Text("${lang.family} · ${lang.runtime}", color = XNACyan)
                        val profile = ProgrammingLanguageEngine.find(lang.name)
                        Text("Tahap kode: ${profile?.stages?.joinToString { it.name } ?: "Belum dipetakan"}", color = XNACyan)
                        Text(profile?.notes.orEmpty())
                        Text("Paradigma: ${lang.paradigms.joinToString()}")
                        Spacer(Modifier.height(4.dp))
                        Text("Kekuatan: ${lang.strengths}")
                        Text("Dipakai untuk: ${lang.commonUses}")
                        Text("Jalur belajar: ${lang.learningPath.joinToString(" → ")}")
                    }
                }
            }
        } else {
            item { Text("${algorithms.size} algoritma terindeks") }
            items(algorithms) { algo ->
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(algo.name, fontWeight = FontWeight.Bold)
                        Text("${algo.domain} · ${algo.complexity}", color = XNACyan)
                        Text(algo.idea)
                        Spacer(Modifier.height(4.dp))
                        Text("Langkah: ${algo.steps.joinToString(" → ")}")
                        Text("Dipakai untuk: ${algo.uses}")
                    }
                }
            }
        }
    }
}
