package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BoardDocument
import com.example.data.model.MemoryItem
import com.example.data.model.ReminderItem
import com.example.data.model.SkillPlaybook
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface MayaDao {

    // --- 1. UserProfile ---
    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfileOnce(userId: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfile)

    // --- 2. BoardDocument ---
    @Query("SELECT * FROM board_documents WHERE userId = :userId ORDER BY pinned DESC, updatedAtMillis DESC")
    fun observeDocuments(userId: String): Flow<List<BoardDocument>>

    @Query("SELECT * FROM board_documents WHERE userId = :userId AND id = :docId LIMIT 1")
    suspend fun getDocumentById(userId: String, docId: String): BoardDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: BoardDocument)

    @Query("DELETE FROM board_documents WHERE userId = :userId AND id = :docId")
    suspend fun deleteDocument(userId: String, docId: String)

    // --- 3. MemoryItem ---
    @Query("SELECT * FROM memories WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeMemories(userId: String): Flow<List<MemoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(item: MemoryItem)

    @Query("DELETE FROM memories WHERE userId = :userId AND id = :memoryId")
    suspend fun deleteMemory(userId: String, memoryId: String)

    // --- 4. SkillPlaybook ---
    @Query("SELECT * FROM skills WHERE userId = :userId ORDER BY updatedAtMillis DESC")
    fun observeSkills(userId: String): Flow<List<SkillPlaybook>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkill(playbook: SkillPlaybook)

    @Query("DELETE FROM skills WHERE userId = :userId AND id = :skillId")
    suspend fun deleteSkill(userId: String, skillId: String)

    // --- 5. ReminderItem ---
    @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY completed ASC, updatedAtMillis DESC")
    fun observeReminders(userId: String): Flow<List<ReminderItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(item: ReminderItem)

    @Query("DELETE FROM reminders WHERE userId = :userId AND id = :reminderId")
    suspend fun deleteReminder(userId: String, reminderId: String)
}
