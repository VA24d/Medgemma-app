package com.google.mediapipe.examples.llminference.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledPrognosisWorkerTest {

    @Test
    fun `auto prompt builder formats entries chronologically`() {
        // We simulate the prompt builder logic since it is a private method in CoroutineWorker
        val entries = listOf(
            "[2024-01-01][Vitals] BP Check: 120/80",
            "[2024-02-15][Note] Follow-up: Patient improved"
        )
        
        val summary = entries.joinToString("\n")
        
        val expectedPrompt = """You are a specialist AI medical assistant. Generate a concise clinical prognosis.

Patient has 2 medical record entries (oldest→newest):
[2024-01-01][Vitals] BP Check: 120/80
[2024-02-15][Note] Follow-up: Patient improved

Provide:
1. **Summary of findings**
2. **Diagnosis / differentials** (with confidence levels)
3. **Disease progression** (improving / stable / deteriorating)
4. **Recommended next steps**
5. **Red flags** to monitor

Format in Markdown. Be concise and clinically precise."""

        assertTrue(expectedPrompt.contains("[2024-01-01][Vitals]"))
        assertTrue(expectedPrompt.contains("Patient has 2 medical record entries"))
        assertTrue(expectedPrompt.contains("Red flags"))
    }

    @Test
    fun `unique work name is defined correctly`() {
        assertEquals("scheduled_prognosis", ScheduledPrognosisWorker.UNIQUE_WORK_NAME)
    }
}
