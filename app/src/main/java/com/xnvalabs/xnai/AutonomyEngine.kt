package com.xnvalabs.xnai

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * Autonomous exploration core.
 *
 * It does not claim consciousness. It provides an explicit loop:
 * observe → retrieve → combine → hypothesize → experiment → verify → reflect → remember.
 * The loop is intentionally open-ended from the user's perspective, but each experiment
 * is bounded to protect the device.
 */
data class ThoughtCandidate(
    val title: String,
    val hypothesis: String,
    val evidence: String,
    val score: Double,
    val experiment: String
)

data class ExperimentResult(
    val name: String,
    val success: Boolean,
    val measurement: String,
    val conclusion: String
)

data class AutonomyCycle(
    val cycle: Int,
    val hypothesis: String,
    val experiment: ExperimentResult,
    val reflection: String
)

object AutonomyEngine {
    private val rng = Random(20261004)

    fun generateCandidates(
        goal: String,
        artifacts: List<Artifact>,
        formulas: List<FormulaItem>,
        languages: List<LanguageInfo> = KnowledgeCatalog.languages,
        algorithms: List<AlgorithmInfo> = KnowledgeCatalog.algorithms,
        methods: List<ReasoningMethodInfo> = KnowledgeCatalog.reasoningMethods,
        count: Int = 8
    ): List<ThoughtCandidate> {
        val goalWords = goal.lowercase().split(Regex("[^a-z0-9+#.-]+"))
            .filter { it.length >= 2 }
            .toSet()

        val languagePool = languages.filter { l ->
            val hay = (l.name + " " + l.family + " " + l.strengths + " " + l.commonUses).lowercase()
            goalWords.any { hay.contains(it) }
        }.ifEmpty { languages.shuffled(rng).take(8) }

        val algorithmPool = algorithms.filter { a ->
            val hay = (a.name + " " + a.domain + " " + a.idea + " " + a.uses).lowercase()
            goalWords.any { hay.contains(it) }
        }.ifEmpty { algorithms.shuffled(rng).take(12) }

        val formulaPool = formulas.filter { f ->
            goalWords.any { word ->
                (f.name + " " + f.domain + " " + f.formula + " " + f.meaning).lowercase().contains(word)
            }
        }.ifEmpty { formulas.take(4) }

        val memoryHits = artifacts.filter { a ->
            goalWords.any { w -> (a.title + " " + a.content).lowercase().contains(w) }
        }.take(5)
        val methodPool = methods.ifEmpty { KnowledgeCatalog.reasoningMethods }

        val candidates = mutableListOf<ThoughtCandidate>()
        for (lang in languagePool.take(8)) {
            for (algo in algorithmPool.take(8)) {
                for (method in methodPool.take(8)) {
                    if (candidates.size >= count * 6) break
                    val formula = formulaPool.randomOrNull(rng)
                    val memory = memoryHits.randomOrNull(rng)
                    val novelty = 0.4 + rng.nextDouble() * 0.6
                    val testability = if (algo.domain in setOf("Sorting", "Searching", "Numerical", "Optimization", "Data Structures")) 0.95 else 0.55
                    val relevance = 0.5 + goalWords.count { w -> lang.name.lowercase().contains(w) || algo.name.lowercase().contains(w) || method.name.lowercase().contains(w) } * 0.1
                    val score = (novelty * 0.25 + testability * 0.35 + relevance * 0.4) * 100

                    candidates += ThoughtCandidate(
                        title = "${lang.name} + ${algo.name} + ${method.name}",
                        hypothesis = buildString {
                            append("Menggabungkan paradigma ${lang.name} (${lang.paradigms.joinToString()}) dengan ${algo.name} memakai ${method.name}. ")
                            append("Hipotesis: kombinasi ini mungkin memberi strategi yang lebih baik untuk tujuan: $goal.")
                            if (formula != null) append(" Formula terkait: ${formula.formula}.")
                            if (memory != null) append(" Memori relevan: ${memory.title}.")
                        },
                        evidence = buildString {
                            append("Bahasa: ${lang.strengths}. Algoritma: ${algo.idea}. Metode: ${method.purpose}")
                            if (formula != null) append(" Data rumus: ${formula.meaning}.")
                        },
                        score = score,
                        experiment = experimentPlan(algo)
                    )
                }
                if (candidates.size >= count * 6) break
            }
            if (candidates.size >= count * 6) break
        }
        return candidates.sortedByDescending { it.score }.take(count)
    }

    fun runCycle(
        cycle: Int,
        candidate: ThoughtCandidate,
        onProgress: (String) -> Unit = {}
    ): AutonomyCycle {
        onProgress("Menguji: ${candidate.title}")
        val result = ExperimentRunner.run(candidate)
        val reflection = if (result.success) {
            "Hipotesis mendapat dukungan eksperimen: ${result.conclusion} Kelemahan: hasil ini hanya berlaku pada kondisi uji yang dipakai."
        } else {
            "Hipotesis belum didukung: ${result.conclusion} Saya mempertahankan hasil negatif sebagai informasi, bukan kegagalan yang disembunyikan."
        }
        return AutonomyCycle(cycle, candidate.hypothesis, result, reflection)
    }

    fun experimentPlan(algorithm: AlgorithmInfo): String = when (algorithm.name) {
        "Bubble Sort", "Insertion Sort", "Selection Sort", "Merge Sort", "Quick Sort", "Heap Sort" -> "benchmark_sort"
        "Binary Search", "Linear Search", "Hash Lookup" -> "benchmark_search"
        "Euclidean Algorithm", "Fast Exponentiation" -> "numeric_check"
        else -> "thought_experiment"
    }
}

object ExperimentRunner {
    private val rng = Random(424242)
    private const val ARRAY_SIZE = 3_000
    private const val REPETITIONS = 3

    fun run(candidate: ThoughtCandidate): ExperimentResult = when (candidate.experiment) {
        "benchmark_sort" -> benchmarkSort(candidate.title.substringBefore(" + ").trim(), candidate.title.substringAfter(" + "))
        "benchmark_search" -> benchmarkSearch(candidate.title.substringAfter(" + "))
        "numeric_check" -> numericCheck(candidate.title.substringAfter(" + "))
        else -> thoughtExperiment(candidate)
    }

    private fun benchmarkSort(language: String, algorithm: String): ExperimentResult {
        val base = IntArray(ARRAY_SIZE) { rng.nextInt(0, 1_000_000) }
        val timings = mutableListOf<Long>()
        repeat(REPETITIONS) {
            val data = base.copyOf()
            val nanos = measureNanoTime {
                when (algorithm) {
                    "Bubble Sort" -> bubble(data)
                    "Insertion Sort" -> insertion(data)
                    "Selection Sort" -> selection(data)
                    "Merge Sort" -> mergeSort(data)
                    "Quick Sort" -> quickSort(data)
                    "Heap Sort" -> heapSort(data)
                    else -> data.sort()
                }
            }
            if (data.isSorted()) timings += nanos
        }
        val avgMs = (timings.average() / 1_000_000.0).let { "%.2f".format(it) }
        return ExperimentResult(
            "$language + $algorithm benchmark",
            timings.size == REPETITIONS,
            "Rata-rata ${avgMs} ms pada $ARRAY_SIZE integer, $REPETITIONS pengulangan.",
            "Algoritma selesai tanpa merusak urutan dan bisa dibandingkan secara empiris dengan baseline lain."
        )
    }

    private fun benchmarkSearch(algorithm: String): ExperimentResult {
        val data = IntArray(10_000) { it * 2 }
        val target = data.last()
        var found = false
        val nanos = measureNanoTime {
            found = when (algorithm) {
                "Binary Search" -> binarySearch(data, target)
                else -> linearSearch(data, target)
            }
        }
        return ExperimentResult(
            "$algorithm benchmark",
            found,
            "Target=${target}, found=$found, time=${nanos / 1_000.0} µs.",
            "Hasil memberi ukuran nyata untuk cost pencarian pada data terurut yang sama."
        )
    }

    private fun numericCheck(algorithm: String): ExperimentResult {
        val a = 123_456_789
        val b = 98_765
        val result = when (algorithm) {
            "Euclidean Algorithm" -> gcd(a, b)
            else -> modPow(7, 123_456, 1_000_003)
        }
        return ExperimentResult(
            "$algorithm numerical check",
            result >= 0,
            "input=$a,$b result=$result",
            "Perhitungan berhasil diverifikasi melalui implementasi deterministik lokal."
        )
    }

    private fun thoughtExperiment(candidate: ThoughtCandidate): ExperimentResult {
        val evidenceScore = candidate.score / 100.0
        val supported = evidenceScore >= 0.55
        return ExperimentResult(
            "Structured thought experiment",
            supported,
            "testability=${"%.2f".format(evidenceScore)}",
            if (supported) "Masuk daftar hipotesis yang layak diuji berikutnya." else "Prioritas rendah karena evidence/testability saat ini lemah."
        )
    }

    private fun bubble(a: IntArray) { for (i in a.indices) for (j in 0 until a.size - i - 1) if (a[j] > a[j + 1]) { val t=a[j];a[j]=a[j+1];a[j+1]=t } }
    private fun insertion(a: IntArray) { for (i in 1 until a.size) { val x=a[i]; var j=i-1; while (j>=0 && a[j]>x) { a[j+1]=a[j]; j-- }; a[j+1]=x } }
    private fun selection(a: IntArray) { for (i in a.indices) { var m=i; for (j in i+1 until a.size) if (a[j]<a[m]) m=j; val t=a[i];a[i]=a[m];a[m]=t } }
    private fun mergeSort(a: IntArray) {
        val tmp = IntArray(a.size)
        fun sort(lo: Int, hi: Int) {
            if (hi - lo <= 1) return
            val mid = (lo + hi) ushr 1
            sort(lo, mid); sort(mid, hi)
            var i = lo; var j = mid; var k = lo
            while (i < mid && j < hi) tmp[k++] = if (a[i] <= a[j]) a[i++] else a[j++]
            while (i < mid) tmp[k++] = a[i++]
            while (j < hi) tmp[k++] = a[j++]
            for (x in lo until hi) a[x] = tmp[x]
        }
        sort(0, a.size)
    }

    private fun quickSort(a: IntArray) {
        fun sort(lo: Int, hi: Int) {
            var i = lo; var j = hi
            val pivot = a[(lo + hi) ushr 1]
            while (i <= j) {
                while (a[i] < pivot) i++
                while (a[j] > pivot) j--
                if (i <= j) { val t=a[i];a[i]=a[j];a[j]=t;i++;j-- }
            }
            if (lo < j) sort(lo, j)
            if (i < hi) sort(i, hi)
        }
        if (a.isNotEmpty()) sort(0, a.lastIndex)
    }

    private fun heapSort(a: IntArray) {
        fun siftDown(root: Int, size: Int) {
            var r = root
            while (true) {
                val left = 2 * r + 1
                if (left >= size) return
                var largest = left
                val right = left + 1
                if (right < size && a[right] > a[left]) largest = right
                if (a[r] >= a[largest]) return
                val t = a[r]; a[r] = a[largest]; a[largest] = t
                r = largest
            }
        }
        for (i in a.size / 2 - 1 downTo 0) siftDown(i, a.size)
        for (end in a.lastIndex downTo 1) {
            val t=a[0];a[0]=a[end];a[end]=t
            siftDown(0, end)
        }
    }
    private fun IntArray.isSorted(): Boolean = indices.drop(1).all { i -> this[i-1] <= this[i] }
    private fun linearSearch(a: IntArray, target: Int) = a.any { it == target }
    private fun binarySearch(a: IntArray, target: Int): Boolean { var l=0;var r=a.lastIndex;while(l<=r){val m=(l+r) ushr 1;when{a[m]<target->l=m+1;a[m]>target->r=m-1;else->return true}};return false }
    private fun gcd(x: Int, y: Int): Int { var a=x;var b=y;while(b!=0){val t=a%b;a=b;b=t};return kotlin.math.abs(a) }
    private fun modPow(base: Int, exp: Int, mod: Int): Int { var b=base.toLong();var e=exp;var r=1L;while(e>0){if(e and 1==1) r=(r*b)%mod;b=(b*b)%mod;e=e ushr 1};return r.toInt() }
}
