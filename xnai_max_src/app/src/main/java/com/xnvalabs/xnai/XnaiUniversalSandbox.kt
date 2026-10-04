package com.xnvalabs.xnai

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Topic-neutral sandbox orchestration. Any subject may be represented as a task;
 * execution is delegated only to explicitly registered, trusted adapters.
 * Resource budgets protect the host and do not restrict the subject being studied.
 */
class XnaiUniversalSandbox(private val policy: () -> UniversalSandboxPolicy) {
    data class UniversalSandboxPolicy(
        val enabled: Boolean = true,
        val maxInputChars: Int = 250_000,
        val maxOutputChars: Int = 250_000,
        val maxSteps: Int = 100_000,
        val maxTaskMillis: Long = 10_000,
        val allowResearch: Boolean = true,
        val allowExternalEffects: Boolean = false
    )

    data class Task(
        val id: String,
        val subject: String,
        val objective: String,
        val operation: String,
        val input: String = "",
        val evidenceRefs: List<String> = emptyList(),
        val requestedSteps: Int = 1
    )

    data class TaskContext(val policy: UniversalSandboxPolicy, val task: Task)
    data class AdapterOutput(
        val output: String,
        val evidence: List<String> = emptyList(),
        val measurements: Map<String, String> = emptyMap(),
        val proposedConclusion: String = ""
    )
    data class TaskResult(
        val taskId: String,
        val status: String,
        val verified: Boolean,
        val output: String,
        val evidence: List<String>,
        val measurements: Map<String, String>,
        val conclusion: String,
        val adapterId: String,
        val inputSha256: String
    )

    fun interface Adapter { fun execute(context: TaskContext): AdapterOutput }
    private data class RegisteredAdapter(val id: String, val adapter: Adapter)
    private val adapters = ConcurrentHashMap<String, RegisteredAdapter>()

    /** Register app-owned, reviewed adapters (e.g. formula, code-analysis, simulator, parser). */
    fun register(operation: String, adapterId: String, adapter: Adapter) {
        require(operation.isNotBlank() && adapterId.isNotBlank())
        adapters[operation.trim().lowercase()] = RegisteredAdapter(adapterId, adapter)
    }

    fun supportedOperations(): Set<String> = adapters.keys.toSortedSet()

    /** Any nonblank subject is accepted. Unknown operations are retained as queued, not executed. */
    fun run(task: Task): TaskResult {
        val p = policy()
        val fingerprint = sha256("${task.subject}\n${task.objective}\n${task.operation}\n${task.input}")
        fun result(status: String, verified: Boolean = false, output: String = "", evidence: List<String> = emptyList(), measurements: Map<String, String> = emptyMap(), conclusion: String, adapterId: String = "none") =
            TaskResult(task.id, status, verified, output, evidence, measurements, conclusion, adapterId, fingerprint)

        if (!p.enabled) return result("paused", conclusion = "Sandbox is paused by owner policy.")
        if (task.subject.isBlank() || task.objective.isBlank() || task.operation.isBlank())
            return result("invalid", conclusion = "Subject, objective, and operation are required.")
        if (task.input.length > p.maxInputChars)
            return result("budget_rejected", conclusion = "Input exceeds configured memory-safe character budget.")
        if (task.requestedSteps !in 1..p.maxSteps)
            return result("budget_rejected", conclusion = "Requested steps exceed the configured task budget.")
        val registered = adapters[task.operation.trim().lowercase()]
            ?: return result("queued_adapter_missing", conclusion = "Topic accepted; no reviewed execution adapter is registered for this operation.")
        val started = System.nanoTime()
        return try {
            val output = registered.adapter.execute(TaskContext(p, task))
            val elapsed = (System.nanoTime() - started) / 1_000_000L
            if (elapsed > p.maxTaskMillis) result("timeout", conclusion = "Adapter exceeded time budget; output is not validated.", adapterId = registered.id)
            else if (output.output.length > p.maxOutputChars) result("budget_rejected", conclusion = "Adapter output exceeds configured output budget.", adapterId = registered.id)
            else TaskResult(task.id, "completed_unverified", false, output.output, output.evidence, output.measurements,
                output.proposedConclusion.ifBlank { "Adapter completed; independent verification is still required." }, registered.id, fingerprint)
        } catch (t: Throwable) {
            result("adapter_error", conclusion = "Adapter failed (${t.javaClass.simpleName}); failure retained for reflection.", adapterId = registered.id)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
