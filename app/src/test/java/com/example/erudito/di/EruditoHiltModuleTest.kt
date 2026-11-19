package com.example.erudito.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EruditoHiltModuleTest {

    @Test
    fun `wikipedia user agent identifies Erudito project without exposing personal email`() {
        val userAgent = EruditoHiltModule.WIKIPEDIA_USER_AGENT

        assertTrue(userAgent.startsWith("Erudito/"))
        assertTrue(userAgent.contains("https://github.com/LlyanDarc/Erudito"))
        assertFalse(userAgent.contains("@"))
    }
}
