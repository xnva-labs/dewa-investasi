package com.xnvalabs.xnai

import android.content.Context
import java.security.MessageDigest

/**
 * Word IDs are simple sequential integers. Composite symbols deliberately do not use
 * sequential numbers: their identity is a stable content-derived code.
 */
data class CodebookSymbol(
    val id: String,
    val level: Int,
    val children: List<String>,
    val definition: String,
    val version: Int,
    val provenance: String,
    val checksum: String
)

class RecursiveCodebook(
    private val namespace: String = "xnai-v1",
    context: Context? = null
) {
    private val persistent = context?.let(::XnaiLexiconStore)
    private val symbols = linkedMapOf<String, CodebookSymbol>()
    private val lexical = linkedMapOf<String, String>()

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    @Synchronized
    fun registerToken(token: String, provenance: String): CodebookSymbol {
        val normalized = token.trim().lowercase()
        require(normalized.isNotEmpty()) { "Token kosong tidak dapat diregistrasi." }
        persistent?.let { store ->
            val existing = store.lookup(normalized)
            val id = existing ?: store.register(normalized, provenance)
            return CodebookSymbol(id, 0, emptyList(), normalized, 1, provenance, digest("$id|$normalized"))
        }
        lexical[normalized]?.let { return symbols.getValue(it) }
        val id = ((symbols.keys.mapNotNull { it.toBigIntegerOrNull() }.maxOrNull()?.plus(java.math.BigInteger.ONE)) ?: java.math.BigInteger.ONE).toString()
        val item = CodebookSymbol(id, 0, emptyList(), normalized, 1, provenance, digest("$id|$normalized"))
        symbols[id] = item
        lexical[normalized] = id
        return item
    }

    /** Efficient bulk lexical registration. Numeric IDs are allocated in first-seen order. */
    @Synchronized
    fun registerTokens(tokens: Iterable<String>, provenance: String): Long {
        val store = persistent
        if (store != null) return store.registerBatch(tokens, provenance)
        var added = 0L
        tokens.forEach { token ->
            val before = tokenId(token)
            registerToken(token, provenance)
            if (before == null) added++
        }
        return added
    }

    @Synchronized
    fun restore(symbol: CodebookSymbol) {
        if (symbol.children.isEmpty() && symbol.id.toBigIntegerOrNull() != null) {
            persistent?.restoreWord(symbol.id, symbol.definition, symbol.provenance) ?: run {
                lexical[symbol.definition.lowercase()] = symbol.id
                symbols.putIfAbsent(symbol.id, symbol)
            }
        } else {
            persistent?.putSymbol(symbol)
            symbols.putIfAbsent(symbol.id, symbol)
        }
    }

    @Synchronized
    fun combine(children: List<String>, definition: String, provenance: String): CodebookSymbol {
        require(children.size >= 2) { "Minimal dua child symbol." }
        require(children.distinct().size == children.size) { "Child symbol tidak boleh duplikat." }
        require(children.all(::exists)) { "Semua child harus sudah ada." }
        val canonical = children.joinToString("|")
        val id = "S-" + digest("$namespace|$canonical").take(24)
        get(id)?.let { return it }
        val level = children.maxOf { levelOf(it) } + 1
        val safeDefinition = definition.ifBlank { children.joinToString(" ") { decode(it) } }
        val item = CodebookSymbol(id, level, children.toList(), safeDefinition, 1, provenance, digest("$canonical|$safeDefinition"))
        persistent?.putSymbol(item) ?: symbols.put(id, item)
        return item
    }

    @Synchronized
    fun decode(id: String): String {
        word(id)?.let { return it }
        val symbol = get(id) ?: error("Definisi simbol tidak ditemukan: $id")
        return symbol.children.joinToString(" ") { decode(it) }
    }

    @Synchronized fun get(id: String): CodebookSymbol? = persistent?.getSymbol(id) ?: symbols[id]

    @Synchronized fun all(limit: Int = 5000): List<CodebookSymbol> = persistent?.allSymbols(limit) ?: symbols.values.take(limit)

    @Synchronized fun tokenId(token: String): String? = persistent?.lookup(token) ?: lexical[token.trim().lowercase()]

    /**
     * Returns only opaque structural metadata for display/export. Do not expose definitions
     * or decoded lexical content through diagnostics, analytics, or public UI.
     */
    @Synchronized
    fun opaqueSymbol(id: String): OpaqueSymbolProjection? = get(id)?.let(OpaqueSymbolProjectionFactory::from)

    /** Allocates the next numeric lexical ID internally on first registration. */
    @Synchronized
    fun registerTokenWithOpaqueResult(token: String, provenance: String): OpaqueSymbolProjection {
        val registered = registerToken(token, provenance)
        return OpaqueSymbolProjectionFactory.from(registered)
    }

    fun wordCount(): Long = persistent?.count() ?: lexical.size.toLong()

    private fun exists(id: String): Boolean = word(id) != null || get(id) != null
    private fun word(id: String): String? = persistent?.wordForId(id) ?: symbols[id]?.takeIf { it.children.isEmpty() }?.definition
    private fun levelOf(id: String): Int = if (word(id) != null) 0 else get(id)?.level ?: error("Symbol tidak ditemukan: $id")
}
