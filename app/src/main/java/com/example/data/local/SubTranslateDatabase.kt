package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.SubtitleLineDao
import com.example.data.local.dao.TranslationCacheDao
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.SubtitleLineEntity
import com.example.data.local.entity.TranslationCacheEntity

@Database(
    entities = [
        ProjectEntity::class,
        SubtitleLineEntity::class,
        TranslationCacheEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SubTranslateDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun subtitleLineDao(): SubtitleLineDao
    abstract fun translationCacheDao(): TranslationCacheDao

    companion object {
        @Volatile
        private var INSTANCE: SubTranslateDatabase? = null

        fun getDatabase(context: Context): SubTranslateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SubTranslateDatabase::class.java,
                    "subtranslate_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
