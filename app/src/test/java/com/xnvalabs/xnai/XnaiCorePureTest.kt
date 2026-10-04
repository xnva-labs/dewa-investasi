package com.xnvalabs.xnai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XnaiCorePureTest {
    @Test fun checksum_is_stable() {
        assertEquals("abc".sha256Hex(), "abc".sha256Hex())
        assertTrue("abc".sha256Hex().length == 64)
    }

    @Test fun codebook_ids_are_stable() {
        val a = RecursiveCodebook()
        val b = RecursiveCodebook()
        assertEquals(a.registerToken("gravity", "test").id, b.registerToken("gravity", "test").id)
        val x = a.registerToken("gravity", "test")
        val y = a.registerToken("black-hole", "test")
        val parent = a.combine(listOf(x.id, y.id), "relation", "test")
        assertEquals(1, parent.level)
        assertTrue(a.decode(parent.id).contains("gravity"))
    }

    @Test fun hunspell_entries_strip_affix_flags_and_keep_lexical_form() {
        assertEquals("mengapa", HunspellLexiconParser.parseLine("Mengapa"))
        assertEquals("berjalan", HunspellLexiconParser.parseLine("berjalan/B0Dk"))
        assertEquals(null, HunspellLexiconParser.parseLine("31129", isHeader = true))
        assertEquals(null, HunspellLexiconParser.parseLine("# comment"))
        assertEquals(null, HunspellLexiconParser.parseLine("word with spaces"))
    }
}

class ProgrammingLanguageEngineTest {
    @Test fun resolves_common_language_aliases() {
        assertEquals("Python", ProgrammingLanguageEngine.find("py")?.canonicalName)
        assertEquals("JavaScript", ProgrammingLanguageEngine.find("node")?.canonicalName)
        assertEquals("C++", ProgrammingLanguageEngine.find("cpp")?.canonicalName)
        assertEquals("Kotlin", ProgrammingLanguageEngine.find("kt")?.canonicalName)
    }

    @Test fun plan_is_explicit_about_unverified_runtime() {
        val plan = ProgrammingLanguageEngine.plan("Rust", "Create a CLI parser")
        assertTrue(plan != null)
        assertTrue(plan!!.safety.any { it.contains("secrets") })
        assertTrue(plan.readiness.contains("not yet verified"))
        assertEquals(null, ProgrammingLanguageEngine.plan("", "task"))
        assertEquals(null, ProgrammingLanguageEngine.plan("Python", " "))
    }
}
