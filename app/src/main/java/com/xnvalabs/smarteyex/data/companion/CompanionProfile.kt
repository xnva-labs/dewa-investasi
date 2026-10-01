package com.xnvalabs.smarteyex.data.companion

enum class CompanionMode {
    FRIEND, TEACHER, MENTOR, RESEARCHER, ENGINEER, COACH, SILENT
}

data class CompanionProfile(
    val mode: CompanionMode = CompanionMode.FRIEND,
    val warmth: Float = 0.7f,
    val proactivity: Float = 0.55f,
    val verbosity: Float = 0.35f,
    val humor: Float = 0.25f,
    val emotionalExpression: Float = 0.7f,
    val boundaryStrength: Float = 1f,
)
