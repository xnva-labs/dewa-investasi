package com.xnvalabs.xnai

import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/** Restricted in-process experiment runner. It never evaluates arbitrary generated shell/code. */
class XnaiSafeSandbox(private val policy: () -> SandboxPolicy) {
    enum class ExperimentKind { SORT_VARIANT, SEARCH_VARIANT, NUMERIC_DIFFERENTIAL, COMPRESSION_ACCOUNTING, CROSS_CHECK }

    data class Result(
        val status: String,
        val verified: Boolean,
        val measurement: String,
        val conclusion: String,
        val inputFingerprint: String
    )

    fun run(plan: AlgorithmSynthesis.Plan): Result {
        val p = policy()
        if (!p.enabled) return Result("blocked", false, "", "Sandbox disabled by policy.", plan.describe.sha256Hex())
        val started = System.nanoTime()
        val result = when (plan.kind) {
            ExperimentKind.SORT_VARIANT -> sortVariant(p)
            ExperimentKind.SEARCH_VARIANT -> searchVariant(p)
            ExperimentKind.NUMERIC_DIFFERENTIAL -> numericDifferential(p)
            ExperimentKind.COMPRESSION_ACCOUNTING -> compressionAccounting(p)
            ExperimentKind.CROSS_CHECK -> crossCheck(p)
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000L
        return if (elapsed > p.maxExperimentMs) result.copy(
            status = "timeout",
            verified = false,
            conclusion = "Sandbox budget exceeded (${elapsed}ms > ${p.maxExperimentMs}ms). Result retained but not validated."
        ) else result
    }

    private fun sortVariant(p: SandboxPolicy): Result {
        val n = minOf(p.maxGeneratedItems, 4_000)
        val base = IntArray(n) { ((it * 1103515245L + 12345L) xor (it.toLong() shl 17)).toInt() }
        val expected = base.sortedArray()
        val actual = base.copyOf()
        val t = measureTimeMillis { binaryInsertion(actual) }
        val ok = actual.contentEquals(expected)
        return Result(if (ok) "verified" else "failed", ok, "n=$n; binary-insertion=${t}ms; invariant=sorted+permutation", if (ok) "Variant lolos differential check terhadap baseline sortedArray()." else "Variant mengubah atau salah mengurutkan data.", (base.joinToString(",")).sha256Hex())
    }

    private fun searchVariant(p: SandboxPolicy): Result {
        val n = minOf(p.maxGeneratedItems, 20_000)
        val data = IntArray(n) { it * 2 }
        val targets = intArrayOf(0, n - 1, n / 2, -10, n * 2)
        var ok = true
        val elapsed = measureTimeMillis {
            for (target in targets) {
                val a = gallopingSearch(data, target)
                val b = data.indexOf(target)
                if (a != b) ok = false
            }
        }
        return Result(if (ok) "verified" else "failed", ok, "n=$n; cases=${targets.size}; ${elapsed}ms", if (ok) "Adaptive search matches baseline indices on boundary and interior cases." else "Search variant differs from baseline.", "search-$n".sha256Hex())
    }

    private fun numericDifferential(p: SandboxPolicy): Result {
        var ok = true
        var mismatches = 0
        val samples = minOf(p.maxGeneratedItems, 2_000)
        val random = Random(20261004)
        repeat(samples) {
            val a = random.nextInt(1, 100_000)
            val b = random.nextInt(1, 10_000)
            val g1 = gcd(a, b)
            val g2 = naiveGcd(a, b)
            if (g1 != g2) { ok = false; mismatches++ }
        }
        return Result(if (ok) "verified" else "failed", ok, "samples=$samples; mismatches=$mismatches", if (ok) "Deterministic GCD variant matched differential oracle." else "Numeric implementation mismatch detected.", "gcd-$samples".sha256Hex())
    }

    private fun compressionAccounting(p: SandboxPolicy): Result {
        val source = "ABRACADABRA".repeat(minOf(20_000, p.maxGeneratedItems))
        val frequencies = source.groupingBy { it }.eachCount()
        val naiveBits = source.length * 8L
        val payloadBits = source.length.toLong() * 4L
        val dictionaryBits = frequencies.size * 16L
        val total = payloadBits + dictionaryBits
        val saving = naiveBits - total
        val ok = saving > 0
        return Result(if (ok) "verified" else "inconclusive", ok, "source=${source.length}B; naive=${naiveBits}b; modeled=$total" , if (ok) "Cost-aware accounting predicts net savings for repetitive data; this is an accounting experiment, not universal compression proof." else "Dictionary overhead erases modeled payload savings.", source.take(200).sha256Hex())
    }

    private fun crossCheck(p: SandboxPolicy): Result {
        val a = (0 until minOf(1_000, p.maxGeneratedItems)).toList()
        val sum1 = a.sumOf { it.toLong() }
        val sum2 = a.size.toLong() * (a.size - 1L) / 2L
        val ok = sum1 == sum2
        return Result(if (ok) "verified" else "failed", ok, "n=${a.size}; invariant=sum arithmetic", if (ok) "Independent implementations agree on a known invariant." else "Invariant mismatch.", a.joinToString(",").sha256Hex())
    }

    private fun binaryInsertion(a: IntArray) {
        for (i in 1 until a.size) {
            val x = a[i]
            var lo = 0
            var hi = i
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (a[mid] <= x) lo = mid + 1 else hi = mid
            }
            var j = i
            while (j > lo) { a[j] = a[j - 1]; j-- }
            a[lo] = x
        }
    }

    private fun gallopingSearch(a: IntArray, target: Int): Int {
        if (a.isEmpty()) return -1
        if (a[0] == target) return 0
        var bound = 1
        while (bound < a.size && a[bound] < target) bound = bound shl 1
        var lo = bound ushr 1
        var hi = minOf(bound, a.size - 1)
        if (a[hi] < target) return -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                a[mid] < target -> lo = mid + 1
                a[mid] > target -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun gcd(a0: Int, b0: Int): Int { var a = abs(a0); var b = abs(b0); while (b != 0) { val r = a % b; a = b; b = r }; return a }
    private fun naiveGcd(a: Int, b: Int): Int { val m = minOf(a, b); for (d in m downTo 1) if (a % d == 0 && b % d == 0) return d; return 1 }
}
