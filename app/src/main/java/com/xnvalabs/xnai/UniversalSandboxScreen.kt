package com.xnvalabs.xnai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun UniversalSandboxScreen() {
    val context = LocalContext.current
    val system = remember(context) { XnaiAutonomousSystem(context) }
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf("") }
    var objective by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var operation by remember { mutableStateOf("learn") }
    var result by remember { mutableStateOf<XnaiUniversalSandbox.TaskResult?>(null) }
    var running by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Eksplorasi topik apa pun", style = MaterialTheme.typography.headlineSmall)
        Text("Pilih operasi, tulis tujuan, lalu jalankan. Hasil AI adalah draf sampai diverifikasi dengan bukti atau pengujian.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth(), label = { Text("Topik / bahasa / bidang") }, placeholder = { Text("Contoh: Rust, fotosintesis, teori graf") }, singleLine = true)
        OutlinedTextField(objective, { objective = it }, Modifier.fillMaxWidth(), label = { Text("Apa yang ingin dipelajari atau dikerjakan?") }, minLines = 3)
        OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), label = { Text("Bahan atau konteks (opsional)") }, minLines = 3)
        Text("Operasi", style = MaterialTheme.typography.titleMedium)
        listOf("learn" to "Belajar topik", "code-draft" to "Buat draf kode", "programming-plan" to "Rencana pemrograman", "text-analysis" to "Analisis teks").chunked(2).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                group.forEach { (id, label) ->
                    if (operation == id) Button(onClick = { operation = id }, modifier = Modifier.weight(1f)) { Text(label) }
                    else androidx.compose.material3.OutlinedButton(onClick = { operation = id }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
        }
        Button(onClick = {
            if (!running && subject.isNotBlank() && objective.isNotBlank()) {
                running = true; result = null
                scope.launch {
                    try { result = system.runUniversalSandboxTask(XnaiUniversalSandbox.Task(
                        id = "user-${System.currentTimeMillis()}", subject = subject.trim(), objective = objective.trim(),
                        operation = operation, input = input
                    )) } finally { running = false }
                }
            }
        }, enabled = !running && subject.isNotBlank() && objective.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (running) CircularProgressIndicator() else Text("Jalankan eksplorasi")
        }
        result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status: ${r.status} · Terverifikasi: ${r.verified}", style = MaterialTheme.typography.titleSmall)
                    Text(r.conclusion)
                    if (r.output.isNotBlank()) Text(r.output, style = MaterialTheme.typography.bodyMedium)
                    if (r.measurements.isNotEmpty()) Text(r.measurements.entries.joinToString(" · ") { "${it.key}: ${it.value}" }, style = MaterialTheme.typography.bodySmall)
                    Text("ID: ${r.taskId} · Adapter: ${r.adapterId}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
