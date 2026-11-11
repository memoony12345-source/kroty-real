package com.example.domain.scanner

class MultiFrameConsensusTracker(private val requiredMatches: Int = 2) {
    private val window = ArrayDeque<ScannedVoucherCandidate>()
    private val maxWindowSize = 5

    fun onCandidateDetected(candidate: ScannedVoucherCandidate): Pair<Float, ScannedVoucherCandidate?> {
        window.addLast(candidate)
        if (window.size > maxWindowSize) {
            window.removeFirst()
        }
        val count = window.count { it.code == candidate.code }
        val progress = (count.toFloat() / requiredMatches.toFloat()).coerceIn(0f, 1f)
        return if (count >= requiredMatches) {
            progress to candidate
        } else {
            progress to null
        }
    }

    fun reset() {
        window.clear()
    }
}
