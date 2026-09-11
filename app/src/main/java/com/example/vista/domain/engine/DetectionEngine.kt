package com.example.vista.domain.engine

import androidx.camera.core.ImageProxy
import com.example.vista.domain.model.DetectionItem

/**
 * Interface defining the contract for frame processing and entity detection.
 * Future models (ML Kit Object Detection, Text OCR, TensorFlow Lite, YOLO)
 * will implement this interface.
 */
interface DetectionEngine {
    /**
     * Process an incoming camera frame.
     * Implementations MUST ensure imageProxy.close() is called when processing is finished.
     */
    suspend fun processFrame(imageProxy: ImageProxy): List<DetectionItem>

    /**
     * Resets any internal tracker, cache, or state held by the detection engine.
     */
    fun reset()
}
