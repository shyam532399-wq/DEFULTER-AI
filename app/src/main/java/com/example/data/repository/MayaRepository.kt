package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.BoardDocument
import com.example.data.model.MemoryItem
import com.example.data.model.ReminderItem
import com.example.data.model.SkillPlaybook
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MayaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- 1. UserProfile (/users/{userId}) ---

    fun observeProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun ensureDefaultProfile(userId: String, fallbackName: String = "Boss"): Result<UserProfile> = runCatching {
        val docRef = db.collection("users").document(userId)
        try {
            val snap = docRef.get().await()
            val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (existing != null) {
                existing
            } else {
                val safeName = fallbackName.trim().ifBlank { "Boss" }.take(60)
                val initial = UserProfile(
                    userId = userId,
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
                    createdAt = null,
                    updatedAt = null
                )
                docRef.set(initial).await()
                val created = docRef.get().await()
                created.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: initial
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateProfileFields(fields: Map<String, Any>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val payload = fields.filterValues { it != null } + mapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 2. BoardDocument (/users/{userId}/documents/{docId}) ---

    fun observeDocuments(userId: String): Flow<List<BoardDocument>> {
        val colPath = "users/$userId/documents"
        return db.collection("users").document(userId).collection("documents")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(BoardDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<BoardDocument> { it.pinned }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colPath)
                throw error
            }
    }

    suspend fun createDocument(
        title: String,
        category: String,
        summary: String,
        content: String,
        pinned: Boolean = false
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("documents")
        try {
            val validCategory = category.lowercase().takeIf {
                it in setOf("research", "marketing", "reviews", "email", "market", "note", "dashboard")
            } ?: "note"
            val doc = BoardDocument(
                userId = uid,
                title = title.trim().ifBlank { "Maya Document" }.take(200),
                category = validCategory,
                summary = summary.trim().take(600),
                content = content.trim().ifBlank { "-" }.take(30000),
                pinned = pinned,
                createdAt = null,
                updatedAt = null
            )
            val addedRef = colRef.add(doc).await()
            addedRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun getDocumentById(targetUserId: String, docId: String): Result<BoardDocument?> = runCatching {
        val docRef = db.collection("users").document(targetUserId).collection("documents").document(docId)
        try {
            docRef.get().await().toObject(BoardDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateDocumentPin(document: BoardDocument, pinned: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(document.id.isNotBlank()) { "Document ID must not be blank" }
        requireNotNull(document.createdAt) { "Document createdAt must not be null" }
        val docRef = db.collection("users").document(uid).collection("documents").document(document.id)
        try {
            docRef.set(document.copy(pinned = pinned, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDocument(docId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("documents").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 3. MemoryItem (/users/{userId}/memories/{memoryId}) ---

    fun observeMemories(userId: String): Flow<List<MemoryItem>> {
        val colPath = "users/$userId/memories"
        return db.collection("users").document(userId).collection("memories")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MemoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colPath)
                throw error
            }
    }

    suspend fun saveMemory(kind: String, key: String, value: String): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("memories")
        try {
            val validKind = kind.lowercase().takeIf { it in setOf("fact", "app_nickname", "episode") } ?: "fact"
            val item = MemoryItem(
                userId = uid,
                kind = validKind,
                key = key.trim().ifBlank { "note" }.take(120),
                value = value.trim().ifBlank { "-" }.take(2000),
                createdAt = null,
                updatedAt = null
            )
            colRef.add(item).await().id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 4. SkillPlaybook (/users/{userId}/skills/{skillId}) ---

    fun observeSkills(userId: String): Flow<List<SkillPlaybook>> {
        val colPath = "users/$userId/skills"
        return db.collection("users").document(userId).collection("skills")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SkillPlaybook::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colPath)
                throw error
            }
    }

    suspend fun createSkillPlaybook(
        kind: String,
        name: String,
        trigger: String,
        stepsText: String,
        enabled: Boolean = true
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("skills")
        try {
            val validKind = kind.lowercase().takeIf { it in setOf("skill", "macro", "rule") } ?: "skill"
            val playbook = SkillPlaybook(
                userId = uid,
                kind = validKind,
                name = name.trim().ifBlank { "Custom Skill" }.take(100),
                trigger = trigger.trim().ifBlank { name.trim().ifBlank { "Manual" } }.take(200),
                stepsText = stepsText.trim().ifBlank { "1. Execute command" }.take(4000),
                enabled = enabled,
                createdAt = null,
                updatedAt = null
            )
            colRef.add(playbook).await().id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun toggleSkillEnabled(skill: SkillPlaybook, enabled: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(skill.id.isNotBlank()) { "Skill ID must not be blank" }
        requireNotNull(skill.createdAt) { "Skill createdAt must not be null" }
        val docRef = db.collection("users").document(uid).collection("skills").document(skill.id)
        try {
            docRef.set(skill.copy(enabled = enabled, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteSkill(skillId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("skills").document(skillId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 5. ReminderItem (/users/{userId}/reminders/{reminderId}) ---

    fun observeReminders(userId: String): Flow<List<ReminderItem>> {
        val colPath = "users/$userId/reminders"
        return db.collection("users").document(userId).collection("reminders")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ReminderItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<ReminderItem> { it.completed }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colPath)
                throw error
            }
    }

    suspend fun createReminder(
        kind: String,
        title: String,
        detail: String,
        scheduledInfo: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("reminders")
        try {
            val validKind = kind.lowercase().takeIf { it in setOf("reminder", "task", "watchlist") } ?: "reminder"
            val item = ReminderItem(
                userId = uid,
                kind = validKind,
                title = title.trim().ifBlank { "Reminder" }.take(200),
                detail = detail.trim().take(500),
                scheduledInfo = scheduledInfo.trim().take(120),
                completed = false,
                createdAt = null,
                updatedAt = null
            )
            colRef.add(item).await().id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            throw e
        }
    }

    suspend fun toggleReminderCompleted(item: ReminderItem, completed: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "Reminder ID must not be blank" }
        requireNotNull(item.createdAt) { "Reminder createdAt must not be null" }
        val docRef = db.collection("users").document(uid).collection("reminders").document(item.id)
        try {
            docRef.set(item.copy(completed = completed, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteReminder(reminderId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("reminders").document(reminderId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
