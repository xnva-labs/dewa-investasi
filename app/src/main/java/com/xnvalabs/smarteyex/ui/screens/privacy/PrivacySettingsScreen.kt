package com.xnvalabs.smarteyex.ui.screens.privacy

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.xnvalabs.smarteyex.data.auth.AuthRepository
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.ui.theme.AccentOrange
import com.xnvalabs.smarteyex.ui.theme.LightBgWarm
import com.xnvalabs.smarteyex.ui.theme.LightSurface
import com.xnvalabs.smarteyex.ui.theme.TextMutedLight
import com.xnvalabs.smarteyex.ui.theme.TextPrimaryLight

private data class ToggleRow(
    val label: String,
    val description: String,
    val checked: Boolean,
    val onToggle: (Boolean) -> Boolean,
)

/**
 * Privacy Control Screen — Tahap 1 of the MVP build order (see
 * "Ringkasan Prioritas" sheet: Privacy → Memory → Backend XNAI → Camera).
 * Every toggle here maps 1:1 to a [PrivacyRepository] field; nothing else
 * in the app may touch camera/mic/memory/cloud/face-recognition data
 * without checking these first.
 *
 * "Hapus semua memori & reset izin" wipes [MemoryRepository] too, via
 * the onClearMemory hook (Tahap 2).
 *
 * User Authentication section (feature #26) added afterward: PIN is
 * fully optional (this feature is non-MVP) — [onSetPin] routes to
 * MainActivity's PinLockScreen in SETUP mode; removing an existing PIN
 * happens right here, same pattern as the memory wipe button, since it
 * needs no multi-step flow.
 *
 * [onBack] fires from the "‹" button. [onSetPin] fires from "Set PIN".
 */
@Composable
fun PrivacySettingsScreen(onBack: () -> Unit, onSetPin: () -> Unit, onOpenPrivacyPolicy: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { PrivacyRepository.init(context) }
    val settings = PrivacyRepository.settings.value
    val writeError = PrivacyRepository.lastWriteError.value
    val pinSet = AuthRepository.isPinSetState.value

    val rows = listOf(
        ToggleRow(
            label = "Camera",
            description = "Izinkan SmartEyeX mengakses kamera untuk computer vision & OCR.",
            checked = settings.cameraEnabled,
            onToggle = { PrivacyRepository.setCameraEnabled(it) },
        ),
        ToggleRow(
            label = "Microphone",
            description = "Izinkan SmartEyeX mendengar voice command & percakapan.",
            checked = settings.microphoneEnabled,
            onToggle = { PrivacyRepository.setMicrophoneEnabled(it) },
        ),
        ToggleRow(
            label = "Memory",
            description = "Izinkan SmartEyeX menyimpan memori pribadi (orang, catatan, jadwal).",
            checked = settings.memoryEnabled,
            onToggle = { PrivacyRepository.setMemoryEnabled(it) },
        ),
        ToggleRow(
            label = "Cloud Processing",
            description = "Izinkan pertanyaan kompleks diproses lewat cloud AI, bukan cuma on-device.",
            checked = settings.cloudProcessingEnabled,
            onToggle = { PrivacyRepository.setCloudProcessingEnabled(it) },
        ),
        ToggleRow(
            label = "Notification Content",
            description = "Izinkan SmartEyeX membaca isi notifikasi untuk Communication Assistant.",
            checked = settings.notificationContentEnabled,
            onToggle = { PrivacyRepository.setNotificationContentEnabled(it) },
        ),
        ToggleRow(
            label = "Voice Personalization",
            description = "Simpan statistik prosodi terbatas agar gaya bicara XNAI dapat menyesuaikan tempo dan intonasi. Audio mentah tidak disimpan oleh fitur ini.",
            checked = settings.voicePersonalizationEnabled,
            onToggle = { PrivacyRepository.setVoicePersonalizationEnabled(it) },
        ),
        ToggleRow(
            label = "Face Recognition",
            description = "Consent pengenalan wajah on-device untuk orang yang sudah setuju didaftarkan. Mematikannya menghentikan pemrosesan dan menghapus semua data wajah.",
            checked = settings.faceRecognitionEnabled,
            onToggle = { PrivacyRepository.setFaceRecognitionEnabled(it) },
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBgWarm),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "‹",
                    fontSize = 26.sp,
                    color = TextPrimaryLight,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 12.dp),
                )
                Text("Privacy Control", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Semua OFF secara default. Izin Android dan consent SmartEyeX sama-sama diperlukan sebelum data dipakai.",
                fontSize = 13.sp,
                color = TextMutedLight,
            )

            Spacer(modifier = Modifier.height(20.dp))

            writeError?.let {
                Text(it, fontSize = 12.sp, color = AccentOrange, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LightSurface, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(row.description, fontSize = 12.sp, color = TextMutedLight)
                        }
                        Switch(
                            checked = row.checked,
                            onCheckedChange = { checked -> row.onToggle(checked) },
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentOrange),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("USER AUTHENTICATION", fontSize = 11.sp, color = TextMutedLight, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightSurface, RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PIN Lock", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                    Text(
                        if (pinSet) "Aktif — app terkunci tiap dibuka" else "Belum diaktifkan",
                        fontSize = 12.sp,
                        color = TextMutedLight,
                    )
                }
                Text(
                    if (pinSet) "Hapus PIN" else "Set PIN",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange,
                    modifier = Modifier.clickable {
                        if (pinSet) AuthRepository.clearPin() else onSetPin()
                    },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightSurface, RoundedCornerShape(14.dp))
                    .clickable { onOpenPrivacyPolicy() }
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Privacy & Data", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
                    Text("Lihat bagaimana SmartEyeX memperlakukan data sensitif.", fontSize = 12.sp, color = TextMutedLight)
                }
                Text("›", fontSize = 18.sp, color = TextMutedLight)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightSurface, RoundedCornerShape(14.dp))
                    .clickable {
                        PrivacyRepository.clearAllData()
                    }
                    .padding(16.dp),
            ) {
                Text(
                    "Hapus semua memori & reset izin",
                    fontSize = 14.sp,
                    color = AccentOrange,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
