package com.xnvalabs.xnai

/** Persistent skills + learning state, kept in the on-device lexicon database. */
class LexiconMindStore(private val lexicon: XnaiLexiconStore) : MindStore {
    override fun loadSkills(): List<Skill> = lexicon.mindSkills().mapNotNull { row ->
        try {
            Skill(row.id, row.arity, ProgCodec.fromText(row.prog), row.uses, row.gain, row.origin)
        } catch (_: Throwable) {
            null
        }
    }

    override fun addSkill(arity: Int, prog: List<Instr>, origin: String, gain: Int): Int =
        lexicon.addMindSkill(arity, ProgCodec.toText(prog), gain, origin)

    override fun bumpUse(id: Int) = lexicon.bumpMindSkill(id)
    override fun getMeta(key: String): String? = lexicon.mindMeta(key)
    override fun putMeta(key: String, value: String) = lexicon.putMindMeta(key, value)
}
