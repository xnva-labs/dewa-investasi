package com.xnvalabs.xnai

/**
 * Exact byte-preserving hierarchical dictionary compression.
 * Candidate phrases of length 2..8 become recursive symbols only when net savings after
 * dictionary-entry cost are positive. Arbitrary incompressible data is not promised to shrink.
 */
object RecursiveCompression {
    data class Symbol(val id: Int, val children: IntArray)

    data class Result(
        val payload: IntArray,
        val dictionary: List<Symbol>,
        val originalBytes: Int,
        val estimatedEncodedBytes: Int,
        val levels: Int
    ) {
        fun decode(): ByteArray {
            val map = dictionary.associateBy { it.id }
            val memo = HashMap<Int, ByteArray>()
            fun expand(id: Int): ByteArray {
                memo[id]?.let { return it }
                map[id]?.let { symbol ->
                    val out = java.io.ByteArrayOutputStream()
                    symbol.children.forEach { child -> out.write(expand(child)) }
                    return out.toByteArray().also { memo[id] = it }
                }
                return byteArrayOf(id.toByte()).also { memo[id] = it }
            }
            val out = java.io.ByteArrayOutputStream(originalBytes)
            payload.forEach { out.write(expand(it)) }
            return out.toByteArray()
        }
    }

    fun compress(input: ByteArray, maxLevels: Int = 4096): Result {
        if (input.isEmpty()) return Result(IntArray(0), emptyList(), 0, 0, 0)
        var sequence = input.map { it.toInt() and 0xFF }
        val dictionary = mutableListOf<Symbol>()
        var nextId = 256
        var level = 0

        while (level < maxLevels && sequence.size >= 4) {
            data class Candidate(val phrase: List<Int>, val count: Int, val saving: Long)
            var best: Candidate? = null
            val maxPhrase = minOf(8, sequence.size)
            for (length in 2..maxPhrase) {
                val counts = HashMap<List<Int>, Int>()
                var i = 0
                while (i + length <= sequence.size) {
                    val phrase = sequence.subList(i, i + length).toList()
                    counts[phrase] = (counts[phrase] ?: 0) + 1
                    i++
                }
                counts.forEach { (phrase, _) ->
                    val count = nonOverlappingCount(sequence, phrase)
                    if (count < 2) return@forEach
                    val newId = nextId
                    val oldCost = count.toLong() * phrase.sumOf(::varIntSize)
                    val newCost = count.toLong() * varIntSize(newId) + varIntSize(newId).toLong() + phrase.sumOf(::varIntSize)
                    val saving = oldCost - newCost
                    if (saving > 0 && (best == null || saving > best!!.saving)) best = Candidate(phrase, count, saving)
                }
            }
            val selected = best ?: break
            val id = nextId++
            val next = ArrayList<Int>()
            var i = 0
            while (i < sequence.size) {
                val matches = i + selected.phrase.size <= sequence.size && sequence.subList(i, i + selected.phrase.size) == selected.phrase
                if (matches) {
                    next += id
                    i += selected.phrase.size
                } else {
                    next += sequence[i]
                    i++
                }
            }
            sequence = next
            dictionary += Symbol(id, selected.phrase.toIntArray())
            level++
        }

        val payloadBytes = sequence.sumOf(::varIntSize)
        val dictionaryBytes = dictionary.sumOf { varIntSize(it.id) + it.children.sumOf(::varIntSize) }
        return Result(sequence.toIntArray(), dictionary, input.size, payloadBytes + dictionaryBytes, level)
    }

    fun estimateCompressionRatio(result: Result): Double =
        if (result.estimatedEncodedBytes == 0) Double.POSITIVE_INFINITY else result.originalBytes.toDouble() / result.estimatedEncodedBytes


    private fun nonOverlappingCount(sequence: List<Int>, phrase: List<Int>): Int {
        var count = 0
        var i = 0
        while (i + phrase.size <= sequence.size) {
            if (sequence.subList(i, i + phrase.size) == phrase) {
                count++
                i += phrase.size
            } else i++
        }
        return count
    }

    private fun varIntSize(value: Int): Int {
        var v = value
        var size = 1
        while (v ushr 7 != 0) { v = v ushr 7; size++ }
        return size
    }
}
