package com.example.vista.domain.engine

import androidx.camera.core.ImageProxy
import com.example.vista.domain.model.DetectionCategory
import com.example.vista.domain.model.DetectionItem

/**
 * Milestone 1 mock detection engine.
 * Safely consumes and closes incoming CameraX frames without running ML inference.
 * Simulates contextual detections to validate the complete UI and Text-to-Speech pipeline.
 */
class MockDetectionEngine : DetectionEngine {

    private var frameCount = 0L
    private var lastEmittedTime = 0L

    private val simulatedDetections = listOf(
        DetectionItem(
            label = "Doorway",
            confidence = 0.94f,
            category = DetectionCategory.OBSTACLE,
            description = "Clear doorway directly ahead"
        ),
        DetectionItem(
            label = "Chair",
            confidence = 0.89f,
            category = DetectionCategory.OBSTACLE,
            description = "Chair approximately 2 meters ahead"
        ),
        DetectionItem(
            label = "Table",
            confidence = 0.91f,
            category = DetectionCategory.OBJECT,
            description = "Table on your left"
        ),
        DetectionItem(
            label = "Person",
            confidence = 0.96f,
            category = DetectionCategory.PERSON,
            description = "Person standing 3 meters ahead"
        ),
        DetectionItem(
            label = "Signboard",
            confidence = 0.88f,
            category = DetectionCategory.TEXT_OCR,
            description = "Sign detected: Exit"
        )
    )

    private var sampleIndex = 0

    override suspend fun processFrame(imageProxy: ImageProxy): List<DetectionItem> {
        try {
            frameCount++
            val now = System.currentTimeMillis()
            // Emit a new simulated detection approximately every 3.5 seconds
            if (now - lastEmittedTime >= 3500) {
                lastEmittedTime = now
                val item = simulatedDetections[sampleIndex % simulatedDetections.size].copy(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = now
                )
                sampleIndex++
                return listOf(item)
            }
            return emptyList()
        } finally {
            // Crucial: CameraX ImageAnalysis requires closing each frame to avoid blocking the pipeline
            imageProxy.close()
        }
    }

    override fun reset() {
        frameCount = 0L
        lastEmittedTime = 0L
        sampleIndex = 0
    }
}
