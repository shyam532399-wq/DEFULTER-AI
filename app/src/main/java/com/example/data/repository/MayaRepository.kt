package com.example.data.repository

import android.content.Context
import com.example.data.local.MayaDao
import com.example.data.local.MayaDatabase
import com.example.data.model.BoardDocument
import com.example.data.model.MemoryItem
import com.example.data.model.ReminderItem
import com.example.data.model.SkillPlaybook
import com.example.data.model.UserProfile
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class MayaRepository(
    private val dao: MayaDao,
    private val defaultUserId: String = LOCAL_BOSS_ID
) {
    constructor(context: Context, userId: String = LOCAL_BOSS_ID) : this(
        dao = MayaDatabase.getInstance(context).mayaDao(),
        defaultUserId = userId
    )

    // --- 1. UserProfile ---

    fun observeProfile(userId: String = defaultUserId): Flow<UserProfile?> {
        return dao.observeProfile(userId.ifBlank { defaultUserId })
    }

    suspend fun ensureDefaultProfile(
        userId: String = defaultUserId,
        fallbackName: String = "Boss"
    ): Result<UserProfile> = runCatching {
        val resolvedId = userId.ifBlank { defaultUserId }
        try {
            val existing = dao.getProfileOnce(resolvedId)
            if (existing != null) {
                existing
            } else {
                val now = System.currentTimeMillis()
                val safeName = fallbackName.trim().ifBlank { "Boss" }.take(60)
                val initial = UserProfile(
                    userId = resolvedId,
                    bossName = safeName,
                    persona = "maya",
                    tone = "calm",
                    listenMode = "boss_only",
                    personalWiki = "$safeName ko crisp Hinglish me kaam ki baat pehle pasand hai. Bina poochhe delete ya message send nahi karna.",
                    voiceEnrolled = false,
                    voicePassphrase = "",
                    voiceModel = "powerful",
                    guardArmed = false,
                    touchGuardArmed = false,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                dao.upsertProfile(initial)
                initial
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun updateProfileFields(fields: Map<String, Any>): Result<Unit> = runCatching {
        try {
            val current = dao.getProfileOnce(defaultUserId)
                ?: ensureDefaultProfile(defaultUserId, "Boss").getOrThrow()
            val now = System.currentTimeMillis()
            val updated = current.copy(
                bossName = (fields["bossName"] as? String)?.trim()?.ifBlank { current.bossName }?.take(60)
                    ?: current.bossName,
                persona = (fields["persona"] as? String) ?: current.persona,
                tone = (fields["tone"] as? String) ?: current.tone,
                listenMode = (fields["listenMode"] as? String) ?: current.listenMode,
                personalWiki = (fields["personalWiki"] as? String)?.take(4000) ?: current.personalWiki,
                voiceEnrolled = (fields["voiceEnrolled"] as? Boolean) ?: current.voiceEnrolled,
                voicePassphrase = (fields["voicePassphrase"] as? String)?.take(200) ?: current.voicePassphrase,
                voiceModel = (fields["voiceModel"] as? String) ?: current.voiceModel,
                guardArmed = (fields["guardArmed"] as? Boolean) ?: current.guardArmed,
                touchGuardArmed = (fields["touchGuardArmed"] as? Boolean) ?: current.touchGuardArmed,
                updatedAtMillis = now
            )
            dao.upsertProfile(updated)
        } catch (e: CancellationException) {
            throw e
        }
    }

    // --- 2. BoardDocument ---

    fun observeDocuments(userId: String = defaultUserId): Flow<List<BoardDocument>> {
        return dao.observeDocuments(userId.ifBlank { defaultUserId })
    }

    suspend fun createDocument(
        title: String,
        category: String,
        summary: String,
        content: String,
        pinned: Boolean = false
    ): Result<String> = runCatching {
        try {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val validCategory = category.lowercase().takeIf {
                it in setOf("research", "marketing", "reviews", "email", "market", "note", "dashboard")
            } ?: "note"
            val doc = BoardDocument(
                id = id,
                userId = defaultUserId,
                title = title.trim().ifBlank { "Maya Document" }.take(200),
                category = validCategory,
                summary = summary.trim().take(600),
                content = content.trim().ifBlank { "-" }.take(30000),
                pinned = pinned,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertDocument(doc)
            id
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun getDocumentById(targetUserId: String, docId: String): Result<BoardDocument?> = runCatching {
        try {
            dao.getDocumentById(targetUserId.ifBlank { defaultUserId }, docId)
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun updateDocumentPin(document: BoardDocument, pinned: Boolean): Result<Unit> = runCatching {
        require(document.id.isNotBlank()) { "Document ID must not be blank" }
        try {
            dao.upsertDocument(
                document.copy(
                    userId = document.userId.ifBlank { defaultUserId },
                    pinned = pinned,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun deleteDocument(docId: String): Result<Unit> = runCatching {
        try {
            dao.deleteDocument(defaultUserId, docId)
        } catch (e: CancellationException) {
            throw e
        }
    }

    // --- 3. MemoryItem ---

    fun observeMemories(userId: String = defaultUserId): Flow<List<MemoryItem>> {
        return dao.observeMemories(userId.ifBlank { defaultUserId })
    }

    suspend fun saveMemory(kind: String, key: String, value: String): Result<String> = runCatching {
        try {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val validKind = kind.lowercase().takeIf { it in setOf("fact", "app_nickname", "episode") } ?: "fact"
            val item = MemoryItem(
                id = id,
                userId = defaultUserId,
                kind = validKind,
                key = key.trim().ifBlank { "note" }.take(120),
                value = value.trim().ifBlank { "-" }.take(2000),
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertMemory(item)
            id
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> = runCatching {
        try {
            dao.deleteMemory(defaultUserId, memoryId)
        } catch (e: CancellationException) {
            throw e
        }
    }

    // --- 4. SkillPlaybook ---

    fun observeSkills(userId: String = defaultUserId): Flow<List<SkillPlaybook>> {
        return dao.observeSkills(userId.ifBlank { defaultUserId })
    }

    suspend fun createSkillPlaybook(
        kind: String,
        name: String,
        trigger: String,
        stepsText: String,
        enabled: Boolean = true
    ): Result<String> = runCatching {
        try {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val validKind = kind.lowercase().takeIf { it in setOf("skill", "macro", "rule") } ?: "skill"
            val playbook = SkillPlaybook(
                id = id,
                userId = defaultUserId,
                kind = validKind,
                name = name.trim().ifBlank { "Custom Skill" }.take(100),
                trigger = trigger.trim().ifBlank { name.trim().ifBlank { "Manual" } }.take(200),
                stepsText = stepsText.trim().ifBlank { "1. Execute command" }.take(4000),
                enabled = enabled,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertSkill(playbook)
            id
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun toggleSkillEnabled(skill: SkillPlaybook, enabled: Boolean): Result<Unit> = runCatching {
        require(skill.id.isNotBlank()) { "Skill ID must not be blank" }
        try {
            dao.upsertSkill(
                skill.copy(
                    userId = skill.userId.ifBlank { defaultUserId },
                    enabled = enabled,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun deleteSkill(skillId: String): Result<Unit> = runCatching {
        try {
            dao.deleteSkill(defaultUserId, skillId)
        } catch (e: CancellationException) {
            throw e
        }
    }

    // --- 5. ReminderItem ---

    fun observeReminders(userId: String = defaultUserId): Flow<List<ReminderItem>> {
        return dao.observeReminders(userId.ifBlank { defaultUserId })
    }

    suspend fun createReminder(
        kind: String,
        title: String,
        detail: String,
        scheduledInfo: String
    ): Result<String> = runCatching {
        try {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val validKind = kind.lowercase().takeIf { it in setOf("reminder", "task", "watchlist") } ?: "reminder"
            val item = ReminderItem(
                id = id,
                userId = defaultUserId,
                kind = validKind,
                title = title.trim().ifBlank { "Reminder" }.take(200),
                detail = detail.trim().take(500),
                scheduledInfo = scheduledInfo.trim().take(120),
                completed = false,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertReminder(item)
            id
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun toggleReminderCompleted(item: ReminderItem, completed: Boolean): Result<Unit> = runCatching {
        require(item.id.isNotBlank()) { "Reminder ID must not be blank" }
        try {
            dao.upsertReminder(
                item.copy(
                    userId = item.userId.ifBlank { defaultUserId },
                    completed = completed,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun deleteReminder(reminderId: String): Result<Unit> = runCatching {
        try {
            dao.deleteReminder(defaultUserId, reminderId)
        } catch (e: CancellationException) {
            throw e
        }
    }

    companion object {
        const val LOCAL_BOSS_ID = "local_boss"
    }
}
