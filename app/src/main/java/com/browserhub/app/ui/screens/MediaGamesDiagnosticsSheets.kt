package com.browserhub.app.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.browserhub.app.ai.AiAttemptLog
import com.browserhub.app.browser.BenchmarkReport
import com.browserhub.app.browser.DevToolsLogEntry
import com.browserhub.app.diagnostics.DiagnosticsEngine
import com.browserhub.app.diagnostics.DiagnosticsReport
import com.browserhub.app.downloads.AcceleratedDownloadTask
import com.browserhub.app.downloads.DetectedMediaItem
import com.browserhub.app.downloads.DownloadStatus
import com.browserhub.app.downloads.MediaKind
import com.browserhub.app.games.BrowserGame
import com.browserhub.app.games.GamesCatalog
import com.browserhub.app.ui.theme.FlagshipCrimsonDarkBg
import com.browserhub.app.ui.theme.FlagshipEmeraldDarkBg
import com.browserhub.app.ui.theme.LocalBrowserColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VideoGrabberSheet(
    detectedMedia: List<DetectedMediaItem>,
    downloads: List<AcceleratedDownloadTask>,
    isProbing: Boolean,
    probeError: String?,
    onCheckUrl: (String) -> Unit,
    onRescanPage: () -> Unit = {},
    onSaveAllPhotos: () -> Unit = {},
    onStartDownload: (DetectedMediaItem) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    var urlInput by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf<MediaKind?>(null) }

    val filteredMedia = remember(detectedMedia, selectedKind) {
        if (selectedKind == null) detectedMedia else detectedMedia.filter { it.kind == selectedKind }
    }
    val videoCount = remember(detectedMedia) { detectedMedia.count { it.kind == MediaKind.VIDEO } }
    val audioCount = remember(detectedMedia) { detectedMedia.count { it.kind == MediaKind.AUDIO } }
    val photoCount = remember(detectedMedia) { detectedMedia.count { it.kind == MediaKind.PHOTO } }

    ModalSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FlagshipEmeraldDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = browserColors.emerald, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Media Grabber (Video · Audio · Photos)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "16-Thread SnapTube-style Extractor · MP4/HLS/MP3/Photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_grabber_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Paste any page URL or direct media link
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("Paste page link or MP4/M3U8/MP3/Photo URL") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("video_grabber_url_input")
                    )
                    Button(
                        onClick = { if (urlInput.isNotBlank()) onCheckUrl(urlInput) },
                        enabled = urlInput.isNotBlank() && !isProbing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("video_grabber_check_button")
                    ) {
                        if (isProbing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Extract", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Rescan current page + Save All Photos batch action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRescanPage,
                        enabled = !isProbing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("video_grabber_rescan_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rescan Page", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onSaveAllPhotos,
                        enabled = photoCount > 0,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("video_grabber_save_photos_button")
                    ) {
                        Icon(Icons.Outlined.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Photos ($photoCount)", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }

            // Filter Chips: All / Video / Audio / Photos
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MediaFilterChip(
                        label = "All (${detectedMedia.size})",
                        selected = selectedKind == null,
                        onClick = { selectedKind = null },
                        testTag = "media_filter_all",
                        modifier = Modifier.weight(1f)
                    )
                    MediaFilterChip(
                        label = "Video ($videoCount)",
                        selected = selectedKind == MediaKind.VIDEO,
                        onClick = { selectedKind = MediaKind.VIDEO },
                        testTag = "media_filter_video",
                        modifier = Modifier.weight(1f)
                    )
                    MediaFilterChip(
                        label = "Audio ($audioCount)",
                        selected = selectedKind == MediaKind.AUDIO,
                        onClick = { selectedKind = MediaKind.AUDIO },
                        testTag = "media_filter_audio",
                        modifier = Modifier.weight(1f)
                    )
                    MediaFilterChip(
                        label = "Photo ($photoCount)",
                        selected = selectedKind == MediaKind.PHOTO,
                        onClick = { selectedKind = MediaKind.PHOTO },
                        testTag = "media_filter_photo",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!probeError.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = FlagshipCrimsonDarkBg,
                        border = BorderStroke(1.dp, browserColors.crimson.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = browserColors.crimson, modifier = Modifier.size(18.dp))
                            Text(text = probeError, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFDA4AF))
                        }
                    }
                }
            }

            item {
                Text(
                    text = "DETECTED STREAMS & FILES (${filteredMedia.size})",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (filteredMedia.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, browserColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open any webpage with non-DRM video, audio, or high-res photos and tap Rescan Page, or paste any page/media URL above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(filteredMedia, key = { it.id }) { item ->
                    val kindIcon = when (item.kind) {
                        MediaKind.VIDEO -> Icons.Outlined.VideoLibrary
                        MediaKind.AUDIO -> Icons.Outlined.AudioFile
                        MediaKind.PHOTO -> Icons.Outlined.Photo
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, browserColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (item.isDrmProtected) FlagshipCrimsonDarkBg else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = kindIcon,
                                        contentDescription = item.kind.label,
                                        tint = if (item.isDrmProtected) browserColors.crimson else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = item.formatBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (item.isDrmProtected) browserColors.crimson else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = if (item.isDrmProtected) {
                                    item.drmReason ?: "DRM protected"
                                } else {
                                    val qual = item.qualityLabel.ifBlank { item.sizeLabel }
                                    "${item.kind.label} • $qual • ${item.mimeType}"
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (!item.isDrmProtected) {
                                Button(
                                    onClick = { onStartDownload(item) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = browserColors.emerald,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }

            if (downloads.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "DOWNLOADS (${downloads.size})",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(downloads, key = { it.id }) { dl ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, browserColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dl.fileName,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${dl.threadsUsed} threads",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (dl.status == DownloadStatus.RUNNING) {
                                    IconButton(onClick = { onCancelDownload(dl.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Cancel", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            if (dl.status == DownloadStatus.RUNNING && dl.totalBytes > 0) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(progress = { dl.progressFraction }, modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = when (dl.status) {
                                    DownloadStatus.RUNNING -> "${dl.downloadedBytes / 1024} KB"
                                    DownloadStatus.COMPLETED -> "Saved to ${dl.savedLocation}"
                                    DownloadStatus.FAILED -> dl.errorMessage ?: "Download failed"
                                    DownloadStatus.CANCELLED -> "Cancelled"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (dl.status) {
                                    DownloadStatus.COMPLETED -> browserColors.emerald
                                    DownloadStatus.FAILED -> browserColors.crimson
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder),
        modifier = modifier
            .height(34.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.5.sp
                ),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AboutDiagnosticsSheet(
    report: DiagnosticsReport,
    aiAttempts: List<AiAttemptLog>,
    onRecheck: () -> Unit,
    onShowToast: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val browserColors = LocalBrowserColors.current

    val aiText = remember(aiAttempts) {
        if (aiAttempts.isEmpty()) "No AI requests recorded in this session yet."
        else aiAttempts.joinToString("\n") { a ->
            val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(a.timestampMs))
            "[$t] ${a.provider}: ${a.outcome} (${a.latencyMs}ms) — ${a.detail}"
        }
    }

    ModalSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "About BrowserHub",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Real system capability checks — nothing here is fabricated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("diagnostics_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val rows = listOf(
                    Triple("Runtime", report.runtimeSummary, MaterialTheme.colorScheme.primary),
                    Triple("Native bridge", if (report.nativeBridgeConnected) "connected" else "disconnected", browserColors.emerald),
                    Triple("Device tier", report.deviceTier, MaterialTheme.colorScheme.primary),
                    Triple("3D graphics", report.webGlStatus, if (report.webGlSupported) browserColors.emerald else browserColors.amber),
                    Triple("WebAssembly", if (report.wasmAvailable) "available" else "unavailable", if (report.wasmAvailable) browserColors.emerald else browserColors.crimson),
                    Triple("CPU cores", "${report.cpuCores} cores", MaterialTheme.colorScheme.primary),
                    Triple("Gamepad API", "${report.gamepadCount} connected", MaterialTheme.colorScheme.onSurfaceVariant),
                    Triple("Extensions working", "${report.extensionsWorking} of ${report.extensionsTotal} installed", browserColors.emerald),
                    Triple("Browser games", "${report.gamesCount} listed", browserColors.amber),
                    Triple("Location VPN", report.vpnStatusSummary, if (report.vpnActive) browserColors.emerald else MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { (k, v, accent) ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, browserColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = k,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.42f)
                                )
                                Text(
                                    text = v,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = accent,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.58f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "LAST AI ATTEMPTS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, browserColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = aiText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val text = DiagnosticsEngine.formatCopyableReport(report, aiText)
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("BrowserHub Diagnostics", text))
                    onShowToast("Diagnostics report copied to clipboard")
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("diagnostics_copy_button")
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy report")
            }
            Button(
                onClick = onRecheck,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("diagnostics_recheck_button")
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Re-check", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GamesHubSheet(
    activeGame: BrowserGame?,
    onLaunchGame: (BrowserGame) -> Unit,
    onCloseGame: () -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current

    ModalSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FlagshipCrimsonDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.SportsEsports, contentDescription = null, tint = browserColors.crimson, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BrowserHub Games Arcade",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${GamesCatalog.games.size} playable offline HTML5 & Canvas games",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("games_hub_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(GamesCatalog.games.chunked(2)) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { game ->
                        val accent = Color(game.accentHex)
                        Surface(
                            onClick = { onLaunchGame(game) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, browserColors.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("game_card_${game.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.16f)) {
                                        Text(
                                            text = game.category,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            color = accent,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Play", tint = accent, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = game.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = game.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (activeGame != null) {
        val html = remember(activeGame.id) { GamesCatalog.buildPlayableGameHtml(activeGame) }
        Dialog(
            onDismissRequest = onCloseGame,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.SportsEsports, contentDescription = null, tint = Color(activeGame.accentHex), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = activeGame.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onCloseGame,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("game_player_close")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Exit game")
                        }
                    }
                    HorizontalDivider(color = browserColors.cardBorder)
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("game_player_webview"),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                loadDataWithBaseURL("https://browserhub.local/", html, "text/html", "utf-8", null)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedBenchmarkSheet(
    report: BenchmarkReport?,
    isRunning: Boolean,
    onRunBenchmark: () -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current

    ModalSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FlagshipEmeraldDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = browserColors.emerald, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Engine Speed Benchmark",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Live WebView V8 JS · DOM Layout · Canvas2D · Wasm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("benchmark_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, browserColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = report?.totalScore?.toString() ?: "—",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = browserColors.emerald
                    )
                    Text(
                        text = if (report != null) "BrowserHub Composite Score" else "Tap Run Benchmark to test live WebView speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (report != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BenchMetricBox("JS Compute", "${report.jsOpsMs} ms", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    BenchMetricBox("DOM Render", "${report.domRenderMs} ms", browserColors.violet, Modifier.weight(1f))
                    BenchMetricBox("Canvas 2D", "${report.canvas2dMs} ms", browserColors.emerald, Modifier.weight(1f))
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, browserColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GPU / WebGL Renderer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(report.webGlRenderer, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (report.wasmAvailable) FlagshipEmeraldDarkBg else FlagshipCrimsonDarkBg
                        ) {
                            Text(
                                text = if (report.wasmAvailable) "Wasm Ready" else "No Wasm",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (report.wasmAvailable) browserColors.emerald else browserColors.crimson,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onRunBenchmark,
                enabled = !isRunning,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(bottom = 0.dp)
                    .testTag("benchmark_run_button")
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Running Live JS & DOM Suite...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run Benchmark", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun BenchMetricBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, browserColors.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WebDevToolsPane(
    logs: List<DevToolsLogEntry>,
    onEvalExpression: (String) -> Unit,
    onClearLogs: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserColors = LocalBrowserColors.current
    var exprInput by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, browserColors.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .testTag("web_devtools_pane")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Code, contentDescription = null, tint = browserColors.amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "DevTools Console",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearLogs, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close DevTools", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "Console attached. Type any JS expression below (e.g. document.title)",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(logs) { entry ->
                        val color = when (entry.level) {
                            "ERR", "ERROR" -> browserColors.crimson
                            "WARN" -> browserColors.amber
                            "IN" -> MaterialTheme.colorScheme.primary
                            "OUT" -> browserColors.emerald
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = "[${entry.level}] ${entry.message}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = color,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = browserColors.cardBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = exprInput,
                    onValueChange = { exprInput = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (exprInput.isNotBlank()) {
                                onEvalExpression(exprInput)
                                exprInput = ""
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("devtools_eval_input"),
                    decorationBox = { inner ->
                        if (exprInput.isEmpty()) {
                            Text(
                                text = "> document.title",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                )
                IconButton(
                    onClick = {
                        if (exprInput.isNotBlank()) {
                            onEvalExpression(exprInput)
                            exprInput = ""
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Evaluate JS", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
