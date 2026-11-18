package com.codecon.despertador.toothbrush

class ToothbrushVerifier(
    val requiredFrames: Int = 3,
    val minScore: Float = 0.5f,
    val fallbackAfterSeconds: Double = 60.0
) {
    var isConfirmed: Boolean = false
        private set

    var consecutiveFrames: Int = 0
        private set

    private var firstFeedTime: Double? = null

    fun feed(detections: List<Detection>, timeSeconds: Double) {
        if (firstFeedTime == null) {
            firstFeedTime = timeSeconds
        }

        if (isConfirmed) {
            return
        }

        val hasToothbrush = detections.any { detection ->
            detection.label.equals("toothbrush", ignoreCase = true) && detection.score >= minScore
        }

        if (hasToothbrush) {
            consecutiveFrames++
            if (consecutiveFrames >= requiredFrames) {
                isConfirmed = true
            }
        } else {
            consecutiveFrames = 0
        }
    }

    fun shouldOfferFallback(timeSeconds: Double): Boolean {
        if (isConfirmed) {
            return false
        }
        val firstTime = firstFeedTime ?: return false
        return (timeSeconds - firstTime) >= fallbackAfterSeconds
    }
}
