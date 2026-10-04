package com.xnvalabs.xnai

/** Parser for MALINDO Morph 2024 TSV. The documented 2024 layout is:
 * ID, root, derived, prefix, suffix, circumfix, reduplication, source, stem, lemma.
 */
data class MalindoMorphEntry(
    val id: String,
    val root: String,
    val derived: String,
    val prefix: String,
    val suffix: String,
    val circumfix: String,
    val reduplication: String,
    val source: String,
    val stem: String,
    val lemma: String
)

object MalindoMorphParser {
    fun parseLine(raw: String): MalindoMorphEntry? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        val c = line.split('\t')
        if (c.size < 10) return null
        val id = c[0].trim()
        val root = c[1].trim()
        val derived = c[2].trim()
        if (id.isBlank() || (root.isBlank() && derived.isBlank())) return null
        return MalindoMorphEntry(
            id = id,
            root = root,
            derived = derived,
            prefix = c[3].trim(),
            suffix = c[4].trim(),
            circumfix = c[5].trim(),
            reduplication = c[6].trim(),
            source = c[7].trim(),
            stem = c[8].trim(),
            lemma = c[9].trim()
        )
    }
}
