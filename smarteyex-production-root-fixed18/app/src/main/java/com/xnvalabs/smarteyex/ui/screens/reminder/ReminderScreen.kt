package com.xnvalabs.smarteyex.ui.screens.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xnvalabs.smarteyex.data.companion.VoiceCommandRouter
import com.xnvalabs.smarteyex.data.reminder.ReminderEntry
import com.xnvalabs.smarteyex.data.reminder.ReminderRepository
import com.xnvalabs.smarteyex.ui.theme.AccentOrange
import com.xnvalabs.smarteyex.ui.theme.LightBgWarm
import com.xnvalabs.smarteyex.ui.theme.LightSurface
import com.xnvalabs.smarteyex.ui.theme.TextMutedLight
import com.xnvalabs.smarteyex.ui.theme.TextPrimaryLight
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CATEGORIES = listOf("PERSONAL", "SCHOOL", "PROJECT", "MEETING", "TASK", "REMINDER", "OTHER")
private val REMINDER_OPTIONS = listOf(
    "None" to null,
    "At time" to 0,
    "5 minutes before" to 5,
    "15 minutes before" to 15,
    "30 minutes before" to 30,
    "1 hour before" to 60,
    "1 day before" to 1440,
)
private val REPEAT_OPTIONS = listOf("Never", "Daily", "Weekly", "Monthly")
private val DELIVERY_OPTIONS = listOf("GETAR", "SPEAK", "DERING", "SENYAP")
private val DAYS = listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)

private data class ComposerDefaults(
    val dateMillis: Long,
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val location: String,
    val category: String,
    val reminderMinutes: Int?,
    val soundEnabled: Boolean,
    val volume: Int,
    val repeat: String,
    val voiceName: Boolean,
    val voiceTime: Boolean,
    val voiceLocation: Boolean,
    val delivery: String,
)

private fun todayMillis(): Long = normalizeDate(System.currentTimeMillis())

private fun normalizeDate(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayOffset(base: Long, offset: Int): Long = Calendar.getInstance().apply {
    timeInMillis = normalizeDate(base)
    add(Calendar.DAY_OF_YEAR, offset)
}.timeInMillis

private fun dateLabel(millis: Long): String = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(millis))
private fun shortDate(millis: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))

/** Observable locale for composables; recomposes when the device language changes. */
@Composable
@ReadOnlyComposable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun ReminderScreen(
    onBack: () -> Unit,
    initialTitle: String = "",
    initialHour: Int? = null,
    initialMinute: Int? = null,
    initialDayOffset: Int = 0,
    initialDateSpecified: Boolean = false,
    onPrefillConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) { ReminderRepository.init(context) }
    val entries = ReminderRepository.reminders.value
    val repositoryStatus = ReminderRepository.lastStatus.value
    val lifecycleOwner = LocalLifecycleOwner.current

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedDate by remember { mutableStateOf(todayMillis()) }
    var composerVisible by remember { mutableStateOf(false) }
    var composer by remember { mutableStateOf(defaultComposer(todayMillis())) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var assistVisible by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var quickText by remember { mutableStateOf("") }
    var quickError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ReminderRepository.refreshScheduling()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var hasExactAlarm by remember { mutableStateOf(canScheduleExact(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasExactAlarm = canScheduleExact(context)
                hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val todayEvents = occurrencesForDate(entries, todayMillis()).sortedBy { it.first.hour * 60 + it.first.minute }
    val selectedEvents = occurrencesForDate(entries, selectedDate).sortedBy { it.first.hour * 60 + it.first.minute }

    Box(modifier = Modifier.fillMaxSize().background(LightBgWarm)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("‹", fontSize = 28.sp, color = TextPrimaryLight, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Schedule", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                    Text(
                        SimpleDateFormat("EEEE, d MMMM yyyy", locale).format(Date(now)),
                        fontSize = 11.sp,
                        color = TextMutedLight,
                    )
                }
                Text(
                    SimpleDateFormat("HH:mm:ss", locale).format(Date(now)),
                    fontSize = 12.sp,
                    color = AccentOrange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            DateNavigator(
                selectedDate = selectedDate,
                onSelected = { selectedDate = it },
                onToday = { selectedDate = todayMillis() },
            )

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightSurface, RoundedCornerShape(14.dp))
                    .clickable { assistVisible = !assistVisible }
                    .padding(14.dp),
            ) {
                Column {
                    Text("✦  JADWAL CEPAT", fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                    Text(
                        "Ketik satu kalimat, mis. \"besok jam 8 malam minum obat\". Dibaca di perangkat, tanpa internet.",
                        fontSize = 11.sp,
                        color = TextMutedLight,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            if (assistVisible) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (quickText.isEmpty()) Text("besok jam 8 malam minum obat", fontSize = 13.sp, color = TextMutedLight)
                        BasicTextField(
                            value = quickText,
                            onValueChange = { quickText = it.take(200) },
                            singleLine = true,
                            textStyle = TextStyle(color = TextPrimaryLight, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "BUAT",
                        fontSize = 11.sp,
                        color = AccentOrange,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val parsed = VoiceCommandRouter.parse("ingatkan " + quickText.trim())
                            val hour = parsed.timeHour
                            val minute = parsed.timeMinute ?: 0
                            if (parsed.argument.isBlank() || hour == null) {
                                quickError = "Jam atau nama kegiatan belum terbaca. Contoh: \"jam 7 malam belajar\"."
                            } else {
                                // Without a spoken day: today if the time is still ahead, otherwise tomorrow (one-off).
                                val nowCal = Calendar.getInstance()
                                val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
                                val offset = if (parsed.dateSpecified) parsed.dayOffset else if (hour * 60 + minute > nowMinutes) 0 else 1
                                composer = defaultComposer(dayOffset(todayMillis(), offset)).copy(
                                    title = parsed.argument.take(200),
                                    startHour = hour,
                                    startMinute = minute,
                                    endHour = hour,
                                    endMinute = minute,
                                    category = "REMINDER",
                                    repeat = "Never",
                                    reminderMinutes = null,
                                )
                                editingId = null
                                saveError = null
                                quickError = null
                                quickText = ""
                                composerVisible = true
                                assistVisible = false
                            }
                        }.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
                quickError?.let { Text(it, fontSize = 11.sp, color = AccentOrange, modifier = Modifier.padding(top = 6.dp)) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("DAILY TIMELINE", fontSize = 10.sp, color = TextMutedLight, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedDate == todayMillis()) {
                    item {
                        NowMarker(now = now, selectedDate = selectedDate)
                    }
                }
                if (selectedEvents.isEmpty()) {
                    item { Text("Tidak ada jadwal di hari ini.", fontSize = 13.sp, color = TextMutedLight, modifier = Modifier.padding(vertical = 12.dp)) }
                } else {
                    items(selectedEvents, key = { it.first.id }) { pair ->
                        ScheduleCard(
                            entry = pair.first,
                            dateMillis = pair.second,
                            now = now,
                            onDelete = { ReminderRepository.remove(pair.first.id) },
                            onEdit = {
                                composer = pair.first.toComposer(selectedDate)
                                editingId = pair.first.id
                                saveError = null
                                composerVisible = true
                            },
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("TODAY SUMMARY", fontSize = 10.sp, color = TextMutedLight, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryCard(todayEvents.map { it.first }, now)
                }

                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("UPCOMING", fontSize = 10.sp, color = TextMutedLight, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    UpcomingCard(entries, now)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (!hasNotificationPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightSurface, RoundedCornerShape(12.dp))
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        .padding(12.dp),
                ) {
                    Text("Izin notifikasi belum aktif. Ketuk untuk mengizinkan notifikasi Schedule.", fontSize = 11.sp, color = AccentOrange)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (!hasExactAlarm) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightSurface, RoundedCornerShape(12.dp))
                        .clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName)),
                            )
                        }
                        .padding(12.dp),
                ) {
                    Text("Izin alarm presisi belum aktif. Ketuk untuk membuka Settings.", fontSize = 11.sp, color = AccentOrange)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            repositoryStatus?.let { Text(it, fontSize = 11.sp, color = TextMutedLight) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentOrange, RoundedCornerShape(16.dp))
                    .clickable {
                        composer = defaultComposer(selectedDate)
                        editingId = null
                        saveError = null
                        composerVisible = true
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("＋  CREATE SCHEDULE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LightBgWarm)
            }
        }

        if (initialTitle.isNotBlank() || initialHour != null || initialMinute != null) {
            LaunchedEffect(initialTitle, initialHour, initialMinute, initialDayOffset) {
                val prefillDate = if (initialDateSpecified) dayOffset(todayMillis(), initialDayOffset) else selectedDate
                composer = defaultComposer(prefillDate).copy(
                    title = initialTitle,
                    startHour = initialHour ?: 8,
                    startMinute = initialMinute ?: 0,
                    endHour = (initialHour ?: 8).coerceAtMost(23),
                    endMinute = initialMinute ?: 0,
                    category = "REMINDER",
                    // A spoken day ("besok", "lusa") means a one-off reminder; otherwise keep the daily default.
                    repeat = if (initialDateSpecified) "Never" else "Daily",
                    reminderMinutes = null,
                )
                editingId = null
                composerVisible = true
                onPrefillConsumed()
            }
        }

        if (composerVisible) {
            ScheduleComposer(
                context = context,
                value = composer,
                onChange = { composer = it },
                saveError = saveError,
                editing = editingId != null,
                onDismiss = {
                    composerVisible = false
                    editingId = null
                },
                onSave = {
                    val eid = editingId
                    val saved = if (eid != null) ReminderRepository.updateSchedule(
                        id = eid,
                        title = composer.title,
                        dateMillis = composer.dateMillis,
                        startHour = composer.startHour,
                        startMinute = composer.startMinute,
                        endHour = composer.endHour,
                        endMinute = composer.endMinute,
                        location = composer.location,
                        category = composer.category,
                        reminderMinutes = composer.reminderMinutes,
                        soundEnabled = composer.soundEnabled,
                        volume = composer.volume,
                        repeat = composer.repeat,
                        voiceName = composer.voiceName,
                        voiceTime = composer.voiceTime,
                        voiceLocation = composer.voiceLocation,
                        delivery = composer.delivery,
                    ) else ReminderRepository.addSchedule(
                        title = composer.title,
                        dateMillis = composer.dateMillis,
                        startHour = composer.startHour,
                        startMinute = composer.startMinute,
                        endHour = composer.endHour,
                        endMinute = composer.endMinute,
                        location = composer.location,
                        category = composer.category,
                        reminderMinutes = composer.reminderMinutes,
                        soundEnabled = composer.soundEnabled,
                        volume = composer.volume,
                        repeat = composer.repeat,
                        voiceName = composer.voiceName,
                        voiceTime = composer.voiceTime,
                        voiceLocation = composer.voiceLocation,
                        delivery = composer.delivery,
                    )
                    if (saved) {
                        saveError = null
                        composerVisible = false
                        editingId = null
                    } else {
                        saveError = ReminderRepository.lastStatus.value ?: "Schedule gagal disimpan."
                    }
                },
            )
        }
    }
}

private fun ReminderEntry.toComposer(fallbackDate: Long): ComposerDefaults = ComposerDefaults(
    dateMillis = normalizeDate(dateMillis ?: fallbackDate),
    title = title,
    startHour = hour,
    startMinute = minute,
    endHour = endHour,
    endMinute = endMinute,
    location = location,
    category = category,
    reminderMinutes = reminderMinutes,
    soundEnabled = soundEnabled,
    volume = volume,
    repeat = repeat,
    voiceName = voiceName,
    voiceTime = voiceTime,
    voiceLocation = voiceLocation,
    delivery = delivery,
)

private fun defaultComposer(dateMillis: Long): ComposerDefaults = ComposerDefaults(
    dateMillis = normalizeDate(dateMillis),
    title = "",
    startHour = 8,
    startMinute = 0,
    endHour = 9,
    endMinute = 0,
    location = "",
    category = "PERSONAL",
    reminderMinutes = 15,
    soundEnabled = true,
    volume = 65,
    repeat = "Never",
    voiceName = true,
    voiceTime = true,
    voiceLocation = false,
    delivery = "GETAR",
)

@Composable
private fun DateNavigator(selectedDate: Long, onSelected: (Long) -> Unit, onToday: () -> Unit) {
    val locale = currentLocale()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("‹", fontSize = 22.sp, color = TextPrimaryLight, modifier = Modifier.clickable { onSelected(dayOffset(selectedDate, -1)) }.padding(4.dp))
        LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((-3..3).toList()) { offset ->
                val date = dayOffset(selectedDate, offset)
                val selected = date == selectedDate
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(if (selected) AccentOrange else LightSurface, RoundedCornerShape(14.dp))
                        .clickable { onSelected(date) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        SimpleDateFormat("EEE", locale).format(Date(date)).uppercase(locale),
                        fontSize = 9.sp,
                        color = if (selected) LightBgWarm else TextMutedLight,
                    )
                    Text(
                        SimpleDateFormat("d", locale).format(Date(date)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) LightBgWarm else TextPrimaryLight,
                    )
                }
            }
        }
        Text("TODAY", fontSize = 9.sp, color = AccentOrange, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onToday() }.padding(4.dp))
        Text("›", fontSize = 22.sp, color = TextPrimaryLight, modifier = Modifier.clickable { onSelected(dayOffset(selectedDate, 1)) }.padding(4.dp))
    }
}

@Composable
private fun NowMarker(now: Long, selectedDate: Long) {
    val locale = currentLocale()
    if (selectedDate != normalizeDate(now)) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(8.dp).background(AccentOrange, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text("NOW ${SimpleDateFormat("HH:mm", locale).format(Date(now))}", fontSize = 10.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).alpha(.35f).background(AccentOrange).padding(start = 8.dp))
    }
}

@Composable
private fun ScheduleCard(entry: ReminderEntry, dateMillis: Long, now: Long, onDelete: () -> Unit, onEdit: () -> Unit) {
    val start = calendarAt(dateMillis, entry.hour, entry.minute)
    val end = calendarAt(dateMillis, entry.endHour, entry.endMinute)
    val state = when {
        end.timeInMillis < now -> "COMPLETED"
        start.timeInMillis <= now && end.timeInMillis >= now -> "ACTIVE"
        else -> "UPCOMING"
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(52.dp)) {
            Text("%02d:%02d".format(entry.hour, entry.minute), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
            Text(entry.category, fontSize = 7.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
            Text(
                "%02d:%02d – %02d:%02d${if (entry.location.isNotBlank()) " · ${entry.location}" else ""}".format(entry.hour, entry.minute, entry.endHour, entry.endMinute),
                fontSize = 11.sp,
                color = TextMutedLight,
            )
            if (entry.repeat != "Never" || entry.reminderMinutes != null) {
                Text(
                    buildString {
                        if (entry.repeat != "Never") append(entry.repeat)
                        if (entry.reminderMinutes != null) {
                            if (isNotEmpty()) append(" · ")
                            append(reminderLabel(entry.reminderMinutes))
                        }
                    },
                    fontSize = 10.sp,
                    color = TextMutedLight,
                )
            }
        }
        Text(state, fontSize = 8.sp, color = if (state == "ACTIVE") AccentOrange else TextMutedLight, fontWeight = FontWeight.Bold)
        Text("✎", fontSize = 14.sp, color = TextMutedLight, modifier = Modifier.clickable { onEdit() }.padding(start = 10.dp))
        Text("✕", fontSize = 12.sp, color = TextMutedLight, modifier = Modifier.clickable { onDelete() }.padding(start = 10.dp))
    }
}

@Composable
private fun SummaryCard(today: List<ReminderEntry>, now: Long) {
    val counts = today.map { entry ->
        val date = todayMillis()
        val start = calendarAt(date, entry.hour, entry.minute).timeInMillis
        val end = calendarAt(date, entry.endHour, entry.endMinute).timeInMillis
        when {
            end < now -> "COMPLETED"
            start <= now && end >= now -> "ACTIVE"
            else -> "UPCOMING"
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("EVENTS" to today.size, "COMPLETED" to counts.count { it == "COMPLETED" }, "ACTIVE" to counts.count { it == "ACTIVE" }, "UPCOMING" to counts.count { it == "UPCOMING" }).forEach { (label, number) ->
            Column(modifier = Modifier.weight(1f).background(LightSurface, RoundedCornerShape(12.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(number.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                Text(label, fontSize = 7.sp, color = TextMutedLight)
            }
        }
    }
}

@Composable
private fun UpcomingCard(entries: List<ReminderEntry>, now: Long) {
    val locale = currentLocale()
    val future = entries.mapNotNull { nextOccurrenceForUi(it, now) }
        .sortedBy { it.second }
        .take(4)
    if (future.isEmpty()) {
        Text("Belum ada jadwal mendatang.", fontSize = 12.sp, color = TextMutedLight)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            future.forEach { (entry, millis) ->
                Row(
                    modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.width(62.dp)) {
                        Text(shortDate(millis), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                        Text(SimpleDateFormat("HH:mm", locale).format(Date(millis)), fontSize = 10.sp, color = AccentOrange)
                    }
                    Text(entry.title, fontSize = 13.sp, color = TextPrimaryLight, modifier = Modifier.weight(1f))
                    Text(entry.category, fontSize = 8.sp, color = TextMutedLight)
                }
            }
        }
    }
}

@Composable
private fun ScheduleComposer(
    context: Context,
    value: ComposerDefaults,
    onChange: (ComposerDefaults) -> Unit,
    saveError: String?,
    editing: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().imePadding().background(AccentOrange.copy(alpha = 0.12f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(.94f)
                .background(LightBgWarm, RoundedCornerShape(22.dp, 22.dp, 0.dp, 0.dp))
                .clickable(
                    onClick = {},
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(if (editing) "EDIT SCHEDULE" else "CREATE SCHEDULE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight, modifier = Modifier.weight(1f))
                Text("Batal", fontSize = 12.sp, color = TextMutedLight, modifier = Modifier.clickable { onDismiss() })
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                item { EditorTextField("NAMA", value.title, "Nama kegiatan") { onChange(value.copy(title = it.take(200))) } }
                item {
                    EditorValueRow("TANGGAL", dateLabel(value.dateMillis), Modifier.clickable {
                        val c = Calendar.getInstance().apply { timeInMillis = value.dateMillis }
                        DatePickerDialog(context, { _, y, m, d ->
                            val picked = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                            onChange(value.copy(dateMillis = picked.timeInMillis))
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    })
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        EditorValueRow("WAKTU MULAI", "%02d:%02d".format(value.startHour, value.startMinute), Modifier.weight(1f).clickable {
                            TimePickerDialog(context, { _, h, m -> onChange(value.copy(startHour = h, startMinute = m)) }, value.startHour, value.startMinute, true).show()
                        })
                        EditorValueRow("WAKTU SELESAI", "%02d:%02d".format(value.endHour, value.endMinute), Modifier.weight(1f).clickable {
                            TimePickerDialog(context, { _, h, m -> onChange(value.copy(endHour = h, endMinute = m)) }, value.endHour, value.endMinute, true).show()
                        })
                    }
                }
                item { EditorTextField("LOKASI", value.location, "Opsional") { onChange(value.copy(location = it.take(200))) } }
                item { ChipEditor("CATEGORY", CATEGORIES, value.category, { onChange(value.copy(category = it)) }) }
                item { RadioEditor("REMINDER", REMINDER_OPTIONS, value.reminderMinutes) { onChange(value.copy(reminderMinutes = it)) } }
                item {
                    Column(modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text("REMINDER SOUND", fontSize = 9.sp, color = TextMutedLight, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text("SmartEyeX Default", fontSize = 13.sp, color = TextPrimaryLight, modifier = Modifier.weight(1f))
                            Text("▶", fontSize = 15.sp, color = AccentOrange, modifier = Modifier.clickable { previewTone(value.volume) }.padding(8.dp))
                        }
                        Text("Volume ${value.volume}%", fontSize = 10.sp, color = TextMutedLight, modifier = Modifier.padding(top = 4.dp))
                        androidx.compose.material3.Slider(
                            value = value.volume.toFloat(),
                            onValueChange = { onChange(value.copy(volume = it.toInt().coerceIn(0, 100))) },
                            valueRange = 0f..100f,
                        )
                    }
                }
                item { ChipEditor("REPEAT", REPEAT_OPTIONS, value.repeat, { onChange(value.copy(repeat = it)) }) }
                item {
                    Column(modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text("VOICE REMINDER", fontSize = 9.sp, color = TextMutedLight, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        VoiceCheckRow("Nama jadwal", value.voiceName) { onChange(value.copy(voiceName = !value.voiceName)) }
                        VoiceCheckRow("Waktu", value.voiceTime) { onChange(value.copy(voiceTime = !value.voiceTime)) }
                        VoiceCheckRow("Lokasi", value.voiceLocation) { onChange(value.copy(voiceLocation = !value.voiceLocation)) }
                    }
                }
                item { ChipEditor("REMINDER DELIVERY", DELIVERY_OPTIONS, value.delivery, { onChange(value.copy(delivery = it)) }) }
                item { saveError?.let { Text(it, fontSize = 11.sp, color = AccentOrange) } }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "SIMPAN JADWAL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LightBgWarm,
                modifier = Modifier.fillMaxWidth().background(AccentOrange, RoundedCornerShape(14.dp)).clickable { onSave() }.padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EditorTextField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(label, fontSize = 9.sp, color = TextMutedLight, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Box(modifier = Modifier.padding(top = 7.dp)) {
            if (value.isBlank()) Text(placeholder, fontSize = 13.sp, color = TextMutedLight)
            BasicTextField(value = value, onValueChange = onChange, textStyle = TextStyle(color = TextPrimaryLight, fontSize = 13.sp), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EditorValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(LightSurface, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(label, fontSize = 9.sp, color = TextMutedLight, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, fontSize = 13.sp, color = TextPrimaryLight, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun ChipEditor(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(label, fontSize = 9.sp, color = TextMutedLight, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(options) { option ->
                val selectedNow = option == selected
                Text(
                    option,
                    fontSize = 9.sp,
                    color = if (selectedNow) LightBgWarm else TextPrimaryLight,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(if (selectedNow) AccentOrange else LightBgWarm, RoundedCornerShape(999.dp)).clickable { onSelected(option) }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RadioEditor(label: String, options: List<Pair<String, Int?>>, selected: Int?, onSelected: (Int?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(LightSurface, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(label, fontSize = 9.sp, color = TextMutedLight, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Column(modifier = Modifier.padding(top = 4.dp)) {
            options.forEach { (name, minutes) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelected(minutes) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(12.dp).background(if (minutes == selected) AccentOrange else LightBgWarm, CircleShape))
                    Text(name, fontSize = 12.sp, color = TextPrimaryLight, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun VoiceCheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(18.dp).background(if (checked) AccentOrange else LightBgWarm, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            if (checked) Text("✓", fontSize = 11.sp, color = LightBgWarm, fontWeight = FontWeight.Bold)
        }
        Text(label, fontSize = 12.sp, color = TextPrimaryLight, modifier = Modifier.padding(start = 10.dp))
    }
}

private fun reminderLabel(minutes: Int): String = when (minutes) {
    0 -> "At time"
    5 -> "5 minutes before"
    15 -> "15 minutes before"
    30 -> "30 minutes before"
    60 -> "1 hour before"
    1440 -> "1 day before"
    else -> "$minutes minutes before"
}

private fun occurrencesForDate(entries: List<ReminderEntry>, dateMillis: Long): List<Pair<ReminderEntry, Long>> {
    val day = normalizeDate(dateMillis)
    return entries.mapNotNull { entry ->
        if (!occursOn(entry, day)) return@mapNotNull null
        entry to day
    }
}

private fun occursOn(entry: ReminderEntry, dayMillis: Long): Boolean {
    val base = entry.dateMillis ?: return true
    val day = Calendar.getInstance().apply { timeInMillis = dayMillis }
    val original = Calendar.getInstance().apply { timeInMillis = normalizeDate(base) }
    if (day.before(original)) return false
    return when (entry.repeat) {
        "Daily" -> true
        "Weekly" -> day.get(Calendar.DAY_OF_WEEK) == original.get(Calendar.DAY_OF_WEEK)
        "Monthly" -> {
            val desired = original.get(Calendar.DAY_OF_MONTH)
            desired.coerceAtMost(day.getActualMaximum(Calendar.DAY_OF_MONTH)) == day.get(Calendar.DAY_OF_MONTH)
        }
        else -> dayMillis == original.timeInMillis
    }
}

private fun nextOccurrenceForUi(entry: ReminderEntry, now: Long): Pair<ReminderEntry, Long>? {
    val startNow = Calendar.getInstance().apply { timeInMillis = now }
    val base = entry.dateMillis?.let { normalizeDate(it) }
    if (base == null) {
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, entry.hour)
            set(Calendar.MINUTE, entry.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!candidate.after(startNow)) candidate.add(Calendar.DAY_OF_YEAR, 1)
        return entry to candidate.timeInMillis
    }
    var candidate = calendarAt(base, entry.hour, entry.minute)
    if (entry.repeat == "Never") return if (candidate.after(startNow)) entry to candidate.timeInMillis else null
    if (entry.repeat == "Monthly") {
        val desiredDay = Calendar.getInstance().apply { timeInMillis = base }.get(Calendar.DAY_OF_MONTH)
        candidate.set(Calendar.DAY_OF_MONTH, 1)
        candidate.set(Calendar.DAY_OF_MONTH, desiredDay.coerceAtMost(candidate.getActualMaximum(Calendar.DAY_OF_MONTH)))
        candidate.set(Calendar.HOUR_OF_DAY, entry.hour)
        candidate.set(Calendar.MINUTE, entry.minute)
        candidate.set(Calendar.SECOND, 0)
        candidate.set(Calendar.MILLISECOND, 0)
        while (!candidate.after(startNow)) {
            candidate.set(Calendar.DAY_OF_MONTH, 1)
            candidate.add(Calendar.MONTH, 1)
            candidate.set(Calendar.DAY_OF_MONTH, desiredDay.coerceAtMost(candidate.getActualMaximum(Calendar.DAY_OF_MONTH)))
            candidate.set(Calendar.HOUR_OF_DAY, entry.hour)
            candidate.set(Calendar.MINUTE, entry.minute)
            candidate.set(Calendar.SECOND, 0)
            candidate.set(Calendar.MILLISECOND, 0)
        }
        return entry to candidate.timeInMillis
    }
    var guard = 0
    while (!candidate.after(startNow) && guard < 1000) {
        candidate.add(Calendar.DAY_OF_YEAR, if (entry.repeat == "Weekly") 7 else 1)
        guard++
    }
    return if (candidate.after(startNow)) entry to candidate.timeInMillis else null
}

private fun monthlySameDay(entry: ReminderEntry, month: Calendar): Calendar {
    val desired = Calendar.getInstance().apply { timeInMillis = normalizeDate(entry.dateMillis ?: month.timeInMillis) }.get(Calendar.DAY_OF_MONTH)
    return month.clone().let { it as Calendar }.apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.DAY_OF_MONTH, desired.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
        set(Calendar.HOUR_OF_DAY, entry.hour)
        set(Calendar.MINUTE, entry.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private fun calendarAt(dateMillis: Long, hour: Int, minute: Int): Calendar = Calendar.getInstance().apply {
    timeInMillis = normalizeDate(dateMillis)
    set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
    set(Calendar.MINUTE, minute.coerceIn(0, 59))
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun previewTone(volume: Int) {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume.coerceIn(0, 100))
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 450)
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 500L)
    }
}

private fun canScheduleExact(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}
