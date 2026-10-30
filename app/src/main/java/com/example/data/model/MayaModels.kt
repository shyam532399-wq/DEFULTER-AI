package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MayaPersona(
    val id: String,
    val displayName: String,
    val tagline: String,
    val pitch: Float,
    val speechRate: Float
) {
    MAYA("maya", "Maya", "Warm, sharp Hinglish & halki si shararat", 1.08f, 1.03f),
    FRIDAY("friday", "Friday", "Crisp tactical intelligence & precision", 1.0f, 1.12f),
    VENOM("venom", "Venom", "Fierce symbiote protector — bold & direct", 0.72f, 0.94f);

    companion object {
        fun fromId(id: String): MayaPersona =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MAYA
    }
}

enum class MayaTone(val id: String, val label: String, val pitchDelta: Float, val rateDelta: Float) {
    CALM("calm", "Calm", 0f, -0.04f),
    EXCITED("excited", "Excited", 0.08f, 0.10f),
    SOFT("soft", "Soft", -0.04f, -0.08f);

    companion object {
        fun fromId(id: String): MayaTone =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CALM
    }
}

enum class ListenMode(val id: String, val label: String, val sublabel: String) {
    BOSS_ONLY("boss_only", "Sirf meri suno", "Voice Guardian speaker check"),
    EVERYONE("everyone", "Sabki suno", "Open mic for everyone");

    companion object {
        fun fromId(id: String): ListenMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BOSS_ONLY
    }
}

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val userId: String = "local_boss",
    val bossName: String = "Boss",
    val persona: String = "maya",
    val tone: String = "calm",
    val listenMode: String = "boss_only",
    val personalWiki: String = "Boss ko crisp Hinglish updates pasand hain. Kaam pehle, baatein chhoti.",
    val voiceEnrolled: Boolean = false,
    val voicePassphrase: String = "",
    val voiceModel: String = "powerful",
    val guardArmed: Boolean = false,
    val touchGuardArmed: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "board_documents")
data class BoardDocument(
    @PrimaryKey val id: String = "",
    val userId: String = "local_boss",
    val title: String = "",
    val category: String = "note", // research, marketing, reviews, email, market, note, dashboard
    val summary: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryItem(
    @PrimaryKey val id: String = "",
    val userId: String = "local_boss",
    val kind: String = "fact", // fact, app_nickname, episode
    val key: String = "",
    val value: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "skills")
data class SkillPlaybook(
    @PrimaryKey val id: String = "",
    val userId: String = "local_boss",
    val kind: String = "skill", // skill, macro, rule
    val name: String = "",
    val trigger: String = "",
    val stepsText: String = "",
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderItem(
    @PrimaryKey val id: String = "",
    val userId: String = "local_boss",
    val kind: String = "reminder", // reminder, task, watchlist
    val title: String = "",
    val detail: String = "",
    val scheduledInfo: String = "",
    val completed: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}
