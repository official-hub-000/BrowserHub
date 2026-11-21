package com.browserhub.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val domain: String,
    val isSpeedDial: Boolean = false,
    val accentIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val domain: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val description: String,
    val sourceBrowser: String,
    val manifestVersion: Int,
    val installDir: String,
    val popupPath: String?,
    val optionsPath: String? = null,
    val permissionsJson: String,
    val contentScriptsJson: String,
    val backgroundScriptsJson: String = "[]",
    val enabled: Boolean = true,
    val allowInIncognito: Boolean = true,
    val installedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "extension_storage",
    primaryKeys = ["extensionId", "key"]
)
data class ExtensionStorageEntity(
    val extensionId: String,
    val key: String,
    val valueJson: String
)
