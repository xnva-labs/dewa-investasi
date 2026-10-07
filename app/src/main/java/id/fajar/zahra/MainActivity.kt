@file:OptIn(ExperimentalMaterial3Api::class)

package id.fajar.zahra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.fajar.zahra.backup.BackupRepository
import id.fajar.zahra.backup.PortableBackupCrypto
import id.fajar.zahra.camera.CameraProofScreen
import id.fajar.zahra.core.RepeatRules
import id.fajar.zahra.data.AppRepository
import id.fajar.zahra.data.MissionEntity
import id.fajar.zahra.data.ProfileEntity
import id.fajar.zahra.data.ZahraDatabase
import id.fajar.zahra.settings.SettingsStore
import id.fajar.zahra.bridge.GameBridge
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Primary = Color(0xFF8C6A83)
private val Background = Color(0xFFFFFAFC)

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching {
                GameBridge.consumeGameEvents(
                    applicationContext,
                    ZahraDatabase.get(applicationContext)
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.createChannel(this)
        val db = ZahraDatabase.get(applicationContext)
        val settingsStore = SettingsStore(applicationContext)
        val repo = AppRepository(db, applicationContext, settingsStore)
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.factory(repo, application))
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Primary,
                    background = Background,
                    surface = Background
                )
            ) {
                ZahraApp(
                    vm = vm,
                    db = db,
                    settings = settingsStore,
                    onGame = { startActivity(Intent(this, GameActivity::class.java)) }
                )
            }
        }
    }
}

@Composable
fun ZahraApp(
    vm: AppViewModel,
    db: ZahraDatabase,
    settings: SettingsStore,
    onGame: () -> Unit
) {
    val profile by vm.profile.collectAsState(initial = null)
    val nav = rememberNavController()
    // Tujuan awal ditentukan sekali dari database supaya pengguna lama tidak melihat layar sambutan sekilas.
    val startDestination by produceState<String?>(initialValue = null) {
        value = if (vm.profile.first() == null) "welcome" else "dashboard"
    }

    LaunchedEffect(profile?.id) {
        if (profile != null) vm.refreshReminders()
    }

    val start = startDestination ?: return
    NavHost(navController = nav, startDestination = start) {
        composable("welcome") {
            Welcome { name, age ->
                vm.saveProfile(name, age) {
                    nav.navigate("dashboard") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            }
        }
        composable("dashboard") {
            Dashboard(profile?.name.orEmpty(), vm, onGame) { nav.navigate(it) }
        }
        composable("world") {
            WorldCommandCenter(db = db, onGame = onGame)
        }
        composable("missions") {
            Missions(vm) { route -> nav.navigate(route) }
        }
        composable("edit/{missionId}") { entry ->
            val missionId = entry.arguments?.getString("missionId")?.toLongOrNull() ?: 0L
            val mission = vm.missions.collectAsState(emptyList()).value.firstOrNull { it.id == missionId }
            if (mission != null) MissionEditScreen(mission, vm) { nav.popBackStack() }
        }
        composable("lists") {
            ListsScreen(vm) { listId -> nav.navigate("list/$listId") }
        }
        composable("list/{listId}") { entry ->
            val listId = entry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            val list = vm.lists.collectAsState(emptyList()).value.firstOrNull { it.id == listId }
            if (list != null) ListDetailScreen(list, vm) { nav.popBackStack() }
        }
        composable("rewards") { Rewards(vm) }
        composable("stats") { Stats(vm) }
        composable("history") { History(vm) }
        composable("calendar") { CalendarScreen(vm) }
        composable("profile") {
            ProfileScreen(profile = profile, vm = vm)
        }
        composable("camera") {
            CameraProofScreen(onRecorded = { result ->
                if (result.missionId > 0L) vm.recordProof(result)
            })
        }
        composable("camera/{missionId}/{proofType}/{proofTarget}") { entry ->
            val missionId = entry.arguments?.getString("missionId")?.toLongOrNull() ?: 0L
            val proofType = entry.arguments?.getString("proofType") ?: "PHOTO"
            val target = Uri.decode(entry.arguments?.getString("proofTarget").orEmpty())
            CameraProofScreen(missionId, proofType, target) { result -> vm.recordProof(result) }
        }
        composable("settings") {
            SettingsScreen(vm, db, settings) {
                nav.navigate("welcome") {
                    popUpTo("settings") { inclusive = true }
                }
            }
        }
    }
}


@Composable
fun WorldCommandCenter(db: ZahraDatabase, onGame: () -> Unit) {
    val events by db.eventDao().observeRecent().collectAsState(emptyList())
    val worldEvents = events.filter { it.type == "GAME:WORLD_STATE" || it.type.startsWith("GAME:") }.take(12)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Zahra World") },
                navigationIcon = {}
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Command Center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Kontrol dan ringkasan dunia 3D: kehidupan, agama, bisnis, dan politik.")
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AGAMA", fontWeight = FontWeight.Bold)
                        Text("Masjid, salat, Ramadan, kajian, zakat, sedekah, wakaf, dan komunitas berjalan sebagai sistem dunia.")
                        LinearProgressIndicator(progress = { 0.68f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("BISNIS", fontWeight = FontWeight.Bold)
                        Text("Produksi → stok → harga → permintaan → pendapatan → upah → pajak → layanan publik.")
                        LinearProgressIndicator(progress = { 0.61f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("POLITIK", fontWeight = FontWeight.Bold)
                        Text("Faksi, konsultasi publik, kebijakan, anggaran, reputasi, koalisi, pemilu fiktif, dan karier pelayanan.")
                        LinearProgressIndicator(progress = { 0.57f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Button(onClick = onGame, modifier = Modifier.fillMaxWidth()) {
                    Text("Buka Dunia 3D")
                }
            }
            item {
                Text("Sinkronisasi dunia", style = MaterialTheme.typography.titleLarge)
            }
            if (worldEvents.isEmpty()) {
                item { Text("Belum ada event dunia. Buka game 3D untuk mulai sinkronisasi.") }
            } else {
                items(worldEvents, key = { it.id }) { event ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(event.title, fontWeight = FontWeight.SemiBold)
                            Text(event.detail, style = MaterialTheme.typography.bodySmall)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                                    .format(Date(event.createdAt)),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Welcome(save: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Zahra", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Tujuan, aktivitas, dan sebuah dunia untuk dijelajahi.")
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nama") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                age,
                { age = it.filter(Char::isDigit).take(3) },
                label = { Text("Usia") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(18.dp))
            Button(
                enabled = name.isNotBlank() && (age.toIntOrNull() ?: 0) in 1..120,
                onClick = { save(name.trim(), age.toInt()) }
            ) { Text("Masuk") }
            Spacer(Modifier.height(8.dp))
            Text("by :fjr  ·  for :zahra", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun Dashboard(name: String, vm: AppViewModel, onGame: () -> Unit, go: (String) -> Unit) {
    val missions by vm.missions.collectAsState(emptyList())
    val points by vm.points.collectAsState(0)
    val done by vm.completedCount.collectAsState(0)
    val actionMessage by vm.actionMessage.collectAsState(null)

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("dashboard" to "Hari ini", "missions" to "Misi", "stats" to "Statistik", "profile" to "Profil").forEach { (route, label) ->
                    NavigationBarItem(
                        selected = route == "dashboard",
                        onClick = { if (route != "dashboard") go(route) },
                        icon = { Text(if (route == "dashboard") "⌂" else if (route == "missions") "✓" else if (route == "stats") "↗" else "○") },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Halo, $name 🌷", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Satu langkah pada satu waktu.")
                actionMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(points.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Poin")
                        }
                    }
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(done.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Total selesai")
                        }
                    }
                }
            }
            item { Text("Misi aktif", style = MaterialTheme.typography.titleLarge) }
            items(missions.filter { it.status == "ACTIVE" }.take(5), key = { it.id }) { mission ->
                MissionCard(
                    mission,
                    done = { vm.complete(mission) },
                    pause = { vm.pause(mission.id) },
                    resume = { vm.resume(mission.id) },
                    archive = { vm.archive(mission) },
                    showControls = false,
                    onProof = if (mission.proofType == "NONE") null else {
                        { go("camera/${mission.id}/${mission.proofType}/${Uri.encode(mission.proofTarget)}") }
                    },
                    edit = { go("edit/${mission.id}") }
                )
            }
            if (missions.none { it.status == "ACTIVE" }) item { Text("Belum ada misi aktif.") }
            item { Button(onClick = { go("missions") }, modifier = Modifier.fillMaxWidth()) { Text("Kelola misi") } }
            item { OutlinedButton(onClick = { go("calendar") }, modifier = Modifier.fillMaxWidth()) { Text("Buka kalender") } }
            item { OutlinedButton(onClick = { go("lists") }, modifier = Modifier.fillMaxWidth()) { Text("Daftar & checklist") } }
            item { OutlinedButton(onClick = { go("rewards") }, modifier = Modifier.fillMaxWidth()) { Text("Reward") } }
            item { OutlinedButton(onClick = { go("camera") }, modifier = Modifier.fillMaxWidth()) { Text("Kamera proof") } }
            item { OutlinedButton(onClick = { go("world") }, modifier = Modifier.fillMaxWidth()) { Text("Zahra World Command Center") } }
            item { OutlinedButton(onClick = onGame, modifier = Modifier.fillMaxWidth()) { Text("Masuk ke dunia 3D") } }
            item { TextButton(onClick = { go("history") }) { Text("Lihat history") } }
            item { TextButton(onClick = { go("settings") }) { Text("Pengaturan & backup") } }
        }
    }
}

@Composable
fun Missions(vm: AppViewModel, go: (String) -> Unit) {
    val missions by vm.missions.collectAsState(emptyList())
    val actionMessage by vm.actionMessage.collectAsState(null)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var points by remember { mutableStateOf("10") }
    var difficulty by remember { mutableStateOf("1") }
    var delay by remember { mutableStateOf("0") }
    var scheduleText by remember { mutableStateOf("") }
    var scheduleError by remember { mutableStateOf<String?>(null) }
    var repeat by remember { mutableStateOf(RepeatRules.NONE) }
    var proof by remember { mutableStateOf("NONE") }
    var target by remember { mutableStateOf("") }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Misi") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Nama misi") }, singleLine = true) }
            item { OutlinedTextField(description, { description = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Deskripsi (opsional)") }, minLines = 2) }
            item { OutlinedTextField(category, { category = it.take(50) }, Modifier.fillMaxWidth(), label = { Text("Kategori") }, singleLine = true) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(points, { points = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("Poin") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(difficulty, { difficulty = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Kesulitan") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
            item { Text("Jenis bukti", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NONE" to "Tanpa", "PHOTO" to "Foto", "POSE" to "Pose", "OBJECT" to "Objek").forEach { (value, label) ->
                        FilterChip(selected = proof == value, onClick = { proof = value }, label = { Text(label) })
                    }
                }
            }
            if (proof == "OBJECT") item {
                OutlinedTextField(target, { target = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Target objek, contoh: sepatu") }, singleLine = true)
            }
            if (proof == "POSE") item {
                Text("Analyzer dasar saat ini mendukung evidence squat. AI bukan hakim mutlak.", style = MaterialTheme.typography.bodySmall)
            }
            item { Text("Pengulangan", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(RepeatRules.NONE to "Sekali", RepeatRules.DAILY to "Harian", RepeatRules.WEEKLY to "Mingguan", RepeatRules.MONTHLY to "Bulanan").forEach { (value, label) ->
                        FilterChip(selected = repeat == value, onClick = { repeat = value }, label = { Text(label) })
                    }
                }
            }
            item {
                OutlinedTextField(
                    scheduleText,
                    { scheduleText = it.take(16); scheduleError = null },
                    Modifier.fillMaxWidth(),
                    label = { Text("Jadwal (YYYY-MM-DD HH:mm, opsional)") },
                    placeholder = { Text("2026-10-04 19:30") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    delay,
                    { delay = it.filter(Char::isDigit).take(6) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Atau reminder cepat dalam menit") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text("Tanggal/jam memiliki prioritas. Reminder mengikuti pengaturan notifikasi.", style = MaterialTheme.typography.bodySmall)
                scheduleError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                Button(
                    enabled = title.isNotBlank() && (proof != "OBJECT" || target.isNotBlank()) && scheduleError == null,
                    onClick = {
                        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
                        val rawSchedule = scheduleText.trim()
                        val parsedSchedule = rawSchedule.takeIf { it.isNotBlank() }?.let { raw ->
                            runCatching { parser.parse(raw)?.time }.getOrNull().also { parsed ->
                                if (parsed == null || parsed < System.currentTimeMillis()) scheduleError = "Jadwal tidak valid atau sudah lewat."
                            }
                        }
                        if (rawSchedule.isNotBlank() && parsedSchedule == null) return@Button
                        val mins: Long = delay.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        val whenAt = parsedSchedule?.takeIf { it >= System.currentTimeMillis() }
                            ?: if (scheduleText.isBlank() && mins > 0) System.currentTimeMillis() + mins * 60_000L else null
                        vm.addMission(
                            title,
                            description,
                            category,
                            points.toIntOrNull() ?: 10,
                            difficulty.toIntOrNull() ?: 1,
                            proof,
                            target,
                            whenAt,
                            repeat
                        )
                        title = ""
                        description = ""
                        delay = "0"
                        scheduleText = ""
                        target = ""
                        proof = "NONE"
                        difficulty = "1"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tambah misi") }
            }
            item {
                Text("Misi tidak dihapus. Yang tidak relevan diarsipkan.", style = MaterialTheme.typography.bodySmall)
                actionMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
            items(missions.filter { it.status != "ARCHIVED" }, key = { it.id }) { mission ->
                MissionCard(
                    mission,
                    done = { vm.complete(mission) },
                    pause = { vm.pause(mission.id) },
                    resume = { vm.resume(mission.id) },
                    archive = { vm.archive(mission) },
                    showControls = true,
                    onProof = if (mission.proofType == "NONE") null else {
                        { go("camera/${mission.id}/${mission.proofType}/${Uri.encode(mission.proofTarget)}") }
                    }
                )
            }
        }
    }
}

@Composable
fun MissionEditScreen(mission: MissionEntity, vm: AppViewModel, back: () -> Unit) {
    var title by remember(mission.id) { mutableStateOf(mission.title) }
    var description by remember(mission.id) { mutableStateOf(mission.description) }
    var category by remember(mission.id) { mutableStateOf(mission.category) }
    var points by remember(mission.id) { mutableStateOf(mission.points.toString()) }
    var difficulty by remember(mission.id) { mutableStateOf(mission.difficulty.toString()) }
    var proof by remember(mission.id) { mutableStateOf(mission.proofType) }
    var target by remember(mission.id) { mutableStateOf(mission.proofTarget) }
    var repeat by remember(mission.id) { mutableStateOf(mission.repeatRule ?: RepeatRules.NONE) }
    var scheduleText by remember(mission.id) {
        mutableStateOf(mission.scheduledAt?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it)) }.orEmpty())
    }
    var error by remember(mission.id) { mutableStateOf<String?>(null) }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Edit misi") }, navigationIcon = { TextButton(onClick = back) { Text("Kembali") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Perubahan tidak membuat history lama hilang.", style = MaterialTheme.typography.bodySmall) }
            item { OutlinedTextField(title, { title=it.take(120) }, Modifier.fillMaxWidth(), label={Text("Nama misi")}, singleLine=true) }
            item { OutlinedTextField(description, { description=it.take(500) }, Modifier.fillMaxWidth(), label={Text("Deskripsi")}, minLines=2) }
            item { OutlinedTextField(category, { category=it.take(50) }, Modifier.fillMaxWidth(), label={Text("Kategori")}, singleLine=true) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(points, { points=it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label={Text("Poin")}, singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                OutlinedTextField(difficulty, { difficulty=it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label={Text("Kesulitan")}, singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            }}
            item { Text("Jenis bukti", style=MaterialTheme.typography.titleSmall) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("NONE" to "Tanpa","PHOTO" to "Foto","POSE" to "Pose","OBJECT" to "Objek").forEach { (v,l)-> FilterChip(selected=proof==v,onClick={proof=v},label={Text(l)}) } } }
            if (proof == "OBJECT") item { OutlinedTextField(target,{target=it.take(100)},Modifier.fillMaxWidth(),label={Text("Target objek")},singleLine=true) }
            item { Text("Pengulangan", style=MaterialTheme.typography.titleSmall) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(RepeatRules.NONE to "Sekali",RepeatRules.DAILY to "Harian",RepeatRules.WEEKLY to "Mingguan",RepeatRules.MONTHLY to "Bulanan").forEach{(v,l)->FilterChip(selected=repeat==v,onClick={repeat=v},label={Text(l)})} } }
            item { OutlinedTextField(scheduleText,{scheduleText=it.take(16);error=null},Modifier.fillMaxWidth(),label={Text("Jadwal YYYY-MM-DD HH:mm atau kosong")},singleLine=true) }
            error?.let { item { Text(it, color=MaterialTheme.colorScheme.error) } }
            item { Button(enabled=title.isNotBlank() && (proof!="OBJECT" || target.isNotBlank()), modifier=Modifier.fillMaxWidth(), onClick={
                val parser=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).apply{isLenient=false}
                val schedule=scheduleText.trim().takeIf{it.isNotBlank()}?.let{raw->runCatching{parser.parse(raw)?.time}.getOrNull()}
                if (scheduleText.isNotBlank() && (schedule==null || schedule < System.currentTimeMillis())) { error="Jadwal tidak valid atau sudah lewat."; return@Button }
                vm.updateMission(mission,title,description,category,points.toIntOrNull()?:10,difficulty.toIntOrNull()?:1,proof,target,schedule,repeat)
                back()
            }) { Text("Simpan perubahan") } }
        }
    }
}

@Composable
fun MissionCard(
    m: MissionEntity,
    done: () -> Unit,
    pause: () -> Unit,
    resume: () -> Unit,
    archive: () -> Unit,
    showControls: Boolean,
    onProof: (() -> Unit)? = null,
    edit: (() -> Unit)? = null
) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("${m.category} · +${m.points} · ${m.status}", style = MaterialTheme.typography.labelSmall)
            if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodySmall)
            if (m.repeatRule != null) Text("Berulang: ${m.repeatRule}", style = MaterialTheme.typography.labelSmall)
            if (m.proofType != "NONE") Text("Bukti: ${m.proofType}${m.proofTarget.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall)
            m.scheduledAt?.let { Text("Jadwal: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}", style = MaterialTheme.typography.labelSmall) }
            if (showControls && m.status == "ACTIVE") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    onProof?.let { TextButton(onClick = it) { Text("Bukti") } }
                    TextButton(onClick = done) { Text("Selesai") }
                    TextButton(onClick = pause) { Text("Jeda") }
                    TextButton(onClick = archive) { Text("Arsip") }
                }
            }
            if (showControls && m.status == "PAUSED") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = resume) { Text("Lanjutkan") }
                    TextButton(onClick = archive) { Text("Arsip") }
                }
            }
        }
    }
}

@Composable
fun ListsScreen(vm: AppViewModel, openList: (Long) -> Unit) {
    val lists by vm.lists.collectAsState(emptyList())
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val actionMessage by vm.actionMessage.collectAsState(null)
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Daftar") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Daftar fleksibel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Gunakan untuk belanja, buku, barang, ide, checklist, atau hal apa pun yang ingin disimpan.", style = MaterialTheme.typography.bodySmall)
            }
            item { OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Nama daftar") }, singleLine = true) }
            item { OutlinedTextField(description, { description = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Deskripsi (opsional)") }, minLines = 2) }
            item {
                Button(
                    enabled = title.isNotBlank(),
                    onClick = { vm.addList(title, description); title = ""; description = "" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Buat daftar") }
            }
            actionMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
            if (lists.isEmpty()) item { Text("Belum ada daftar.") }
            items(lists, key = { it.id }) { list ->
                ElevatedCard(onClick = { openList(list.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(list.title, fontWeight = FontWeight.Medium)
                        if (list.description.isNotBlank()) Text(list.description, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Buka checklist")
                            TextButton(onClick = { vm.archiveList(list.id) }) { Text("Arsip") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListDetailScreen(list: id.fajar.zahra.data.ListEntity, vm: AppViewModel, back: () -> Unit) {
    val itemsFlow = remember(list.id) { vm.listItems(list.id) }
    val itemsState by itemsFlow.collectAsState(emptyList())
    var itemTitle by remember { mutableStateOf("") }
    val actionMessage by vm.actionMessage.collectAsState(null)
    val checked = itemsState.count { it.checked }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text(list.title) }, navigationIcon = { TextButton(onClick = back) { Text("Kembali") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("$checked/${itemsState.size} selesai", style = MaterialTheme.typography.titleMedium)
                if (list.description.isNotBlank()) Text(list.description, style = MaterialTheme.typography.bodySmall)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(itemTitle, { itemTitle = it.take(160) }, Modifier.weight(1f), label = { Text("Item baru") }, singleLine = true)
                    Spacer(Modifier.padding(4.dp))
                    Button(enabled = itemTitle.isNotBlank(), onClick = { vm.addListItem(list.id, itemTitle); itemTitle = "" }) { Text("Tambah") }
                }
            }
            actionMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
            if (itemsState.isEmpty()) item { Text("Daftar masih kosong.") }
            items(itemsState, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = item.checked, onCheckedChange = { vm.setListItemChecked(item.id, it) })
                        Text(item.title, Modifier.weight(1f), fontWeight = if (item.checked) FontWeight.Normal else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun Rewards(vm: AppViewModel) {
    val rewards by vm.rewards.collectAsState(emptyList())
    val points by vm.points.collectAsState(0)
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Reward") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Goodness Points: $points", style = MaterialTheme.typography.headlineSmall)
                Text("Poin ini hanya mekanik gamifikasi, bukan ukuran pahala.", style = MaterialTheme.typography.bodySmall)
            }
            items(rewards, key = { it.id }) { reward ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(reward.title, fontWeight = FontWeight.Medium)
                        Text(reward.description)
                        Text("${reward.threshold} poin")
                        LinearProgressIndicator(progress = { (points.toFloat() / reward.threshold).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        when {
                            reward.claimed -> Text("Sudah diklaim ✓")
                            reward.unlocked -> Button(onClick = { vm.claimReward(reward.id) }) { Text("Klaim reward") }
                            else -> Text("Belum terbuka")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Stats(vm: AppViewModel) {
    val missions by vm.missions.collectAsState(emptyList())
    val points by vm.points.collectAsState(0)
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Statistik") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Poin: $points", style = MaterialTheme.typography.headlineSmall)
            Text("Total completion: ${missions.sumOf { it.completionCount }}")
            Text("Misi aktif: ${missions.count { it.status == "ACTIVE" }}")
            Text("Misi dijeda: ${missions.count { it.status == "PAUSED" }}")
            Text("Misi diarsipkan: ${missions.count { it.status == "ARCHIVED" }}")
            Text("Statistik dibuat untuk membantu melihat progres, bukan menghakimi.")
        }
    }
}

@Composable
fun History(vm: AppViewModel) {
    val events by vm.events.collectAsState(emptyList())
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("History") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.id }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(event.title, fontWeight = FontWeight.Medium)
                        Text(event.detail)
                        Text(event.type, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(profile: ProfileEntity?, vm: AppViewModel) {
    var name by remember(profile?.name) { mutableStateOf(profile?.name.orEmpty()) }
    var age by remember(profile?.age) { mutableStateOf(profile?.age?.toString().orEmpty()) }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Profil") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Nama") }, singleLine = true)
            OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("Usia") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Dibuat: ${profile?.createdAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            Button(enabled = name.isNotBlank() && (age.toIntOrNull() ?: 0) in 1..120, onClick = { vm.saveProfile(name, age.toInt()) }, modifier = Modifier.fillMaxWidth()) { Text("Simpan perubahan") }
        }
    }
}

@Composable
fun CalendarScreen(vm: AppViewModel) {
    val missions by vm.missions.collectAsState(emptyList())
    val planned = missions.filter { it.scheduledAt != null && it.status != "ARCHIVED" }.sortedBy { it.scheduledAt }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Kalender") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (planned.isEmpty()) {
                item { Text("Belum ada aktivitas terjadwal.") }
            } else {
                items(planned, key = { it.id }) { mission ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(mission.title, fontWeight = FontWeight.Medium)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(mission.scheduledAt!!)))
                            Text("${mission.status} · ${mission.repeatRule ?: "sekali"}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, db: ZahraDatabase, settings: SettingsStore, onReset: () -> Unit) {
    val scope = rememberCoroutineScope()
    val notifications by settings.notifications.collectAsState(false)
    val haptics by settings.haptics.collectAsState(true)
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val backupRepo = remember { BackupRepository(db) }

    val requestNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotifications(granted)
        status = if (granted) "Notifikasi aktif." else "Notifikasi tetap mati karena permission ditolak."
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    require(password.length >= 8) { "Password minimal 8 karakter" }
                    require(password == confirm) { "Konfirmasi password tidak sama" }
                    val plain = withContext(Dispatchers.IO) { backupRepo.exportPlaintext() }
                    val encrypted = withContext(Dispatchers.Default) { PortableBackupCrypto.encrypt(plain, password.toCharArray()) }
                    context.contentResolver.openOutputStream(uri)?.use { it.write(encrypted) } ?: error("File tidak dapat ditulis")
                    status = "Backup berhasil dibuat."
                } catch (t: Throwable) {
                    status = t.message ?: "Backup gagal"
                }
            }
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    require(password.length >= 8) { "Password minimal 8 karakter" }
                    val blob = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("File tidak dapat dibaca")
                    }
                    val plain = withContext(Dispatchers.Default) { PortableBackupCrypto.decrypt(blob, password.toCharArray()) }
                    withContext(Dispatchers.IO) { backupRepo.importPlaintext(plain) }
                    vm.seedRewards()
                    vm.refreshReminders()
                    status = "Backup berhasil dipulihkan."
                } catch (t: Throwable) {
                    status = t.message ?: "Restore gagal"
                }
            }
        }
    }

    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset aplikasi?") },
            text = { Text("Semua data lokal, misi, poin, reward, proof, dan history akan dihapus. Buat backup terlebih dahulu bila masih dibutuhkan.") },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    vm.resetApplication(onDone = onReset)
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal") } }
        )
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Pengaturan") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Notifikasi", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aktifkan reminder")
                    Switch(
                        checked = notifications,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                vm.setNotifications(false)
                            } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setNotifications(true)
                            }
                        }
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Haptic")
                    Switch(checked = haptics, onCheckedChange = { enabled -> scope.launch { settings.setHaptics(enabled) } })
                }
            }
            item {
                Text("Portable backup", style = MaterialTheme.typography.titleLarge)
                Text("Backup terenkripsi dapat dipindahkan ke perangkat lain menggunakan password yang sama.", style = MaterialTheme.typography.bodySmall)
            }
            item { OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password backup") }, singleLine = true) }
            item { OutlinedTextField(confirm, { confirm = it }, Modifier.fillMaxWidth(), label = { Text("Konfirmasi password") }, singleLine = true) }
            item {
                Button(
                    onClick = { create.launch("zahra-${System.currentTimeMillis()}.backup") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.length >= 8 && password == confirm
                ) { Text("Export backup") }
            }
            item { OutlinedButton(
                onClick = { open.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = password.length >= 8
            ) { Text("Import backup") } }
            status?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) } }
            item {
                Text("Reset lokal", style = MaterialTheme.typography.titleLarge)
                Text("Gunakan hanya untuk menghapus seluruh data aplikasi di perangkat ini.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset aplikasi") }
            }
            item { Text("Import backup menggantikan data aplikasi saat ini setelah file lolos validasi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
