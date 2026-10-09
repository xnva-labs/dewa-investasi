@file:OptIn(ExperimentalMaterial3Api::class)

package id.fajar.zahra

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.animateContentSize
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.fajar.zahra.backup.BackupRepository
import id.fajar.zahra.backup.PortableBackupCrypto
import id.fajar.zahra.camera.CameraProofScreen
import id.fajar.zahra.core.RepeatRules
import id.fajar.zahra.core.MissionDifficultyEstimator
import id.fajar.zahra.core.ProgressionRules
import id.fajar.zahra.data.AppRepository
import id.fajar.zahra.data.MissionEntity
import id.fajar.zahra.data.ProfileEntity
import id.fajar.zahra.data.ZahraDatabase
import id.fajar.zahra.data.YearlyProgressEntity
import id.fajar.zahra.settings.SettingsStore
import id.fajar.zahra.islamic.IslamicContentRepository
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlinx.coroutines.withContext

private val Primary = Color(0xFF477A65)
private val Secondary = Color(0xFF8174A8)
private val Teal = Color(0xFF4F8D91)
private val Background = Color(0xFFF5F8F5)
private val CardWhite = Color(0xFFFFFFFF)

private fun waterRewardForDifficulty(difficulty: Int): Int = ProgressionRules.waterForDifficulty(difficulty)

private val LEAF_MESSAGES = listOf(
    "Aku suka caramu tetap berusaha, bahkan saat langkahmu pelan.",
    "Ada seseorang yang diam-diam berharap harimu terasa lebih ringan.",
    "Semoga Allah menjaga hati dan langkahmu. Aku ikut senang melihatmu tumbuh.",
    "Kalau hari ini berat, kamu tetap pantas mendapat kelembutan.",
    "Hal kecil yang kamu lakukan bisa membuat dunia seseorang lebih hangat.",
    "Ada doa sederhana yang kuselipkan: semoga kamu selalu diberi ketenangan.",
    "Aku suka melihat kebaikan tumbuh dalam dirimu, sedikit demi sedikit.",
    "Daun ini jatuh, tapi harapan baikku untukmu tetap tinggal."
)

class MainActivity : ComponentActivity() {
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
                    secondary = Secondary,
                    tertiary = Teal,
                    background = Background,
                    surface = CardWhite,
                    onPrimary = Color.White,
                    onBackground = Color(0xFF24352D),
                    onSurface = Color(0xFF24352D)
                )
            ) {
                ZahraApp(
                    vm = vm,
                    db = db,
                    settings = settingsStore,
                )
            }
        }
    }
}

@Composable
fun ZahraApp(
    vm: AppViewModel,
    db: ZahraDatabase,
    settings: SettingsStore
) {
    val profile by vm.profile.collectAsState(initial = null)
    val nav = rememberNavController()
    // Tujuan awal ditentukan sekali dari database supaya pengguna lama tidak melihat layar sambutan sekilas.
    val startDestination by produceState<String?>(initialValue = null) {
        value = if (vm.profile.first() == null) "welcome" else "dashboard"
    }

    LaunchedEffect(profile?.id) {
        if (profile != null) {
            vm.ensureDefaultMissions()
            vm.seedRewards()
            vm.refreshReminders()
        }
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
            Dashboard(profile?.name.orEmpty(), vm) { nav.navigate(it) }
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
        composable("islamic-content") { IslamicContentScreen() }
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
            Text("Ibadah, aktivitas harian, dan Mimi si kucing—dalam satu ruang yang tenang.")
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
fun Dashboard(name: String, vm: AppViewModel, go: (String) -> Unit) {
    val missions by vm.missions.collectAsState(emptyList())
    val points by vm.points.collectAsState(0)
    val done by vm.completedCount.collectAsState(0)
    val actionMessage by vm.actionMessage.collectAsState(null)
    val annualRecords by vm.yearlyProgress.collectAsState(emptyList())
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val annual = annualRecords.firstOrNull { it.year == year } ?: YearlyProgressEntity(year = year)
    val mimiMotion = rememberInfiniteTransition(label = "mimi-motion")
    val mimiOffset by mimiMotion.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800), repeatMode = RepeatMode.Reverse),
        label = "mimi-float"
    )
    val active = missions.filter { it.status == "ACTIVE" }
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val completedToday = missions.count { (it.completedAt ?: 0L) >= todayStart }
    val progress = if (missions.isEmpty()) 0f else (completedToday.toFloat() / (completedToday + active.size).coerceAtLeast(1)).coerceIn(0f, 1f)

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("dashboard" to "Beranda", "missions" to "Misi", "stats" to "Progres", "profile" to "Profil").forEach { (route, label) ->
                    NavigationBarItem(
                        selected = route == "dashboard",
                        onClick = { if (route != "dashboard") go(route) },
                        icon = { Text(when (route) { "dashboard" -> "⌂"; "missions" -> "✓"; "stats" -> "↗"; else -> "○" }) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ZAHRA · DAILY COMPANION", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        Text("Hai, ${name.ifBlank { "teman" }}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Langkah kecil, hari yang lebih bermakna.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Surface(shape = RoundedCornerShape(18.dp), color = Primary.copy(alpha = .12f)) {
                        Text("✿", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.headlineSmall, color = Primary)
                    }
                }
                actionMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
            item {
                ElevatedCard(
                    Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(28.dp),
                    colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = Color(0xFFEAF3EC))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFD6E7DB), modifier = Modifier.padding(2.dp).offset(y = mimiOffset.dp)) {
                            Text("🐈", modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp), style = MaterialTheme.typography.displaySmall)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("TEMAN HARIANMU", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                            Text("Mimi si kucing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    annual.level >= 8 -> "Mimi bangga kamu terus tumbuh. Jangan lupa beristirahat juga."
                                    annual.level >= 3 -> "Mimi mulai akrab. Setiap misi memberi pakan dan membuat kebunmu bertumbuh."
                                    else -> "Mimi siap menemani langkah pertamamu. Kita tumbuh pelan-pelan, ya."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("Pakan Mimi: ${annual.catFood}", style = MaterialTheme.typography.labelMedium, color = Primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { vm.feedCat() }, enabled = annual.catFood > 0, shape = RoundedCornerShape(14.dp)) { Text("Kasih makan") }
                                TextButton(onClick = { go("missions") }) { Text("Misi hari ini →") }
                            }
                        }
                    }
                }
            }
            item { GardenProgressCard(annual, hasCurrentYearRecord = annualRecords.any { it.year == year }) }
            item {
                ElevatedCard(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Level perjalanan $year", fontWeight = FontWeight.Bold)
                            Text("LV. ${annual.level}", color = Primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        val inLevel = ProgressionRules.experienceIntoLevel(annual.experience)
                        val nextLevel = ProgressionRules.experienceToNextLevel(annual.level)
                        LinearProgressIndicator(progress = { (inLevel.toFloat() / nextLevel).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("$inLevel / $nextLevel EXP · ${annual.experience} EXP tahun ini", style = MaterialTheme.typography.bodySmall)
                        Text("Level setiap tahun disimpan terpisah. Progres tahun sebelumnya tetap ada.", style = MaterialTheme.typography.bodySmall, color = Secondary)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElevatedCard(Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✦", color = Primary)
                            Text(annual.water.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Tetes air", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    ElevatedCard(Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✓", color = Primary)
                            Text(done.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Misi selesai", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ringkasan aktivitas", fontWeight = FontWeight.SemiBold)
                            Text("${active.size} aktif", color = Primary, style = MaterialTheme.typography.labelLarge)
                        }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(if (missions.isEmpty()) "Mulai dengan menambahkan satu misi kecil." else "${(progress * 100).toInt()}% dari daftar misi berstatus selesai.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Akses cepat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { go("missions") }) { Text("Semua misi") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { go("islamic-content") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("☾  Hadis & Doa") }
                    OutlinedButton(onClick = { go("calendar") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("▦  Kalender") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { go("lists") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("☷  Checklist") }
                    OutlinedButton(onClick = { go("rewards") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("◇  Reward") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { go("history") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("◷  Riwayat") }
                    OutlinedButton(onClick = { go("stats") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("↗  Statistik") }
                }
            }
            item { Text("Misi aktif", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (active.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Belum ada misi aktif", fontWeight = FontWeight.SemiBold)
                            Text("Tambahkan rutinitas sederhana supaya kamu punya langkah pertama hari ini.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { go("missions") }) { Text("Buat atau pilih misi") }
                        }
                    }
                }
            } else {
                items(active.take(5), key = { it.id }) { mission ->
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
            }
            item { OutlinedButton(onClick = { go("camera") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Buka kamera bukti") } }
            item { TextButton(onClick = { go("settings") }, modifier = Modifier.fillMaxWidth()) { Text("Pengaturan, backup, dan privasi") } }
        }
    }
}

@Composable
private fun GardenProgressCard(progress: YearlyProgressEntity, hasCurrentYearRecord: Boolean) {
    val plantStage = ProgressionRules.plantStage(progress.water)
    var showFallingLeaf by remember { mutableStateOf(false) }
    var hasInitializedLeaves by remember { mutableStateOf(false) }
    var previousLeafDrops by remember { mutableStateOf(progress.leafDrops) }
    var displayedLeafMessageIndex by remember { mutableStateOf(progress.lastLeafMessageIndex) }
    var leafHadith by remember { mutableStateOf<IslamicContentRepository.TextItem?>(null) }
    var leafHadithLoading by remember { mutableStateOf(false) }
    var leafHadithError by remember { mutableStateOf(false) }
    var leafHadithRetryToken by remember { mutableStateOf(0) }
    LaunchedEffect(progress.leafDrops, hasCurrentYearRecord, leafHadithRetryToken) {
        if (hasCurrentYearRecord && progress.leafDrops > 0) {
            leafHadithLoading = true
            leafHadithError = false
            leafHadith = null
            try {
                val hadithNumber = ((progress.leafDrops - 1) % 1_000) + 1
                leafHadith = IslamicContentRepository.getHadith("bukhari", hadithNumber)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                leafHadithError = true
            } finally {
                leafHadithLoading = false
            }
        }
    }
    LaunchedEffect(progress.leafDrops, hasCurrentYearRecord) {
        if (!hasCurrentYearRecord) return@LaunchedEffect
        if (!hasInitializedLeaves) {
            previousLeafDrops = progress.leafDrops
            displayedLeafMessageIndex = progress.lastLeafMessageIndex
            hasInitializedLeaves = true
            return@LaunchedEffect
        }
        val newLeaves = (progress.leafDrops - previousLeafDrops).coerceAtLeast(0).coerceAtMost(3)
        if (newLeaves > 0) {
            for (leafNumber in (previousLeafDrops + 1)..(previousLeafDrops + newLeaves)) {
                displayedLeafMessageIndex = ProgressionRules.secretMessageIndexForLeaf(leafNumber, LEAF_MESSAGES.size)
                showFallingLeaf = true
                delay(900)
                showFallingLeaf = false
                delay(300)
            }
        } else {
            displayedLeafMessageIndex = progress.lastLeafMessageIndex
        }
        previousLeafDrops = progress.leafDrops
        displayedLeafMessageIndex = progress.lastLeafMessageIndex
    }
    val leafOffset by animateFloatAsState(
        targetValue = if (showFallingLeaf) 28f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "leaf-fall-offset"
    )
    val leafAlpha by animateFloatAsState(
        targetValue = if (showFallingLeaf) 0f else 1f,
        animationSpec = tween(durationMillis = 900),
        label = "leaf-fall-alpha"
    )
    val growth by animateFloatAsState(
        targetValue = (plantStage / 6f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 850),
        label = "plant-growth"
    )
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF4F1E9))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Kebun kecil Zahra", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Misi yang selesai menyiram benih ini.", style = MaterialTheme.typography.bodySmall)
                }
                Text("💧 ${progress.water}", color = Primary, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.padding(2.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxWidth(.40f).height(124.dp)) {
                        val centerX = size.width / 2f
                        val groundY = size.height * .87f
                        val stemHeight = size.height * .68f * growth
                        drawLine(
                            color = Color(0xFF4B8063),
                            start = Offset(centerX, groundY),
                            end = Offset(centerX, groundY - stemHeight),
                            strokeWidth = 4.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawOval(
                            color = Color(0xFFB78C65),
                            topLeft = Offset(centerX - 29.dp.toPx(), groundY - 3.dp.toPx()),
                            size = Size(58.dp.toPx(), 12.dp.toPx())
                        )
                        val leafPairs = plantStage.coerceIn(0, 6)
                        for (index in 0 until leafPairs) {
                            val y = groundY - stemHeight * (0.22f + index * 0.125f)
                            val side = if (index % 2 == 0) -1f else 1f
                            val leafColor = if (index % 2 == 0) Color(0xFF6B9C79) else Color(0xFF8FB797)
                            rotate(degrees = side * 28f, pivot = Offset(centerX, y)) {
                                drawOval(
                                    color = leafColor,
                                    topLeft = Offset(centerX + side * 2.dp.toPx(), y - 8.dp.toPx()),
                                    size = Size(29.dp.toPx(), 15.dp.toPx())
                                )
                            }
                        }
                        if (plantStage == 0) {
                            drawOval(color = Color(0xFF9E7754), topLeft = Offset(centerX - 6.dp.toPx(), groundY - 14.dp.toPx()), size = Size(12.dp.toPx(), 15.dp.toPx()))
                        }
                        if (plantStage >= 5) {
                            drawCircle(color = Color(0xFFE8C76B), radius = 5.dp.toPx(), center = Offset(centerX, groundY - stemHeight - 2.dp.toPx()))
                        }
                    }
                    if (showFallingLeaf) {
                        Text("🍃", Modifier.align(Alignment.TopEnd).offset(y = leafOffset.dp).alpha(leafAlpha), fontSize = 19.sp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        when (plantStage) {
                            0 -> "Benih sedang menunggu tetes pertamanya."
                            1 -> "Tunas kecil mulai tumbuh."
                            2 -> "Daun pertama muncul. Teruskan pelan-pelan."
                            3 -> "Batang mulai meninggi dan daun bertambah."
                            4 -> "Kebun kecilmu makin rimbun."
                            5 -> "Ada bunga kecil dari konsistensimu."
                            else -> "Tanamanmu tumbuh kuat dari langkah yang terkumpul."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text("Tahap tanaman $plantStage/6 · ${progress.leafDrops} daun jatuh", style = MaterialTheme.typography.labelSmall, color = Secondary)
                    LinearProgressIndicator(progress = { ProgressionRules.plantStageProgress(progress.water) }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (progress.leafDrops > 0) {
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("HADIS DI BALIK DAUN", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                    when {
                        leafHadithLoading -> Text("Mengambil hadis untuk momen ini…", style = MaterialTheme.typography.bodySmall)
                        leafHadith != null -> {
                            leafHadith?.let { entry ->
                                if (entry.arabic.isNotBlank()) {
                                    Text(entry.arabic, Modifier.fillMaxWidth(), fontSize = 20.sp, lineHeight = 33.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                }
                                if (entry.translation.isNotBlank()) Text(entry.translation, style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp)
                                Text("${entry.source} · ${entry.reference}", style = MaterialTheme.typography.labelSmall, color = Secondary)
                            }
                        }
                        leafHadithError -> {
                            Text("Hadis belum termuat. Periksa koneksi internet.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { leafHadithRetryToken += 1 }) { Text("Coba lagi") }
                        }
                    }
                    Text("Rujukan berasal dari sumber daring; cek kembali untuk kajian mendalam.", style = MaterialTheme.typography.labelSmall, color = Secondary)
                }
            }
            AnimatedVisibility(
                visible = progress.leafDrops > 0 && displayedLeafMessageIndex in LEAF_MESSAGES.indices,
                enter = fadeIn(tween(450)) + scaleIn(tween(450)),
                exit = fadeOut(tween(250))
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("PESAN KECIL DARI DAUN", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                    Text(LEAF_MESSAGES[displayedLeafMessageIndex], style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Catatan pribadi, bukan hadis atau kutipan agama.", style = MaterialTheme.typography.labelSmall, color = Secondary)
                }
            }
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
            item {
                Text("Misi wajib dan sunnah dasar sudah disiapkan otomatis. Tambahkan aktivitas harianmu sendiri di bawah.", style = MaterialTheme.typography.bodySmall)
            }
            item { OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Nama misi") }, singleLine = true) }
            item { OutlinedTextField(description, { description = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Deskripsi (opsional)") }, minLines = 2) }
            item { OutlinedTextField(category, { category = it.take(50) }, Modifier.fillMaxWidth(), label = { Text("Kategori") }, singleLine = true) }
            item {
                val estimate = MissionDifficultyEstimator.estimate(title, description, category)
                val band = MissionDifficultyEstimator.band(estimate)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF0F5F1))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Estimasi aktivitas otomatis", style = MaterialTheme.typography.labelMedium, color = Primary)
                        Text("${band.label} · +${waterRewardForDifficulty(estimate)} tetes air · +${ProgressionRules.catFoodFor(estimate)} pakan Mimi", fontWeight = FontWeight.SemiBold)
                        Text("Perkiraan lokal dari judul dan deskripsi. Kamu tetap bisa mengubah aktivitas kapan saja.", style = MaterialTheme.typography.bodySmall)
                    }
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
                    listOf(RepeatRules.NONE to "Sekali", RepeatRules.DAILY to "Harian", RepeatRules.WEEKLY to "Mingguan", RepeatRules.MONTHLY to "Bulanan", RepeatRules.DAWUD to "Selang-seling (Daud)").forEach { (value, label) ->
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
                            waterRewardForDifficulty(MissionDifficultyEstimator.estimate(title, description, category)),
                            MissionDifficultyEstimator.estimate(title, description, category),
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
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tambah misi") }
            }
            item {
                Text("Reward air adalah progres aplikasi, bukan ukuran pahala. Pilih jumlah yang terasa wajar dan bisa kamu jalani.", style = MaterialTheme.typography.bodySmall)
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
            item {
                val estimate = MissionDifficultyEstimator.estimate(title, description, category)
                Text("Estimasi: ${MissionDifficultyEstimator.band(estimate).label} · +${waterRewardForDifficulty(estimate)} tetes air · +${ProgressionRules.catFoodFor(estimate)} pakan Mimi", style = MaterialTheme.typography.bodySmall, color = Primary)
            }
            item { Text("Jenis bukti", style=MaterialTheme.typography.titleSmall) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("NONE" to "Tanpa","PHOTO" to "Foto","POSE" to "Pose","OBJECT" to "Objek").forEach { (v,l)-> FilterChip(selected=proof==v,onClick={proof=v},label={Text(l)}) } } }
            if (proof == "OBJECT") item { OutlinedTextField(target,{target=it.take(100)},Modifier.fillMaxWidth(),label={Text("Target objek")},singleLine=true) }
            item { Text("Pengulangan", style=MaterialTheme.typography.titleSmall) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(RepeatRules.NONE to "Sekali",RepeatRules.DAILY to "Harian",RepeatRules.WEEKLY to "Mingguan",RepeatRules.MONTHLY to "Bulanan",RepeatRules.DAWUD to "Selang-seling (Daud)").forEach{(v,l)->FilterChip(selected=repeat==v,onClick={repeat=v},label={Text(l)})} } }
            item { OutlinedTextField(scheduleText,{scheduleText=it.take(16);error=null},Modifier.fillMaxWidth(),label={Text("Jadwal YYYY-MM-DD HH:mm atau kosong")},singleLine=true) }
            error?.let { item { Text(it, color=MaterialTheme.colorScheme.error) } }
            item { Button(enabled=title.isNotBlank() && (proof!="OBJECT" || target.isNotBlank()), modifier=Modifier.fillMaxWidth(), onClick={
                val parser=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).apply{isLenient=false}
                val schedule=scheduleText.trim().takeIf{it.isNotBlank()}?.let{raw->runCatching{parser.parse(raw)?.time}.getOrNull()}
                if (scheduleText.isNotBlank() && (schedule==null || schedule < System.currentTimeMillis())) { error="Jadwal tidak valid atau sudah lewat."; return@Button }
                val estimate = MissionDifficultyEstimator.estimate(title, description, category)
                vm.updateMission(mission,title,description,category,waterRewardForDifficulty(estimate),estimate,proof,target,schedule,repeat)
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
    ElevatedCard(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("${m.category} · +${ProgressionRules.waterForDifficulty(m.difficulty)} tetes air · +${m.points} poin · ${when (m.status) { "ACTIVE" -> "Aktif"; "PAUSED" -> "Dijeda"; "DONE" -> "Selesai"; else -> m.status }}", style = MaterialTheme.typography.labelSmall, color = Secondary)
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
                Text("Progres checklist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("$checked dari ${itemsState.size} selesai", style = MaterialTheme.typography.bodyMedium, color = Primary)
                LinearProgressIndicator(progress = { if (itemsState.isEmpty()) 0f else checked.toFloat() / itemsState.size }, modifier = Modifier.fillMaxWidth())
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
                Card(
                    Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(18.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (item.checked) Color(0xFFEAF3EC) else CardWhite)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = item.checked, onCheckedChange = { vm.setListItemChecked(item.id, it) })
                        Text(item.title, Modifier.weight(1f), fontWeight = if (item.checked) FontWeight.Normal else FontWeight.Medium, color = if (item.checked) Color(0xFF65756B) else MaterialTheme.colorScheme.onSurface)
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
                Text("Tetes air terkumpul: $points", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Tetes air adalah sistem progres Zahra, bukan ukuran pahala atau nilai ibadah.", style = MaterialTheme.typography.bodySmall)
            }
            items(rewards, key = { it.id }) { reward ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        val rewardTitle = when (reward.id) {
                            1L -> "Tema lembut"
                            2L -> "Momen istirahat bersama Mimi"
                            3L -> "Kartu apresiasi diri"
                            4L -> "Lencana konsistensi"
                            else -> reward.title
                        }
                        val rewardDescription = when (reward.id) {
                            1L -> "Milestone untuk menjaga ruang Zahra tetap nyaman."
                            2L -> "Pengingat untuk berhenti sejenak dan menyayangi diri sendiri."
                            3L -> "Catatan kecil untuk merayakan usaha yang sudah dilakukan."
                            4L -> "Tanda progres dari kebiasaan yang kamu bangun."
                            else -> reward.description
                        }
                        Text(rewardTitle, fontWeight = FontWeight.Medium)
                        Text(rewardDescription)
                        Text("Target ${reward.threshold} tetes air")
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
    val annualRecords by vm.yearlyProgress.collectAsState(emptyList())
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Statistik") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val currentAnnual = annualRecords.firstOrNull { it.year == currentYear }
            Text("Tetes air tahun ini: ${currentAnnual?.water ?: 0}", style = MaterialTheme.typography.headlineSmall)
            Text("Poin reward terkumpul: $points", style = MaterialTheme.typography.bodyMedium)
            Text("Total completion: ${missions.sumOf { it.completionCount }}")
            Text("Misi aktif: ${missions.count { it.status == "ACTIVE" }}")
            Text("Misi dijeda: ${missions.count { it.status == "PAUSED" }}")
            Text("Misi diarsipkan: ${missions.count { it.status == "ARCHIVED" }}")
            Text("Statistik dibuat untuk membantu melihat progres, bukan menghakimi.")
            Spacer(Modifier.height(8.dp))
            Text("Riwayat level tahunan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            annualRecords.forEach { record ->
                Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${record.year} · Level ${record.level}", fontWeight = FontWeight.SemiBold)
                        Text("${record.experience} EXP · ${record.water} tetes air · ${record.leafDrops} daun", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
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
