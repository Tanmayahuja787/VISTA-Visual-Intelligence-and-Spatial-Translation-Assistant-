package com.example.vista.ui.main

import com.example.vista.domain.model.DetectionCategory
import com.example.vista.domain.model.DetectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VistaUiStateTest {

    @Test
    fun defaultUiState_hasCorrectInitialValues() {
        val state = VistaUiState()
        assertFalse(state.isDetecting)
        assertFalse(state.isTtsReady)
        assertFalse(state.isMuted)
        assertTrue(state.detectedItems.isEmpty())
        assertEquals("Ready to assist. Press Start Detection.", state.lastSpokenAlert)
    }

    @Test
    fun uiState_copyUpdatesFieldsProperly() {
        val testItem = DetectionItem(
            label = "Doorway",
            confidence = 0.95f,
            category = DetectionCategory.OBSTACLE,
            description = "Clear doorway directly ahead"
        )
        val state = VistaUiState().copy(
            isDetecting = true,
            detectedItems = listOf(testItem),
            lastSpokenAlert = testItem.toSpokenText()
        )

        assertTrue(state.isDetecting)
        assertEquals(1, state.detectedItems.size)
        assertEquals("Doorway", state.detectedItems[0].label)
        assertEquals("Clear doorway directly ahead", state.lastSpokenAlert)
    }
}
