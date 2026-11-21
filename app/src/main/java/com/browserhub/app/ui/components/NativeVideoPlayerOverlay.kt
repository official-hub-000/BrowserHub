package com.browserhub.app.ui.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.browserhub.app.privacy.ResolvedYouTubeStream
import com.browserhub.app.ui.theme.LocalBrowserColors
import kotlinx.coroutines.delay

data class NativeVideoPlayerState(
    val isVisible: Boolean = false,
    val videoId: String = "",
    val title: String = "Ad-Free Video Player",
    val pageUrl: String = "",
    val streams: List<ResolvedYouTubeStream> = emptyList(),
    val activeStreamIndex: Int = 0,
    val isResolving: Boolean = false,
    val errorMessage: String? = null
) {
    val activeStream: ResolvedYouTubeStream?
        get() = streams.getOrNull(activeStreamIndex)
}

@Composable
fun NativeVideoPlayerOverlay(
    state: NativeVideoPlayerState,
    onSwitchSource: (Int) -> Unit,
    onRetryResolve: () -> Unit,
    onDownloadStream: (ResolvedYouTubeStream) -> Unit,
    onClose: () -> Unit
) {
    if (!state.isVisible) return

    val browserColors = LocalBrowserColors.current
    val activeStream = state.activeStream

    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeSurfaceRef by remember { mutableStateOf<Surface?>(null) }
    var currentBoundUrl by remember { mutableStateOf<String?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var playbackError by remember(activeStream?.streamUrl) { mutableStateOf<String?>(null) }

    fun releasePlayerOnly() {
        runCatching { mediaPlayerRef?.stop() }
        runCatching { mediaPlayerRef?.release() }
        mediaPlayerRef = null
        isPrepared = false
        isPlaying = false
    }

    fun prepareStreamOnSurface(ctx: Context, stream: ResolvedYouTubeStream, surface: Surface) {
        releasePlayerOnly()
        currentBoundUrl = stream.streamUrl
        positionMs = 0
        durationMs = 0
        playbackError = null
        val mp = MediaPlayer()
        mediaPlayerRef = mp
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            mp.setSurface(surface)
            val headers = mapOf(
                "User-Agent" to stream.userAgent,
                "Referer" to "https://www.youtube.com/"
            )
            mp.setDataSource(ctx, Uri.parse(stream.streamUrl), headers)
            mp.setOnVideoSizeChangedListener { _, width, height ->
                if (width > 0 && height > 0) {
                    videoAspectRatio = (width.toFloat() / height.toFloat()).coerceIn(0.45f, 2.5f)
                }
            }
            mp.setOnPreparedListener { prepared ->
                isPrepared = true
                durationMs = prepared.duration.coerceAtLeast(0)
                val vw = prepared.videoWidth
                val vh = prepared.videoHeight
                if (vw > 0 && vh > 0) {
                    videoAspectRatio = (vw.toFloat() / vh.toFloat()).coerceIn(0.45f, 2.5f)
                }
                runCatching {
                    if (isMuted) prepared.setVolume(0f, 0f) else prepared.setVolume(1f, 1f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playbackSpeed != 1.0f) {
                        prepared.playbackParams = prepared.playbackParams.setSpeed(playbackSpeed)
                    }
                }
                prepared.start()
                isPlaying = true
            }
            mp.setOnCompletionListener {
                isPlaying = false
            }
            mp.setOnErrorListener { _, what, extra ->
                isPrepared = false
                isPlaying = false
                val nextIdx = state.activeStreamIndex + 1
                if (nextIdx < state.streams.size) {
                    onSwitchSource(nextIdx)
                } else {
                    playbackError = "Playback error ($what/$extra). Tap Retry or try another source."
                }
                true
            }
            mp.prepareAsync()
        }.onFailure { err ->
            playbackError = err.message ?: "Unable to initialize video stream."
        }
    }

    BackHandler {
        releasePlayerOnly()
        onClose()
    }

    LaunchedEffect(isPlaying, isPrepared, mediaPlayerRef) {
        while (isPrepared && mediaPlayerRef != null) {
            runCatching {
                val mp = mediaPlayerRef ?: return@runCatching
                positionMs = mp.currentPosition
                val d = mp.duration
                if (d > 0) durationMs = d
                isPlaying = mp.isPlaying
            }
            delay(400)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            releasePlayerOnly()
            runCatching { activeSurfaceRef?.release() }
            activeSurfaceRef = null
        }
    }

    Surface(
        color = Color(0xFF030712),
        modifier = Modifier
            .fillMaxSize()
            .testTag("native_video_player_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeStream?.title ?: state.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            state.isResolving -> "Resolving direct YouTube HD streams..."
                            activeStream != null -> "${activeStream.sourceName} • ${if (activeStream.isHls) "HLS HD" else "MP4"}"
                            else -> "Direct Texture Video Player"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = browserColors.emerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (activeStream != null) {
                    IconButton(
                        onClick = { onDownloadStream(activeStream) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("native_player_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download video",
                            tint = browserColors.cyan
                        )
                    }
                }
                IconButton(
                    onClick = {
                        releasePlayerOnly()
                        onClose()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("native_player_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close player",
                        tint = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (activeStream != null && playbackError == null) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(videoAspectRatio)
                            .testTag("native_player_textureview"),
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        surfaceTexture: SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {
                                        val surface = Surface(surfaceTexture)
                                        activeSurfaceRef = surface
                                        prepareStreamOnSurface(ctx, activeStream, surface)
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surfaceTexture: SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) = Unit

                                    override fun onSurfaceTextureDestroyed(
                                        surfaceTexture: SurfaceTexture
                                    ): Boolean {
                                        releasePlayerOnly()
                                        runCatching { activeSurfaceRef?.release() }
                                        activeSurfaceRef = null
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                                }
                            }
                        },
                        update = { tv ->
                            val surface = activeSurfaceRef
                            if (surface != null && currentBoundUrl != activeStream.streamUrl) {
                                prepareStreamOnSurface(tv.context, activeStream, surface)
                            }
                        }
                    )
                }

                if (state.isResolving || (activeStream != null && !isPrepared && playbackError == null)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = browserColors.emerald)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (state.isResolving) "Fetching direct ad-free stream..." else "Buffering HD video...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                } else if (state.errorMessage != null || playbackError != null || (activeStream == null && !state.isResolving)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = playbackError ?: state.errorMessage ?: "No direct stream available for this video.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                playbackError = null
                                currentBoundUrl = null
                                onRetryResolve()
                            },
                            modifier = Modifier.testTag("native_player_retry_button")
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Stream")
                        }
                    }
                }
            }

            Surface(
                color = Color(0xFF0B1120),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (durationMs > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatVideoMillis(positionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Slider(
                                value = positionMs.coerceIn(0, durationMs).toFloat(),
                                onValueChange = { nextPos ->
                                    positionMs = nextPos.toInt()
                                    runCatching { mediaPlayerRef?.seekTo(positionMs) }
                                },
                                valueRange = 0f..durationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = browserColors.emerald,
                                    activeTrackColor = browserColors.emerald
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                text = formatVideoMillis(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (positionMs - 10_000).coerceAtLeast(0)
                                positionMs = target
                                runCatching { mediaPlayerRef?.seekTo(target) }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("native_player_rewind_button")
                        ) {
                            Icon(Icons.Outlined.Replay10, contentDescription = "Back 10 seconds", tint = Color.White)
                        }

                        FilledIconButton(
                            onClick = {
                                val mp = mediaPlayerRef ?: return@FilledIconButton
                                if (!isPrepared) return@FilledIconButton
                                runCatching {
                                    if (mp.isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        mp.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = browserColors.emerald,
                                contentColor = Color(0xFF022C22)
                            ),
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("native_player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val maxDur = if (durationMs > 0) durationMs else Int.MAX_VALUE
                                val target = (positionMs + 10_000).coerceAtMost(maxDur)
                                positionMs = target
                                runCatching { mediaPlayerRef?.seekTo(target) }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("native_player_forward_button")
                        ) {
                            Icon(Icons.Outlined.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                val nextMute = !isMuted
                                isMuted = nextMute
                                runCatching {
                                    mediaPlayerRef?.setVolume(
                                        if (nextMute) 0f else 1f,
                                        if (nextMute) 0f else 1f
                                    )
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("native_player_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) browserColors.amber else Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val selected = playbackSpeed == speed
                            Surface(
                                onClick = {
                                    playbackSpeed = speed
                                    runCatching {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isPrepared) {
                                            mediaPlayerRef?.let { mp ->
                                                mp.playbackParams = mp.playbackParams.setSpeed(speed)
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) browserColors.cyan.copy(alpha = 0.24f) else Color(0xFF1E293B),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) browserColors.cyan else Color(0xFF334155)
                                ),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    text = "${speed}x",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) browserColors.cyan else Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (state.streams.size > 1) {
                            FilledTonalButton(
                                onClick = {
                                    playbackError = null
                                    currentBoundUrl = null
                                    onSwitchSource((state.activeStreamIndex + 1) % state.streams.size)
                                },
                                modifier = Modifier.testTag("native_player_switch_source_button")
                            ) {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Source ${state.activeStreamIndex + 1}/${state.streams.size}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatVideoMillis(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%02d:%02d".format(mins, secs)
}
