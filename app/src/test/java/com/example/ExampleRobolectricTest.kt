package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MayaPersona
import com.example.domain.MayaVoiceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_and_firestoreConfig_areConfigured() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Maya", context.getString(R.string.app_name))
        assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
    }

    @Test
    fun voiceSanitizer_stripsUrls_and_masksUnknownPhoneNumbers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = MayaVoiceEngine(context)
        val sanitized = engine.sanitizeForVoice(
            "Boss, report https://example.com/report pe hai aur +919876543210 se message aaya."
        )
        assertFalse(sanitized.contains("https://"))
        assertFalse(sanitized.contains("9876543210"))
        assertTrue(sanitized.contains("unknown number"))
        engine.shutdown()
    }

    @Test
    fun personaMapping_supportsMayaFridayAndVenom() {
        assertEquals(MayaPersona.MAYA, MayaPersona.fromId("maya"))
        assertEquals(MayaPersona.FRIDAY, MayaPersona.fromId("friday"))
        assertEquals(MayaPersona.VENOM, MayaPersona.fromId("venom"))
    }
}
