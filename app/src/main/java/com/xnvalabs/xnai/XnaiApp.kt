package com.xnvalabs.xnai

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

val XNABg = Color(0xFF07111F)
val XNASurface = Color(0xFF0D1B2E)
val XNACyan = Color(0xFF37D6DC)
val XNABlue = Color(0xFF469CFF)

enum class Screen(val label: String) {
    HOME("Beranda"),
    CORE("XNAI Core"),
    VOICE("Voice"),
    LIBRARY("Perpustakaan"),
    KNOWLEDGE("Pengetahuan"),
    COMPRESS("Kompres Kompleks"),
    CODE("Code Book"),
    PROMPTS("Cara Berfikir"),
    LANGUAGES("Bahasa & Algoritma"),
    REASONING("Kekuatan Reasoning"),
    HISTORY("Riwayat"),
    SETTINGS("Pengaturan"),
    VISION("Vision Lab")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XnaiApp(repository: XnaiRepository) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<Any?>(null) }

    fun refresh() { version++ }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = XNABg,
            surface = XNASurface,
            primary = XNABlue,
            secondary = XNACyan
        )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Color(0xFF081524)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("XNAI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("AI Learning & Reasoning Academy", color = XNACyan)
                    }
                    HorizontalDivider()
                    Screen.values().filter { it != Screen.VISION }.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            selected = screen == item,
                            onClick = {
                                screen = item
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text("Vision Lab") },
                        selected = screen == Screen.VISION,
                        onClick = {
                            screen = Screen.VISION
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(screen.label) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("☰", color = XNACyan)
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        listOf(Screen.HOME, Screen.CORE, Screen.LIBRARY, Screen.REASONING, Screen.SETTINGS).forEach { item ->
                            NavigationBarItem(
                                selected = screen == item,
                                onClick = { screen = item },
                                icon = { Text(item.label.take(1)) },
                                label = { Text(item.label.take(8)) }
                            )
                        }
                    }
                }
            ) { padding ->
                Surface(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    color = XNABg
                ) {
                    key(version, screen) {
                        when (screen) {
                        Screen.HOME -> HomeScreen(
                            repository = repository,
                            onOpen = { screen = it }
                        )
                        Screen.CORE -> CoreScreen(repository)
                        Screen.VOICE -> VoiceScreen(repository)
                        Screen.LIBRARY -> LibraryScreen(
                            repository = repository,
                            onOpenVision = { screen = Screen.VISION },
                            onChanged = { refresh() }
                        )
                        Screen.KNOWLEDGE -> KnowledgeScreen(repository)
                        Screen.COMPRESS -> CompressScreen(repository, onChanged = { refresh() })
                        Screen.CODE -> CodeScreen(repository, onChanged = { refresh() })
                        Screen.PROMPTS -> AutonomyScreen(repository, onChanged = { refresh() })
                        Screen.LANGUAGES -> LanguageAtlasScreen()
                        Screen.REASONING -> ReasoningScreen(repository, onChanged = { refresh() })
                        Screen.HISTORY -> HistoryScreen(repository)
                        Screen.SETTINGS -> SettingsScreen(repository, onReset = {
                            repository.clearDemoData()
                            refresh()
                            Toast.makeText(
                                context,
                                "Data demo lokal direset.",
                                Toast.LENGTH_SHORT
                            ).show()
                        })
                        Screen.VISION -> VisionLabScreen(
                            repository = repository,
                            onSaved = { refresh() }
                        )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { item { content() } }
}

@Composable
fun HomeScreen(repository: XnaiRepository, onOpen: (Screen) -> Unit) {
    val artifacts = repository.artifacts()
    val formulas = repository.formulas()
    val nodes = repository.nodes()

    PageColumn {
        Card {
            Column(Modifier.padding(20.dp)) {
                Text("RUANG BELAJAR AI", color = XNACyan, fontWeight = FontWeight.Bold)
                Text(
                    "Tempat XNAI belajar, mengingat, dan menalar.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text("Native Android. Kamera, OCR, reasoning lokal, knowledge graph, rumus, dan perpustakaan berjalan langsung di aplikasi.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                    Button(onClick = { onOpen(Screen.CORE) }) { Text("Mulai Core") }
                    OutlinedButton(onClick = { onOpen(Screen.VISION) }) { Text("Vision Lab") }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Otak eksplorasi", color = XNACyan, fontWeight = FontWeight.Bold)
                Text("Cara Berfikir sekarang punya loop mandiri: gabungkan pengetahuan → buat hipotesis → jalankan eksperimen → verifikasi → simpan refleksi.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { onOpen(Screen.PROMPTS) }) { Text("Buka eksplorasi") }
                    OutlinedButton(onClick = { onOpen(Screen.LANGUAGES) }) { Text("Atlas bahasa") }
                }
            }
        }

        Text("Modul utama", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Artifact", artifacts.size.toString(), Modifier.weight(1f))
            MetricCard("Rumus", formulas.size.toString(), Modifier.weight(1f))
            MetricCard("Node", nodes.size.toString(), Modifier.weight(1f))
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Alur vision", fontWeight = FontWeight.Bold)
                Text("Foto kertas → OCR tulisan → teks tersimpan. Realtime → text + objek + bentuk → hasil bisa disimpan sebagai knowledge.")
                LinearProgressIndicator(progress = { 0.82f }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        }

        Text("Catatan penting", fontWeight = FontWeight.Bold)
        Text("Model object detector bawaan ML Kit mengenali objek dengan kategori umum. Untuk nama objek yang sangat spesifik, siapkan custom model di tahap berikutnya.")
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color.LightGray)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = XNACyan)
        }
    }
}

@Composable
fun CoreScreen(repository: XnaiRepository) {
    val context = LocalContext.current
    val service = remember { XnaiReasoningService(context) }
    val voice = remember { XnaiVoice(context) }
    val messages = remember { mutableStateListOf(
        ChatMessage(1, "Saya XNAI Core. Saya membedakan memori, bukti, hipotesis, dan kesimpulan.", false)
    ) }
    var input by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { voice.release() } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                    Card { Text(msg.text, modifier = Modifier.padding(12.dp)) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Jelaskan F = ma", "Hubungkan konsep", "Debugging", "Apa yang belum XNAI ketahui?").forEach {
                AssistChip(onClick = { input = it }, label = { Text(it) })
            }
        }

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                enabled = !busy,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tanyakan sesuatu pada XNAI…") }
            )
            Spacer(Modifier.size(8.dp))
            Button(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    val q = input.trim()
                    messages += ChatMessage(System.nanoTime(), q, true)
                    input = ""
                    busy = true
                    scope.launch {
                        val answer = service.answer(q)
                        messages += ChatMessage(System.nanoTime(), answer, false)
                        repository.log("sesi Core dijalankan", "Respons XNAI")
                        busy = false
                    }
                }
            ) { Text(if (busy) "…" else "Kirim") }
        }
    }
}

@Composable
fun LibraryScreen(
    repository: XnaiRepository,
    onOpenVision: () -> Unit,
    onChanged: () -> Unit
) {
    val artifacts = repository.artifacts()
    var query by rememberSaveable { mutableStateOf("") }
    var addText by rememberSaveable { mutableStateOf(false) }

    val filtered = artifacts.filter {
        query.isBlank() || (it.title + it.content + it.category).contains(query, ignoreCase = true)
    }

    PageColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenVision) { Text("📷 Scan kertas / realtime") }
            OutlinedButton(onClick = { addText = true }) { Text("Tambah teks") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari artifact") }
        )
        if (filtered.isEmpty()) {
            Text("Belum ada artifact.")
        }
        filtered.forEach { item ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text("${item.format} · ${item.category}", color = XNACyan)
                    Spacer(Modifier.height(4.dp))
                    Text(item.content.take(500))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = {
                            repository.saveArtifact(item.copy(status = if (item.status == "favorite") "active" else "favorite"))
                            onChanged()
                        }) {
                            Text(if (item.status == "favorite") "★ Favorit" else "☆ Favorit")
                        }
                        TextButton(onClick = {
                            repository.deleteArtifact(item.id)
                            repository.log("dihapus", item.title)
                            onChanged()
                        }) { Text("Hapus") }
                    }
                }
            }
        }
        val libCtx = LocalContext.current
        GitHubAccountPanel(remember(libCtx) { XnaiSettings(libCtx) })
    }

    if (addText) {
        TextArtifactDialog(
            onDismiss = { addText = false },
            onSave = { title, content, category ->
                repository.saveArtifact(Artifact(Ids.next(), title, "TXT", category, content))
                repository.log("dibuat", title)
                onChanged()
                addText = false
            }
        )
    }
}

@Composable
private fun TextArtifactDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pengetahuan") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah artifact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Judul") })
                OutlinedTextField(category, { category = it }, label = { Text("Kategori") })
                OutlinedTextField(content, { content = it }, label = { Text("Isi") }, minLines = 5)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank() && content.isNotBlank()) onSave(title, content, category)
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun KnowledgeScreen(repository: XnaiRepository) {
    val nodes = repository.nodes()
    var add by remember { mutableStateOf(false) }

    PageColumn {
        Button(onClick = { add = true }) { Text("Tambah node") }
        nodes.forEach { node ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(node.name, fontWeight = FontWeight.Bold)
                    Text(node.representation, color = XNACyan)
                    Text(node.definition)
                    Text("Confidence ${node.confidence}%")
                }
            }
        }
    }

    if (add) {
        var name by remember { mutableStateOf("") }
        var rep by remember { mutableStateOf("") }
        var def by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text("Tambah node pengetahuan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nama") })
                    OutlinedTextField(rep, { rep = it }, label = { Text("Representasi, contoh: Ek = ½mv²") })
                    OutlinedTextField(def, { def = it }, label = { Text("Definisi") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && rep.isNotBlank()) {
                        repository.saveNode(KnowledgeNode(Ids.next(), name, rep, def.ifBlank { name }))
                        repository.log("node dibuat", name)
                        add = false
                    }
                }) { Text("Simpan") }
            }
        )
    }
}

@Composable
fun CompressScreen(repository: XnaiRepository, onChanged: () -> Unit) {
    val formulas = repository.formulas()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(formulas.firstOrNull()?.id) }
    var showEditor by remember { mutableStateOf(false) }
    var compressionText by rememberSaveable { mutableStateOf("") }
    var compressionResult by remember { mutableStateOf<RecursiveCompression.Result?>(null) }
    var compressionVerified by remember { mutableStateOf<Boolean?>(null) }

    PageColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showEditor = true }) { Text("+ Tambah rumus") }
            Text("Nama → domain → rumus → arti → variabel → contoh.")
        }
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recursive byte compression", fontWeight = FontWeight.Bold)
                Text("Exact decode + cost-aware recursive symbols. Data baru tidak dipaksa masuk ke simbol jika dictionary overhead membuatnya rugi.")
                OutlinedTextField(
                    value = compressionText,
                    onValueChange = { compressionText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text("Data uji") }
                )
                Button(enabled = compressionText.isNotEmpty(), onClick = {
                    val input = compressionText.toByteArray(Charsets.UTF_8)
                    val result = RecursiveCompression.compress(input)
                    compressionResult = result
                    compressionVerified = result.decode().contentEquals(input)
                    repository.log("recursive compression", "${result.levels} levels")
                }) { Text("Kompres + verifikasi") }
                compressionResult?.let { r ->
                    Text("Original: ${r.originalBytes} B · Encoded estimate: ${r.estimatedEncodedBytes} B · Levels: ${r.levels}")
                    Text("Ratio: ${"%.2f".format(RecursiveCompression.estimateCompressionRatio(r))}")
                    Text(if (compressionVerified == true) "✓ Exact decode berhasil" else "✗ Exact decode gagal", color = if (compressionVerified == true) XNACyan else MaterialTheme.colorScheme.error)
                }
            }
        }

        formulas.forEach { f ->
            Card(onClick = { selectedId = f.id }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selectedId == f.id, onCheckedChange = { selectedId = if (it) f.id else null })
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.Bold)
                        Text(f.formula, color = Color(0xFFFFC766), style = MaterialTheme.typography.titleLarge)
                        Text(f.domain, color = XNACyan)
                    }
                }
            }
        }

        val selected = formulas.firstOrNull { it.id == selectedId }
        if (selected != null) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Hasil kompres belajar", fontWeight = FontWeight.Bold)
                    Text("INTI: ${selected.name}\nRUMUS: ${selected.formula}\nMAKNA: ${selected.meaning}\nVARIABEL: ${selected.variables}\nCONTOH: ${selected.example}\n\nKUNCI: rumus + kondisi berlaku + satuan + contoh.")
                    Button(onClick = {
                        repository.saveArtifact(Artifact(Ids.next(), "Ringkasan ${selected.name}", "TXT", "Kompres Kompleks", "${selected.formula}\n${selected.meaning}\n${selected.variables}\n${selected.example}"))
                        repository.log("dikompres", selected.name)
                        onChanged()
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Simpan ringkasan") }
                }
            }
        }
    }

    if (showEditor) {
        FormulaEditorDialog(
            onDismiss = { showEditor = false },
            onSave = { item ->
                repository.saveFormula(item)
                repository.log("dibuat", item.name)
                onChanged()
                showEditor = false
            }
        )
    }
}

@Composable
private fun FormulaEditorDialog(
    onDismiss: () -> Unit,
    onSave: (FormulaItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("Fisika") }
    var formula by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var variables by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah rumus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("1. Nama rumus") })
                OutlinedTextField(domain, { domain = it }, label = { Text("2. Domain") })
                OutlinedTextField(formula, { formula = it }, label = { Text("3. Rumus utama, contoh F = m × a") })
                OutlinedTextField(meaning, { meaning = it }, label = { Text("4. Artinya") })
                OutlinedTextField(variables, { variables = it }, label = { Text("5. Variabel") })
                OutlinedTextField(example, { example = it }, label = { Text("6. Contoh") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && formula.isNotBlank()) {
                    onSave(FormulaItem(Ids.next(), name, domain, formula, meaning, variables, example))
                }
            }) { Text("Simpan rumus") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun CodeScreen(repository: XnaiRepository, onChanged: () -> Unit) {
    val snippets = repository.snippets()
    var selected by remember { mutableStateOf(snippets.firstOrNull()) }
    var add by remember { mutableStateOf(false) }

    PageColumn {
        Button(onClick = { add = true }) { Text("Snippet baru") }
        snippets.forEach { item ->
            Card(onClick = { selected = item }) {
                Column(Modifier.padding(14.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text("${item.language} · ${item.note}", color = XNACyan)
                }
            }
        }
        selected?.let {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(it.title, fontWeight = FontWeight.Bold)
                    Surface(color = Color(0xFF061321), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(it.code, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }

    if (add) {
        var title by remember { mutableStateOf("") }
        var lang by remember { mutableStateOf("Kotlin") }
        var code by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text("Snippet baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Judul") })
                    OutlinedTextField(lang, { lang = it }, label = { Text("Bahasa") })
                    OutlinedTextField(code, { code = it }, label = { Text("Kode") }, minLines = 5)
                    OutlinedTextField(note, { note = it }, label = { Text("Catatan") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && code.isNotBlank()) {
                        repository.saveSnippet(Snippet(Ids.next(), title, lang, code, note))
                        repository.log("dibuat", title)
                        add = false
                        onChanged()
                    }
                }) { Text("Simpan") }
            }
        )
    }
}

@Composable
fun PromptsScreen(onUse: (String) -> Unit) {
    val prompts = listOf(
        "First Principles" to "Uraikan menjadi fakta dasar.",
        "Debugging" to "Hipotesis → uji → verifikasi.",
        "Sebab–Akibat" to "Telusuri mekanisme sebelum menyimpulkan.",
        "Verifikasi" to "Cari bukti, asumsi, dan batas kesimpulan.",
        "Perbandingan" to "Bandingkan opsi dengan kriteria yang sama."
    )

    PageColumn {
        prompts.forEach { (title, body) ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(body)
                    OutlinedButton(onClick = { onUse(body) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Gunakan di Core")
                    }
                }
            }
        }
    }
}

@Composable
fun ReasoningScreen(repository: XnaiRepository, onChanged: () -> Unit) {
    val questions = listOf(
        "Jika semua A adalah B dan beberapa B adalah C, apakah semua A pasti C?" to
            "Tidak. Sebagian B yang C tidak membuktikan semua A adalah C.",
        "Benda bermassa 2 kg dipercepat 3 m/s². Berapa gaya resultannya?" to
            "6 N, karena F = m × a.",
        "Apakah korelasi selalu membuktikan sebab-akibat?" to
            "Tidak. Variabel lain bisa menjelaskan hubungan."
    )
    var index by rememberSaveable { mutableIntStateOf(0) }
    var answer by rememberSaveable { mutableStateOf("") }
    var confidence by rememberSaveable { mutableStateOf(3f) }
    var feedback by rememberSaveable { mutableStateOf("") }
    val answered = repository.logs().count { it.action == "latihan dijawab" }

    PageColumn {
        Text("Soal ${index + 1} / ${questions.size}", color = XNACyan)
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(questions[index].first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Jawaban + alasan") },
                    minLines = 5
                )
                Text("Confidence ${confidence.toInt()}/5")
                Slider(value = confidence, onValueChange = { confidence = it }, valueRange = 1f..5f)
                Button(
                    onClick = {
                        feedback = "Referensi: ${questions[index].second}"
                        repository.log("latihan dijawab", "Soal reasoning")
                        onChanged()
                    }
                ) { Text("Periksa") }
                if (feedback.isNotBlank()) {
                    Text(feedback, color = XNACyan, modifier = Modifier.padding(top = 10.dp))
                    TextButton(onClick = {
                        index = (index + 1) % questions.size
                        answer = ""
                        feedback = ""
                    }) { Text("Soal berikutnya") }
                }
            }
        }
        Text("Sudah dijawab: $answered")
    }
}

@Composable
fun HistoryScreen(repository: XnaiRepository) {
    val logs = repository.logs()
    val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale("id", "ID")) }

    PageColumn {
        if (logs.isEmpty()) {
            Text("Belum ada aktivitas.")
        }
        logs.forEach {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(it.title, fontWeight = FontWeight.Bold)
                        Text(it.action, color = XNACyan)
                    }
                    Text(formatter.format(Date(it.time)), color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(repository: XnaiRepository, onReset: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { XnaiSettings(context) }
    var autonomy by rememberSaveable { mutableStateOf(settings.autonomyEnabled) }
    var network by rememberSaveable { mutableStateOf(settings.sandboxPolicy.allowNetworkResearch) }
    var providerEnabled by rememberSaveable { mutableStateOf(settings.provider.enabled) }
    var providerUrl by rememberSaveable { mutableStateOf(settings.provider.baseUrl) }
    var providerModel by rememberSaveable { mutableStateOf(settings.provider.model) }
    var providerKey by rememberSaveable { mutableStateOf("") }
    var ghEnabled by rememberSaveable { mutableStateOf(settings.github.enabled || settings.githubAccounts().any { it.enabled }) }
    var ghAccountId by rememberSaveable { mutableStateOf("primary") }
    var ghOwner by rememberSaveable { mutableStateOf(settings.github.owner) }
    var ghRepo by rememberSaveable { mutableStateOf(settings.github.repo) }
    var ghBranch by rememberSaveable { mutableStateOf(settings.github.branch) }
    var ghToken by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var highContrast by rememberSaveable { mutableStateOf(false) }
    var reducedMotion by rememberSaveable { mutableStateOf(false) }
    val dictionaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val count = XnaiDictionaryImporter(context).importTextDictionary(context, uri, "user-selected-dictionary")
            message = "$count token masuk ke codebook. Pastikan sumber memiliki izin penggunaan."
        }
    }

    PageColumn {
        PreferenceRow("Autonomous learning", "XNAI memilih siklus berikutnya sendiri dan dijadwalkan oleh WorkManager.", autonomy) {
            autonomy = it
            settings.autonomyEnabled = it
            XnaiAutonomyScheduler.setEnabled(context, it)
        }
        PreferenceRow("Riset web", "Izinkan retrieval terbatas dari sumber publik melalui sandbox.", network) {
            network = it
            settings.sandboxPolicy = settings.sandboxPolicy.copy(allowNetworkResearch = it)
        }
        PreferenceRow("Provider AI", "Gunakan endpoint OpenAI-compatible bila API key tersedia.", providerEnabled) {
            providerEnabled = it
            settings.provider = settings.provider.copy(enabled = it)
        }
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Provider AI", fontWeight = FontWeight.Bold)
                OutlinedTextField(providerUrl, { providerUrl = it; settings.provider = settings.provider.copy(baseUrl = it) }, label = { Text("Base URL") })
                OutlinedTextField(providerModel, { providerModel = it; settings.provider = settings.provider.copy(model = it) }, label = { Text("Model") })
                OutlinedTextField(providerKey, { providerKey = it }, label = { Text("API key (tidak disimpan plaintext)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { settings.setProviderApiKey(providerKey); providerKey = ""; message = "API key disimpan via Android Keystore." }) { Text("Simpan key") }
                    OutlinedButton(onClick = { settings.clearProviderApiKey(); providerKey = ""; message = "API key dihapus." }) { Text("Hapus key") }
                }
            }
        }
        GitHubAccountPanel(settings)
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Codebook / dictionary", fontWeight = FontWeight.Bold)
                Text("Import TXT/CSV/TSV dari sumber yang secara legal boleh digunakan. Definisi dan provenance disimpan bersama ID stabil.")
                OutlinedButton(onClick = { dictionaryPicker.launch("text/*") }) { Text("Import dictionary") }
            }
        }
        PreferenceRow("Kontras tinggi", "Tingkatkan keterbacaan.", highContrast) { highContrast = it }
        PreferenceRow("Kurangi animasi", "Minimalkan gerak dekoratif.", reducedMotion) { reducedMotion = it }
        Card {
            Column(Modifier.padding(14.dp)) {
                Text("Sandbox", fontWeight = FontWeight.Bold)
                Text("Cage aktif: CPU/waktu/memori/data generasi dibatasi. Arbitrary host command tidak dieksekusi. External actions default OFF.")
                Text("Network requests/cycle: ${settings.sandboxPolicy.maxNetworkRequestsPerCycle}")
                OutlinedButton(onClick = {
                    settings.sandboxPolicy = SandboxPolicy(
                        enabled = settings.sandboxPolicy.enabled,
                        maxExperimentMs = settings.sandboxPolicy.maxExperimentMs,
                        maxMemoryBytes = settings.sandboxPolicy.maxMemoryBytes,
                        maxGeneratedItems = settings.sandboxPolicy.maxGeneratedItems,
                        allowNetworkResearch = settings.sandboxPolicy.allowNetworkResearch,
                        allowExternalActions = false,
                        allowCodeExecution = false,
                        maxNetworkRequestsPerCycle = settings.sandboxPolicy.maxNetworkRequestsPerCycle
                    )
                    message = "External actions dan arbitrary code execution tetap terkunci."
                }) { Text("Kunci external actions") }
            }
        }
        Card {
            Column(Modifier.padding(14.dp)) {
                Text("Data lokal", fontWeight = FontWeight.Bold)
                Text("Data lama tetap tersedia; library XNAI baru tersimpan di filesDir/XNAI_LIBRARY dengan unit yang dapat disegmentasi.")
                OutlinedButton(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) { Text("Reset data demo") }
            }
        }
        if (message.isNotBlank()) Text(message, color = XNACyan)
    }
}

@Composable
private fun PreferenceRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.LightGray)
            }
            Switch(checked, onCheckedChange = onChecked)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionLabScreen(
    repository: XnaiRepository,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(VisionMode.ALL) }
    var latest by remember { mutableStateOf(VisionResult()) }
    var permission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permission = granted
        if (!granted) {
            Toast.makeText(context, "Izin kamera diperlukan.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!permission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VisionMode.values().forEach {
                AssistChip(
                    onClick = { mode = it },
                    label = { Text(it.name.lowercase()) }
                )
            }
        }

        if (permission) {
            CameraSurface(
                mode = mode,
                onVision = { latest = it },
                onSavedArtifact = { result ->
                    val content = buildString {
                        if (result.text.isNotBlank()) append("OCR:\n${result.text}\n\n")
                        if (result.objects.isNotEmpty()) append("Objek:\n")
                        result.objects.forEach { append("- ${it.label} (${(it.confidence * 100).toInt()}%)\n") }
                        if (result.shapes.isNotEmpty()) append("\nBentuk:\n")
                        result.shapes.forEach { append("- ${it.name}\n") }
                    }
                    if (content.isNotBlank()) {
                        repository.saveArtifact(
                            Artifact(
                                Ids.next(),
                                "Vision ${SimpleDateFormat("dd-MM HH:mm", Locale("id")).format(Date())}",
                                "TXT",
                                "Vision",
                                content
                            )
                        )
                        repository.log("hasil media dianalisis", "Vision")
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Izinkan kamera")
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Hasil analisis", fontWeight = FontWeight.Bold)
                if (latest.text.isNotBlank()) {
                    Text("Tulisan:", color = XNACyan)
                    Text(latest.text.take(1500))
                }
                if (latest.objects.isNotEmpty()) {
                    Text("Objek:", color = XNACyan)
                    latest.objects.take(5).forEach { Text("• ${it.label} ${(it.confidence * 100).toInt()}%") }
                }
                if (latest.shapes.isNotEmpty()) {
                    Text("Bentuk:", color = XNACyan)
                    latest.shapes.take(10).forEach { Text("• ${it.name}") }
                }
                if (latest.labels.isNotEmpty()) {
                    Text("Label visual:", color = XNACyan)
                    latest.labels.take(8).forEach { Text("• $it") }
                }
                if (latest.faceCount > 0) Text("Wajah terdeteksi: ${latest.faceCount}", color = XNACyan)
                if (latest.barcodes.isNotEmpty()) {
                    Text("Barcode / QR:", color = XNACyan)
                    latest.barcodes.forEach { Text("• $it") }
                }
                if (latest.text.isBlank() && latest.objects.isEmpty() && latest.shapes.isEmpty() && latest.labels.isEmpty() && latest.faceCount == 0 && latest.barcodes.isEmpty()) {
                    Text("Arahkan kamera ke kertas, tulisan, objek, wajah, kode, atau bentuk geometris.")
                }
            }
        }
    }
}

@Composable
private fun CameraSurface(
    mode: VisionMode,
    onVision: (VisionResult) -> Unit,
    onSavedArtifact: (VisionResult) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var latest by remember { mutableStateOf(VisionResult()) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Box(modifier) {
        val previewView = remember { PreviewView(context) }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        DisposableEffect(previewView, lifecycleOwner, mode) {
            var cameraProvider: ProcessCameraProvider? = null
            val future = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                try {
                    cameraProvider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    val analyzer = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analyzer.setAnalyzer(
                        executor,
                        NativeVisionAnalyzer(mode) { result ->
                            latest = result
                            onVision(result)
                        }
                    )

                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                        analyzer
                    )
                } catch (_: Throwable) {
                }
            }
            future.addListener(listener, ContextCompat.getMainExecutor(context))

            onDispose {
                cameraProvider?.unbindAll()
                imageCapture = null
            }
        }

        Button(
            onClick = {
                val capture = imageCapture ?: return@Button
                val file = File.createTempFile("xnai_capture_", ".jpg", context.cacheDir)
                val output = ImageCapture.OutputFileOptions.Builder(file).build()

                capture.takePicture(
                    output,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            try {
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                if (bitmap != null) {
                                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                    val detector = ObjectDetection.getClient(
                                        ObjectDetectorOptions.Builder()
                                            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                                            .enableMultipleObjects()
                                            .enableClassification()
                                            .build()
                                    )
                                    val input = InputImage.fromBitmap(bitmap, 0)
                                    val textTask = recognizer.process(input)
                                    val objectTask = detector.process(input)
                                    val shapes = ShapeDetector.detect(bitmap)
                                    val extras = VisionExtras()
                                    var textResult = ""
                                    var objectResult = emptyList<ObjectResult>()
                                    var extraResult = VisionExtraResult()
                                    val pending = AtomicInteger(2)

                                    fun finish() {
                                        if (pending.decrementAndGet() == 0) {
                                            extras.process(input) { extra ->
                                                extraResult = extra
                                                val result = VisionResult(
                                                    text = textResult,
                                                    objects = objectResult,
                                                    shapes = shapes,
                                                    labels = extraResult.labels,
                                                    faceCount = extraResult.faceCount,
                                                    barcodes = extraResult.barcodes
                                                )
                                                latest = result
                                                onVision(result)
                                                onSavedArtifact(result)
                                                bitmap.recycle()
                                                file.delete()
                                            }
                                        }
                                    }

                                    textTask
                                        .addOnSuccessListener { textResult = it.text }
                                        .addOnCompleteListener { finish() }

                                    objectTask
                                        .addOnSuccessListener { objects ->
                                            objectResult = objects.mapNotNull { obj ->
                                                obj.labels.firstOrNull()?.let { label ->
                                                    ObjectResult(label.text, label.confidence)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener { finish() }
                                }
                            } catch (_: Throwable) {
                            } finally {
                                // Temporary file is deleted after both ML Kit tasks finish.
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            file.delete()
                        }
                    }
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
        ) {
            Text("Ambil foto & baca")
        }
    }
}
