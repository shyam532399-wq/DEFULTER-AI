package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.BoardDocument
import com.example.data.model.MemoryItem
import com.example.data.model.ReminderItem
import com.example.data.model.SkillPlaybook
import com.example.data.model.UserProfile

@Database(
    entities = [
        UserProfile::class,
        BoardDocument::class,
        MemoryItem::class,
        SkillPlaybook::class,
        ReminderItem::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MayaDatabase : RoomDatabase() {

    abstract fun mayaDao(): MayaDao

    companion object {
        private const val DB_NAME = "maya_local.db"

        @Volatile
        private var instance: MayaDatabase? = null

        fun getInstance(context: Context): MayaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MayaDatabase::class.java,
                    DB_NAME
                ).build().also { instance = it }
            }
        }

        fun createInMemory(context: Context): MayaDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                MayaDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
