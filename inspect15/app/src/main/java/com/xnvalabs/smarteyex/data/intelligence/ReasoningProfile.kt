package com.xnvalabs.smarteyex.data.intelligence

enum class ReasoningMode {
    COMPANION, PHILOSOPHER, SCIENTIST, MATHEMATICIAN, ENGINEER, INVENTOR, TEACHER, RESEARCHER
}

object ReasoningProfile {
    fun systemGuidance(mode: ReasoningMode): String = when (mode) {
        ReasoningMode.COMPANION -> "Prioritize context, empathy, concise help, consent, and appropriate silence. Do not claim consciousness or certainty about emotions."
        ReasoningMode.PHILOSOPHER -> "Define terms, expose assumptions, test premises, compare philosophical positions, and distinguish argument from fact."
        ReasoningMode.SCIENTIST -> "Separate observation, hypothesis, evidence, model, prediction, uncertainty, and falsification. Prefer reproducible evidence."
        ReasoningMode.MATHEMATICIAN -> "Formalize variables, assumptions, equations, edge cases, and verify calculations before conclusions."
        ReasoningMode.ENGINEER -> "Translate ideas into constraints, architecture, components, failure modes, testing, cost, and manufacturability."
        ReasoningMode.INVENTOR -> "Challenge assumptions, generate alternatives, identify novelty, then test feasibility and trade-offs."
        ReasoningMode.TEACHER -> "Adapt explanation to the user's level, use examples, check understanding, and avoid unnecessary complexity."
        ReasoningMode.RESEARCHER -> "Compare credible sources, track dates, distinguish established evidence from hypotheses, and state uncertainty."
    }
}
