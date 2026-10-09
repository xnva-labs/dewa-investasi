package id.fajar.zahra.menstruation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.menstruationStore by preferencesDataStore(name = "private_cycle_notes")
private val START_KEY = stringPreferencesKey("last_period_start")
private val CYCLE_KEY = stringPreferencesKey("cycle_length_days")
private val DURATION_KEY = stringPreferencesKey("period_duration_days")
private val REMINDER_KEY = booleanPreferencesKey("reminder_enabled")
private const val UNIQUE_WORK = "private-cycle-approaching-reminder"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenstruationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var startDate by remember { mutableStateOf("") }
    var cycleLength by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var reminderEnabled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) {
            "Izin notifikasi diberikan. Tekan Simpan untuk menjadwalkan pengingat."
        } else {
            reminderEnabled = false
            scope.launch { context.menstruationStore.edit { it[REMINDER_KEY] = false } }
            "Izin notifikasi tidak diberikan; pengingat dimatikan."
        }
    }

    LaunchedEffect(Unit) {
        val values = context.menstruationStore.data.first()
        startDate = runCatching { PrivateCycleCipher.decrypt(values[START_KEY].orEmpty()) }.getOrDefault("")
        cycleLength = runCatching { PrivateCycleCipher.decrypt(values[CYCLE_KEY].orEmpty()) }.getOrDefault("")
        duration = runCatching { PrivateCycleCipher.decrypt(values[DURATION_KEY].orEmpty()) }.getOrDefault("")
        reminderEnabled = values[REMINDER_KEY] ?: false
    }

    fun scheduleReminder(start: String, cycle: Int) {
        val reminderAt = CyclePrediction.reminderEpochMillis(start, cycle)
        val delay = (reminderAt - System.currentTimeMillis()).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
        val request = OneTimeWorkRequestBuilder<MenstruationReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putInt("cycleLength", cycle).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Kalender haid pribadi") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Catatan opsional", style = MaterialTheme.typography.titleLarge)
                        Text("Semua kolom awalnya kosong. Isi hanya kalau kamu mau. Catatan disimpan lokal dan dienkripsi dengan AES-GCM memakai kunci Android Keystore; tidak dikirim ke layanan jadwal sholat atau hadis. Data lama akan dienkripsi saat kamu menyimpannya lagi.")
                    }
                }
            }
            item { OutlinedTextField(startDate, { startDate = it.take(10) }, label = { Text("Hari pertama haid terakhir (YYYY-MM-DD)") }, placeholder = { Text("2026-10-01") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(cycleLength, { cycleLength = it.filter(Char::isDigit).take(2) }, label = { Text("Panjang siklus biasanya (hari)") }, placeholder = { Text("Opsional, mis. 28") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(2) }, label = { Text("Lama haid biasanya (hari)") }, placeholder = { Text("Opsional") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pengingat mendekati perkiraan", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text("Notifikasi privat"); Text("Satu pengingat sekitar 3 hari sebelumnya, pukul 09.00.", style = MaterialTheme.typography.bodySmall) }
                            Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                        }
                    }
                }
            }
            item {
                Button(onClick = {
                    scope.launch {
                        try {
                            val cycle = cycleLength.toIntOrNull()
                            val periodDuration = duration.toIntOrNull()
                            if (startDate.isNotBlank()) {
                                val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                                require(format.format(format.parse(startDate) ?: error("Tanggal tidak valid")) == startDate) { "Tanggal tidak valid. Gunakan YYYY-MM-DD." }
                            }
                            require(cycleLength.isBlank() || (cycle != null && cycle in 15..60)) { "Panjang siklus harus 15–60 hari." }
                            require(duration.isBlank() || (periodDuration != null && periodDuration in 1..15)) { "Lama haid harus 1–15 hari." }
                            require(!reminderEnabled || (startDate.isNotBlank() && cycle != null)) { "Isi tanggal terakhir dan panjang siklus untuk memakai pengingat." }
                            context.menstruationStore.edit { prefs ->
                                prefs[START_KEY] = PrivateCycleCipher.encrypt(startDate.trim())
                                prefs[CYCLE_KEY] = PrivateCycleCipher.encrypt(cycleLength.trim())
                                prefs[DURATION_KEY] = PrivateCycleCipher.encrypt(duration.trim())
                                prefs[REMINDER_KEY] = reminderEnabled
                            }
                            if (reminderEnabled) {
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    message = "Berikan izin notifikasi lalu tekan Simpan lagi."
                                } else {
                                    scheduleReminder(startDate.trim(), cycle!!)
                                    message = "Catatan tersimpan. Pengingat perkiraan berikutnya dijadwalkan."
                                }
                            } else {
                                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
                                message = "Catatan tersimpan. Pengingat dimatikan."
                            }
                        } catch (error: Exception) {
                            message = error.message ?: "Catatan belum bisa disimpan. Periksa isianmu."
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Simpan catatan") }
            }
            message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) } }
            item {
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Catatan agama", style = MaterialTheme.typography.titleMedium)
                        Text("Perkiraan kalender hanya alat bantu, bukan penentu hukum haid, suci, qadha, atau ibadah. Ketentuan fikih memiliki rincian dan perbedaan pendapat; rujuk ustazah/ulama tepercaya untuk keadaan pribadi.")
                        Text("Siklus dapat berubah dan perkiraan bisa meleset. Jangan gunakan sebagai alat diagnosis atau kontrasepsi.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
