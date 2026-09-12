package com.example.vista.domain.engine

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.vista.domain.model.DetectionCategory
import com.example.vista.domain.model.DetectionBox
import com.example.vista.domain.model.DetectionItem
import com.example.vista.vision.GeminiVisionClient
import com.example.vista.vision.YoloV8TfliteDetector
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TensorFlowLiteDetectionEngine(context: Context) : DetectionEngine {

    private val detector = YoloV8TfliteDetector(context)
    private val detectorMutex = Mutex()
    private val geminiClient = GeminiVisionClient()
    private var lastGeminiRequestAt = 0L
    private val geminiRequestInFlight = AtomicBoolean(false)
    private val geminiScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var latestGeminiResults: List<DetectionItem> = emptyList()
    @Volatile
    private var geminiEnabled = false

    override suspend fun processFrame(imageProxy: ImageProxy): List<DetectionItem> {
        return try {
            detectorMutex.withLock {
                val bitmap = imageProxy.toBitmap()
                val localResults = detector.detect(bitmap).map { detection ->
                    DetectionItem(
                        label = detection.label,
                        confidence = detection.confidence,
                        category = categoryFor(detection.label),
                        description = "${detection.label} detected",
                        box = DetectionBox(
                            left = detection.left,
                            top = detection.top,
                            right = detection.right,
                            bottom = detection.bottom
                        )
                    )
                }
                val now = System.currentTimeMillis()
                if (geminiEnabled && now - lastGeminiRequestAt >= 2_500L &&
                    geminiRequestInFlight.compareAndSet(false, true)
                ) {
                    lastGeminiRequestAt = now
                    val requestBitmap = bitmap.copy(bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                    geminiScope.launch {
                        try {
                            val result = geminiClient.analyze(requestBitmap)
                            latestGeminiResults = result.objects
                                .filter { it.confidence >= 0.6f }
                                .map {
                                    DetectionItem(
                                        label = it.label,
                                        confidence = it.confidence,
                                        category = categoryFor(it.label),
                                        description = "${it.label} ${it.direction}",
                                        box = it.box?.let { box ->
                                            DetectionBox(
                                                left = box.left.coerceIn(0f, 1f),
                                                top = box.top.coerceIn(0f, 1f),
                                                right = box.right.coerceIn(0f, 1f),
                                                bottom = box.bottom.coerceIn(0f, 1f)
                                            )
                                        }
                                    )
                                }
                            Log.d(TAG, "Gemini returned ${latestGeminiResults.size} objects")
                        } catch (error: Exception) {
                            Log.w(TAG, "Gemini analysis unavailable; using local results", error)
                        } finally {
                            requestBitmap.recycle()
                            geminiRequestInFlight.set(false)
                        }
                    }
                } else {
                    Unit
                }
                bitmap.recycle()
                if (geminiEnabled && latestGeminiResults.isNotEmpty()) {
                    latestGeminiResults.distinctBy { it.label.lowercase(Locale.US) }
                } else {
                    localResults
                }
            }
        } finally {
            imageProxy.close()
        }
    }

    override fun reset() {
        latestGeminiResults = emptyList()
        lastGeminiRequestAt = 0L
    }

    fun setGeminiEnabled(enabled: Boolean) {
        geminiEnabled = enabled
        if (!enabled) {
            reset()
        }
    }

    fun close() {
        geminiScope.cancel()
        detector.close()
    }

    private fun categoryFor(label: String): DetectionCategory {
        return when (label.lowercase(Locale.US)) {
            "person" -> DetectionCategory.PERSON
            "stop sign", "traffic light", "fire hydrant" -> DetectionCategory.OBSTACLE
            else -> DetectionCategory.OBJECT
        }
    }

    private companion object {
        const val TAG = "VISTA_Detection"
    }
}
