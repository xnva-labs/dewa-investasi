package com.xnvalabs.xnai

/** Capability stages are deliberately explicit: catalog knowledge is not a compiler/runtime. */
enum class CodeCapabilityStage { KNOWLEDGE_INDEXED, CODE_DRAFTING, STATIC_ANALYSIS, SANDBOX_EXECUTION, BUILD_AND_TEST }

data class ProgrammingLanguageProfile(
    val canonicalName: String,
    val aliases: Set<String>,
    val paradigm: String,
    val sourceExtensions: Set<String>,
    val executionFamily: String,
    val toolchain: String,
    val stages: Set<CodeCapabilityStage>,
    val notes: String
)

data class CodeLearningPlan(
    val language: ProgrammingLanguageProfile,
    val task: String,
    val stages: List<String>,
    val safety: List<String>,
    val readiness: String
)

/** Language-neutral registry and planner. Runtime claims remain disabled until adapters are integrated and tested. */
object ProgrammingLanguageEngine {
    private val explicit = listOf(
        profile("Python", setOf("py", "python3"), "multi-paradigm", setOf(".py", ".pyi"), "Interpreter", "CPython / PyPy / MicroPython"),
        profile("JavaScript", setOf("js", "ecmascript", "node"), "multi-paradigm", setOf(".js", ".mjs", ".cjs"), "Interpreter / JIT", "V8 / SpiderMonkey / JavaScriptCore"),
        profile("TypeScript", setOf("ts"), "typed JavaScript", setOf(".ts", ".tsx"), "Transpiler + JS runtime", "TypeScript compiler + JS runtime"),
        profile("Kotlin", setOf("kt", "kts"), "object-oriented / functional", setOf(".kt", ".kts"), "JVM / Native / JS", "Kotlin compiler + target runtime"),
        profile("Java", setOf("java"), "object-oriented", setOf(".java"), "JVM", "javac + JVM"),
        profile("C", setOf("c", "h"), "procedural / systems", setOf(".c", ".h"), "Native", "Clang / GCC"),
        profile("C++", setOf("cpp", "cxx", "cc", "hpp"), "multi-paradigm / systems", setOf(".cpp", ".cc", ".cxx", ".hpp"), "Native", "Clang / GCC / MSVC"),
        profile("C#", setOf("cs", "csharp"), "object-oriented / functional", setOf(".cs"), ".NET", ".NET SDK"),
        profile("Go", setOf("golang"), "concurrent / systems", setOf(".go"), "Native", "Go toolchain"),
        profile("Rust", setOf("rs"), "systems / functional", setOf(".rs"), "Native", "rustc / Cargo"),
        profile("Swift", setOf("swift"), "protocol-oriented", setOf(".swift"), "Native", "Swift toolchain"),
        profile("Dart", setOf("dart"), "object-oriented / async", setOf(".dart"), "VM / Native / JS", "Dart SDK"),
        profile("Ruby", setOf("rb"), "object-oriented", setOf(".rb"), "Interpreter", "Ruby runtime"),
        profile("PHP", setOf("php"), "web / object-oriented", setOf(".php"), "Interpreter", "PHP runtime"),
        profile("Lua", setOf("lua"), "embedded / multi-paradigm", setOf(".lua"), "Interpreter", "Lua VM"),
        profile("R", setOf("r-lang"), "statistical / functional", setOf(".r", ".R"), "Interpreter", "R runtime"),
        profile("Julia", setOf("jl"), "multiple dispatch", setOf(".jl"), "JIT", "Julia runtime"),
        profile("SQL", setOf("sqlite sql", "postgresql"), "declarative", setOf(".sql"), "Database engine", "Specific SQL database engine"),
        profile("Bash", setOf("sh", "shell"), "shell / imperative", setOf(".sh", ".bash"), "Shell", "POSIX shell / Bash"),
        profile("PowerShell", setOf("ps1", "pwsh"), "object pipeline", setOf(".ps1", ".psm1"), ".NET host", "PowerShell runtime"),
        profile("HTML", setOf("htm"), "markup", setOf(".html", ".htm"), "Browser", "Browser parser (not a general-purpose language)"),
        profile("CSS", setOf("scss", "sass", "less"), "stylesheet / declarative", setOf(".css", ".scss", ".sass", ".less"), "Browser / preprocessor", "Browser CSS engine or preprocessor"),
        profile("Solidity", setOf("sol"), "contract-oriented", setOf(".sol"), "EVM", "Solidity compiler + EVM-compatible test chain"),
        profile("Haskell", setOf("hs"), "pure functional", setOf(".hs", ".lhs"), "Native / bytecode", "GHC"),
        profile("Elixir", setOf("ex", "exs"), "functional / concurrent", setOf(".ex", ".exs"), "BEAM", "Elixir + Erlang/OTP")
    )

    val profiles: List<ProgrammingLanguageProfile> = (explicit + KnowledgeCatalog.languages
        .filterNot { known -> explicit.any { it.canonicalName.equals(known.name, true) } }
        .map { lang -> ProgrammingLanguageProfile(lang.name, setOf(lang.name.lowercase()), lang.paradigms.joinToString(", "),
            emptySet(), lang.runtime, lang.runtime, setOf(CodeCapabilityStage.KNOWLEDGE_INDEXED),
            "Catalog knowledge only; language-specific generation, analysis, compiler/runtime adapter, and tests require validation.") })
        .distinctBy { it.canonicalName.lowercase() }

    fun find(name: String): ProgrammingLanguageProfile? {
        val key = name.trim().lowercase()
        return profiles.firstOrNull { it.canonicalName.lowercase() == key || it.aliases.any { a -> a.lowercase() == key } }
    }

    fun plan(languageName: String, task: String): CodeLearningPlan? {
        val profile = find(languageName) ?: return null
        val cleanTask = task.trim().take(12_000)
        if (cleanTask.isBlank()) return null
        val stages = listOf(
            "Clarify requirements, target platform, inputs, outputs, and constraints",
            "Retrieve language syntax, standard library, idioms, and version-specific documentation",
            "Design algorithm, data structures, interfaces, and failure cases",
            "Draft source code and explain assumptions",
            "Run syntax/type/static checks when a trusted adapter is available",
            "Execute tests in an isolated, resource-limited sandbox when a runtime is available",
            "Compare expected and actual behavior; repair and repeat bounded tests",
            "Record code, toolchain versions, test evidence, limitations, and lessons"
        )
        val safety = listOf(
            "No arbitrary execution on the Android host or access to private files/secrets",
            "Network, filesystem writes, subprocesses, and external actions disabled unless explicitly authorized",
            "Apply CPU, memory, wall-time, output-size, and retry budgets",
            "Treat generated code and retrieved packages as untrusted; preserve logs and rollback"
        )
        return CodeLearningPlan(profile, cleanTask, stages, safety,
            if (profile.stages.contains(CodeCapabilityStage.BUILD_AND_TEST)) "Configured capability; still requires runtime/device validation"
            else "Knowledge-indexed only; drafting and execution are not yet verified for this language")
    }

    private fun profile(name: String, aliases: Set<String>, paradigm: String, ext: Set<String>, family: String, toolchain: String) =
        ProgrammingLanguageProfile(name, aliases + name.lowercase(), paradigm, ext, family, toolchain,
            setOf(CodeCapabilityStage.KNOWLEDGE_INDEXED), "Runtime adapter not integrated; do not claim executable support.")
}
