package com.xnvalabs.xnai

/**
 * Human-facing/export-safe representation of a composite codebook symbol.
 * It intentionally omits lexical text, semantic definitions, and decoded content.
 * The internal codebook retains its definitions so authorized XNAI reasoning can decode.
 */
data class OpaqueSymbolProjection(
    val symbolId: String,
    val level: Int,
    val childIds: List<String>,
    val version: Int,
    val checksum: String
)

object OpaqueSymbolProjectionFactory {
    fun from(symbol: CodebookSymbol): OpaqueSymbolProjection = OpaqueSymbolProjection(
        symbolId = symbol.id,
        level = symbol.level,
        childIds = symbol.children.toList(),
        version = symbol.version,
        checksum = symbol.checksum
    )
}
