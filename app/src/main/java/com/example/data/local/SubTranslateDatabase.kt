package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }

        /** Adiciona a fonte de captura (OCR ou áudio); projetos existentes continuam como OCR. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN captureSource TEXT NOT NULL DEFAULT 'OCR_VISUAL'"
                )
            }
        }
    }
}
