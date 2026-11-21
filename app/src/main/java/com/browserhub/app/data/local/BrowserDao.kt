package com.browserhub.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {

    @Query("SELECT * FROM bookmarks WHERE isSpeedDial = 0 ORDER BY createdAt DESC")
    fun observeSavedBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE isSpeedDial = 1 ORDER BY createdAt ASC")
    fun observeSpeedDialSites(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url AND isSpeedDial = 0)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Query("SELECT * FROM bookmarks WHERE url = :url AND isSpeedDial = 0 LIMIT 1")
    suspend fun getSavedBookmarkByUrl(url: String): BookmarkEntity?

    @Query("SELECT COUNT(*) FROM bookmarks WHERE isSpeedDial = 1")
    suspend fun countSpeedDialSites(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url AND isSpeedDial = 0")
    suspend fun deleteSavedBookmarkByUrl(url: String)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 250")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // Extensions & Extension Storage
    @Query("SELECT * FROM extensions ORDER BY installedAt DESC")
    fun observeExtensions(): Flow<List<ExtensionEntity>>

    @Query("SELECT COUNT(*) FROM extensions")
    suspend fun countExtensions(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(extension: ExtensionEntity)

    @Query("UPDATE extensions SET enabled = :enabled WHERE id = :id")
    suspend fun setExtensionEnabled(id: String, enabled: Boolean)

    @Query("UPDATE extensions SET allowInIncognito = :allowed WHERE id = :id")
    suspend fun setExtensionAllowInIncognito(id: String, allowed: Boolean)

    @Query("DELETE FROM extensions WHERE id = :id")
    suspend fun deleteExtensionById(id: String)

    @Query("SELECT * FROM extension_storage WHERE extensionId = :extensionId")
    suspend fun getAllStorageForExtension(extensionId: String): List<ExtensionStorageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStorageValue(entry: ExtensionStorageEntity)

    @Query("DELETE FROM extension_storage WHERE extensionId = :extensionId AND key = :key")
    suspend fun removeStorageKey(extensionId: String, key: String)

    @Query("DELETE FROM extension_storage WHERE extensionId = :extensionId")
    suspend fun clearStorageForExtension(extensionId: String)
}
