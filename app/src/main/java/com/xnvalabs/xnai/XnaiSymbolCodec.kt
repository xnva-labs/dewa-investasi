package com.xnvalabs.xnai

/**
 * Self-delimiting numeric alphabet: every value is written with 5 bits per character and needs no separator.
 * Continuation characters and terminal characters are disjoint sets, so a stream decodes unambiguously.
 */
object SymbolAlphabet {
    private const val CONT = "abcdefghijklmnopqrstuvwxyzABCDEF"
    private const val TERM = "0123456789GHIJKLMNOPQRSTUVWXYZ-_"
    private val contIndex = IntArray(128) { -1 }
    private val termIndex = IntArray(128) { -1 }

    init {
        CONT.forEachIndexed { i, c -> contIndex[c.code] = i }
        TERM.forEachIndexed { i, c -> termIndex[c.code] = i }
    }

    fun put(sb: StringBuilder, value: Long) {
        require(value >= 0) { "Nilai simbol harus non-negatif." }
        var x = value
        val tmp = CharArray(14)
        var n = 0
        tmp[n++] = TERM[(x and 31L).toInt()]
        x = x ushr 5
        while (x > 0) {
            tmp[n++] = CONT[(x and 31L).toInt()]
            x = x ushr 5
        }
        for (i in n - 1 downTo 0) sb.append(tmp[i])
    }

    fun encode(values: LongArray): String {
        val sb = StringBuilder(values.size * 3)
        values.forEach { put(sb, it) }
        return sb.toString()
    }

    fun decode(text: String): LongArray {
        val out = ArrayList<Long>(text.length / 2 + 1)
        var acc = 0L
        var open = false
        for (ch in text) {
            val code = ch.code
            if (code >= 128) error("Karakter simbol tidak valid.")
            val t = termIndex[code]
            if (t >= 0) {
                out.add(acc * 32L + t)
                acc = 0L
                open = false
                continue
            }
            val c = contIndex[code]
            if (c < 0) error("Karakter simbol tidak valid.")
            acc = acc * 32L + c
            open = true
        }
        check(!open) { "Aliran simbol terpotong." }
        return out.toLongArray()
    }
}

/** Storage backend for the symbol tables. SQLite in the app, in-memory in tests. */
interface SymbolBackend {
    fun wordId(token: String): Long?
    fun wordOf(id: Long): String?
    fun exactId(token: String): Long
    fun exactOf(id: Long): String?
    fun mergeFor(a: Long, b: Long): Long?
    fun createMerge(a: Long, b: Long): Long
    fun mergeChildren(id: Long): Pair<Long, Long>?
}

class LexiconSymbolBackend(private val store: XnaiLexiconStore) : SymbolBackend {
    override fun wordId(token: String): Long? = try {
        (store.lookup(token) ?: store.register(token, "xnai-thought")).toLongOrNull()
    } catch (_: Throwable) { null }

    override fun wordOf(id: Long): String? = store.wordForId(id.toString())
    override fun exactId(token: String): Long = store.exactId(token)
    override fun exactOf(id: Long): String? = store.exactToken(id)
    override fun mergeFor(a: Long, b: Long): Long? = store.mergeFor(a, b)
    override fun createMerge(a: Long, b: Long): Long = store.createMerge(a, b)
    override fun mergeChildren(id: Long): Pair<Long, Long>? = store.mergeChildren(id)
}

class InMemorySymbolBackend : SymbolBackend {
    private val words = HashMap<String, Long>()
    private val wordsRev = HashMap<Long, String>()
    private val exact = HashMap<String, Long>()
    private val exactRev = HashMap<Long, String>()
    private val merges = HashMap<Pair<Long, Long>, Long>()
    private val mergesRev = HashMap<Long, Pair<Long, Long>>()

    override fun wordId(token: String): Long? {
        val w = java.text.Normalizer.normalize(token.trim().lowercase(), java.text.Normalizer.Form.NFKC)
        if (w.isEmpty()) return null
        return words.getOrPut(w) { (words.size + 1).toLong().also { wordsRev[it] = w } }
    }
    override fun wordOf(id: Long): String? = wordsRev[id]
    override fun exactId(token: String): Long = exact.getOrPut(token) { (exact.size + 1).toLong().also { exactRev[it] = token } }
    override fun exactOf(id: Long): String? = exactRev[id]
    override fun mergeFor(a: Long, b: Long): Long? = merges[a to b]
    override fun createMerge(a: Long, b: Long): Long =
        merges.getOrPut(a to b) { (merges.size + 1).toLong().also { mergesRev[it] = a to b } }
    override fun mergeChildren(id: Long): Pair<Long, Long>? = mergesRev[id]
}

/**
 * Lossless text -> symbol codec.
 *
 * Every element is a Long: (id shl 3) or kind.
 *  kind 0/1/2 = lexicon word (lower / Capitalized / UPPER), 3 = exact token, 4 = learned composite symbol,
 *  5 = 15-hex-digit chunk, 6 = short hex tail, 7 = decimal integer.
 * Composite symbols are learned (pair merges) from XNAI's own recurring thoughts and nest without limit.
 */
class SymbolCodec(private val backend: SymbolBackend, initialThreshold: Int = 3) {
    /** How often a pair must recur before XNAI binds it to a new symbol. XNAI tunes this itself from measured results. */
    var threshold: Int = initialThreshold.coerceIn(MIN_T, MAX_T)
        private set
    var onPolicyChange: ((Int) -> Unit)? = null
    private var winRaw = 0L
    private var winOut = 0L
    private var winMerges = 0
    private var winRecords = 0
    private var prevScore = -1.0
    private var direction = -1

    private data class Key(val a: Long, val b: Long)

    private val mergeCache = HashMap<Key, Long>()
    private val childCache = HashMap<Long, Pair<Long, Long>>()
    private val wordCache = HashMap<String, Long>()
    private val wordOfCache = HashMap<Long, String>()
    private val exactCache = HashMap<String, Long>()
    private val exactOfCache = HashMap<Long, String>()
    private val pairCounts = HashMap<Key, Int>()
    private val useCounts = HashMap<Long, Int>()

    companion object {
        private val RUN = Regex("[\\p{L}\\p{N}_]+")
        private const val CACHE_MAX = 400_000
        private const val MIN_T = 2
        private const val MAX_T = 12
        private const val TUNE_WINDOW = 150
        private fun cap(s: String): String = if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)
    }

    @Synchronized
    fun exactIdOf(token: String): Long = exactIdCached(token)

    @Synchronized
    fun exactTokenOf(id: Long): String? = exactOfCached(id)

    @Synchronized
    fun encode(text: String): LongArray {
        val tokens = ArrayList<Long>(text.length / 3 + 4)
        tokenize(text, tokens)
        val merged = applyMerges(tokens)
        winRaw += tokens.size
        winOut += merged.size
        winRecords++
        learn(merged)
        tune()
        merged.forEach { if ((it and 7L) == 4L) useCounts[it] = (useCounts[it] ?: 0) + 1 }
        return merged.toLongArray()
    }

    @Synchronized
    fun encodeToString(text: String): String = SymbolAlphabet.encode(encode(text))

    @Synchronized
    fun decode(values: LongArray): String {
        val sb = StringBuilder()
        val stack = ArrayDeque<Long>()
        for (v in values) {
            stack.addLast(v)
            while (stack.isNotEmpty()) expand(stack.removeLast(), sb, stack)
        }
        return sb.toString()
    }

    @Synchronized
    fun decodeString(symbols: String): String = decode(SymbolAlphabet.decode(symbols))

    /** The composite symbols XNAI used most since the last call; used by XNAI to name its own files. */
    @Synchronized
    fun takeDominantSymbols(n: Int): List<Long> {
        val top = useCounts.entries.sortedByDescending { it.value }.take(n).map { it.key }
        useCounts.clear()
        return top
    }

    // ---- tokenization ----

    private fun tokenize(text: String, out: MutableList<Long>) {
        var last = 0
        for (m in RUN.findAll(text)) {
            if (m.range.first > last) emitExact(text.substring(last, m.range.first), out)
            emitRun(m.value, out)
            last = m.range.last + 1
        }
        if (last < text.length) emitExact(text.substring(last), out)
    }

    private fun emitExact(s: String, out: MutableList<Long>) {
        var i = 0
        while (i < s.length) {
            var end = minOf(s.length, i + 64)
            if (end < s.length && Character.isLowSurrogate(s[end])) end--
            out.add((exactIdCached(s.substring(i, end)) shl 3) or 3L)
            i = end
        }
    }

    private fun emitRun(r: String, out: MutableList<Long>) {
        if (r.length <= 18 && r.all { it in '0'..'9' } && (r.length == 1 || r[0] != '0')) {
            out.add((r.toLong() shl 3) or 7L)
            return
        }
        if (r.length >= 16 && r.all { it in '0'..'9' || it in 'a'..'f' }) {
            var i = 0
            while (i + 15 <= r.length) {
                out.add((r.substring(i, i + 15).toLong(16) shl 3) or 5L)
                i += 15
            }
            if (i < r.length) {
                val tail = r.substring(i)
                val packed = (tail.length.toLong() shl 56) or tail.toLong(16)
                out.add((packed shl 3) or 6L)
            }
            return
        }
        if (r.length <= 48) {
            wordCache[r]?.let { out.add(it); return }
            val id = backend.wordId(r)
            val base = id?.let { wordOfCached(it) }
            if (id != null && base != null) {
                val kind = when {
                    r == base -> 0L
                    r == cap(base) -> 1L
                    r == base.uppercase() -> 2L
                    else -> -1L
                }
                if (kind >= 0) {
                    val v = (id shl 3) or kind
                    if (wordCache.size > CACHE_MAX) wordCache.clear()
                    wordCache[r] = v
                    out.add(v)
                    return
                }
            }
        }
        emitExact(r, out)
    }

    /** Hill-climbs the binding threshold on (symbols written + dictionary growth) / raw tokens. */
    private fun tune() {
        if (winRecords < TUNE_WINDOW) return
        val score = (winOut + 3.0 * winMerges) / maxOf(1L, winRaw).toDouble()
        if (prevScore >= 0.0 && score > prevScore) direction = -direction
        prevScore = score
        val next = (threshold + direction).coerceIn(MIN_T, MAX_T)
        if (next == threshold) {
            direction = -direction
        } else {
            threshold = next
            onPolicyChange?.invoke(next)
        }
        winRaw = 0L; winOut = 0L; winMerges = 0; winRecords = 0
    }

    // ---- composite symbols ----

    private fun mergeOf(a: Long, b: Long): Long? {
        val key = Key(a, b)
        mergeCache[key]?.let { return if (it < 0) null else it }
        val id = backend.mergeFor(a, b)
        if (mergeCache.size > CACHE_MAX) mergeCache.clear()
        mergeCache[key] = if (id == null) -1L else (id shl 3) or 4L
        return id?.let { (it shl 3) or 4L }
    }

    private fun applyMerges(input: List<Long>): List<Long> {
        var cur = input
        while (true) {
            val out = ArrayList<Long>(cur.size)
            var i = 0
            var changed = false
            while (i < cur.size) {
                if (i + 1 < cur.size) {
                    val m = mergeOf(cur[i], cur[i + 1])
                    if (m != null) {
                        out.add(m)
                        i += 2
                        changed = true
                        continue
                    }
                }
                out.add(cur[i])
                i++
            }
            cur = out
            if (!changed) return cur
        }
    }

    private fun learn(list: List<Long>) {
        if (pairCounts.size > 300_000) pairCounts.clear()
        for (i in 0 until list.size - 1) {
            val key = Key(list[i], list[i + 1])
            val n = (pairCounts[key] ?: 0) + 1
            if (n >= threshold) {
                pairCounts.remove(key)
                if (mergeOf(key.a, key.b) == null) {
                    val id = backend.createMerge(key.a, key.b)
                    winMerges++
                    mergeCache[key] = (id shl 3) or 4L
                }
            } else {
                pairCounts[key] = n
            }
        }
    }

    // ---- decoding ----

    private fun expand(v: Long, sb: StringBuilder, stack: ArrayDeque<Long>) {
        val kind = (v and 7L).toInt()
        val id = v ushr 3
        when (kind) {
            0, 1, 2 -> {
                val base = wordOfCached(id) ?: error("Simbol kata tidak dikenal: $id")
                sb.append(when (kind) { 0 -> base; 1 -> cap(base); else -> base.uppercase() })
            }
            3 -> sb.append(exactOfCached(id) ?: error("Simbol eksak tidak dikenal: $id"))
            4 -> {
                val c = childrenOf(id) ?: error("Simbol komposit tidak dikenal: $id")
                stack.addLast(c.second)
                stack.addLast(c.first)
            }
            5 -> sb.append(java.lang.Long.toString(id, 16).padStart(15, '0'))
            6 -> {
                val len = (id ushr 56).toInt()
                val value = id and ((1L shl 56) - 1L)
                sb.append(java.lang.Long.toString(value, 16).padStart(len, '0'))
            }
            else -> sb.append(id.toString())
        }
    }

    private fun childrenOf(id: Long): Pair<Long, Long>? {
        childCache[id]?.let { return it }
        val c = backend.mergeChildren(id) ?: return null
        if (childCache.size > CACHE_MAX) childCache.clear()
        childCache[id] = c
        return c
    }

    private fun wordOfCached(id: Long): String? {
        wordOfCache[id]?.let { return it }
        val w = backend.wordOf(id) ?: return null
        if (wordOfCache.size > CACHE_MAX) wordOfCache.clear()
        wordOfCache[id] = w
        return w
    }

    private fun exactIdCached(token: String): Long {
        exactCache[token]?.let { return it }
        val id = backend.exactId(token)
        if (exactCache.size > CACHE_MAX) exactCache.clear()
        exactCache[token] = id
        return id
    }

    private fun exactOfCached(id: Long): String? {
        exactOfCache[id]?.let { return it }
        val t = backend.exactOf(id) ?: return null
        if (exactOfCache.size > CACHE_MAX) exactOfCache.clear()
        exactOfCache[id] = t
        return t
    }
}

/** Display projection in the owner's notation (e.g. S³⁷⁸⁷³, A²²², joined with "+"). Never written to storage. */
object GlyphNotation {
    private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private const val LETTERS = "ABCEShtn"

    fun glyph(value: Long): String {
        val kind = (value and 7L).toInt()
        val id = (value ushr 3).toString()
        return buildString {
            append(LETTERS[kind])
            id.forEach { append(SUP[it - '0']) }
        }
    }

    fun render(values: LongArray, maxItems: Int = 24): String {
        val head = values.take(maxItems).joinToString("+") { glyph(it) }
        return if (values.size > maxItems) "$head+…(${values.size})" else head
    }
}
