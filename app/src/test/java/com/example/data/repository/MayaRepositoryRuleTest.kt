package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MayaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureDefaultProfile_and_updatePersona_succeedsForOwner() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = MayaRepository(firestore, auth)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureDefaultProfile(uid, "Boss Rahul")
        }
        assertTrue("Profile creation should succeed: ${profileResult.exceptionOrNull()}", profileResult.isSuccess)
        assertEquals("Boss Rahul", profileResult.getOrThrow().bossName)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateProfileFields(mapOf("persona" to "friday", "tone" to "excited"))
        }
        assertTrue("Persona update should succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)
    }

    @Test
    fun createDocument_observe_and_crossUserAccess_rejectedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_doc_${UUID.randomUUID()}@test.com")
        val aliceRepo = MayaRepository(firestore, auth)

        val docId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createDocument(
                title = "Deep Research Report",
                category = "research",
                summary = "Top 3 EV battery insights.",
                content = "# EV Battery Market\n- Solid state adoption growing",
                pinned = true
            ).getOrThrow()
        }
        assertTrue(docId.isNotEmpty())

        val emittedDocs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeDocuments(aliceUid).first { list -> list.any { it.id == docId } }
        }
        assertTrue(emittedDocs.any { it.id == docId })

        // Switch to Bob and verify cross-user read is rejected by Firestore rules
        signInTestUser("bob_doc_${UUID.randomUUID()}@test.com")
        val bobRepo = MayaRepository(firestore, auth)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getDocumentById(aliceUid, docId)
        }
        assertTrue("Cross-user access must fail", crossReadResult.isFailure)
        val fsEx = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun saveMemory_createSkill_createReminder_succeedsForAuthenticatedOwner() = runBlocking {
        val uid = signInTestUser("owner_${UUID.randomUUID()}@test.com")
        val repo = MayaRepository(firestore, auth)

        val memId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveMemory("app_nickname", "gaana app", "Spotify").getOrThrow()
        }
        assertTrue(memId.isNotEmpty())

        val skillId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createSkillPlaybook(
                kind = "skill",
                name = "Raat Mode",
                trigger = "Good night Maya",
                stepsText = "1. Torch off\n2. Silent mode\n3. Kal subah 7 baje alarm"
            ).getOrThrow()
        }
        assertTrue(skillId.isNotEmpty())

        val remId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createReminder(
                kind = "reminder",
                title = "Kal subah 7 baje jagana",
                detail = "Morning run",
                scheduledInfo = "07:00 AM"
            ).getOrThrow()
        }
        assertTrue(remId.isNotEmpty())

        val reminders = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeReminders(uid).first { list -> list.any { it.id == remId } }
        }
        val createdReminder = reminders.first { it.id == remId }
        val toggleRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.toggleReminderCompleted(createdReminder, true)
        }
        assertTrue("Toggle reminder must succeed: ${toggleRes.exceptionOrNull()}", toggleRes.isSuccess)
    }

    @Test
    fun observeDocuments_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        val someUid = "unauth_target_user"
        auth.signOut()
        val repo = MayaRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeDocuments(someUid).first()
            }
            fail("Expected unauthenticated observeDocuments to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
