package com.example.vista.domain.model

import java.util.UUID

enum class DetectionCategory {
    OBJECT,
    OBSTACLE,
    TEXT_OCR,
    PERSON
}

/**
 * Represents an entity detected by the vision engine.
 * Designed to be modular so future ML models (e.g. YOLO, ML Kit, MobileNet)
 * can map their outputs directly into this structure.
 */
data class DetectionItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val category: DetectionCategory = DetectionCategory.OBJECT,
    val description: String? = null
) {
    fun toSpokenText(): String {
        return description ?: label
    }
}
