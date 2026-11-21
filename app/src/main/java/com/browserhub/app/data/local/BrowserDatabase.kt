package com.browserhub.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        ExtensionEntity::class,
        ExtensionStorageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `extensions` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `sourceBrowser` TEXT NOT NULL,
                        `manifestVersion` INTEGER NOT NULL,
                        `installDir` TEXT NOT NULL,
                        `popupPath` TEXT,
                        `permissionsJson` TEXT NOT NULL,
                        `contentScriptsJson` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `installedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `extension_storage` (
                        `extensionId` TEXT NOT NULL,
                        `key` TEXT NOT NULL,
                        `valueJson` TEXT NOT NULL,
                        PRIMARY KEY(`extensionId`, `key`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `extensions` ADD COLUMN `optionsPath` TEXT")
                db.execSQL("ALTER TABLE `extensions` ADD COLUMN `backgroundScriptsJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `extensions` ADD COLUMN `allowInIncognito` INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getInstance(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "browser_hub.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
