package com.example.vista.vision

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.InputStreamReader

data class YoloDetection(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class YoloV8TfliteDetector(context: Context) {
    private val interpreter = Interpreter(
        FileUtil.loadMappedFile(context, MODEL_ASSET),
        Interpreter.Options().apply { setNumThreads(4) }
    )
    private val labels = context.assets.open(LABELS_ASSET).use { input ->
        BufferedReader(InputStreamReader(input)).readLines().filter { it.isNotBlank() }
    }
    private val inputWidth: Int
    private val inputHeight: Int
    private val outputChannels: Int
    private val outputElements: Int
    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(0f, 255f))
        .add(CastOp(DataType.FLOAT32))
        .build()

    init {
        val inputShape = interpreter.getInputTensor(0).shape()
        require(inputShape.size == 4) { "YOLOv8 input must be a 4D tensor" }
        inputHeight = inputShape[1]
        inputWidth = inputShape[2]

        val outputShape = interpreter.getOutputTensor(0).shape()
        require(outputShape.size == 3) { "YOLOv8 output must be a 3D tensor" }
        outputChannels = outputShape[1]
        outputElements = outputShape[2]
    }

    fun detect(bitmap: Bitmap): List<YoloDetection> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val tensorImage = TensorImage(DataType.FLOAT32).apply { load(resized) }
        val inputBuffer = imageProcessor.process(tensorImage).buffer
        val output = TensorBuffer.createFixedSize(
            intArrayOf(1, outputChannels, outputElements),
            DataType.FLOAT32
        )
        interpreter.run(inputBuffer, output.buffer)
        if (resized !== bitmap) {
            resized.recycle()
        }
        return applyNms(output.floatArray)
    }

    fun close() {
        interpreter.close()
    }

    private fun applyNms(array: FloatArray): List<YoloDetection> {
        val candidates = mutableListOf<Candidate>()
        for (element in 0 until outputElements) {
            var bestConfidence = CONFIDENCE_THRESHOLD
            var bestClass = -1
            for (channel in 4 until outputChannels) {
                val confidence = array[element + outputElements * channel]
                if (confidence > bestConfidence) {
                    bestConfidence = confidence
                    bestClass = channel - 4
                }
            }
            if (bestClass >= 0 && bestClass < labels.size) {
                val rawCx = array[element]
                val rawCy = array[element + outputElements]
                val rawWidth = array[element + outputElements * 2]
                val rawHeight = array[element + outputElements * 3]
                val coordinatesArePixels = maxOf(
                    kotlin.math.abs(rawCx),
                    kotlin.math.abs(rawCy),
                    kotlin.math.abs(rawWidth),
                    kotlin.math.abs(rawHeight)
                ) > 1.5f
                val cx = if (coordinatesArePixels) rawCx / inputWidth else rawCx
                val cy = if (coordinatesArePixels) rawCy / inputHeight else rawCy
                val width = if (coordinatesArePixels) rawWidth / inputWidth else rawWidth
                val height = if (coordinatesArePixels) rawHeight / inputHeight else rawHeight
                val candidate = Candidate(
                    labels[bestClass],
                    bestConfidence,
                    cx - width / 2f,
                    cy - height / 2f,
                    cx + width / 2f,
                    cy + height / 2f
                )
                if (candidate.x1 >= 0f && candidate.y1 >= 0f &&
                    candidate.x2 <= 1f && candidate.y2 <= 1f
                ) {
                    candidates += candidate
                }
            }
        }

        val selected = mutableListOf<Candidate>()
        val remaining = candidates.sortedByDescending { it.confidence }.toMutableList()
        while (remaining.isNotEmpty() && selected.size < MAX_RESULTS) {
            val first = remaining.removeAt(0)
            selected += first
            remaining.removeAll { iou(first, it) >= IOU_THRESHOLD }
        }
        return selected.map {
            YoloDetection(it.label, it.confidence, it.x1, it.y1, it.x2, it.y2)
        }
    }

    private fun iou(first: Candidate, second: Candidate): Float {
        val intersectionWidth = maxOf(0f, minOf(first.x2, second.x2) - maxOf(first.x1, second.x1))
        val intersectionHeight = maxOf(0f, minOf(first.y2, second.y2) - maxOf(first.y1, second.y1))
        val intersection = intersectionWidth * intersectionHeight
        val union = first.area + second.area - intersection
        return if (union > 0f) intersection / union else 0f
    }

    private data class Candidate(
        val label: String,
        val confidence: Float,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    ) {
        val area: Float
            get() = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
    }

    private companion object {
        const val MODEL_ASSET = "yolov8n.tflite"
        const val LABELS_ASSET = "yolov8_labels.txt"
        const val CONFIDENCE_THRESHOLD = 0.45f
        const val IOU_THRESHOLD = 0.5f
        const val MAX_RESULTS = 5
    }
}
