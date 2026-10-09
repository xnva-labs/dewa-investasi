package id.fajar.zahra.prayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import id.fajar.zahra.prayer.PrayerAlarmWorker.Companion.cancel
import id.fajar.zahra.prayer.PrayerAlarmWorker.Companion.schedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val android.content.Context.prayerReminderStore by preferencesDataStore(name = "zahra_prayer_reminders")
private val prayerReminderEnabledKey = booleanPreferencesKey("obligatory_prayer_enabled")
private val fastingReminderEnabledKey = booleanPreferencesKey("fasting_alarm_enabled")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> status = if (granted) "Izin aktif. Tekan Simpan pengingat sekali lagi untuk menjadwalkan alarm." else "Izin notifikasi belum diberikan; pengingat tidak dapat muncul." }
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf<PrayerTimesRepository.DayTimes?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var prayerReminders by remember { mutableStateOf(true) }
    var fastingReminders by remember { mutableStateOf(false) }
    fun refresh() {
        scope.launch {
            busy = true; error = null
            runCatching { PrayerTimesRepository.fetchCirebon() }
                .onSuccess { day = it }
                .onFailure { error = "Jadwal belum bisa dimuat. Periksa koneksi internet lalu coba lagi." }
            busy = false
        }
    }
    LaunchedEffect(Unit) {
        // Keep the user's choices when the screen is reopened or the process is recreated.
        runCatching { context.prayerReminderStore.data.first() }.onSuccess { preferences ->
            prayerReminders = preferences[prayerReminderEnabledKey] ?: true
            fastingReminders = preferences[fastingReminderEnabledKey] ?: false
        }
        refresh()
    }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Waktu Sholat") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFEAF3EC)), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Cirebon, Jawa Barat", style = MaterialTheme.typography.titleLarge)
                        Text(day?.dateLabel ?: "Jadwal harian", style = MaterialTheme.typography.bodyMedium)
                        Text("Jam lokal perangkat · metode perhitungan 20 (Kemenag Indonesia)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (busy) item { Text("Mengambil jadwal terbaru…") }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); Button(onClick = { refresh() }) { Text("Coba lagi") } } }
            day?.prayers?.forEach { prayer -> item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(prayer.name, style = MaterialTheme.typography.titleMedium)
                        Text(prayer.time, style = MaterialTheme.typography.titleMedium)
                    }
                }
            } }
            item {
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pengingat suara lembut", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text("Sholat wajib"); Text("Subuh, Dzuhur, Ashar, Maghrib, Isya", style = MaterialTheme.typography.bodySmall) }
                            Switch(checked = prayerReminders, onCheckedChange = { enabled ->
                                prayerReminders = enabled
                                scope.launch { context.prayerReminderStore.edit { it[prayerReminderEnabledKey] = enabled } }
                            })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text("Alarm puasa"); Text("Pengingat sahur/imsak dan berbuka saat Maghrib", style = MaterialTheme.typography.bodySmall) }
                            Switch(checked = fastingReminders, onCheckedChange = { enabled ->
                                fastingReminders = enabled
                                scope.launch { context.prayerReminderStore.edit { it[fastingReminderEnabledKey] = enabled } }
                            })
                        }
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS); return@Button }
                            val scheduleDay = day
                            if (scheduleDay == null) { status = "Muat jadwal dulu sebelum mengaktifkan pengingat."; return@Button }
                            var count = 0
                            val prayerIds = listOf("Waktu sholat Subuh", "Waktu sholat Dzuhur", "Waktu sholat Ashar", "Waktu sholat Maghrib", "Waktu sholat Isya")
                            if (!prayerReminders) prayerIds.forEach { cancel(context, "cirebon-$it".hashCode()) }
                            if (!fastingReminders) listOf("Alarm sahur", "Waktunya berbuka").forEach { cancel(context, "cirebon-$it".hashCode()) }
                            fun addReminder(name: String, key: String, time: String, original: Long, body: String, leadMinutes: Int = 0) {
                                val alarmTime = original - leadMinutes * 60_000L
                                val adjustedTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta") }.format(java.util.Date(alarmTime))
                                val at = if (alarmTime > System.currentTimeMillis()) alarmTime else alarmTime + 24L * 60L * 60L * 1000L
                                schedule(context, ("cirebon-$name".hashCode()), name, body, adjustedTime, at, key, leadMinutes); count++
                            }
                            if (prayerReminders) scheduleDay.prayers.forEach { prayer ->
                                val key = when (prayer.name) { "Subuh" -> "fajr"; "Dzuhur" -> "dhuhr"; "Ashar" -> "asr"; "Maghrib" -> "maghrib"; else -> "isha" }
                                addReminder("Waktu sholat ${prayer.name}", key, prayer.time, prayer.epochMillis, "Waktunya sholat. Semoga ibadahmu dimudahkan.")
                            }
                            if (fastingReminders) {
                                addReminder("Alarm sahur", "imsak", scheduleDay.imsak.time, scheduleDay.imsak.epochMillis, "Sebentar lagi imsak. Semoga puasamu dimudahkan.", leadMinutes = 30)
                                addReminder("Waktunya berbuka", "maghrib", scheduleDay.maghrib.time, scheduleDay.maghrib.epochMillis, "Waktunya berbuka puasa. Alhamdulillah, semoga berkah.")
                            }
                            status = if (count == 0) "Pilih minimal satu jenis pengingat." else "$count pengingat diaktifkan. Pengiriman bisa sedikit terlambat karena optimasi baterai Android."
                        }, modifier = Modifier.fillMaxWidth()) { Text("Simpan pengingat") }
                        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Text("Jadwal dihitung ulang dari layanan AlAdhan. Cocokkan dengan jadwal masjid setempat; metode dan lokasi dapat memengaruhi selisih beberapa menit.", style = MaterialTheme.typography.bodySmall) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
