package com.browserhub.app.downloads

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.browserhub.app.privacy.ShieldsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class MediaKind(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    PHOTO("Photo")
}

data class DetectedMediaItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val mimeType: String,
    val formatBadge: String,
    val kind: MediaKind = MediaKind.VIDEO,
    val qualityLabel: String = "",
    val sizeLabel: String = "Stream",
    val isHls: Boolean = false,
    val isDash: Boolean = false,
    val isDrmProtected: Boolean = false,
    val drmReason: String? = null,
    val tabUrl: String = ""
)

enum class DownloadStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AcceleratedDownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val url: String,
    val threadsUsed: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus = DownloadStatus.RUNNING,
    val savedLocation: String = "",
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

class DownloadAndMediaEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _detectedMedia = MutableStateFlow<List<DetectedMediaItem>>(emptyList())
    val detectedMedia: StateFlow<List<DetectedMediaItem>> = _detectedMedia.asStateFlow()

    private val _downloads = MutableStateFlow<List<AcceleratedDownloadTask>>(emptyList())
    val downloads: StateFlow<List<AcceleratedDownloadTask>> = _downloads.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    fun recordSniffedMediaUrl(
        url: String,
        tabUrl: String,
        kindHint: MediaKind? = null,
        titleHint: String? = null,
        qualityHint: String? = null
    ) {
        val kind = kindHint ?: classifyMediaKind(url) ?: return
        val lower = url.lowercase(Locale.US)
        val isHls = lower.contains(".m3u8")
        val isDash = lower.contains(".mpd")
        val badge = detectFormatBadge(lower, kind)
        val defaultMime = when (kind) {
            MediaKind.VIDEO -> if (isHls) "application/x-mpegURL" else if (isDash) "application/dash+xml" else if (lower.contains(".webm")) "video/webm" else "video/mp4"
            MediaKind.AUDIO -> when {
                lower.contains(".m4a") -> "audio/mp4"
                lower.contains(".ogg") -> "audio/ogg"
                lower.contains(".wav") -> "audio/wav"
                lower.contains(".flac") -> "audio/flac"
                else -> "audio/mpeg"
            }
            MediaKind.PHOTO -> when {
                lower.contains(".png") -> "image/png"
                lower.contains(".webp") -> "image/webp"
                lower.contains(".gif") -> "image/gif"
                else -> "image/jpeg"
            }
        }
        val fileHint = titleHint?.takeIf { it.isNotBlank() }
            ?: url.substringBefore('?').substringAfterLast('/').ifBlank { "Captured $badge" }
        val sizeText = when {
            !qualityHint.isNullOrBlank() -> qualityHint
            isHls || isDash -> "Adaptive Stream"
            kind == MediaKind.AUDIO -> "Audio Track"
            kind == MediaKind.PHOTO -> "High-Res Photo"
            else -> "Video File"
        }
        val item = DetectedMediaItem(
            url = url,
            title = fileHint.take(72),
            mimeType = defaultMime,
            formatBadge = badge,
            kind = kind,
            qualityLabel = qualityHint.orEmpty(),
            sizeLabel = sizeText,
            isHls = isHls,
            isDash = isDash,
            tabUrl = tabUrl
        )
        _detectedMedia.update { current ->
            if (current.any { it.url == url }) current else (listOf(item) + current).take(60)
        }
    }

    fun recordExtractedDomPayload(rawJson: String, tabUrl: String) {
        val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: return
        for (i in 0 until arr.length()) {
            val entry = arr.opt(i)
            if (entry is JSONObject) {
                val mediaUrl = entry.optString("url")
                if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) {
                    val kind = when (entry.optString("kind").uppercase(Locale.US)) {
                        "AUDIO" -> MediaKind.AUDIO
                        "PHOTO" -> MediaKind.PHOTO
                        else -> MediaKind.VIDEO
                    }
                    recordSniffedMediaUrl(
                        url = mediaUrl,
                        tabUrl = tabUrl,
                        kindHint = kind,
                        titleHint = entry.optString("title").takeIf { it.isNotBlank() },
                        qualityHint = entry.optString("quality").takeIf { it.isNotBlank() }
                    )
                }
            } else if (entry is String && (entry.startsWith("http://") || entry.startsWith("https://"))) {
                recordSniffedMediaUrl(entry, tabUrl)
            }
        }
    }

    /**
     * SnapTube / yt-dlp style link extractor:
     * Inspects direct media files (MP4/WebM/HLS/DASH, MP3/M4A/OGG, JPG/PNG/WebP/GIF)
     * OR parses HTML pages for video, audio, and photo streams (OpenGraph, JSON-LD,
     * HTML5 media elements, and embedded player formats). Strictly refuses DRM streams.
     */
    suspend fun extractMediaFromLink(rawUrl: String): List<DetectedMediaItem> = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        require(trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            "Enter a valid http:// or https:// media or page link."
        }

        val ytId = com.browserhub.app.privacy.YouTubeStreamResolver.extractVideoId(trimmed)
        if (ytId != null) {
            val resolved = com.browserhub.app.privacy.YouTubeStreamResolver.resolvePlayableStream(ytId)
            if (resolved != null) {
                val ytItems = buildList {
                    add(
                        DetectedMediaItem(
                            url = resolved.streamUrl,
                            title = "${resolved.title.take(48)} (Video)",
                            mimeType = if (resolved.isHls) "application/x-mpegURL" else "video/mp4",
                            formatBadge = if (resolved.isHls) "HLS" else "MP4",
                            kind = MediaKind.VIDEO,
                            qualityLabel = "Ad-Free Stream",
                            sizeLabel = resolved.sourceName,
                            isHls = resolved.isHls,
                            tabUrl = trimmed
                        )
                    )
                    resolved.audioUrl?.let { aud ->
                        add(
                            DetectedMediaItem(
                                url = aud,
                                title = "${resolved.title.take(48)} (Audio)",
                                mimeType = "audio/mp4",
                                formatBadge = "M4A",
                                kind = MediaKind.AUDIO,
                                qualityLabel = "Audio Track",
                                sizeLabel = "High-Bitrate Audio",
                                tabUrl = trimmed
                            )
                        )
                    }
                }
                _detectedMedia.update { current ->
                    val existing = current.map { it.url }.toSet()
                    val fresh = ytItems.filterNot { it.url in existing }
                    (fresh + current).take(60)
                }
                return@withContext ytItems
            }
        }

        val lower = trimmed.lowercase(Locale.US)
        val conn = (URL(trimmed).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
            setRequestProperty("Accept", "*/*")
        }

        try {
            val code = conn.responseCode
            if (code >= 400 && code != 416) {
                throw IllegalStateException("Server returned HTTP $code")
            }
            val contentType = conn.contentType.orEmpty().lowercase(Locale.US)
            val contentLength = conn.getHeaderField("Content-Range")
                ?.substringAfter("/")
                ?.toLongOrNull()
                ?: conn.contentLengthLong.takeIf { it > 0 } ?: 0L

            // If HTML page, extract all video, audio, and photo URLs inside it
            if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                val html = conn.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(196_608)
                    val total = StringBuilder()
                    while (total.length < 196_608) {
                        val n = r.read(buf)
                        if (n == -1) break
                        total.append(buf, 0, n)
                    }
                    total.toString()
                }
                val extracted = mutableListOf<DetectedMediaItem>()
                if (lower.contains("reddit.com/")) {
                    extracted += tryExtractRedditJson(trimmed)
                }
                extracted += parseHtmlMediaItems(html, trimmed)
                val distinct = extracted.distinctBy { it.url }
                if (distinct.isEmpty()) {
                    throw IllegalStateException("No downloadable non-DRM media streams found on this page.")
                }
                _detectedMedia.update { current ->
                    val existingUrls = current.map { it.url }.toSet()
                    val fresh = distinct.filterNot { it.url in existingUrls }
                    (fresh + current).take(60)
                }
                return@withContext distinct
            }

            // Otherwise inspect direct stream/file
            val snippet = if (!contentType.startsWith("image/")) {
                try {
                    conn.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(8192)
                        val n = r.read(buf)
                        if (n > 0) String(buf, 0, n) else ""
                    }
                } catch (_: Exception) {
                    ""
                }
            } else ""

            val isHls = lower.contains(".m3u8") || contentType.contains("mpegurl") || snippet.contains("#EXTM3U")
            val isDash = lower.contains(".mpd") || contentType.contains("dash+xml") || snippet.contains("<MPD")
            val hasWidevine = snippet.contains("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed", true) ||
                snippet.contains("SAMPLE-AES", true) ||
                snippet.contains("com.widevine", true) ||
                snippet.contains("com.microsoft.playready", true)

            val kind = when {
                isHls || isDash || contentType.startsWith("video/") -> MediaKind.VIDEO
                contentType.startsWith("audio/") -> MediaKind.AUDIO
                contentType.startsWith("image/") -> MediaKind.PHOTO
                else -> classifyMediaKind(trimmed) ?: MediaKind.VIDEO
            }
            val badge = when {
                hasWidevine -> "DRM"
                isHls -> "HLS"
                isDash -> "DASH"
                else -> detectFormatBadge(lower, kind, contentType)
            }
            val fileName = trimmed.substringBefore('?').substringAfterLast('/')
                .ifBlank {
                    when (kind) {
                        MediaKind.VIDEO -> if (isHls) "stream.ts" else "video.mp4"
                        MediaKind.AUDIO -> "audio.mp3"
                        MediaKind.PHOTO -> "photo.jpg"
                    }
                }
            val sizeText = if (contentLength > 0) formatBytes(contentLength) else if (isHls) "HLS Playlist" else kind.label

            val item = DetectedMediaItem(
                url = trimmed,
                title = fileName,
                mimeType = contentType.substringBefore(';').ifBlank { "application/octet-stream" },
                formatBadge = badge,
                kind = kind,
                sizeLabel = sizeText,
                isHls = isHls,
                isDash = isDash,
                isDrmProtected = hasWidevine,
                drmReason = if (hasWidevine) "Refused: Widevine / PlayReady encrypted DRM stream cannot be downloaded." else null
            )
            _detectedMedia.update { current ->
                if (current.any { it.url == item.url }) current else listOf(item) + current
            }
            listOf(item)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun inspectMediaLink(rawUrl: String): DetectedMediaItem {
        return extractMediaFromLink(rawUrl).first()
    }

    private fun tryExtractRedditJson(url: String): List<DetectedMediaItem> {
        return try {
            val clean = url.substringBefore('?').trimEnd('/') + ".json"
            val raw = String(fetchBytes(clean), Charsets.UTF_8)
            val arr = JSONArray(raw)
            val data = arr.optJSONObject(0)?.optJSONObject("data")
                ?.optJSONArray("children")?.optJSONObject(0)?.optJSONObject("data") ?: return emptyList()
            val title = data.optString("title", "Reddit Media").take(64)
            val found = mutableListOf<DetectedMediaItem>()
            val fallbackVideo = data.optJSONObject("secure_media")
                ?.optJSONObject("reddit_video")?.optString("fallback_url")
                ?: data.optJSONObject("media")?.optJSONObject("reddit_video")?.optString("fallback_url")
            if (!fallbackVideo.isNullOrBlank()) {
                found += DetectedMediaItem(
                    url = fallbackVideo,
                    title = "$title (Video)",
                    mimeType = "video/mp4",
                    formatBadge = "MP4",
                    kind = MediaKind.VIDEO,
                    qualityLabel = "Reddit HD",
                    sizeLabel = "HD MP4",
                    tabUrl = url
                )
            }
            val postUrl = data.optString("url")
            if (postUrl.startsWith("http") && classifyMediaKind(postUrl) == MediaKind.PHOTO) {
                found += DetectedMediaItem(
                    url = postUrl,
                    title = "$title (Photo)",
                    mimeType = "image/jpeg",
                    formatBadge = detectFormatBadge(postUrl.lowercase(Locale.US), MediaKind.PHOTO),
                    kind = MediaKind.PHOTO,
                    sizeLabel = "Original Photo",
                    tabUrl = url
                )
            }
            found
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseHtmlMediaItems(html: String, baseUrl: String): List<DetectedMediaItem> =
        Companion.parseHtmlMediaItems(html, baseUrl)

    fun startAcceleratedDownload(
        url: String,
        suggestedName: String? = null,
        isHlsHint: Boolean = false,
        mimeTypeHint: String? = null
    ): AcceleratedDownloadTask {
        val cleanBase = (suggestedName ?: url.substringBefore('?').substringAfterLast('/'))
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "download_${System.currentTimeMillis()}" }
        val ext = inferExtension(url, mimeTypeHint, isHlsHint)
        val finalName = if (cleanBase.contains('.') && cleanBase.substringAfterLast('.').length in 2..5) {
            cleanBase
        } else {
            "$cleanBase.$ext"
        }

        val task = AcceleratedDownloadTask(
            fileName = finalName,
            url = url,
            threadsUsed = 16,
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.RUNNING
        )
        _downloads.update { listOf(task) + it }

        val job = scope.launch(Dispatchers.IO) {
            try {
                if (isHlsHint || url.lowercase(Locale.US).contains(".m3u8")) {
                    downloadHlsStream(task.id, url, finalName)
                } else {
                    downloadMultiThread(task.id, url, finalName, maxThreads = 16, mimeHint = mimeTypeHint)
                }
            } catch (e: CancellationException) {
                updateTask(task.id) { it.copy(status = DownloadStatus.CANCELLED) }
                throw e
            } catch (e: Exception) {
                updateTask(task.id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.localizedMessage ?: "Download failed"
                    )
                }
            } finally {
                activeJobs.remove(task.id)
            }
        }
        activeJobs[task.id] = job
        return task
    }

    fun cancelDownload(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        updateTask(taskId) { it.copy(status = DownloadStatus.CANCELLED) }
    }

    fun clearMediaList() {
        _detectedMedia.value = emptyList()
    }

    private suspend fun downloadMultiThread(
        taskId: String,
        url: String,
        fileName: String,
        maxThreads: Int,
        mimeHint: String?
    ) = coroutineScope {
        val headConn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
        }
        val acceptRanges: Boolean
        val totalSize: Long
        val resolvedMime: String
        try {
            headConn.responseCode
            acceptRanges = headConn.getHeaderField("Accept-Ranges")?.equals("bytes", true) == true
            totalSize = headConn.contentLengthLong
            resolvedMime = headConn.contentType?.substringBefore(';')?.ifBlank { null }
                ?: mimeHint
                ?: guessMimeFromFileName(fileName)
        } finally {
            headConn.disconnect()
        }

        val tempFile = File(context.cacheDir, "dl_${taskId}_$fileName")
        val threads = if (acceptRanges && totalSize >= 256 * 1024) maxThreads else 1
        updateTask(taskId) { it.copy(threadsUsed = threads, totalBytes = totalSize.coerceAtLeast(0L)) }

        if (threads > 1 && totalSize > 0L) {
            RandomAccessFile(tempFile, "rw").use { raf -> raf.setLength(totalSize) }
            val downloadedCounter = AtomicLong(0L)
            val chunkSize = (totalSize + threads - 1) / threads
            val jobs = (0 until threads).map { idx ->
                val start = idx * chunkSize
                val end = minOf(totalSize - 1, (idx + 1) * chunkSize - 1)
                async(Dispatchers.IO) {
                    if (start > end) return@async
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12_000
                        readTimeout = 20_000
                        setRequestProperty("User-Agent", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
                        setRequestProperty("Range", "bytes=$start-$end")
                    }
                    try {
                        conn.inputStream.use { input ->
                            RandomAccessFile(tempFile, "rw").use { raf ->
                                raf.seek(start)
                                val buf = ByteArray(16_384)
                                var read: Int
                                while (input.read(buf).also { read = it } != -1) {
                                    raf.write(buf, 0, read)
                                    val acc = downloadedCounter.addAndGet(read.toLong())
                                    updateTask(taskId) { it.copy(downloadedBytes = acc) }
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
            }
            jobs.awaitAll()
        } else {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
            }
            try {
                val len = conn.contentLengthLong.coerceAtLeast(0L)
                updateTask(taskId) { it.copy(totalBytes = len) }
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { out ->
                        val buf = ByteArray(16_384)
                        var acc = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            acc += read
                            updateTask(taskId) { it.copy(downloadedBytes = acc) }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

        val finalDest = saveToDownloads(tempFile, fileName, resolvedMime)
        tempFile.delete()
        updateTask(taskId) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = maxOf(it.downloadedBytes, it.totalBytes),
                savedLocation = finalDest
            )
        }
    }

    private suspend fun downloadHlsStream(
        taskId: String,
        playlistUrl: String,
        fileName: String
    ) {
        val playlistText = fetchText(playlistUrl)
        if (playlistText.contains("SAMPLE-AES", true) || playlistText.contains("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed", true)) {
            throw IllegalStateException("DRM-encrypted HLS streams are refused.")
        }

        var mediaPlaylistUrl = playlistUrl
        var mediaText = playlistText
        if (playlistText.contains("#EXT-X-STREAM-INF")) {
            val firstVariant = playlistText.lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: throw IllegalStateException("No playable variant stream found in HLS playlist.")
            mediaPlaylistUrl = URL(URL(playlistUrl), firstVariant).toString()
            mediaText = fetchText(mediaPlaylistUrl)
        }

        var aesKeyBytes: ByteArray? = null
        var aesIvBytes: ByteArray? = null
        val segments = mutableListOf<String>()
        for (line in mediaText.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-KEY:")) {
                if (trimmed.contains("METHOD=AES-128", true)) {
                    val keyUriMatch = Regex("URI=\"([^\"]+)\"").find(trimmed)
                    val keyUrl = keyUriMatch?.groupValues?.getOrNull(1)?.let {
                        URL(URL(mediaPlaylistUrl), it).toString()
                    }
                    if (keyUrl != null) {
                        aesKeyBytes = fetchBytes(keyUrl)
                    }
                    val ivHex = Regex("IV=0x([0-9a-fA-F]+)").find(trimmed)?.groupValues?.getOrNull(1)
                    if (ivHex != null && ivHex.length >= 32) {
                        aesIvBytes = ivHex.takeLast(32).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    }
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                segments.add(URL(URL(mediaPlaylistUrl), trimmed).toString())
            }
        }
        if (segments.isEmpty()) throw IllegalStateException("No segments found in HLS playlist.")

        val tempFile = File(context.cacheDir, "hls_${taskId}_$fileName")
        updateTask(taskId) { it.copy(threadsUsed = 4, totalBytes = segments.size.toLong()) }
        FileOutputStream(tempFile).use { out ->
            segments.forEachIndexed { idx, segUrl ->
                var segBytes = fetchBytes(segUrl)
                val key = aesKeyBytes
                if (key != null && key.size == 16) {
                    val iv = aesIvBytes ?: ByteArray(16).apply { this[15] = idx.toByte() }
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                    segBytes = cipher.doFinal(segBytes)
                }
                out.write(segBytes)
                updateTask(taskId) { it.copy(downloadedBytes = (idx + 1).toLong()) }
            }
        }
        val finalDest = saveToDownloads(tempFile, fileName, "video/mp2t")
        tempFile.delete()
        updateTask(taskId) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                savedLocation = finalDest
            )
        }
    }

    private fun saveToDownloads(sourceFile: File, fileName: String, mimeType: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BrowserHub")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                return "Downloads/BrowserHub/$fileName"
            }
        }
        val destDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "BrowserHub").apply { mkdirs() }
        val dest = File(destDir, fileName)
        sourceFile.copyTo(dest, overwrite = true)
        return dest.absolutePath
    }

    private fun fetchText(url: String): String = fetchBytes(url).toString(Charsets.UTF_8)

    private fun fetchBytes(rawUrl: String): ByteArray {
        val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", ShieldsEngine.CLEAN_MOBILE_CHROME_UA)
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} while fetching $rawUrl")
            }
            val bos = ByteArrayOutputStream()
            conn.inputStream.use { it.copyTo(bos) }
            return bos.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    private fun updateTask(id: String, transform: (AcceleratedDownloadTask) -> AcceleratedDownloadTask) {
        _downloads.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    internal fun classifyMediaKind(url: String): MediaKind? = Companion.classifyMediaKind(url)

    private fun detectFormatBadge(lowerUrl: String, kind: MediaKind, contentType: String = ""): String =
        Companion.detectFormatBadge(lowerUrl, kind, contentType)

    private fun inferExtension(url: String, mimeHint: String?, isHlsHint: Boolean): String {
        if (isHlsHint || url.lowercase(Locale.US).contains(".m3u8")) return "ts"
        val pathExt = url.substringBefore('?').substringAfterLast('.').lowercase(Locale.US)
        if (pathExt in setOf("mp4", "webm", "mp3", "m4a", "ogg", "wav", "flac", "jpg", "jpeg", "png", "webp", "gif", "zip", "pdf", "apk")) {
            return pathExt
        }
        return when {
            mimeHint?.startsWith("image/png") == true -> "png"
            mimeHint?.startsWith("image/webp") == true -> "webp"
            mimeHint?.startsWith("image/gif") == true -> "gif"
            mimeHint?.startsWith("image/") == true -> "jpg"
            mimeHint?.startsWith("audio/mp4") == true -> "m4a"
            mimeHint?.startsWith("audio/") == true -> "mp3"
            else -> "mp4"
        }
    }

    private fun guessMimeFromFileName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "jpg", "jpeg" -> "image/jpeg"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "ts" -> "video/mp2t"
            else -> "video/mp4"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    companion object {
        fun classifyMediaKind(url: String): MediaKind? {
            val lower = url.lowercase(Locale.US)
            return when {
                lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".m3u8") ||
                    lower.contains(".mpd") || lower.contains(".mov") || lower.contains(".mkv") ||
                    lower.contains("mime=video") -> MediaKind.VIDEO
                lower.contains(".mp3") || lower.contains(".m4a") || lower.contains(".aac") ||
                    lower.contains(".ogg") || lower.contains(".flac") || lower.contains(".wav") ||
                    lower.contains(".opus") || lower.contains("mime=audio") -> MediaKind.AUDIO
                lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
                    lower.contains(".webp") || lower.contains(".gif") || lower.contains(".avif") -> MediaKind.PHOTO
                else -> null
            }
        }

        fun detectFormatBadge(lowerUrl: String, kind: MediaKind, contentType: String = ""): String {
            return when {
                lowerUrl.contains(".m3u8") || contentType.contains("mpegurl") -> "HLS"
                lowerUrl.contains(".mpd") || contentType.contains("dash") -> "DASH"
                lowerUrl.contains(".webm") || contentType.contains("webm") -> "WEBM"
                lowerUrl.contains(".mp4") -> "MP4"
                lowerUrl.contains(".m4a") -> "M4A"
                lowerUrl.contains(".mp3") -> "MP3"
                lowerUrl.contains(".ogg") -> "OGG"
                lowerUrl.contains(".flac") -> "FLAC"
                lowerUrl.contains(".wav") -> "WAV"
                lowerUrl.contains(".png") || contentType.contains("png") -> "PNG"
                lowerUrl.contains(".webp") || contentType.contains("webp") -> "WEBP"
                lowerUrl.contains(".gif") || contentType.contains("gif") -> "GIF"
                kind == MediaKind.AUDIO -> "AUDIO"
                kind == MediaKind.PHOTO -> "JPG"
                else -> "MP4"
            }
        }

        fun parseHtmlMediaItems(html: String, baseUrl: String): List<DetectedMediaItem> {
            val results = mutableListOf<DetectedMediaItem>()
            val pageTitle = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim()
                .orEmpty().ifBlank { "Page Media" }

            val base = runCatching { URL(baseUrl) }.getOrNull()

            fun addCandidate(rawCandidate: String, kind: MediaKind, quality: String = "") {
                val abs = runCatching {
                    if (base != null) URL(base, rawCandidate.trim()).toString() else rawCandidate.trim()
                }.getOrNull() ?: return
                if (!abs.startsWith("http://") && !abs.startsWith("https://")) return
                val lower = abs.lowercase(Locale.US)
                val isDrm = lower.contains("widevine") || lower.contains("playready")
                val isHls = lower.contains(".m3u8")
                val isDash = lower.contains(".mpd")
                val badge = if (isDrm) "DRM" else detectFormatBadge(lower, kind)
                results += DetectedMediaItem(
                    url = abs,
                    title = "${pageTitle.take(44)} • ${abs.substringBefore('?').substringAfterLast('/').ifBlank { badge }}",
                    mimeType = when (kind) {
                        MediaKind.VIDEO -> if (isHls) "application/x-mpegURL" else "video/mp4"
                        MediaKind.AUDIO -> "audio/mpeg"
                        MediaKind.PHOTO -> "image/jpeg"
                    },
                    formatBadge = badge,
                    kind = kind,
                    qualityLabel = quality,
                    sizeLabel = quality.ifBlank { kind.label },
                    isHls = isHls,
                    isDash = isDash,
                    isDrmProtected = isDrm,
                    drmReason = if (isDrm) "DRM-protected stream cannot be saved." else null,
                    tabUrl = baseUrl
                )
            }

            // OpenGraph / Twitter meta tags
            Regex("<meta[^>]+(?:property|name)\\s*=\\s*[\"']([^\"']+)[\"'][^>]+content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { m ->
                    val prop = m.groupValues[1].lowercase(Locale.US)
                    val url = m.groupValues[2]
                    when {
                        prop.contains("og:video") || prop == "twitter:player:stream" -> addCandidate(url, MediaKind.VIDEO, "Page Video")
                        prop.contains("og:audio") -> addCandidate(url, MediaKind.AUDIO, "Page Audio")
                        prop == "og:image" || prop == "twitter:image" -> addCandidate(url, MediaKind.PHOTO, "Cover Photo")
                    }
                }

            // Direct media URLs in HTML attributes/scripts (.mp4, .webm, .m3u8, .mp3, .m4a)
            Regex("(https?://[^\"'\\s<>\\\\]+?\\.(?:mp4|webm|m3u8|m4a|mp3|ogg|jpg|jpeg|png|webp)(?:\\?[^\"'\\s<>\\\\]*)?)", RegexOption.IGNORE_CASE)
                .findAll(html).take(30).forEach { m ->
                    val found = m.groupValues[1].replace("\\u0026", "&").replace("\\/", "/")
                    val kind = classifyMediaKind(found) ?: return@forEach
                    addCandidate(found, kind)
                }

            return results
        }

        const val PROBE_PAGE_MEDIA_JS: String = """
            (function() {
                var out = [];
                var seen = {};
                var add = function(url, kind, title, quality) {
                    if (!url || (!url.startsWith('http://') && !url.startsWith('https://'))) return;
                    if (seen[url]) return;
                    seen[url] = true;
                    out.push({ url: url, kind: kind, title: title || '', quality: quality || '' });
                };
                var pageTitle = (document.title || '').slice(0, 48);
                // 1. HTML5 Video & Audio
                document.querySelectorAll('video, video source').forEach(function(el) {
                    var src = el.currentSrc || el.src || el.getAttribute('src');
                    var w = el.videoWidth || 0;
                    var h = el.videoHeight || 0;
                    var q = (h > 0) ? (h + 'p Video') : 'Video Stream';
                    add(src, 'VIDEO', pageTitle, q);
                });
                document.querySelectorAll('audio, audio source').forEach(function(el) {
                    var src = el.currentSrc || el.src || el.getAttribute('src');
                    add(src, 'AUDIO', pageTitle, 'Audio Track');
                });
                // 2. OpenGraph / Meta media
                var ogVid = document.querySelector('meta[property="og:video"], meta[property="og:video:secure_url"], meta[name="twitter:player:stream"]');
                if (ogVid && ogVid.content) add(ogVid.content, 'VIDEO', pageTitle, 'Featured Video');
                var ogAud = document.querySelector('meta[property="og:audio"]');
                if (ogAud && ogAud.content) add(ogAud.content, 'AUDIO', pageTitle, 'Featured Audio');
                var ogImg = document.querySelector('meta[property="og:image"], meta[name="twitter:image"]');
                if (ogImg && ogImg.content) add(ogImg.content, 'PHOTO', pageTitle + ' Cover', 'Cover Photo');
                // 3. High-resolution Photos (>= 200px wide or tall)
                document.querySelectorAll('img').forEach(function(img) {
                    if (out.length >= 45) return;
                    var w = img.naturalWidth || img.width || 0;
                    var h = img.naturalHeight || img.height || 0;
                    if (w >= 200 || h >= 200) {
                        var src = img.currentSrc || img.src;
                        add(src, 'PHOTO', (img.alt || pageTitle || 'Photo').slice(0, 48), w + 'x' + h + ' Photo');
                    }
                });
                return JSON.stringify(out);
            })();
        """

        const val PROBE_PAGE_VIDEOS_JS: String = PROBE_PAGE_MEDIA_JS
    }
}
