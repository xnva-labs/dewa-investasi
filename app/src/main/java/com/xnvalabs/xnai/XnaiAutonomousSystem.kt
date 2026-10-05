package com.xnvalabs.xnai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Self-directed learning/orchestration layer. Human input supplies permissions and context. */
class XnaiAutonomousSystem(context: Context) {
    private val appContext = context.applicationContext
    private val repository = XnaiRepository(appContext)
    private val library = XnaiLibraryStore(appContext)
    private val settings = XnaiSettings(appContext)
    private val research = XnaiResearchGateway {
        settings.githubAccounts().firstOrNull { it.enabled }?.let { settings.githubTokenFor(it.id) } ?: settings.githubToken()
    }
    private val model = XnaiModelProvider({ settings.provider }, { settings.providerApiKey() })
    private val github = GitHubSyncClient({ settings.githubAccounts() }, { id -> settings.githubTokenFor(id) })
    private val vault = XnaiSymbolVault.get(appContext)
    private val codebook = vault.codebook
    private val sandbox = XnaiSafeSandbox(settings::sandboxPolicy)

    data class CycleOutput(
        val cycleId: String,
        val question: ThoughtQuestionRecord,
        val hypothesis: HypothesisRecord,
        val experiment: ExperimentRecord,
        val reflection: ReflectionRecord,
        val sources: List<ResearchSourceRecord>,
        val validated: Boolean,
        val glyph: String = ""
    )

    suspend fun runOneCycle(): CycleOutput = withContext(Dispatchers.Default) {
        vault.migrateLegacyPlaintext()
        val snapshot = snapshot()
        val observation = buildObservationText(snapshot)
        val question = chooseQuestion(snapshot, observation)
        persist("thinking", question.toJson())

        val sources = retrieveRelevant(question, snapshot)
        val hypothesis = formHypothesis(question, sources)
        persist("thinking", hypothesis.toJson())

        val plan = AlgorithmSynthesis.plan(question.question, sources, snapshot.experiments)
        persist("thinking", plan.proposal.toJson())

        val started = System.currentTimeMillis()
        val result = sandbox.run(plan)
        val finished = System.currentTimeMillis()
        val experiment = ExperimentRecord(
            id = "exp-${(started.toString() + question.id).sha256Hex().take(24)}",
            hypothesisId = hypothesis.id,
            plan = plan.describe,
            status = result.status,
            measurement = result.measurement,
            conclusion = result.conclusion,
            startedAt = started,
            finishedAt = finished,
            reproducibilityKey = (hypothesis.statement + result.inputFingerprint).sha256Hex()
        )
        persist("experiments", experiment.toJson())

        val verified = result.verified && result.status == "verified"
        val reflectionText = reflect(question, hypothesis, experiment, sources)
        val nextQuestions = nextQuestions(question, experiment, verified)
        nextQuestions.forEach { persist("thinking", it.toJson()) }
        val reflection = ReflectionRecord(
            id = "ref-${experiment.id}",
            experimentId = experiment.id,
            reflection = reflectionText,
            nextQuestionIds = nextQuestions.map { it.id }
        )
        persist("thinking", reflection.toJson())

        val experience = ExperienceRecord(
            id = "experience-${experiment.id}",
            eventType = if (verified) "validated_experiment" else "negative_or_inconclusive_experiment",
            summary = question.question,
            evidence = "hypothesis=${hypothesis.id}; experiment=${experiment.id}; sources=${sources.size}",
            outcome = experiment.conclusion,
            lesson = reflectionText,
            linkedIds = listOf(question.id, hypothesis.id, experiment.id) + sources.map { it.id }
        )
        persist("memory", experience.toJson())

        val validated = if (verified) {
            val statement = hypothesis.statement
            val knowledge = ValidatedKnowledgeRecord(
                id = "vk-${statement.sha256Hex().take(24)}",
                title = question.question,
                statement = statement,
                evidenceIds = listOf(experiment.id) + sources.map { it.id },
                confidence = min(0.98, max(0.55, hypothesis.confidence + 0.30)),
                validationStatus = "validated_in_sandbox",
                provenance = "XNAI autonomous cycle ${experiment.id}",
                checksum = statement.sha256Hex()
            )
            persist("memory", knowledge.toJson())
            true
        } else false

        integrateRelations(question, hypothesis, experiment, sources)
        integrateCodebook(question, hypothesis)

        library.writeHumanReport(
            "cycle-${experiment.id}.md",
            """# XNAI Autonomous Cycle

**Question**
${question.question}

**Why**
${question.why}

**Evidence sources**
${sources.joinToString("\n") { "- ${it.source}: ${it.title} (${it.url})" }}

**Hypothesis**
${hypothesis.statement}

**Experiment**
${experiment.plan}

**Measurement**
${experiment.measurement}

**Conclusion**
${experiment.conclusion}

**Reflection**
$reflectionText

**Validated**
$validated
"""
        )

        repository.log("XNAI autonomous cycle", experiment.id)
        if (settings.githubAccounts().any { it.enabled }) {
            withContext(Dispatchers.IO) {
                val ledger = XnaiSyncLedger(appContext)
                val pending = ledger.pending(library.listLogicalFiles(), vault.activeFiles())
                if (pending.isNotEmpty()) {
                    val result = github.uploadFiles(pending, appContext.cacheDir)
                    ledger.markUploaded(pending.filter { f -> result.uploaded > 0 && result.errors.none { it.startsWith(f.name + ":") } })
                    result.errors.firstOrNull()?.let { repository.log("GitHub sync", it.take(300)) }
                }
            }
        }
        CycleOutput(
            "cycle-${experiment.id}", question, hypothesis, experiment, reflection, sources, validated,
            glyph = GlyphNotation.render(vault.codec.encode(question.question + " " + hypothesis.statement))
        )
    }

    fun snapshot(): XnaiSnapshot {
        val thinking = library.readAll("thinking", 3000)
        val memory = library.readAll("memory", 3000)
        return XnaiSnapshot(
            questions = thinking.mapNotNull(::questionFromJson),
            hypotheses = thinking.mapNotNull(::hypothesisFromJson),
            experiments = library.readAll("experiments", 3000).mapNotNull(::experimentFromJson),
            reflections = thinking.mapNotNull(::reflectionFromJson),
            experiences = memory.mapNotNull(::experienceFromJson),
            validated = memory.mapNotNull(::validatedFromJson),
            relations = library.readAll("index", 3000).mapNotNull(::relationFromJson),
            sources = library.readAll("knowledge", 3000).mapNotNull(::sourceFromJson),
            proposals = thinking.mapNotNull(::proposalFromJson)
        )
    }

    fun vaultStats(): String = vault.stats()

    private fun buildObservationText(snapshot: XnaiSnapshot): String = buildString {
        append("artifacts=${repository.artifacts().size}; nodes=${repository.nodes().size}; formulas=${repository.formulas().size}; snippets=${repository.snippets().size}; ")
        append("questions=${snapshot.questions.size}; experiments=${snapshot.experiments.size}; validated=${snapshot.validated.size}. ")
        append(snapshot.experiences.takeLast(12).joinToString(" ") { it.lesson.take(180) })
    }

    private fun chooseQuestion(snapshot: XnaiSnapshot, observation: String): ThoughtQuestionRecord {
        val candidates = mutableListOf<ThoughtQuestionRecord>()
        repository.nodes().filter { it.confidence < 90 }.take(20).forEach { node ->
            candidates += ThoughtQuestionRecord(
                id = "q-${("verify|" + node.name).sha256Hex().take(24)}",
                question = "Apa bukti yang dapat memverifikasi atau membatasi pengetahuan '${node.name}'?",
                why = "Node memiliki confidence ${node.confidence}%.",
                priority = ((100 - node.confidence) / 100.0).coerceIn(.1, .95)
            )
        }
        val artifacts = repository.artifacts()
        artifacts.flatMapIndexed { i, a -> artifacts.drop(i + 1).take(12).map { a to it } }.take(50).forEach { (a, b) ->
            val overlap = lexicalOverlap(a.title + " " + a.content, b.title + " " + b.content)
            if (overlap >= 2) {
                candidates += ThoughtQuestionRecord(
                    id = "q-${("link|${a.id}|${b.id}").sha256Hex().take(24)}",
                    question = "Apakah ada hubungan yang dapat diuji antara '${a.title}' dan '${b.title}'?",
                    why = "Ditemukan $overlap istilah bermakna yang overlap.",
                    priority = min(.95, .55 + overlap * .05)
                )
            }
        }
        snapshot.experiments.filter { it.status != "verified" }.takeLast(25).forEach { e ->
            candidates += ThoughtQuestionRecord(
                id = "q-${("repair|" + e.id).sha256Hex().take(24)}",
                question = "Mengapa eksperimen '${e.id}' belum meyakinkan dan kondisi uji apa yang perlu diperbaiki?",
                why = "Kegagalan dan hasil tidak konklusif adalah pengalaman pembelajaran.",
                priority = .90
            )
        }
        candidates += ThoughtQuestionRecord(
            id = "q-meta-${System.currentTimeMillis() / 60_000L}",
            question = "Pengetahuan atau metode apa yang paling meningkatkan kemampuan XNAI untuk menjawab pertanyaan berikutnya?",
            why = "Pertanyaan meta-learning untuk memilih informasi bernilai tinggi.",
            priority = .88
        )
        val prior = snapshot.questions.groupingBy { it.question }.eachCount()
        return candidates.distinctBy { it.id }.maxByOrNull { q ->
            q.priority / (1 + (prior[q.question] ?: 0)) + if (observation.length % 2 == 0) .01 else 0.0
        } ?: ThoughtQuestionRecord(
            "q-bootstrap", "Apa pengetahuan penting yang masih kosong dalam memori XNAI?", "Bootstrap knowledge gap.", .7
        )
    }

    private fun retrieveRelevant(q: ThoughtQuestionRecord, snapshot: XnaiSnapshot): List<ResearchSourceRecord> {
        val terms = q.question.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 4 }.distinct().take(8)
        val local = repository.artifacts().filter { a -> terms.any { a.title.contains(it, true) || a.content.contains(it, true) } }
            .take(12)
            .map {
                ResearchSourceRecord("local-${it.id}", "local-library", it.title, "local://artifact/${it.id}", it.content.take(1600), it.createdAt, it.content.sha256Hex(), "owned-local")
            }
        val remote = if (settings.sandboxPolicy.allowNetworkResearch && settings.provider.researchEnabled) {
            research.search(terms.joinToString(" "), budget = settings.sandboxPolicy.maxNetworkRequestsPerCycle).map {
                ResearchSourceRecord(
                    "src-${(it.source + it.url).sha256Hex().take(24)}", it.source, it.title, it.url, it.snippet,
                    System.currentTimeMillis(), (it.title + it.url + it.snippet).sha256Hex()
                )
            }
        } else emptyList()
        remote.forEach { persist("knowledge", it.toJson()) }
        // XNAI uses what it already validated as evidence for new questions.
        val learned = snapshot.validated
            .filter { v -> terms.any { v.title.contains(it, true) || v.statement.contains(it, true) } }
            .takeLast(6)
            .map {
                ResearchSourceRecord("vk-src-${it.id}", "xnai-validated", it.title, "xnai://validated/${it.id}", it.statement.take(1200), it.createdAt, it.checksum, "owned-local")
            }
        return (local + learned + remote + snapshot.sources).distinctBy { it.id }.take(24)
    }

    private fun formHypothesis(q: ThoughtQuestionRecord, sources: List<ResearchSourceRecord>): HypothesisRecord {
        val evidence = sources.take(8)
        val prompt = """
            Question: ${q.question}
            Evidence packets:
            ${evidence.joinToString("\n") { "- ${it.source} | ${it.title} | ${it.snippet.take(600)}" }}
            Write one falsifiable hypothesis. Do not invent evidence. Include uncertainty implicitly.
        """.trimIndent()
        val ai = if (settings.provider.enabled) model.complete("You are XNAI's hypothesis module.", prompt, 500) else null
        val statement = ai?.trim().orEmpty().ifBlank {
            "Dalam kondisi uji yang terdefinisi, hubungan pada pertanyaan ini akan menghasilkan pengukuran yang konsisten dibandingkan baseline jika hipotesis benar."
        }
        return HypothesisRecord(
            id = "hyp-${(q.id + statement).sha256Hex().take(24)}",
            statement = statement,
            evidenceIds = evidence.map { it.id },
            confidence = if (evidence.isEmpty()) .25 else min(.75, .35 + evidence.size * .05)
        )
    }

    private fun reflect(q: ThoughtQuestionRecord, h: HypothesisRecord, e: ExperimentRecord, sources: List<ResearchSourceRecord>): String {
        val prompt = """
            Question: ${q.question}
            Hypothesis: ${h.statement}
            Experiment: ${e.plan}
            Measurement: ${e.measurement}
            Conclusion: ${e.conclusion}
            Reflect on weaknesses, reproducibility, limits, and the next best experiment.
        """.trimIndent()
        return if (settings.provider.enabled) {
            model.complete("You are XNAI's reflection module. Preserve failures and uncertainty.", prompt, 700)?.trim().orEmpty()
        } else {
            val status = if (e.status == "verified") "Eksperimen konsisten dengan verifier lokal." else "Eksperimen belum cukup untuk menyatakan hipotesis benar."
            "$status Sumber=${sources.size}. Batas: hasil hanya berlaku pada kondisi sandbox yang diuji. Uji berikutnya sebaiknya mengubah satu variabel dan mereplikasi baseline."
        }
    }

    private fun nextQuestions(q: ThoughtQuestionRecord, e: ExperimentRecord, verified: Boolean): List<ThoughtQuestionRecord> = listOf(
        ThoughtQuestionRecord("q-${("replicate|${e.id}").sha256Hex().take(24)}", "Bisakah hasil '${e.id}' direplikasi dengan input berbeda?", "Reproducibility.", if (verified) .88 else .96),
        ThoughtQuestionRecord("q-${("boundary|${e.id}").sha256Hex().take(24)}", "Apa batas kondisi yang membuat kesimpulan '${e.id}' tidak berlaku?", "Boundary testing.", .82),
        ThoughtQuestionRecord("q-${("integrate|${q.id}").sha256Hex().take(24)}", "Pengetahuan apa yang dapat digabungkan dengan hasil '${e.id}'?", "Cumulative integration.", .79)
    )

    private fun integrateRelations(q: ThoughtQuestionRecord, h: HypothesisRecord, e: ExperimentRecord, sources: List<ResearchSourceRecord>) {
        val linked = (repository.nodes().filter { node -> lexicalOverlap(node.name + " " + node.definition, q.question + " " + h.statement) >= 1 }.map { it.id.toString() } + sources.map { it.id })
        linked.take(24).forEach { target ->
            val relation = KnowledgeRelation(
                id = "rel-${(h.id + target).sha256Hex().take(24)}",
                fromId = h.id,
                toId = target,
                relation = "supports_or_relates",
                confidence = h.confidence,
                provenance = "XNAI autonomous relation extraction"
            )
            persist("index", relation.toJson())
        }
        persist("index", JSONObject().apply {
            put("id", "dependency-${e.id}")
            put("from", h.id)
            put("to", JSONArray(linked))
            put("type", "experiment-dependency")
        })
    }

    private fun integrateCodebook(q: ThoughtQuestionRecord, h: HypothesisRecord) {
        val words = (q.question + " " + h.statement).split(Regex("[^\\p{L}\\p{N}+#.-]+")).filter { it.length >= 2 }.distinct().take(120)
        val ids = words.map { codebook.registerToken(it, "XNAI-generated-question").id }.distinct()
        val symbol = if (ids.size >= 2) codebook.combine(ids.take(8), "question-symbol:${q.id}", "XNAI autonomous codebook") else null
        // Only opaque structure is persisted; definitions stay inside the on-device codebook.
        persist("codebook", JSONObject().apply {
            put("type", "codebook")
            put("id", "codebook-${q.id}")
            put("wordCount", codebook.wordCount())
            symbol?.let { opaque ->
                val p = OpaqueSymbolProjectionFactory.from(opaque)
                put("symbol", JSONObject().apply {
                    put("id", p.symbolId); put("level", p.level); put("children", JSONArray(p.childIds))
                    put("version", p.version); put("checksum", p.checksum)
                })
            }
        })
    }

    private fun persist(category: String, json: JSONObject) {
        library.append(category, json)
    }
}

object AlgorithmSynthesis {
    data class Plan(val proposal: AlgorithmProposal, val describe: String, val kind: XnaiSafeSandbox.ExperimentKind)

    private fun nameFor(kind: XnaiSafeSandbox.ExperimentKind): String = when (kind) {
        XnaiSafeSandbox.ExperimentKind.SORT_VARIANT -> "StableSortVariant"
        XnaiSafeSandbox.ExperimentKind.SEARCH_VARIANT -> "AdaptiveSearchVariant"
        XnaiSafeSandbox.ExperimentKind.NUMERIC_DIFFERENTIAL -> "DifferentialNumericCheck"
        XnaiSafeSandbox.ExperimentKind.COMPRESSION_ACCOUNTING -> "CostAwareDictionaryCheck"
        XnaiSafeSandbox.ExperimentKind.CROSS_CHECK -> "EvidenceInvariantCheck"
    }

    /** No keyword hint: XNAI picks the experiment family from its own track record (success rate + exploration bonus). */
    private fun chooseByHistory(history: List<ExperimentRecord>): XnaiSafeSandbox.ExperimentKind {
        val total = history.size
        return XnaiSafeSandbox.ExperimentKind.values().maxByOrNull { k ->
            val prefix = nameFor(k) + ":"
            val tried = history.filter { it.plan.startsWith(prefix) }
            val wins = tried.count { it.status == "verified" }
            (wins + 1.0) / (tried.size + 2.0) + 0.5 * kotlin.math.sqrt(2.0 * kotlin.math.ln(total + 2.0) / (tried.size + 1.0))
        } ?: XnaiSafeSandbox.ExperimentKind.CROSS_CHECK
    }

    fun plan(question: String, sources: List<ResearchSourceRecord>, history: List<ExperimentRecord> = emptyList()): Plan {
        val q = question.lowercase()
        val kind = when {
            "sort" in q || "urut" in q -> XnaiSafeSandbox.ExperimentKind.SORT_VARIANT
            "search" in q || "cari" in q -> XnaiSafeSandbox.ExperimentKind.SEARCH_VARIANT
            "angka" in q || "numer" in q || "hitung" in q -> XnaiSafeSandbox.ExperimentKind.NUMERIC_DIFFERENTIAL
            "kompres" in q || "compression" in q -> XnaiSafeSandbox.ExperimentKind.COMPRESSION_ACCOUNTING
            else -> chooseByHistory(history)
        }
        val name = nameFor(kind)
        val qWords = question.lowercase().split(Regex("[^\\p{L}\\p{N}+#.-]+" )).filter { it.length >= 3 }
        val relatedAlgorithm = KnowledgeCatalog.algorithms.firstOrNull { algo ->
            qWords.any { w -> (algo.name + " " + algo.domain + " " + algo.idea + " " + algo.uses).contains(w, true) }
        } ?: KnowledgeCatalog.algorithms.randomOrNull()
        val relatedLanguage = KnowledgeCatalog.languages.firstOrNull { lang ->
            qWords.any { w -> (lang.name + " " + lang.family + " " + lang.strengths).contains(w, true) }
        } ?: KnowledgeCatalog.languages.randomOrNull()
        val relatedMethod = KnowledgeCatalog.reasoningMethods.firstOrNull { method ->
            qWords.any { w -> (method.name + " " + method.purpose).contains(w, true) }
        } ?: KnowledgeCatalog.reasoningMethods.firstOrNull()
        val strategy = listOfNotNull(
            "Generate a bounded variant",
            relatedAlgorithm?.let { "using ${it.name}" },
            relatedLanguage?.let { "through ${it.name}" },
            relatedMethod?.let { "with ${it.name}" },
            "compare with a known baseline",
            "verify invariants",
            "retain the result"
        ).joinToString(" → ")
        val proposal = AlgorithmProposal(
            id = "alg-${(name + question).sha256Hex().take(24)}",
            name = name,
            family = kind.name,
            strategy = strategy,
            operations = listOf("observe", "retrieve", "combine", "generate", "implement", "run", "compare", "verify", "reflect", "remember"),
            origin = "XNAI self-directed synthesis; sources=${sources.size}; catalog algorithm=${relatedAlgorithm?.name}; language=${relatedLanguage?.name}; method=${relatedMethod?.name}"
        )
        val description = "${proposal.name}: ${proposal.strategy} Question=$question"
        return Plan(proposal, description, kind)
    }
}
