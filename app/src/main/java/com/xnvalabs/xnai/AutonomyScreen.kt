package com.xnvalabs.xnai

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun AutonomyScreen(repository: XnaiRepository, onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val system = remember { XnaiAutonomousSystem(context) }
    var running by rememberSaveable { mutableStateOf(false) }
    var continuous by rememberSaveable { mutableStateOf(true) }
    var maxCycles by rememberSaveable { mutableStateOf(25) }
    var localCycles by rememberSaveable { mutableStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    val results = remember { mutableStateListOf<XnaiAutonomousSystem.CycleOutput>() }
    val scope = rememberCoroutineScope()
    val snapshot = remember(results.size) { system.snapshot() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("AUTONOMOUS XNAI", color = XNACyan)
            Text("XNAI memilih sendiri pertanyaan, bukti, hipotesis, eksperimen, refleksi, dan langkah berikutnya.", style = MaterialTheme.typography.headlineSmall)
            Text("Observe → Retrieve → Combine → Hypothesize → Experiment → Verify → Reflect → Try to Implement → Try to Use → Remember → Integrate")
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Mode mandiri", fontWeight = FontWeight.Bold)
                    Text("Tidak ada goal wajib. Tujuan manusia hanya konteks; mesin memilih prioritas berikutnya berdasarkan uncertainty, novelty, evidence, pengalaman, dan batas sandbox.")
                    Spacer(Modifier.height(8.dp))
                    Text("Batas batch: $maxCycles")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { maxCycles = (maxCycles - 5).coerceAtLeast(5) }) { Text("−5") }
                        OutlinedButton(onClick = { maxCycles = (maxCycles + 5).coerceAtMost(200) }) { Text("+5") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(
                            enabled = !running,
                            onClick = {
                                running = true
                                localCycles = 0
                                results.clear()
                                job?.cancel()
                                job = scope.launch {
                                    while (isActive && running && (continuous || localCycles < maxCycles)) {
                                        localCycles++
                                        try {
                                            results += system.runOneCycle()
                                            onChanged()
                                        } catch (e: Throwable) {
                                            repository.log("autonomy error", e.message ?: "unknown")
                                        }
                                        delay(250)
                                        if (!continuous && localCycles >= maxCycles) break
                                    }
                                    running = false
                                }
                            }
                        ) { Text("Mulai mandiri") }
                        OutlinedButton(
                            enabled = running,
                            onClick = {
                                running = false
                                job?.cancel()
                                job = null
                            }
                        ) { Text("STOP") }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text("Library hidup", fontWeight = FontWeight.Bold)
                    Text("Pertanyaan: ${snapshot.questions.size} · Hipotesis: ${snapshot.hypotheses.size} · Eksperimen: ${snapshot.experiments.size} · Refleksi: ${snapshot.reflections.size} · Pengalaman: ${snapshot.experiences.size} · Validated: ${snapshot.validated.size}")
                    LinearProgressIndicator(progress = { if (continuous) 0f else (localCycles.toFloat() / maxCycles).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
        items(results.reversed()) { cycle ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text("Cycle ${cycle.cycleId}", color = XNACyan, fontWeight = FontWeight.Bold)
                    Text("Question: ${cycle.question.question}")
                    Text("Hypothesis: ${cycle.hypothesis.statement}")
                    Text("Experiment: ${cycle.experiment.measurement}")
                    Text("Conclusion: ${cycle.experiment.conclusion}")
                    Text("Reflection: ${cycle.reflection.reflection}")
                    Text(if (cycle.validated) "✓ Masuk validated knowledge" else "• Disimpan sebagai pengalaman/hasil belum tervalidasi")
                }
            }
        }
    }
}
