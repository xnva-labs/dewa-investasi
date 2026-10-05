package com.xnvalabs.xnai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.math.BigInteger
import java.text.Normalizer

/**
 * Persistent numeric lexicon. Words receive only a sequential decimal integer ID:
 * the first new word is 1, the next is 2, etc. Existing IDs are immutable.
 * Composite symbols are stored separately and may use free-form stable content IDs.
 */
class XnaiLexiconStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "xnai_lexicon.db",
    null,
    3
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    private fun createThoughtTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS exact_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT, token TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS merges (id INTEGER PRIMARY KEY AUTOINCREMENT, a INTEGER NOT NULL, b INTEGER NOT NULL, UNIQUE(a,b))")
    }

    override fun onCreate(db: SQLiteDatabase) {
        createThoughtTables(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY NOT NULL, v TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS words (word TEXT PRIMARY KEY NOT NULL, id TEXT UNIQUE NOT NULL, provenance TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_words_id ON words(id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS symbols (id TEXT PRIMARY KEY NOT NULL, level INTEGER NOT NULL, children TEXT NOT NULL, definition TEXT NOT NULL, version INTEGER NOT NULL, provenance TEXT NOT NULL, checksum TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS word_sources (word_id TEXT NOT NULL, source TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'source-attested', confidence REAL NOT NULL DEFAULT 1.0, evidence TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, PRIMARY KEY(word_id, source), FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_sources_word_id ON word_sources(word_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_sources_status ON word_sources(status)")
        db.execSQL("INSERT OR IGNORE INTO meta(k,v) VALUES('next_word_id','1')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) createThoughtTables(db)
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS word_sources (word_id TEXT NOT NULL, source TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'source-attested', confidence REAL NOT NULL DEFAULT 1.0, evidence TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, PRIMARY KEY(word_id, source), FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_sources_word_id ON word_sources(word_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_sources_status ON word_sources(status)")
            db.execSQL("INSERT OR IGNORE INTO word_sources(word_id, source, status, confidence, evidence, created_at) SELECT id, provenance, 'source-attested', 1.0, '', created_at FROM words WHERE provenance <> ''")
        }
    }

    private fun normalize(word: String): String = Normalizer.normalize(
        word.trim().lowercase(), Normalizer.Form.NFKC
    )

    @Synchronized
    fun lookup(word: String): String? {
        val normalized = normalize(word)
        readableDatabase.rawQuery("SELECT id FROM words WHERE word=? LIMIT 1", arrayOf(normalized)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    @Synchronized
    fun wordForId(id: String): String? {
        readableDatabase.rawQuery("SELECT word FROM words WHERE id=? LIMIT 1", arrayOf(id)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    @Synchronized
    fun register(word: String, provenance: String): String {
        val normalized = normalize(word)
        require(normalized.isNotEmpty()) { "Kata kosong." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = lookup(normalized)
            if (existing != null) {
                recordProvenanceInDb(db, existing, provenance)
                db.setTransactionSuccessful()
                return existing
            }
            val next = db.rawQuery("SELECT v FROM meta WHERE k='next_word_id' LIMIT 1", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else "1"
            }
            val id = BigInteger(next).toString()
            db.insertOrThrow("words", null, ContentValues().apply {
                put("word", normalized)
                put("id", id)
                put("provenance", provenance.take(500))
                put("created_at", System.currentTimeMillis())
            })
            recordProvenanceInDb(db, id, provenance)
            db.update("meta", ContentValues().apply {
                put("v", BigInteger(id).add(BigInteger.ONE).toString())
            }, "k='next_word_id'", null)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    private fun recordProvenanceInDb(
        db: SQLiteDatabase,
        id: String,
        source: String,
        status: String = "source-attested",
        confidence: Double = 1.0,
        evidence: String = ""
    ) {
        db.insertWithOnConflict("word_sources", null, ContentValues().apply {
            put("word_id", id)
            put("source", source.take(500))
            put("status", status.take(80))
            put("confidence", confidence.coerceIn(0.0, 1.0))
            put("evidence", evidence.take(4000))
            put("created_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    fun recordProvenance(
        id: String,
        source: String,
        status: String = "source-attested",
        confidence: Double = 1.0,
        evidence: String = ""
    ) {
        recordProvenanceInDb(writableDatabase, id, source, status, confidence, evidence)
    }

    @Synchronized
    fun provenanceForId(id: String, limit: Int = 100): List<String> =
        readableDatabase.rawQuery(
            "SELECT source FROM word_sources WHERE word_id=? ORDER BY created_at,id LIMIT ?",
            arrayOf(id, limit.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    /** Register a large lexical batch in one SQLite transaction to reduce per-word I/O. */
    @Synchronized
    fun registerBatch(words: Iterable<String>, provenance: String): Long {
        val db = writableDatabase
        var added = 0L
        db.beginTransaction()
        try {
            var next = db.rawQuery("SELECT v FROM meta WHERE k='next_word_id' LIMIT 1", null).use { c ->
                if (c.moveToFirst()) BigInteger(c.getString(0)) else BigInteger.ONE
            }
            val lookup = db.compileStatement("SELECT id FROM words WHERE word=? LIMIT 1")
            val insert = db.compileStatement("INSERT OR IGNORE INTO words(word,id,provenance,created_at) VALUES(?,?,?,?)")
            for (raw in words) {
                val normalized = normalize(raw)
                if (normalized.isEmpty()) continue
                lookup.clearBindings()
                lookup.bindString(1, normalized)
                val existing = lookup.simpleQueryForStringOrNull()
                if (existing != null) {
                    recordProvenanceInDb(db, existing, provenance)
                    continue
                }
                insert.clearBindings()
                insert.bindString(1, normalized)
                insert.bindString(2, next.toString())
                insert.bindString(3, provenance.take(500))
                insert.bindLong(4, System.currentTimeMillis())
                if (insert.executeInsert() != -1L) {
                    val id = next.toString()
                    recordProvenanceInDb(db, id, provenance)
                    next = next.add(BigInteger.ONE)
                    added++
                }
            }
            db.update("meta", ContentValues().apply { put("v", next.toString()) }, "k='next_word_id'", null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    private fun android.database.sqlite.SQLiteStatement.simpleQueryForStringOrNull(): String? =
        try { simpleQueryForString() } catch (_: android.database.sqlite.SQLiteDoneException) { null }

    @Synchronized
    fun restoreWord(id: String, word: String, provenance: String) {
        val normalized = normalize(word)
        require(BigInteger(id) > BigInteger.ZERO) { "ID kata harus positif." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = lookup(normalized)
            val actualId = if (existing == null) {
                db.insertWithOnConflict("words", null, ContentValues().apply {
                    put("word", normalized)
                    put("id", id)
                    put("provenance", provenance.take(500))
                    put("created_at", System.currentTimeMillis())
                }, SQLiteDatabase.CONFLICT_IGNORE)
                lookup(normalized) ?: id
            } else existing
            recordProvenanceInDb(db, actualId, provenance, status = "restored")
            val next = db.rawQuery("SELECT v FROM meta WHERE k='next_word_id' LIMIT 1", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else "1"
            }
            val candidate = BigInteger(id).add(BigInteger.ONE)
            if (candidate > BigInteger(next)) {
                db.update("meta", ContentValues().apply { put("v", candidate.toString()) }, "k='next_word_id'", null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM words", null).use { c ->
        if (c.moveToFirst()) c.getLong(0) else 0L
    }

    @Synchronized
    fun putSymbol(symbol: CodebookSymbol) {
        writableDatabase.insertWithOnConflict("symbols", null, ContentValues().apply {
            put("id", symbol.id)
            put("level", symbol.level)
            put("children", symbol.children.joinToString("\u001f"))
            put("definition", symbol.definition)
            put("version", symbol.version)
            put("provenance", symbol.provenance)
            put("checksum", symbol.checksum)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    fun getSymbol(id: String): CodebookSymbol? {
        readableDatabase.rawQuery("SELECT id,level,children,definition,version,provenance,checksum FROM symbols WHERE id=? LIMIT 1", arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            return CodebookSymbol(
                id = c.getString(0),
                level = c.getInt(1),
                children = c.getString(2).split("\u001f").filter { it.isNotEmpty() },
                definition = c.getString(3),
                version = c.getInt(4),
                provenance = c.getString(5),
                checksum = c.getString(6)
            )
        }
    }

    @Synchronized
    fun allSymbols(limit: Int = 5000): List<CodebookSymbol> {
        readableDatabase.rawQuery("SELECT id,level,children,definition,version,provenance,checksum FROM symbols ORDER BY level,id LIMIT ?", arrayOf(limit.toString())).use { c ->
            val out = ArrayList<CodebookSymbol>()
            while (c.moveToNext()) {
                out += CodebookSymbol(
                    c.getString(0), c.getInt(1), c.getString(2).split("\u001f").filter { it.isNotEmpty()},
                    c.getString(3), c.getInt(4), c.getString(5), c.getString(6)
                )
            }
            return out
        }
    }

    // ---- symbol tables used by SymbolCodec (exact tokens + learned composite symbols) ----

    private fun exactLookup(db: SQLiteDatabase, token: String): Long? =
        db.rawQuery("SELECT id FROM exact_tokens WHERE token=? LIMIT 1", arrayOf(token)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }

    @Synchronized
    fun exactId(token: String): Long {
        val db = writableDatabase
        exactLookup(db, token)?.let { return it }
        db.insertWithOnConflict("exact_tokens", null, ContentValues().apply { put("token", token) }, SQLiteDatabase.CONFLICT_IGNORE)
        return exactLookup(db, token) ?: error("Token eksak gagal disimpan.")
    }

    @Synchronized
    fun exactToken(id: Long): String? =
        readableDatabase.rawQuery("SELECT token FROM exact_tokens WHERE id=? LIMIT 1", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    @Synchronized
    fun mergeFor(a: Long, b: Long): Long? =
        readableDatabase.rawQuery("SELECT id FROM merges WHERE a=? AND b=? LIMIT 1", arrayOf(a.toString(), b.toString())).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }

    @Synchronized
    fun createMerge(a: Long, b: Long): Long {
        mergeFor(a, b)?.let { return it }
        writableDatabase.insertWithOnConflict("merges", null, ContentValues().apply {
            put("a", a); put("b", b)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        return mergeFor(a, b) ?: error("Simbol komposit gagal disimpan.")
    }

    @Synchronized
    fun mergeChildren(id: Long): Pair<Long, Long>? =
        readableDatabase.rawQuery("SELECT a,b FROM merges WHERE id=? LIMIT 1", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getLong(0) to c.getLong(1) else null
        }

    @Synchronized
    fun mergeCount(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM merges", null).use { c ->
        if (c.moveToFirst()) c.getLong(0) else 0L
    }
}
