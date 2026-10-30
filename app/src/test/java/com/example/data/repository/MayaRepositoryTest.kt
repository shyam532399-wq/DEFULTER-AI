package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MayaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MayaRepositoryTest {

    private lateinit var database: MayaDatabase
    private lateinit var repository: MayaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = MayaDatabase.createInMemory(context)
        repository = MayaRepository(database.mayaDao(), MayaRepository.LOCAL_BOSS_ID)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun profile_ensuresDefaultAndUpdatesFieldsLocallyWithoutLogin() = runTest {
        val initial = repository.ensureDefaultProfile(fallbackName = "Rajeev").getOrThrow()
        assertEquals("Rajeev", initial.bossName)
        assertEquals("maya", initial.persona)

        repository.updateProfileFields(
            mapOf(
                "persona" to "friday",
                "bossName" to "Boss Rajeev",
                "touchGuardArmed" to true
            )
        ).getOrThrow()

        val observed = repository.observeProfile().first()
        assertNotNull(observed)
        assertEquals("friday", observed?.persona)
        assertEquals("Boss Rajeev", observed?.bossName)
        assertTrue(observed?.touchGuardArmed == true)
    }

    @Test
    fun documents_skills_memories_and_reminders_persistAndEmitFlows() = runTest {
        // 1. Board Documents (pinned first)
        val doc1Id = repository.createDocument("Daily Note", "note", "Summary 1", "Body 1", pinned = false).getOrThrow()
        val doc2Id = repository.createDocument("HUD Report", "dashboard", "Summary 2", "Body 2", pinned = true).getOrThrow()

        val docs = repository.observeDocuments().first()
        assertEquals(2, docs.size)
        assertEquals(doc2Id, docs.first().id)

        repository.deleteDocument(doc1Id).getOrThrow()
        assertEquals(1, repository.observeDocuments().first().size)

        // 2. Memories
        val memId = repository.saveMemory("app_nickname", "gaana app", "Spotify").getOrThrow()
        val memories = repository.observeMemories().first()
        assertEquals(1, memories.size)
        assertEquals("Spotify", memories.first().value)
        repository.deleteMemory(memId).getOrThrow()
        assertTrue(repository.observeMemories().first().isEmpty())

        // 3. Skills
        val skillId = repository.createSkillPlaybook("skill", "HUD Check", "HUD dikhao", "1. GET_SYSTEM_HUD").getOrThrow()
        val skills = repository.observeSkills().first()
        assertEquals(1, skills.size)
        assertTrue(skills.first().enabled)
        repository.toggleSkillEnabled(skills.first(), false).getOrThrow()
        assertFalse(repository.observeSkills().first().first().enabled)
        repository.deleteSkill(skillId).getOrThrow()
        assertTrue(repository.observeSkills().first().isEmpty())

        // 4. Reminders
        val remId = repository.createReminder("watchlist", "BTC", "Crypto watch", "Live").getOrThrow()
        val reminders = repository.observeReminders().first()
        assertEquals(1, reminders.size)
        repository.toggleReminderCompleted(reminders.first(), true).getOrThrow()
        assertTrue(repository.observeReminders().first().first().completed)
        repository.deleteReminder(remId).getOrThrow()
        assertTrue(repository.observeReminders().first().isEmpty())
    }
}
