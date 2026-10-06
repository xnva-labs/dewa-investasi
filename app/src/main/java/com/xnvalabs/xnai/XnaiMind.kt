package com.xnvalabs.xnai

import java.util.Random
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * XNAI's open-ended learning engine.
 *
 * XNAI invents its own tasks, searches the space of programs for a solution, keeps what works as a reusable
 * skill (a new building block it can call from later programs), and raises the difficulty by itself.
 * Learning = finding a program shorter than the data it explains, so every skill is also a compression.
 * Programs run only inside [MindVM]: no I/O, no network, no files, bounded steps and stack. That VM is the sandbox.
 */
data class MindPolicy(
    val maxStepsPerRun: Int = 256,
    val maxStack: Int = 64,
    val maxProgramLen: Int = 32,
    val maxEvalsPerTask: Int = 40_000,
    val maxTasksPerRound: Int = 8,
    val roundMillis: Long = 6_000L,
    val batch: Int = 48,
    val beam: Int = 24
)

object MindOp {
    const val PUSH = 0
    const val DUP = 1
    const val SWAP = 2
    const val DROP = 3
    const val OVER = 4
    const val ADD = 5
    const val SUB = 6
    const val MUL = 7
    const val DIV = 8
    const val MOD = 9
    const val NEG = 10
    const val MIN = 11
    const val MAX = 12
    const val ABS = 13
    const val LT = 14
    const val EQ = 15
    const val SEL = 16
    const val CALL = 17
    const val LIMIT = 1_000_000_000L

    val NEED = intArrayOf(0, 1, 2, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 1, 2, 2, 3, 0)
    val DELTA = intArrayOf(1, 1, 0, -1, 1, -1, -1, -1, -1, -1, 0, -1, -1, 0, -1, -1, -2, 0)
    val CONSTS = longArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, -1, -2, 100)
}

data class Instr(val op: Int, val arg: Long = 0L)

class Skill(val id: Int, val arity: Int, val prog: List<Instr>, var uses: Int, val gain: Int, val origin: String)

data class Example(val inputs: LongArray, val output: Long)

object ProgCodec {
    private fun zig(n: Long): Long = (n shl 1) xor (n shr 63)
    private fun unzig(z: Long): Long = (z ushr 1) xor -(z and 1L)

    fun encode(prog: List<Instr>): LongArray = LongArray(prog.size) { (zig(prog[it].arg) shl 5) or prog[it].op.toLong() }

    fun decode(values: LongArray): List<Instr> = values.map { Instr((it and 31L).toInt(), unzig(it ushr 5)) }

    fun toText(prog: List<Instr>): String = SymbolAlphabet.encode(encode(prog))

    fun fromText(text: String): List<Instr> = decode(SymbolAlphabet.decode(text))
}

interface MindStore {
    fun loadSkills(): List<Skill>
    fun addSkill(arity: Int, prog: List<Instr>, origin: String, gain: Int): Int
    fun bumpUse(id: Int)
    fun getMeta(key: String): String?
    fun putMeta(key: String, value: String)
}

class InMemoryMindStore : MindStore {
    private val rows = ArrayList<Skill>()
    private val meta = HashMap<String, String>()
    override fun loadSkills(): List<Skill> = rows.map { Skill(it.id, it.arity, it.prog, it.uses, it.gain, it.origin) }
    override fun addSkill(arity: Int, prog: List<Instr>, origin: String, gain: Int): Int {
        val id = rows.size + 1
        rows.add(Skill(id, arity, prog, 0, gain, origin))
        return id
    }
    override fun bumpUse(id: Int) { rows.firstOrNull { it.id == id }?.let { it.uses++ } }
    override fun getMeta(key: String): String? = meta[key]
    override fun putMeta(key: String, value: String) { meta[key] = value }
}

/** Stack machine over bounded integers. Every instruction is total; invalid programs simply return null. */
class MindVM(private val policy: MindPolicy, private val skills: Map<Int, Skill>) {
    private var steps = 0
    private val stacks = Array(6) { LongArray(policy.maxStack + 2) }

    fun run(prog: List<Instr>, inputs: LongArray): Long? {
        steps = policy.maxStepsPerRun
        return exec(prog, inputs, 0)
    }

    private fun clamp(v: Long): Long = if (v > MindOp.LIMIT) MindOp.LIMIT else if (v < -MindOp.LIMIT) -MindOp.LIMIT else v

    private fun exec(prog: List<Instr>, inputs: LongArray, depth: Int): Long? {
        val st = stacks[depth]
        var sp = 0
        for (v in inputs) {
            if (sp >= policy.maxStack) return null
            st[sp++] = v
        }
        for (ins in prog) {
            if (--steps < 0) return null
            val op = ins.op
            if (op < 0 || op > MindOp.CALL) return null
            if (op == MindOp.CALL) {
                val sk = skills[ins.arg.toInt()] ?: return null
                if (depth >= 4 || sp < sk.arity) return null
                val args = LongArray(sk.arity) { st[sp - sk.arity + it] }
                sp -= sk.arity
                val r = exec(sk.prog, args, depth + 1) ?: return null
                if (sp >= policy.maxStack) return null
                st[sp++] = r
                continue
            }
            if (sp < MindOp.NEED[op]) return null
            when (op) {
                MindOp.PUSH -> { if (sp >= policy.maxStack) return null; st[sp++] = ins.arg }
                MindOp.DUP -> { if (sp >= policy.maxStack) return null; st[sp] = st[sp - 1]; sp++ }
                MindOp.SWAP -> { val t = st[sp - 1]; st[sp - 1] = st[sp - 2]; st[sp - 2] = t }
                MindOp.DROP -> sp--
                MindOp.OVER -> { if (sp >= policy.maxStack) return null; st[sp] = st[sp - 2]; sp++ }
                MindOp.NEG -> st[sp - 1] = clamp(-st[sp - 1])
                MindOp.ABS -> st[sp - 1] = abs(st[sp - 1])
                MindOp.SEL -> {
                    val b = st[--sp]; val a = st[--sp]; val c = st[--sp]
                    st[sp++] = if (c != 0L) a else b
                }
                else -> {
                    val b = st[--sp]
                    val a = st[--sp]
                    val r = when (op) {
                        MindOp.ADD -> a + b
                        MindOp.SUB -> a - b
                        MindOp.MUL -> a * b
                        MindOp.DIV -> if (b == 0L) 0L else Math.floorDiv(a, b)
                        MindOp.MOD -> if (b == 0L) 0L else Math.floorMod(a, b)
                        MindOp.MIN -> if (a < b) a else b
                        MindOp.MAX -> if (a > b) a else b
                        MindOp.LT -> if (a < b) 1L else 0L
                        else -> if (a == b) 1L else 0L
                    }
                    st[sp++] = clamp(r)
                }
            }
        }
        return if (sp > 0) st[sp - 1] else null
    }
}

data class MindTrace(
    val task: Long, val arity: Int, val difficulty: Int, val strategy: String,
    val evals: Int, val solved: Boolean, val programLen: Int, val ms: Long
)

data class MindReport(
    val attempted: Int,
    val solved: Int,
    val newSkills: List<Skill>,
    val reused: Int,
    val difficulty: Int,
    val totalSkills: Int,
    val bestStrategy: String,
    val traces: List<MindTrace>,
    val rawSymbols: Int,
    val programSymbols: Int,
    val summary: String
)

class XnaiMind(private val store: MindStore, val policy: MindPolicy = MindPolicy()) {
    companion object {
        val STRATEGIES = listOf("acak", "mutasi", "silang", "rakit-skill")
        private const val CAP = 1000.0
        private const val K_DIFFICULTY = "mind.difficulty"
        private const val K_STRATEGY = "mind.strategy"
        private const val K_RECENT = "mind.recent"
        private const val K_TASKS = "mind.tasks"
    }

    private val skills = LinkedHashMap<Int, Skill>()
    private val sigToSkill = HashMap<String, Int>()
    private val vm = MindVM(policy, skills)
    private val pulls = IntArray(STRATEGIES.size)
    private val wins = IntArray(STRATEGIES.size)
    private val recent = ArrayList<Int>()
    private var genIds: List<Int> = emptyList()
    var difficulty: Int = 2
        private set

    private val probes1: List<LongArray> = (-8..14).map { longArrayOf(it.toLong()) }
    private val probes2: List<LongArray> = run {
        val l = ArrayList<LongArray>()
        for (a in -3..4) for (b in -3..4) l.add(longArrayOf(a.toLong(), b.toLong()))
        l
    }
    private val trivialSigs: Set<String> = setOf(
        "1:" + probes1.joinToString("") { "${it[0]}," },
        "2:" + probes2.joinToString("") { "${it[0]}," },
        "2:" + probes2.joinToString("") { "${it[1]}," }
    )

    init {
        store.loadSkills().forEach { skills[it.id] = it }
        skills.values.forEach { sigToSkill.putIfAbsent(signature(it.arity, it.prog), it.id) }
        difficulty = store.getMeta(K_DIFFICULTY)?.toIntOrNull()?.coerceIn(2, policy.maxProgramLen) ?: 2
        store.getMeta(K_STRATEGY)?.split(',')?.mapNotNull { it.toIntOrNull() }?.let { v ->
            for (i in STRATEGIES.indices) {
                pulls[i] = v.getOrElse(i * 2) { 0 }
                wins[i] = v.getOrElse(i * 2 + 1) { 0 }
            }
        }
        store.getMeta(K_RECENT)?.forEach { if (it == '0' || it == '1') recent.add(it - '0') }
    }

    // ---- helpers ----

    private fun probes(arity: Int) = if (arity == 1) probes1 else probes2

    private fun signature(arity: Int, prog: List<Instr>): String {
        val sb = StringBuilder()
        sb.append(arity).append(':')
        for (p in probes(arity)) sb.append(vm.run(prog, p)?.toString() ?: "x").append(',')
        return sb.toString()
    }

    private fun isTrivial(sig: String): Boolean {
        if (sig in trivialSigs) return true
        return sig.substringAfter(':').split(',').filter { it.isNotEmpty() }.toSet().size <= 1
    }

    private fun pickGenIds(): List<Int> = skills.values.sortedByDescending { it.uses * 4 + it.id }.take(48).map { it.id }

    private fun randInstr(rng: Random): Instr {
        if (genIds.isNotEmpty() && rng.nextDouble() < 0.1) return Instr(MindOp.CALL, genIds[rng.nextInt(genIds.size)].toLong())
        val op = rng.nextInt(MindOp.CALL)
        return if (op == MindOp.PUSH) Instr(op, MindOp.CONSTS[rng.nextInt(MindOp.CONSTS.size)]) else Instr(op, 0L)
    }

    private fun randProg(rng: Random, arity: Int, length: Int, ids: List<Int>): List<Instr> {
        val prog = ArrayList<Instr>(length)
        var depth = arity
        for (i in 0 until length) {
            if (ids.isNotEmpty() && rng.nextDouble() < 0.15) {
                val sid = ids[rng.nextInt(ids.size)]
                val sk = skills[sid]
                if (sk != null && depth >= sk.arity) {
                    prog.add(Instr(MindOp.CALL, sid.toLong()))
                    depth = depth - sk.arity + 1
                    continue
                }
            }
            var op = rng.nextInt(MindOp.CALL)
            while (MindOp.NEED[op] > depth) op = rng.nextInt(MindOp.CALL)
            prog.add(if (op == MindOp.PUSH) Instr(op, MindOp.CONSTS[rng.nextInt(MindOp.CONSTS.size)]) else Instr(op, 0L))
            depth += MindOp.DELTA[op]
            if (depth < 1) depth = 1
        }
        return prog
    }

    private fun mutate(rng: Random, prog: List<Instr>): List<Instr> {
        val p = ArrayList<Instr>(prog)
        val r = rng.nextDouble()
        if (p.isEmpty() || r < 0.4) {
            p.add(rng.nextInt(p.size + 1), randInstr(rng))
        } else if (r < 0.6 && p.size > 1) {
            p.removeAt(rng.nextInt(p.size))
        } else {
            p[rng.nextInt(p.size)] = randInstr(rng)
        }
        return if (p.size > policy.maxProgramLen) ArrayList(p.subList(0, policy.maxProgramLen)) else p
    }

    private fun fitness(prog: List<Instr>, examples: List<Example>): Double {
        var total = 0.0
        for (e in examples) {
            val r = vm.run(prog, e.inputs)
            total += if (r == null) CAP else minOf(abs(r - e.output).toDouble(), CAP)
        }
        return total + 0.01 * prog.size
    }

    private class Task(val arity: Int, val train: List<Example>, val test: List<Example>)
    private class Cand(val f: Double, val p: List<Instr>)
    private class Solved(val prog: List<Instr>, val strategy: Int)

    private fun makeTask(rng: Random, arity: Int, length: Int): Task? {
        for (attempt in 0 until 200) {
            val hidden = randProg(rng, arity, length, genIds)
            val points: List<LongArray> = if (arity == 1) (-6..15).map { longArrayOf(it.toLong()) }
            else List(22) { longArrayOf((rng.nextInt(15) - 5).toLong(), (rng.nextInt(15) - 5).toLong()) }
            val ex = ArrayList<Example>(points.size)
            var ok = true
            for (p in points) {
                val r = vm.run(hidden, p)
                if (r == null) { ok = false; break }
                ex.add(Example(p, r))
            }
            if (ok && ex.map { it.output }.toSet().size >= 5) return Task(arity, ex.subList(0, 12).toList(), ex.subList(12, ex.size).toList())
        }
        return null
    }

    private fun pickStrategy(): Int {
        val total = pulls.sum()
        var best = 0
        var bestScore = -1.0
        for (s in STRATEGIES.indices) {
            val score = (wins[s] + 1.0) / (pulls[s] + 2.0) + sqrt(2.0 * ln(total + 2.0) / (pulls[s] + 1.0))
            if (score > bestScore) { bestScore = score; best = s }
        }
        return best
    }

    private fun solve(rng: Random, task: Task, deadline: Long, active: () -> Boolean): Pair<Solved?, Int> {
        var beam = ArrayList<Cand>()
        val seen = HashSet<List<Instr>>()
        var evals = 0
        var best = Double.MAX_VALUE
        while (evals < policy.maxEvalsPerTask) {
            if (!active() || System.currentTimeMillis() > deadline) break
            val k = pickStrategy()
            pulls[k]++
            var improved = false
            for (i in 0 until policy.batch) {
                val cand: List<Instr> = when {
                    k == 0 || beam.isEmpty() -> randProg(rng, task.arity, 1 + rng.nextInt(8), emptyList())
                    k == 3 -> randProg(rng, task.arity, 1 + rng.nextInt(6), genIds)
                    k == 1 -> mutate(rng, beam[rng.nextInt(beam.size)].p)
                    else -> {
                        val a = beam[rng.nextInt(beam.size)].p
                        val b = beam[rng.nextInt(beam.size)].p
                        (a.take(rng.nextInt(a.size + 1)) + b.drop(rng.nextInt(b.size + 1))).take(policy.maxProgramLen)
                    }
                }
                evals++
                if (cand.isEmpty()) continue
                if (seen.size > 200_000) seen.clear()
                if (!seen.add(cand)) continue
                val f = fitness(cand, task.train)
                if (f < best - 1e-9) { best = f; improved = true }
                beam.add(Cand(f, cand))
                if (f < 1.0 && task.test.all { vm.run(cand, it.inputs) == it.output }) {
                    if (improved) wins[k]++
                    return Pair(Solved(cand, k), evals)
                }
            }
            beam.sortBy { it.f }
            if (beam.size > policy.beam) beam = ArrayList(beam.subList(0, policy.beam))
            if (improved) wins[k]++
        }
        return Pair(null, evals)
    }

    private fun minimize(prog: List<Instr>, all: List<Example>): List<Instr> {
        var cur = prog
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < cur.size) {
                val trial = ArrayList<Instr>(cur)
                trial.removeAt(i)
                if (trial.isNotEmpty() && all.all { e -> vm.run(trial, e.inputs) == e.output }) {
                    cur = trial
                    changed = true
                } else {
                    i++
                }
            }
        }
        return cur
    }

    private fun symbolLen(v: Long): Int = SymbolAlphabet.encode(longArrayOf((v shl 1) xor (v shr 63))).length

    private fun bump(id: Int) {
        skills[id]?.let { it.uses++ }
        store.bumpUse(id)
    }

    // ---- public API ----

    @Synchronized
    fun runRound(
        seed: Long,
        budgetMs: Long = policy.roundMillis,
        maxTasks: Int = policy.maxTasksPerRound,
        active: () -> Boolean = { true }
    ): MindReport {
        val rng = Random(seed)
        val deadline = System.currentTimeMillis() + budgetMs
        val traces = ArrayList<MindTrace>()
        val created = ArrayList<Skill>()
        var attempted = 0
        var solvedCount = 0
        var reused = 0
        var rawSyms = 0
        var progSyms = 0
        var taskNo = store.getMeta(K_TASKS)?.toLongOrNull() ?: 0L
        val pullsBefore = pulls.copyOf()
        val winsBefore = wins.copyOf()

        while (attempted < maxTasks && active() && System.currentTimeMillis() < deadline) {
            attempted++
            genIds = pickGenIds()
            val arity = 1 + rng.nextInt(2)
            val length = (difficulty + rng.nextInt(2)).coerceAtMost(policy.maxProgramLen)
            val task = makeTask(rng, arity, length) ?: continue
            taskNo++
            val t0 = System.currentTimeMillis()
            val (solved, evals) = solve(rng, task, deadline, active)
            var progLen = 0
            if (solved != null) {
                solvedCount++
                val all = task.train + task.test
                val minimal = minimize(solved.prog, all)
                progLen = minimal.size
                val sig = signature(arity, minimal)
                val calls = minimal.filter { it.op == MindOp.CALL }.map { it.arg.toInt() }.distinct()
                calls.forEach { bump(it); reused++ }
                val existing = sigToSkill[sig]
                if (existing != null) {
                    bump(existing)
                } else if (!isTrivial(sig)) {
                    val raw = all.sumOf { e -> e.inputs.sumOf { symbolLen(it) } + symbolLen(e.output) }
                    val prog = ProgCodec.toText(minimal).length
                    val id = store.addSkill(arity, minimal, STRATEGIES[solved.strategy], raw - prog)
                    val skill = Skill(id, arity, minimal, 0, raw - prog, STRATEGIES[solved.strategy])
                    skills[id] = skill
                    sigToSkill[sig] = id
                    created.add(skill)
                    rawSyms += raw
                    progSyms += prog
                }
            }
            traces.add(
                MindTrace(
                    taskNo, arity, difficulty, solved?.let { STRATEGIES[it.strategy] } ?: "-",
                    evals, solved != null, progLen, System.currentTimeMillis() - t0
                )
            )
            recent.add(if (solved != null) 1 else 0)
            while (recent.size > 10) recent.removeAt(0)
            if (recent.size >= 8) {
                val rate = recent.sum().toDouble() / recent.size
                if (rate >= 0.7 && difficulty < policy.maxProgramLen) { difficulty++; recent.clear() }
                else if (rate <= 0.2 && difficulty > 2) { difficulty--; recent.clear() }
            }
        }

        store.putMeta(K_DIFFICULTY, difficulty.toString())
        store.putMeta(K_STRATEGY, STRATEGIES.indices.joinToString(",") { "${pulls[it]},${wins[it]}" })
        store.putMeta(K_RECENT, recent.joinToString(""))
        store.putMeta(K_TASKS, taskNo.toString())

        var bestIdx = 0
        var bestRate = -1.0
        for (i in STRATEGIES.indices) {
            val rate = (wins[i] + 1.0) / (pulls[i] + 2.0)
            if (rate > bestRate) { bestRate = rate; bestIdx = i }
        }
        val summary = "Mind: $attempted tugas, $solvedCount berhasil, ${created.size} skill baru (total ${skills.size}), " +
            "dipakai ulang $reused, kesulitan $difficulty, cara berfikir terbaik '${STRATEGIES[bestIdx]}'" +
            if (progSyms > 0) ", pemadatan $rawSyms→$progSyms simbol." else "."
        return MindReport(attempted, solvedCount, created, reused, difficulty, skills.size, STRATEGIES[bestIdx], traces, rawSyms, progSyms, summary)
    }

    @Synchronized
    fun status(): String {
        val ways = STRATEGIES.indices.joinToString(" · ") { "${STRATEGIES[it]} ${wins[it]}/${pulls[it]}" }
        return "skill=${skills.size} · kesulitan=$difficulty · cara berfikir: $ways"
    }

    @Synchronized
    fun skillCount(): Int = skills.size
}
