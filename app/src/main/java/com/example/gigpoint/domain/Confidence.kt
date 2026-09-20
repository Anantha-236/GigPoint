package com.example.gigpoint.domain

enum class ResolutionStatus { RESOLVED, AMBIGUOUS, NOT_FOUND }

data class Confidence(
    val score: Double,
    val reason: String? = null
) {
    init { require(score in 0.0..1.0) }
    val isHigh get() = score >= 0.85
}

data class Resolution<T>(
    val status: ResolutionStatus,
    val value: T? = null,
    val confidence: Confidence = Confidence(0.0),
    val alternatives: List<T> = emptyList()
) {
    val resolved get() = status == ResolutionStatus.RESOLVED && value != null
}
