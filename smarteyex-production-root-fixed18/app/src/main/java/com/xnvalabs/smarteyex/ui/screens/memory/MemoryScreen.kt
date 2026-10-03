package com.xnvalabs.smarteyex.ui.screens.memory

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnvalabs.smarteyex.data.memory.MemoryRepository
import com.xnvalabs.smarteyex.data.memory.MemorySearch
import com.xnvalabs.smarteyex.data.memory.MemoryType
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.ui.theme.AccentOrange
import com.xnvalabs.smarteyex.ui.theme.LightBgWarm
import com.xnvalabs.smarteyex.ui.theme.LightSurface
import com.xnvalabs.smarteyex.ui.theme.TextMutedLight
import com.xnvalabs.smarteyex.ui.theme.TextPrimaryLight

/**
 * Personal Memory screen — Tahap 2, extended for feature #31
 * (Personalization). Two sections, both backed by [MemoryRepository]:
 * "Catatan" (free-form NOTE entries, "XNAI, catat ide" style — feature
 * #13) and "Preferensi" (PREFERENCE entries, "Gue lebih suka jawaban
 * singkat" style — feature #31). Preferences are sent to the XNAI
 * backend as context on every message (see
 * [com.xnvalabs.smarteyex.data.xnai.XnaiRepository.buildContext]) —
 * this screen is just where the user manages that list, the actual
 * "personalization" happens server-side once a backend exists.
 *
 * Profile fields (Name/Interest/Habit/...) live on their own
 * ProfileScreen, not here.
 *
 * Every add/delete goes through [MemoryRepository], which itself checks
 * [PrivacyRepository]'s memory toggle — this screen just reflects that
 * state, it doesn't enforce it.
 *
 * [onBack] fires from the "‹" button.
 */
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val memoryEnabled = PrivacyRepository.settings.value.memoryEnabled
    val allEntries = MemoryRepository.entries.value
    var query by remember { mutableStateOf("") }
    val notes = MemorySearch.filter(allEntries.filter { it.type == MemoryType.NOTE }, query)
    val preferences = MemorySearch.filter(allEntries.filter { it.type == MemoryType.PREFERENCE }, query)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBgWarm),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                Text("Personal Memory", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryLight)
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!memoryEnabled) {
                Text(
                    "Memory sedang OFF — nyalain di Privacy Control biar catatan kesimpen.",
                    fontSize = 13.sp,
                    color = AccentOrange,
                )
            } else {
                Text(
                    "Catatan dan preferensi yang kamu minta SmartEyeX ingat.",
                    fontSize = 13.sp,
                    color = TextMutedLight,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightSurface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (query.isEmpty()) Text("Cari catatan atau preferensi...", fontSize = 14.sp, color = TextMutedLight)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(100) },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimaryLight, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            EntrySection(
                label = "CATATAN",
                placeholder = "Catat ide baru...",
                entries = notes,
                memoryEnabled = memoryEnabled,
                onAdd = { text -> MemoryRepository.addEntry(MemoryType.NOTE, "Note", text) },
                onEdit = { id, text -> MemoryRepository.updateEntry(id, text) },
            )

            Spacer(modifier = Modifier.height(20.dp))

            EntrySection(
                label = "PREFERENSI",
                placeholder = "Mis. \"Gue suka jawaban singkat\"...",
                entries = preferences,
                memoryEnabled = memoryEnabled,
                onAdd = { text -> MemoryRepository.addEntry(MemoryType.PREFERENCE, "Preference", text) },
                onEdit = { id, text -> MemoryRepository.updateEntry(id, text) },
            )
        }
    }
}

@Composable
private fun EntrySection(
    label: String,
    placeholder: String,
    entries: List<com.xnvalabs.smarteyex.data.memory.MemoryEntry>,
    memoryEnabled: Boolean,
    onAdd: (String) -> Boolean,
    onEdit: (String, String) -> Boolean,
) {
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }

    Column {
        Text(label, fontSize = 10.sp, color = TextMutedLight, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(LightSurface, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (draft.isEmpty()) {
                    Text(placeholder, fontSize = 14.sp, color = TextMutedLight)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(color = TextPrimaryLight, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                if (editingId != null) "Perbarui" else "Simpan",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AccentOrange,
                modifier = Modifier.clickable {
                    if (draft.isNotBlank() && memoryEnabled) {
                        val eid = editingId
                        val saved = if (eid != null) onEdit(eid, draft.trim()) else onAdd(draft.trim())
                        if (saved) {
                            draft = ""
                            editingId = null
                            status = null
                        } else {
                            status = "Memory gagal disimpan. Coba lagi."
                        }
                    }
                },
            )
        }

        if (editingId != null) {
            Text(
                "Mengubah catatan. Ketuk di sini untuk batal.",
                fontSize = 11.sp,
                color = TextMutedLight,
                modifier = Modifier.clickable {
                    editingId = null
                    draft = ""
                }.padding(vertical = 6.dp),
            )
        }

        status?.let {
            Text(it, fontSize = 11.sp, color = AccentOrange)
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (entries.isEmpty()) {
            Text("Belum ada.", fontSize = 12.sp, color = TextMutedLight)
        } else {
            LazyColumn(
                modifier = Modifier.height((entries.size.coerceAtMost(3) * 56).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries.sortedByDescending { it.timestamp }, key = { it.id }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LightSurface, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            entry.content,
                            fontSize = 13.sp,
                            color = TextPrimaryLight,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "✎",
                            fontSize = 14.sp,
                            color = TextMutedLight,
                            modifier = Modifier
                                .clickable {
                                    editingId = entry.id
                                    draft = entry.content
                                    status = null
                                }
                                .padding(start = 10.dp),
                        )
                        Text(
                            "✕",
                            fontSize = 13.sp,
                            color = TextMutedLight,
                            modifier = Modifier
                                .clickable {
                                    if (editingId == entry.id) {
                                        editingId = null
                                        draft = ""
                                    }
                                    MemoryRepository.deleteEntry(entry.id)
                                }
                                .padding(start = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
