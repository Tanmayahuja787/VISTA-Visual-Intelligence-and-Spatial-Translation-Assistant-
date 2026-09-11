package com.example.vista.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Manages Android Text-to-Speech (TTS) engine lifecycle, speech queuing,
 * rate adjustments, and speech debouncing for assistive audio alerts.
 */
class TtsManager(
    context: Context,
    onInitialized: ((TtsManager, Boolean) -> Unit)? = null
) {

    companion object {
        private const val TAG = "VISTA_TTS"
        private const val MIN_SPEECH_INTERVAL_MS = 1500L
    }

    private var tts: TextToSpeech? = null
    var isInitialized: Boolean = false
        private set

    private var lastSpokenTime: Long = 0L
    private var lastSpokenText: String = ""

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Locale US not supported, falling back to default locale")
                    tts?.setLanguage(Locale.getDefault())
                }
                // Slower speech rate for clearer assistive feedback
                tts?.setSpeechRate(0.75f)
                tts?.setPitch(1.0f)
                isInitialized = true
                Log.i(TAG, "TTS initialized successfully")
                onInitialized?.invoke(this, true)
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                isInitialized = false
                onInitialized?.invoke(this, false)
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Started speaking: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "Completed speaking: $utteranceId")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Error speaking: $utteranceId")
            }
        })
    }

    /**
     * Speak the provided text.
     * @param text The message to speak
     * @param queueMode QUEUE_FLUSH (interrupt previous) or QUEUE_ADD (enqueue after current)
     * @param forceSpeak If true, bypasses duplicate check and minimum interval
     */
    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        forceSpeak: Boolean = false
    ) {
        if (!isInitialized || text.isBlank()) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (!forceSpeak) {
            // Avoid repeating the exact same alert within short succession
            if (text == lastSpokenText && (currentTime - lastSpokenTime) < MIN_SPEECH_INTERVAL_MS) {
                return
            }
        }

        lastSpokenTime = currentTime
        lastSpokenText = text

        val utteranceId = "utterance_${currentTime}"
        tts?.speak(text, queueMode, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
