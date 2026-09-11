package com.example.vista.ui.main

import android.app.Application
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vista.domain.engine.DetectionEngine
import com.example.vista.domain.engine.TensorFlowLiteDetectionEngine
import com.example.vista.domain.model.DetectionItem
import com.example.vista.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VistaUiState(
    val isDetecting: Boolean = false,
    val detectedItems: List<DetectionItem> = emptyList(),
    val isTtsReady: Boolean = false,
    val isMuted: Boolean = false,
    val isGeminiEnabled: Boolean = false,
    val lastSpokenAlert: String = "Ready to assist. Press Start Detection."
)

class VistaViewModel(
    application: Application,
    private val detectionEngine: DetectionEngine
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, TensorFlowLiteDetectionEngine(application))

    private val _uiState = MutableStateFlow(VistaUiState())
    val uiState: StateFlow<VistaUiState> = _uiState.asStateFlow()

    private val ttsManager: TtsManager = TtsManager(application) { manager, ready ->
        _uiState.update { it.copy(isTtsReady = ready) }
        if (ready) {
            manager.speak("VISTA assistive companion is ready.")
        }
    }

    fun toggleDetection() {
        val nextDetecting = !_uiState.value.isDetecting
        if (nextDetecting) {
            detectionEngine.reset()
            val startMessage = "Detection started"
            _uiState.update {
                it.copy(
                    isDetecting = true,
                    detectedItems = emptyList(),
                    lastSpokenAlert = startMessage
                )
            }
            if (!_uiState.value.isMuted) {
                ttsManager.speak(startMessage, forceSpeak = true)
            }
        } else {
            val stopMessage = "Detection stopped"
            _uiState.update {
                it.copy(
                    isDetecting = false,
                    lastSpokenAlert = stopMessage
                )
            }
            if (!_uiState.value.isMuted) {
                ttsManager.speak(stopMessage, forceSpeak = true)
            }
        }
    }

    fun onFrameReceived(imageProxy: ImageProxy) {
        if (!_uiState.value.isDetecting) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val newItems = detectionEngine.processFrame(imageProxy)
                if (newItems.isNotEmpty()) {
                    val alertText = newItems.first().toSpokenText()
                    _uiState.update { current ->
                        current.copy(
                            detectedItems = newItems,
                            lastSpokenAlert = alertText
                        )
                    }

                    if (!_uiState.value.isMuted) {
                        ttsManager.speak(alertText)
                    }
                }
            } catch (e: Exception) {
                Log.e("VISTA_Detection", "Frame processing failed", e)
            }
        }
    }

    fun repeatLastAlert() {
        val alert = _uiState.value.lastSpokenAlert
        if (alert.isNotBlank()) {
            ttsManager.speak(alert, forceSpeak = true)
        }
    }

    fun toggleMute() {
        _uiState.update { current ->
            val nextMuted = !current.isMuted
            if (nextMuted) {
                ttsManager.stop()
            } else {
                ttsManager.speak("Speech audio unmuted", forceSpeak = true)
            }

            current.copy(isMuted = nextMuted)
        }
    }

    fun toggleGemini() {
        val nextEnabled = !_uiState.value.isGeminiEnabled
        (detectionEngine as? TensorFlowLiteDetectionEngine)?.setGeminiEnabled(nextEnabled)
        _uiState.update { it.copy(isGeminiEnabled = nextEnabled) }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        (detectionEngine as? TensorFlowLiteDetectionEngine)?.close()
    }
}
