package com.xnvalabs.smarteyex.data.intelligence

/** High-level knowledge map used to route reasoning, not a claim of omniscience. */
enum class KnowledgeDomain {
    PHILOSOPHY, PHYSICS, MATHEMATICS, BIOLOGY, CHEMISTRY, COMPUTER_SCIENCE,
    ENGINEERING, ECONOMICS, HISTORY, PSYCHOLOGY, ART, LANGUAGE, EDUCATION,
}

object KnowledgeDomains {
    fun guidance(domain: KnowledgeDomain): String = when (domain) {
        KnowledgeDomain.PHILOSOPHY -> "Use definitions, premises, logic, counterarguments, and historical context."
        KnowledgeDomain.PHYSICS -> "Use physical laws, units, assumptions, equations, dimensional checks, and experimental limits."
        KnowledgeDomain.MATHEMATICS -> "Show assumptions, derivation, intermediate steps, and verify the result."
        KnowledgeDomain.BIOLOGY -> "Distinguish mechanism, evidence, correlation, causation, and uncertainty."
        KnowledgeDomain.CHEMISTRY -> "Track composition, stoichiometry, kinetics, thermodynamics, and safety constraints."
        KnowledgeDomain.COMPUTER_SCIENCE -> "Consider algorithms, complexity, architecture, security, reliability, and testability."
        KnowledgeDomain.ENGINEERING -> "Translate concepts into requirements, components, tolerances, failure modes, testing, and cost."
        KnowledgeDomain.ECONOMICS -> "Separate descriptive evidence from assumptions and distinguish incentives from outcomes."
        KnowledgeDomain.HISTORY -> "Use dates, primary/secondary evidence, causality carefully, and avoid anachronism."
        KnowledgeDomain.PSYCHOLOGY -> "Avoid diagnosis from casual signals; distinguish observed behavior from interpretation."
        KnowledgeDomain.ART -> "Discuss form, context, technique, intent, and multiple interpretations."
        KnowledgeDomain.LANGUAGE -> "Track meaning, register, context, ambiguity, and dialect."
        KnowledgeDomain.EDUCATION -> "Adapt to the learner, use retrieval practice, examples, feedback, and progressive difficulty."
    }
}
