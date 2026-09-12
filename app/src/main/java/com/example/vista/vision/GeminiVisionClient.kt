package com.example.vista.vision

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

data class GeminiVisionResult(
    val description: String,
    val objects: List<GeminiObject>
)

data class GeminiObject(
    val label: String,
    val confidence: Float,
    val direction: String,
    val box: GeminiBox?
)

data class GeminiBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class GeminiVisionClient(
    private val endpoint: String = "http://127.0.0.1:8000/analyze"
) {
    fun analyze(bitmap: Bitmap): GeminiVisionResult {
        val imageOutput = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 55, imageOutput)

        val request = JSONObject()
            .put("image_base64", Base64.encodeToString(imageOutput.toByteArray(), Base64.NO_WRAP))
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Gemini backend returned HTTP ${connection.responseCode}")
        }

        val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        val json = JSONObject(body)
        val objectsJson = json.optJSONArray("objects")
        val objects = buildList {
            if (objectsJson != null) {
                for (index in 0 until objectsJson.length()) {
                    val item = objectsJson.getJSONObject(index)
                    val boxJson = item.optJSONObject("box")
                    add(
                        GeminiObject(
                            label = item.optString("label", "Unknown object"),
                            confidence = item.optDouble("confidence", 0.0).toFloat(),
                            direction = item.optString("direction", "ahead"),
                            box = boxJson?.let {
                                GeminiBox(
                                    left = it.optDouble("left", 0.0).toFloat(),
                                    top = it.optDouble("top", 0.0).toFloat(),
                                    right = it.optDouble("right", 1.0).toFloat(),
                                    bottom = it.optDouble("bottom", 1.0).toFloat()
                                )
                            }
                        )
                    )
                }
            }
        }
        return GeminiVisionResult(
            description = json.optString("description", ""),
            objects = objects
        )
    }
}
