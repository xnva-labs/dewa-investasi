package com.xnvalabs.xnai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun VoiceScreen(repository: XnaiRepository) {
    val context = LocalContext.current
    val voice = remember { XnaiVoice(context) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("Siap") }
    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("VOICE", color = XNACyan)
        Text("Voice input + text-to-speech lokal. Tidak ada perekaman latar belakang otomatis.", style = MaterialTheme.typography.titleLarge)
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status: $state")
                Text(transcript.ifBlank { "Belum ada transkrip." })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!micGranted) launcher.launch(Manifest.permission.RECORD_AUDIO)
                        else voice.listen({ transcript = it }, { state = it })
                    }) { Text("Dengar") }
                    OutlinedButton(enabled = transcript.isNotBlank(), onClick = { voice.speak(transcript); state = "Membacakan…" }) { Text("Bacakan") }
                    TextButton(onClick = { transcript = "" }) { Text("Bersihkan") }
                }
            }
        }
        Text("Voice menjadi input ke Core melalui teks transkrip. Untuk kontrol eksternal, XNAI tetap menunggu izin eksplisit.")
    }
}
