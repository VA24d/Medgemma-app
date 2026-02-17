package com.google.mediapipe.examples.llminference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {

    @Test
    fun testInitialState() {
        val uiState = UiState()
        assertTrue(uiState.messages.isEmpty())
    }

    @Test
    fun testAddMessage() {
        val uiState = UiState()
        uiState.addMessage("Hello", "user")
        
        assertEquals(1, uiState.messages.size)
        assertEquals("Hello", uiState.messages[0].message)
        assertTrue(uiState.messages[0].isFromUser)
    }

    @Test
    fun testCreateLoadingMessage() {
        val uiState = UiState()
        uiState.createLoadingMessage()
        
        assertEquals(1, uiState.messages.size)
        assertEquals(MODEL_PREFIX, uiState.messages[0].author)
        assertTrue(uiState.messages[0].isLoading)
    }

    @Test
    fun testAppendMessage() {
        val uiState = UiState()
        uiState.createLoadingMessage()
        
        // Simulate streaming response
        uiState.appendMessage("Hello")
        uiState.appendMessage(" World")
        
        assertEquals(1, uiState.messages.size)
        assertEquals("Hello World", uiState.messages[0].message)
        // isLoading should be false after append (based on implementation in UiState.kt line 70)
        // Wait, line 70 sets isLoading = false.
        // Let's verify line 70:
        // _messages[index] = _messages[index].copy(..., isLoading = false)
        // Correct.
        assertEquals(false, uiState.messages[0].isLoading) 
    }

    @Test
    fun testClearMessages() {
        val uiState = UiState()
        uiState.addMessage("Test", "user")
        uiState.clearMessages()
        assertTrue(uiState.messages.isEmpty())
    }
}
