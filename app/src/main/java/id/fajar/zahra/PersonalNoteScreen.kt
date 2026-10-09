package id.fajar.zahra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val android.content.Context.personalNoteStore by preferencesDataStore(name = "zahra_personal_note")
private val personalNoteKey = stringPreferencesKey("note_text")

/** Optional, locally stored personal note. This is deliberately not presented as Islamic scripture. */
@Composable
fun PersonalNoteScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        note = runCatching {
            val stored = context.personalNoteStore.data.first()[personalNoteKey].orEmpty()
            PersonalNoteCrypto.decrypt(stored)
        }.getOrDefault("")
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Surat kecil") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ruang pesan pribadi", style = MaterialTheme.typography.titleLarge)
                    Text("Tulis ucapan, surat, atau kalimat penyemangat dengan kata-katamu sendiri. Isinya disimpan terenkripsi di perangkat ini dan tidak ditampilkan sebagai hadis atau doa. Kunci enkripsi dikelola Android Keystore.")
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(4000); status = null },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                label = { Text("Pesan untuknya") },
                placeholder = { Text("Semoga hari-harimu selalu menemukan alasan untuk tersenyum…") },
                minLines = 5,
                maxLines = 12,
                shape = RoundedCornerShape(18.dp)
            )
            Button(onClick = {
                scope.launch {
                    runCatching {
                    val encrypted = PersonalNoteCrypto.encrypt(note.trim())
                    context.personalNoteStore.edit { it[personalNoteKey] = encrypted }
                }
                        .onSuccess { status = "Pesan tersimpan di perangkat ini." }
                        .onFailure { status = "Pesan belum bisa disimpan. Coba lagi." }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Simpan pesan") }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
