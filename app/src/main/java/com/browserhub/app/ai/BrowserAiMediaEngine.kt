package com.browserhub.app.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class AiStudioMode(val label: String) {
    CHAT("Chat & Help"),
    IMAGE("Image Studio"),
    AUDIO("Audio & Voice"),
    VIDEO("Video Reel")
}

enum class AiImageModelOption(
    val displayName: String,
    val badge: String,
    val modelId: String,
    val isFirebase: Boolean
) {
    FLUX_ULTRA_FREE("Flux Ultra", "Free • No Key", "flux", false),
    NANO_BANANA_2_1("Nano Banana 2.1", "Gemini 4K", "gemini-nano-banana-2.1", true),
    GEMINI_3_PRO_IMAGE("Gemini 3 Pro Image", "Studio 4K", "gemini-3-pro-image", true)
}

enum class AiImageQualityOption(
    val label: String,
    val firebaseSize: ImageSize,
    val maxPixels: Int
) {
    ULTRA_4K("4K Ultra Max", ImageSize.SIZE_4K, 2048),
    HIGH_2K("2K Ultra HD", ImageSize.SIZE_2K, 1536),
    STANDARD_1K("1K Fast", ImageSize.SIZE_1K, 1024)
}

enum class AiImageAspectOption(
    val label: String,
    val firebaseAspect: AspectRatio,
    val widthUnits: Int,
    val heightUnits: Int,
    val videoWidth: Int,
    val videoHeight: Int
) {
    SQUARE_1_1("1:1", AspectRatio.SQUARE_1x1, 1, 1, 1024, 1024),
    LANDSCAPE_16_9("16:9", AspectRatio.LANDSCAPE_16x9, 16, 9, 1280, 720),
    PORTRAIT_9_16("9:16", AspectRatio.PORTRAIT_9x16, 9, 16, 720, 1280),
    CLASSIC_4_3("4:3", AspectRatio.LANDSCAPE_4x3, 4, 3, 1280, 960),
    PORTRAIT_3_4("3:4", AspectRatio.PORTRAIT_3x4, 3, 4, 960, 1280)
}

enum class AiVoiceOption(
    val voiceName: String,
    val styleLabel: String
) {
    KORE("Kore", "Firm Studio"),
    PUCK("Puck", "Upbeat Male"),
    AOEDE("Aoede", "Expressive"),
    CHARON("Charon", "Deep Narrator"),
    ZEPHYR("Zephyr", "Bright Female"),
    FENRIR("Fenrir", "Energetic"),
    SULAFAT("Sulafat", "Warm Story"),
    ORUS("Orus", "Confident")
}

data class VideoScenePlan(
    val caption: String,
    val visualPrompt: String,
    val narration: String
)

data class GeneratedImageResult(
    val prompt: String,
    val bitmap: Bitmap,
    val providerLabel: String,
    val resolutionLabel: String,
    val savedLocation: String? = null
)

data class GeneratedAudioResult(
    val prompt: String,
    val spokenScript: String,
    val wavFilePath: String,
    val durationSeconds: Int,
    val voiceLabel: String,
    val providerLabel: String,
    val savedLocation: String? = null
)

data class GeneratedVideoResult(
    val prompt: String,
    val title: String,
    val mp4FilePath: String,
    val companionAudioPath: String?,
    val thumbnail: Bitmap,
    val scenes: List<VideoScenePlan>,
    val durationSeconds: Int,
    val resolutionLabel: String,
    val providerLabel: String,
    val savedLocation: String? = null
)

object AiWavEncoder {
    private const val SAMPLE_RATE_24K = 24_000
    private const val CHANNELS_MONO = 1
    private const val BITS_16 = 16

    fun pcm16MonoToWavBytes(
        pcmData: ByteArray,
        sampleRate: Int = SAMPLE_RATE_24K
    ): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * CHANNELS_MONO * (BITS_16 / 8)
        val out = ByteArrayOutputStream(44 + pcmData.size)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM chunk size
        header.putShort(1.toShort()) // AudioFormat = 1 (PCM)
        header.putShort(CHANNELS_MONO.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((CHANNELS_MONO * (BITS_16 / 8)).toShort())
        header.putShort(BITS_16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)
        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }

    fun estimateDurationSeconds(pcmByteCount: Int, sampleRate: Int = SAMPLE_RATE_24K): Int {
        val bytesPerSecond = sampleRate * CHANNELS_MONO * (BITS_16 / 8)
        if (bytesPerSecond <= 0) return 1
        return max(1, (pcmByteCount + bytesPerSecond - 1) / bytesPerSecond)
    }
}

@OptIn(PublicPreviewAPI::class)
class BrowserAiMediaEngine(private val context: Context) {
    companion object {
        private const val TAG = "BrowserAiMediaEngine"
        private const val IMAGE_TIMEOUT_MS = 60_000L
        private const val AUDIO_TIMEOUT_MS = 60_000L
        private const val STORYBOARD_TIMEOUT_MS = 35_000L
    }

    suspend fun generateImage(
        prompt: String,
        modelOption: AiImageModelOption,
        qualityOption: AiImageQualityOption,
        aspectOption: AiImageAspectOption
    ): Result<GeneratedImageResult> = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter an image prompt first."))
        }

        if (modelOption.isFirebase) {
            try {
                val config = generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE)
                    imageConfig = ImageConfig.builder()
                        .setAspectRatio(aspectOption.firebaseAspect)
                        .setImageSize(qualityOption.firebaseSize)
                        .build()
                }
                val model = Firebase.ai.generativeModel(
                    modelName = modelOption.modelId,
                    generationConfig = config
                )
                val response = withTimeout(IMAGE_TIMEOUT_MS) {
                    model.generateContent(cleanPrompt)
                }
                val bitmap = response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()?.image
                if (bitmap != null) {
                    return@withContext Result.success(
                        GeneratedImageResult(
                            prompt = cleanPrompt,
                            bitmap = bitmap,
                            providerLabel = modelOption.displayName,
                            resolutionLabel = "${bitmap.width}×${bitmap.height} (${qualityOption.label})"
                        )
                    )
                }
                Result.failure(IllegalStateException("Gemini returned no image for that prompt. Try being more descriptive."))
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini image generation timed out", e)
                Result.failure(IllegalStateException("Image generation timed out after 60s. Please retry or try Flux Ultra."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini image generation failed", e)
                Result.failure(IllegalStateException(e.localizedMessage ?: "Gemini image generation failed."))
            }
        } else {
            generatePollinationsFluxImage(cleanPrompt, qualityOption, aspectOption)
        }
    }

    private suspend fun generatePollinationsFluxImage(
        prompt: String,
        qualityOption: AiImageQualityOption,
        aspectOption: AiImageAspectOption
    ): Result<GeneratedImageResult> = withContext(Dispatchers.IO) {
        try {
            val (width, height) = computeDimensions(qualityOption.maxPixels, aspectOption.widthUnits, aspectOption.heightUnits)
            val encoded = URLEncoder.encode(
                "$prompt, ultra detailed, sharp focus, studio lighting, 8k uhd",
                Charsets.UTF_8.name()
            )
            val seed = Random.nextInt(10_000, 999_999)
            val endpoint = "https://image.pollinations.ai/prompt/$encoded?width=$width&height=$height&model=flux&enhance=true&nologo=true&seed=$seed"

            val bytes = withTimeout(IMAGE_TIMEOUT_MS) {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 55_000
                    setRequestProperty("Accept", "image/*")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("Image server returned HTTP $code")
                    }
                    conn.inputStream.use { it.readBytes() }
                } finally {
                    conn.disconnect()
                }
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Could not decode returned image data.")
            Result.success(
                GeneratedImageResult(
                    prompt = prompt,
                    bitmap = bitmap,
                    providerLabel = "Flux Ultra (Free • No Key)",
                    resolutionLabel = "${bitmap.width}×${bitmap.height} (${qualityOption.label})"
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Flux image generation timed out", e)
            Result.failure(IllegalStateException("Image generation timed out after 60s. Check network and retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Flux image generation failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to generate image."))
        }
    }

    suspend fun generateAudio(
        prompt: String,
        voiceOption: AiVoiceOption,
        languageCode: String = "en-US",
        expandTopicToScript: Boolean = false
    ): Result<GeneratedAudioResult> = withContext(Dispatchers.IO) {
        val cleanInput = prompt.trim()
        if (cleanInput.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter text or a voice script topic first."))
        }
        try {
            val spokenText = if (expandTopicToScript) {
                generateSpokenScriptForTopic(cleanInput)
            } else {
                cleanInput
            }

            val ttsConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(
                    voice = Voice(voiceOption.voiceName),
                    languageCode = languageCode
                )
            }
            val ttsModel = Firebase.ai.generativeModel(
                modelName = "gemini-3.1-flash-tts-preview",
                generationConfig = ttsConfig
            )
            val response = withTimeout(AUDIO_TIMEOUT_MS) {
                ttsModel.generateContent(spokenText)
            }
            val audioBytes = response.candidates.firstOrNull()
                ?.content?.parts
                ?.filterIsInstance<InlineDataPart>()
                ?.firstOrNull { it.mimeType.startsWith("audio/", ignoreCase = true) }
                ?.inlineData

            if (audioBytes == null || audioBytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Gemini TTS did not return audio data."))
            }

            val wavBytes = AiWavEncoder.pcm16MonoToWavBytes(audioBytes, sampleRate = 24_000)
            val outFile = File(context.cacheDir, "browserhub_ai_audio_${System.currentTimeMillis()}.wav")
            FileOutputStream(outFile).use { it.write(wavBytes) }

            val durationSec = AiWavEncoder.estimateDurationSeconds(audioBytes.size, sampleRate = 24_000)
            Result.success(
                GeneratedAudioResult(
                    prompt = cleanInput,
                    spokenScript = spokenText,
                    wavFilePath = outFile.absolutePath,
                    durationSeconds = durationSec,
                    voiceLabel = "${voiceOption.voiceName} (${voiceOption.styleLabel})",
                    providerLabel = "Gemini 3.1 Flash TTS • 24kHz HD WAV"
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini TTS timed out", e)
            Result.failure(IllegalStateException("Audio synthesis timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini TTS failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Audio generation failed."))
        }
    }

    private suspend fun generateSpokenScriptForTopic(topic: String): String {
        return try {
            val scriptModel = Firebase.ai.generativeModel("gemini-3.8-flash")
            val prompt = """
                Write a vivid, concise 45-to-75-word spoken narration script about:
                "$topic"
                Return ONLY the plain spoken words ready for voice synthesis. No stage directions, no brackets, no markdown.
            """.trimIndent()
            val response = withTimeout(STORYBOARD_TIMEOUT_MS) {
                scriptModel.generateContent(prompt)
            }
            response.text?.trim()?.takeIf { it.isNotBlank() } ?: topic
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            topic
        }
    }

    suspend fun generateVideoReel(
        prompt: String,
        aspectOption: AiImageAspectOption,
        frameModel: AiImageModelOption,
        voiceOption: AiVoiceOption,
        onProgress: (String) -> Unit
    ): Result<GeneratedVideoResult> = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a video concept or prompt first."))
        }
        try {
            onProgress("1/4 Planning 3-scene storyboard with Gemini 3.8 Flash…")
            val (title, scenes) = buildVideoStoryboard(cleanPrompt)

            onProgress("2/4 Generating Ultra-HD keyframes (${frameModel.displayName})…")
            val frameQuality = AiImageQualityOption.HIGH_2K
            val bitmaps = mutableListOf<Bitmap>()
            for ((index, scene) in scenes.withIndex()) {
                onProgress("2/4 Rendering scene ${index + 1}/${scenes.size} visual…")
                val imgRes = generateImage(
                    prompt = scene.visualPrompt,
                    modelOption = frameModel,
                    qualityOption = frameQuality,
                    aspectOption = aspectOption
                )
                val genResult = imgRes.getOrElse { err ->
                    return@withContext Result.failure(err)
                }
                bitmaps.add(genResult.bitmap)
            }

            onProgress("3/4 Synthesizing studio narration with Gemini 3.1 Flash TTS…")
            val combinedNarration = scenes.joinToString(" ") { it.narration.trim() }
            val audioResult = generateAudio(
                prompt = combinedNarration,
                voiceOption = voiceOption,
                expandTopicToScript = false
            ).getOrNull()

            val totalDurationSec = (audioResult?.durationSeconds ?: 9).coerceIn(6, 24)
            onProgress("4/4 Encoding ${aspectOption.videoWidth}×${aspectOption.videoHeight} HD MP4 reel…")

            val mp4File = File(context.cacheDir, "browserhub_ai_reel_${System.currentTimeMillis()}.mp4")
            encodeScenesToMp4(
                scenes = scenes,
                bitmaps = bitmaps,
                outputMp4 = mp4File,
                width = aspectOption.videoWidth,
                height = aspectOption.videoHeight,
                totalDurationSec = totalDurationSec
            )

            Result.success(
                GeneratedVideoResult(
                    prompt = cleanPrompt,
                    title = title,
                    mp4FilePath = mp4File.absolutePath,
                    companionAudioPath = audioResult?.wavFilePath,
                    thumbnail = bitmaps.first(),
                    scenes = scenes,
                    durationSeconds = totalDurationSec,
                    resolutionLabel = "${aspectOption.videoWidth}×${aspectOption.videoHeight} H.264 MP4",
                    providerLabel = "Gemini Storyboard + ${frameModel.displayName}${if (audioResult != null) " + Gemini TTS" else ""}"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video reel generation failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Video generation failed."))
        }
    }

    private suspend fun buildVideoStoryboard(topic: String): Pair<String, List<VideoScenePlan>> {
        val sceneSchema = Schema.obj(
            properties = mapOf(
                "caption" to Schema.string("Short 4-to-7 word on-screen subtitle"),
                "visualPrompt" to Schema.string("Rich cinematic visual scene prompt for image synthesis"),
                "narration" to Schema.string("1 concise spoken sentence for voiceover")
            )
        )
        val storyboardSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string("Short title for the video reel"),
                "scenes" to Schema.array(
                    items = sceneSchema,
                    description = "Exactly 3 sequential visual scenes"
                )
            )
        )
        return try {
            val config = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = config
            )
            val response = withTimeout(STORYBOARD_TIMEOUT_MS) {
                model.generateContent(
                    "Create a cinematic 3-scene video reel storyboard for: \"$topic\". Return 3 distinct visual scenes in logical sequence."
                )
            }
            val raw = response.text ?: throw IllegalStateException("Empty storyboard")
            val json = JSONObject(raw)
            val title = json.optString("title").ifBlank { topic.take(40) }
            val arr = json.optJSONArray("scenes")
            val parsed = mutableListOf<VideoScenePlan>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val cap = item.optString("caption").ifBlank { "Scene ${i + 1}" }
                    val vis = item.optString("visualPrompt").ifBlank { "$topic cinematic scene ${i + 1}" }
                    val nar = item.optString("narration").ifBlank { cap }
                    parsed.add(VideoScenePlan(cap, vis, nar))
                }
            }
            if (parsed.isEmpty()) throw IllegalStateException("No scenes returned")
            title to parsed.take(3)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            val clean = topic.take(48)
            clean to listOf(
                VideoScenePlan("Opening: $clean", "$topic wide establishing cinematic shot, ultra detailed", "Welcome to $clean."),
                VideoScenePlan("Closer Look", "$topic dramatic close-up lighting, ultra sharp", "Discover every detail up close."),
                VideoScenePlan("Finale", "$topic breathtaking golden hour wide finale, 8k", "A striking view of $clean.")
            )
        }
    }

    private fun encodeScenesToMp4(
        scenes: List<VideoScenePlan>,
        bitmaps: List<Bitmap>,
        outputMp4: File,
        width: Int,
        height: Int,
        totalDurationSec: Int
    ) {
        val fps = 24
        val totalFrames = max(fps * 3, totalDurationSec * fps)
        val bitrate = if (width * height >= 1280 * 720) 6_000_000 else 4_000_000

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        var inputSurface: android.view.Surface? = null

        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = max(26f, height * 0.042f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(6f, 0f, 3f, Color.BLACK)
        }
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(165, 10, 14, 22)
        }
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix()
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var emittedPtsFrames = 0L
            val framesPerScene = max(1, totalFrames / bitmaps.size)
            for (frameIdx in 0 until totalFrames) {
                val sceneIdx = min(bitmaps.size - 1, frameIdx / framesPerScene)
                val nextSceneIdx = min(bitmaps.size - 1, sceneIdx + 1)
                val localFrame = frameIdx - sceneIdx * framesPerScene
                val sceneProgress = (localFrame.toFloat() / framesPerScene.toFloat()).coerceIn(0f, 1f)

                val canvas: Canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    inputSurface.lockHardwareCanvas()
                } else {
                    inputSurface.lockCanvas(null)
                }

                try {
                    canvas.drawColor(Color.BLACK)
                    val curBmp = bitmaps[sceneIdx]
                    drawKenBurnsFrame(
                        canvas = canvas,
                        bitmap = curBmp,
                        width = width,
                        height = height,
                        progress = sceneProgress,
                        alpha = 255,
                        zoomDirection = sceneIdx % 2 == 0,
                        matrix = matrix,
                        paint = fadePaint
                    )

                    // Smooth crossfade near end of scene
                    if (nextSceneIdx != sceneIdx && sceneProgress > 0.85f) {
                        val blend = ((sceneProgress - 0.85f) / 0.15f).coerceIn(0f, 1f)
                        drawKenBurnsFrame(
                            canvas = canvas,
                            bitmap = bitmaps[nextSceneIdx],
                            width = width,
                            height = height,
                            progress = 0f,
                            alpha = (blend * 255).toInt(),
                            zoomDirection = nextSceneIdx % 2 == 0,
                            matrix = matrix,
                            paint = fadePaint
                        )
                    }

                    // Bottom gradient + caption bar
                    val gradTop = height * 0.68f
                    fadePaint.shader = LinearGradient(
                        0f, gradTop, 0f, height.toFloat(),
                        Color.TRANSPARENT, Color.argb(200, 0, 0, 0),
                        Shader.TileMode.CLAMP
                    )
                    fadePaint.alpha = 255
                    canvas.drawRect(0f, gradTop, width.toFloat(), height.toFloat(), fadePaint)
                    fadePaint.shader = null

                    val caption = scenes.getOrNull(sceneIdx)?.caption.orEmpty()
                    if (caption.isNotBlank()) {
                        val maxTextW = (width * 0.84f).toInt().coerceAtLeast(100)
                        val staticLayout = StaticLayout.Builder
                            .obtain(caption, 0, caption.length, captionPaint, maxTextW)
                            .setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .setMaxLines(2)
                            .build()
                        val boxTop = height - staticLayout.height - height * 0.08f
                        val pad = 16f
                        canvas.drawRoundRect(
                            (width - maxTextW) / 2f - pad,
                            boxTop - pad * 0.6f,
                            (width + maxTextW) / 2f + pad,
                            boxTop + staticLayout.height + pad * 0.6f,
                            18f,
                            18f,
                            pillBgPaint
                        )
                        canvas.save()
                        canvas.translate((width - maxTextW) / 2f, boxTop)
                        staticLayout.draw(canvas)
                        canvas.restore()
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Drain available encoded frames cleanly
                val drained = drainEncoder(
                    encoder = encoder,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    trackIndex = trackIndex,
                    muxerStarted = muxerStarted,
                    fps = fps,
                    emittedCountStart = emittedPtsFrames
                )
                trackIndex = drained.trackIndex
                muxerStarted = drained.muxerStarted
                emittedPtsFrames = drained.emittedCount
            }

            encoder.signalEndOfInputStream()
            val finalDrained = drainEncoder(
                encoder = encoder,
                muxer = muxer,
                bufferInfo = bufferInfo,
                endOfStream = true,
                trackIndex = trackIndex,
                muxerStarted = muxerStarted,
                fps = fps,
                emittedCountStart = emittedPtsFrames
            )
            muxerStarted = finalDrained.muxerStarted
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { inputSurface?.release() }
            if (muxer != null) {
                if (muxerStarted) {
                    runCatching { muxer.stop() }
                }
                runCatching { muxer.release() }
            }
        }
    }

    private fun drawKenBurnsFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        progress: Float,
        alpha: Int,
        zoomDirection: Boolean,
        matrix: Matrix,
        paint: Paint
    ) {
        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)
        val baseScale = max(width / bw, height / bh)
        val zoom = if (zoomDirection) 1.0f + 0.12f * progress else 1.12f - 0.12f * progress
        val scale = baseScale * zoom
        val dx = (width - bw * scale) * 0.5f + ((if (zoomDirection) -1f else 1f) * width * 0.02f * progress)
        val dy = (height - bh * scale) * 0.5f
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        paint.alpha = alpha
        paint.isFilterBitmap = true
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private data class DrainResult(
        val trackIndex: Int,
        val muxerStarted: Boolean,
        val emittedCount: Long
    )

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        trackIndex: Int,
        muxerStarted: Boolean,
        fps: Int,
        emittedCountStart: Long
    ): DrainResult {
        var currentTrack = trackIndex
        var started = muxerStarted
        var emitted = emittedCountStart
        val frameDurationUs = 1_000_000L / max(1, fps)
        val timeoutUs = if (endOfStream) 10_000L else 0L
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!started) {
                    currentTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    started = true
                }
            } else if (outIdx >= 0) {
                val encodedData = encoder.getOutputBuffer(outIdx)
                if (encodedData != null && bufferInfo.size > 0 && started &&
                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                ) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    bufferInfo.presentationTimeUs = emitted * frameDurationUs
                    emitted++
                    muxer.writeSampleData(currentTrack, encodedData, bufferInfo)
                }
                encoder.releaseOutputBuffer(outIdx, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
        return DrainResult(currentTrack, started, emitted)
    }

    suspend fun saveBitmapToDevice(bitmap: Bitmap, titlePrefix: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "${titlePrefix}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BrowserHub AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Unable to create Pictures MediaStore entry.")
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: throw IllegalStateException("Unable to write PNG bytes.")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Pictures/BrowserHub AI/$fileName")
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "BrowserHub AI").apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                Result.success(file.absolutePath)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFileToDownloads(
        sourcePath: String,
        displayName: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val src = File(sourcePath)
            if (!src.exists()) throw IllegalStateException("Generated file no longer exists.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BrowserHub AI")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Unable to create Downloads MediaStore entry.")
                resolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Unable to write download stream.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Downloads/BrowserHub AI/$displayName")
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "BrowserHub AI").apply { mkdirs() }
                val dest = File(dir, displayName)
                src.copyTo(dest, overwrite = true)
                Result.success(dest.absolutePath)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun computeDimensions(maxPixels: Int, widthUnits: Int, heightUnits: Int): Pair<Int, Int> {
        val w: Int
        val h: Int
        if (widthUnits >= heightUnits) {
            w = maxPixels
            h = ((maxPixels * heightUnits.toFloat() / widthUnits.toFloat()).toInt() / 16) * 16
        } else {
            h = maxPixels
            w = ((maxPixels * widthUnits.toFloat() / heightUnits.toFloat()).toInt() / 16) * 16
        }
        return w.coerceAtLeast(256) to h.coerceAtLeast(256)
    }
}
