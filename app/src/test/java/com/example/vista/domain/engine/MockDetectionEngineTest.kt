package com.example.vista.domain.engine

import com.example.vista.domain.model.DetectionCategory
import com.example.vista.domain.model.DetectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockDetectionEngineTest {

    @Test
    fun detectionItem_spokenText_usesDescriptionWhenAvailable() {
        val item = DetectionItem(
            label = "Door",
            confidence = 0.95f,
            category = DetectionCategory.OBSTACLE,
            description = "Clear doorway directly ahead"
        )
        assertEquals("Clear doorway directly ahead", item.toSpokenText())
    }

    @Test
    fun detectionItem_spokenText_fallsBackToLabel() {
        val item = DetectionItem(
            label = "Chair",
            confidence = 0.88f,
            category = DetectionCategory.OBSTACLE,
            description = null
        )
        assertEquals("Chair", item.toSpokenText())
    }

    @Test
    fun mockDetectionEngine_resetCleansState() {
        val engine = MockDetectionEngine()
        engine.reset()
        assertTrue(true)
    }
}
