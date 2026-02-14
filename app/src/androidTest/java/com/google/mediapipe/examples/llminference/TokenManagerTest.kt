package com.google.mediapipe.examples.llminference.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for TokenManager secure token storage
 */
@RunWith(AndroidJUnit4::class)
class TokenManagerTest {

    private lateinit var tokenManager: TokenManager
    
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        tokenManager = TokenManager(context)
        tokenManager.clearToken() // Start clean
    }

    @Test
    fun saveAndRetrieveToken() {
        val testToken = "hf_test123abcdefghijklmnop"
        
        tokenManager.saveToken(testToken)
        
        val retrieved = tokenManager.getToken()
        assertEquals(testToken, retrieved)
        assertTrue(tokenManager.hasToken())
        assertTrue(tokenManager.shouldUseToken())
    }

    @Test
    fun clearToken() {
        tokenManager.saveToken("hf_test123")
        
        tokenManager.clearToken()
        
        assertNull(tokenManager.getToken())
        assertFalse(tokenManager.hasToken())
        assertFalse(tokenManager.shouldUseToken())
    }

    @Test
    fun emptyTokenByDefault() {
        assertNull(tokenManager.getToken())
        assertFalse(tokenManager.hasToken())
    }
}
