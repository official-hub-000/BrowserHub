package com.browserhub.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browserhub.app.ai.AiChatMessage
import com.browserhub.app.ai.AiEngineOption
import com.browserhub.app.browser.TranslateLanguage
import com.browserhub.app.data.model.BrowserTab
import com.browserhub.app.data.model.UrlHelper
import com.browserhub.app.ui.theme.FlagshipCrimsonDarkBg
import com.browserhub.app.ui.theme.FlagshipEmeraldDarkBg
import com.browserhub.app.ui.theme.LocalBrowserColors
import com.browserhub.app.ui.viewmodel.AiUiBundle

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.browserhub.app.ai.AiImageAspectOption
import com.browserhub.app.ai.AiImageModelOption
import com.browserhub.app.ai.AiImageQualityOption
import com.browserhub.app.ai.AiQuickNavigationAction
import com.browserhub.app.ai.AiStudioMode
import com.browserhub.app.ai.AiVoiceOption
import java.io.File

private val tryAskingPrompts = listOf(
    "Explain all BrowserHub features & shortcuts",
    "How do I use BrowserHub VPN & Location Shield?",
    "How do I install Chrome, Edge, Opera or Firefox extensions?",
    "How do I download YouTube or page media ad-free?",
    "Summarize this page"
)

private val sampleImagePrompts = listOf(
    "Cyberpunk Tokyo neon street in rain, 8k photorealistic architectural reflection",
    "Minimalist dark emerald & obsidian futuristic HUD compass icon, studio lighting",
    "Majestic alpine mountain lake at sunrise, golden mist, ultra detailed lens"
)

private val sampleAudioPrompts = listOf(
    "Welcome to BrowserHub. Your private browser with built-in extensions, shields, and AI studios.",
    "Explain why encrypted DNS and location masking protect browsing privacy."
)

private val sampleVideoPrompts = listOf(
    "A cinematic journey through deep space nebulae and glowing exoplanets",
    "Why online privacy matters in the modern web era"
)

@Composable
fun AskAiSheet(
    aiState: AiUiBundle,
    activeTab: BrowserTab,
    onSelectEngine: (AiEngineOption) -> Unit,
    onSelectStudioMode: (AiStudioMode) -> Unit,
    onSendPrompt: (String) -> Unit,
    onQuickAction: (AiQuickNavigationAction) -> Unit,
    onClearChat: () -> Unit,
    onSelectImageModel: (AiImageModelOption) -> Unit,
    onSelectImageQuality: (AiImageQualityOption) -> Unit,
    onSelectImageAspect: (AiImageAspectOption) -> Unit,
    onGenerateImage: (String, AiImageModelOption?) -> Unit,
    onSaveImage: () -> Unit,
    onSelectVoice: (AiVoiceOption) -> Unit,
    onToggleScriptAudio: (Boolean) -> Unit,
    onGenerateAudio: (String) -> Unit,
    onSaveAudio: () -> Unit,
    onGenerateVideo: (String) -> Unit,
    onSaveVideo: () -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    var chatInputText by remember { mutableStateOf("") }
    var imagePromptText by remember { mutableStateOf("") }
    var audioPromptText by remember { mutableStateOf("") }
    var videoPromptText by remember { mutableStateOf("") }

    ModalSheetScrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.imePadding()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, browserColors.emerald))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "BrowserHub AI",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = FlagshipEmeraldDarkBg,
                            border = BorderStroke(1.dp, browserColors.emerald.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Unlimited • No Key",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = browserColors.emerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (activeTab.isOnHub) {
                            "Full BrowserHub memory • Chat, 4K Image, Audio & MP4"
                        } else {
                            "Reading: ${UrlHelper.extractHost(activeTab.url)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (aiState.studioMode == AiStudioMode.CHAT && aiState.messages.isNotEmpty()) {
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear AI chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("ask_ai_close")
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Studio Mode Strip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AiStudioMode.entries, key = { it.name }) { mode ->
                    val selected = mode == aiState.studioMode
                    val modeIcon = when (mode) {
                        AiStudioMode.CHAT -> Icons.Outlined.AutoAwesome
                        AiStudioMode.IMAGE -> Icons.Outlined.Image
                        AiStudioMode.AUDIO -> Icons.Outlined.GraphicEq
                        AiStudioMode.VIDEO -> Icons.Outlined.MovieCreation
                    }
                    Surface(
                        onClick = { onSelectStudioMode(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ai_mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = browserColors.cardBorder.copy(alpha = 0.7f))

            when (aiState.studioMode) {
                AiStudioMode.CHAT -> {
                    AiChatStudioContent(
                        aiState = aiState,
                        inputText = chatInputText,
                        onInputChange = { chatInputText = it },
                        onSelectEngine = onSelectEngine,
                        onSendPrompt = { prompt ->
                            onSendPrompt(prompt)
                            chatInputText = ""
                        },
                        onQuickAction = onQuickAction
                    )
                }

                AiStudioMode.IMAGE -> {
                    AiImageStudioContent(
                        aiState = aiState,
                        promptText = imagePromptText,
                        onPromptChange = { imagePromptText = it },
                        onSelectModel = onSelectImageModel,
                        onSelectQuality = onSelectImageQuality,
                        onSelectAspect = onSelectImageAspect,
                        onGenerate = { prompt, overrideModel ->
                            onGenerateImage(prompt, overrideModel)
                        },
                        onSave = onSaveImage
                    )
                }

                AiStudioMode.AUDIO -> {
                    AiAudioStudioContent(
                        aiState = aiState,
                        promptText = audioPromptText,
                        onPromptChange = { audioPromptText = it },
                        onSelectVoice = onSelectVoice,
                        onToggleScriptAudio = onToggleScriptAudio,
                        onGenerate = { onGenerateAudio(it) },
                        onSave = onSaveAudio
                    )
                }

                AiStudioMode.VIDEO -> {
                    AiVideoStudioContent(
                        aiState = aiState,
                        promptText = videoPromptText,
                        onPromptChange = { videoPromptText = it },
                        onSelectAspect = onSelectImageAspect,
                        onSelectFrameModel = onSelectImageModel,
                        onSelectVoice = onSelectVoice,
                        onGenerate = { onGenerateVideo(it) },
                        onSave = onSaveVideo
                    )
                }
            }
        }
    }
}

@Composable
private fun AiChatStudioContent(
    aiState: AiUiBundle,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSelectEngine: (AiEngineOption) -> Unit,
    onSendPrompt: (String) -> Unit,
    onQuickAction: (AiQuickNavigationAction) -> Unit
) {
    val browserColors = LocalBrowserColors.current

    Column {
        // Engine Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ENGINE",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AiEngineOption.entries, key = { it.name }) { opt ->
                    val selected = opt == aiState.selectedEngine
                    Surface(
                        onClick = { onSelectEngine(opt) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                        ),
                        modifier = Modifier.testTag("ai_engine_${opt.name.lowercase()}")
                    ) {
                        Text(
                            text = opt.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 4 Quick Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiQuickTile("App Help", Icons.Outlined.HelpOutline, browserColors.cyan, Modifier.weight(1f)) {
                onSendPrompt("List all major BrowserHub features and tell me how to get started.")
            }
            AiQuickTile("Summarize", Icons.Outlined.Description, browserColors.emerald, Modifier.weight(1f)) {
                onSendPrompt("Summarize this page in concise bullet points.")
            }
            AiQuickTile("Explain", Icons.AutoMirrored.Outlined.MenuBook, browserColors.amber, Modifier.weight(1f)) {
                onSendPrompt("Explain the main concepts of this page simply.")
            }
            AiQuickTile("Privacy", Icons.Outlined.Shield, browserColors.violet, Modifier.weight(1f)) {
                onSendPrompt("How do BrowserHub Shields, VPN, and Location Mask protect my privacy?")
            }
        }

        Spacer(Modifier.height(10.dp))

        // Messages or Welcome Empty State
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (aiState.messages.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, browserColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "BrowserHub Expert + Unlimited Gemini",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Ask for help with any BrowserHub tool (VPN, Extensions, AdBlock, Shortlink Bypass, Media Grabber), live browser diagnostics, page summaries, or switch to Image, Audio & Video Studios above.",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "TRY ASKING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(tryAskingPrompts) { sample ->
                    Surface(
                        onClick = { onSendPrompt(sample) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, browserColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = sample,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                        )
                    }
                }
            } else {
                items(aiState.messages, key = { it.id }) { msg ->
                    AiChatBubble(msg = msg, onQuickAction = onQuickAction)
                }
            }
        }

        // Input Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !aiState.isGenerating) {
                                onSendPrompt(inputText)
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ask_ai_input"),
                    decorationBox = { inner ->
                        if (inputText.isEmpty()) {
                            Text("Ask BrowserHub AI anything...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                )
            }

            Surface(
                onClick = {
                    if (inputText.isNotBlank() && !aiState.isGenerating) {
                        onSendPrompt(inputText)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = if (inputText.isNotBlank() && !aiState.isGenerating) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("ask_ai_send_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (aiState.isGenerating) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Send prompt",
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiImageStudioContent(
    aiState: AiUiBundle,
    promptText: String,
    onPromptChange: (String) -> Unit,
    onSelectModel: (AiImageModelOption) -> Unit,
    onSelectQuality: (AiImageQualityOption) -> Unit,
    onSelectAspect: (AiImageAspectOption) -> Unit,
    onGenerate: (String, AiImageModelOption?) -> Unit,
    onSave: () -> Unit
) {
    val browserColors = LocalBrowserColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "ENGINE MODEL",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AiImageModelOption.entries, key = { it.name }) { model ->
                    val selected = model == aiState.selectedImageModel
                    Surface(
                        onClick = { onSelectModel(model) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder),
                        modifier = Modifier.testTag("ai_img_model_${model.name.lowercase()}")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = model.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiImageQualityOption.entries.forEach { q ->
                    val selected = q == aiState.selectedImageQuality
                    Surface(
                        onClick = { onSelectQuality(q) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("ai_img_quality_${q.name.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)) {
                            Text(
                                text = q.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiImageAspectOption.entries.forEach { aspect ->
                    val selected = aspect == aiState.selectedImageAspect
                    Surface(
                        onClick = { onSelectAspect(aspect) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder)
                    ) {
                        Text(
                            text = aspect.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = promptText,
                        onValueChange = onPromptChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("ai_image_prompt_input"),
                        decorationBox = { inner ->
                            if (promptText.isEmpty()) {
                                Text(
                                    text = "Describe the 4K image you want to create...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                }

                if (promptText.isBlank()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sampleImagePrompts) { sample ->
                            Surface(
                                onClick = { onPromptChange(sample) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, browserColors.cardBorder)
                            ) {
                                Text(
                                    text = sample.take(36) + "…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = {
                        if (promptText.isNotBlank() && !aiState.isGeneratingImage) {
                            onGenerate(promptText, null)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (promptText.isNotBlank() && !aiState.isGeneratingImage) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_generate_image_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (aiState.isGeneratingImage) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Generating ${aiState.selectedImageQuality.label} (up to 60s)…",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Generate ${aiState.selectedImageQuality.label} Image",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (promptText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        aiState.imageError?.let { errorMsg ->
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FlagshipCrimsonDarkBg,
                    border = BorderStroke(1.dp, browserColors.crimson.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = browserColors.crimson, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Image Generation Notice",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = browserColors.crimson
                            )
                        }
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (aiState.selectedImageModel.isFirebase && promptText.isNotBlank()) {
                            Surface(
                                onClick = { onGenerate(promptText, AiImageModelOption.FLUX_ULTRA_FREE) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("ai_fallback_flux_button")
                            ) {
                                Text(
                                    text = "Try Free Flux Ultra Instead",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        aiState.generatedImage?.let { img ->
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, browserColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = img.providerLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = img.resolutionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = browserColors.emerald
                            )
                        }
                        val bmpRatio = (img.bitmap.width.toFloat() / img.bitmap.height.coerceAtLeast(1).toFloat())
                            .coerceIn(0.55f, 1.85f)
                        Image(
                            bitmap = img.bitmap.asImageBitmap(),
                            contentDescription = "AI generated image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(bmpRatio)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = img.savedLocation?.let { "Saved: $it" } ?: "Tap Save to export PNG to Gallery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                onClick = onSave,
                                shape = RoundedCornerShape(10.dp),
                                color = FlagshipEmeraldDarkBg,
                                border = BorderStroke(1.dp, browserColors.emerald.copy(alpha = 0.6f)),
                                modifier = Modifier.testTag("ai_save_image_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, tint = browserColors.emerald, modifier = Modifier.size(16.dp))
                                    Text("Save PNG", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = browserColors.emerald)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAudioStudioContent(
    aiState: AiUiBundle,
    promptText: String,
    onPromptChange: (String) -> Unit,
    onSelectVoice: (AiVoiceOption) -> Unit,
    onToggleScriptAudio: (Boolean) -> Unit,
    onGenerate: (String) -> Unit,
    onSave: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(aiState.generatedAudio?.wavFilePath) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
            isPlaying = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, browserColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Write Script First (Gemini 3.8)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Turn a topic into natural narration before 24kHz Gemini TTS synthesis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = aiState.aiScriptAudioMode,
                        onCheckedChange = onToggleScriptAudio,
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        item {
            Text(
                text = "STUDIO VOICE (24kHz HI-FI WAV)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AiVoiceOption.entries, key = { it.name }) { voice ->
                    val selected = voice == aiState.selectedVoice
                    Surface(
                        onClick = { onSelectVoice(voice) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder),
                        modifier = Modifier.testTag("ai_voice_${voice.name.lowercase()}")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = voice.voiceName,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = voice.styleLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = promptText,
                        onValueChange = onPromptChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("ai_audio_prompt_input"),
                        decorationBox = { inner ->
                            if (promptText.isEmpty()) {
                                Text(
                                    text = if (aiState.aiScriptAudioMode) "Enter a topic for Gemini to narrate..." else "Enter exact text to speak in studio quality...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                }

                if (promptText.isBlank()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sampleAudioPrompts) { sample ->
                            Surface(
                                onClick = { onPromptChange(sample) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, browserColors.cardBorder)
                            ) {
                                Text(
                                    text = sample.take(38) + "…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = {
                        if (promptText.isNotBlank() && !aiState.isGeneratingAudio) {
                            onGenerate(promptText)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (promptText.isNotBlank() && !aiState.isGeneratingAudio) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_generate_audio_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (aiState.isGeneratingAudio) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Synthesizing 24kHz Studio Audio…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        } else {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Generate Studio Audio (${aiState.selectedVoice.voiceName})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (promptText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        aiState.audioError?.let { errorMsg ->
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FlagshipCrimsonDarkBg,
                    border = BorderStroke(1.dp, browserColors.crimson.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        aiState.generatedAudio?.let { audio ->
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, browserColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${audio.voiceLabel} • 24kHz PCM WAV",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${audio.durationSeconds}s",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = browserColors.emerald
                            )
                        }
                        Text(
                            text = audio.spokenScript,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = {
                                    if (isPlaying) {
                                        runCatching {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                        }
                                        mediaPlayer = null
                                        isPlaying = false
                                    } else {
                                        runCatching {
                                            mediaPlayer?.release()
                                            mediaPlayer = MediaPlayer().apply {
                                                setDataSource(audio.wavFilePath)
                                                setOnCompletionListener {
                                                    isPlaying = false
                                                }
                                                prepare()
                                                start()
                                            }
                                            isPlaying = true
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("ai_play_audio_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isPlaying) "Stop Audio" else "Play Audio",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Surface(
                                onClick = onSave,
                                shape = RoundedCornerShape(12.dp),
                                color = FlagshipEmeraldDarkBg,
                                border = BorderStroke(1.dp, browserColors.emerald.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("ai_save_audio_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, tint = browserColors.emerald, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (audio.savedLocation != null) "Saved WAV" else "Save WAV",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = browserColors.emerald
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiVideoStudioContent(
    aiState: AiUiBundle,
    promptText: String,
    onPromptChange: (String) -> Unit,
    onSelectAspect: (AiImageAspectOption) -> Unit,
    onSelectFrameModel: (AiImageModelOption) -> Unit,
    onSelectVoice: (AiVoiceOption) -> Unit,
    onGenerate: (String) -> Unit,
    onSave: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var audioPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingReel by remember { mutableStateOf(false) }

    DisposableEffect(aiState.generatedVideo?.mp4FilePath) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
            runCatching {
                audioPlayerRef?.stop()
                audioPlayerRef?.release()
            }
            audioPlayerRef = null
            isPlayingReel = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, browserColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "AI Motion Video Reel (HD H.264 MP4)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Gemini 3.8 directs a 3-scene storyboard, renders HD AI keyframes with Ken Burns motion, synthesizes voiceover, and exports a real MP4.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    AiImageAspectOption.LANDSCAPE_16_9,
                    AiImageAspectOption.PORTRAIT_9_16,
                    AiImageAspectOption.SQUARE_1_1
                ).forEach { aspect ->
                    val selected = aspect == aiState.selectedImageAspect
                    Surface(
                        onClick = { onSelectAspect(aspect) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "${aspect.label} (${aspect.videoWidth}x${aspect.videoHeight})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AiImageModelOption.entries, key = { it.name }) { model ->
                    val selected = model == aiState.selectedImageModel
                    Surface(
                        onClick = { onSelectFrameModel(model) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder)
                    ) {
                        Text(
                            text = "Frames: ${model.displayName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                items(AiVoiceOption.entries.take(4), key = { it.name }) { voice ->
                    val selected = voice == aiState.selectedVoice
                    Surface(
                        onClick = { onSelectVoice(voice) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) FlagshipEmeraldDarkBg else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) browserColors.emerald else browserColors.cardBorder)
                    ) {
                        Text(
                            text = "Voice: ${voice.voiceName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) browserColors.emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = promptText,
                        onValueChange = onPromptChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("ai_video_prompt_input"),
                        decorationBox = { inner ->
                            if (promptText.isEmpty()) {
                                Text(
                                    text = "Describe your video story or topic...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                }

                if (promptText.isBlank()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sampleVideoPrompts) { sample ->
                            Surface(
                                onClick = { onPromptChange(sample) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, browserColors.cardBorder)
                            ) {
                                Text(
                                    text = sample.take(38) + "…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = {
                        if (promptText.isNotBlank() && !aiState.isGeneratingVideo) {
                            onGenerate(promptText)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (promptText.isNotBlank() && !aiState.isGeneratingVideo) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_generate_video_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (aiState.isGeneratingVideo) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = aiState.videoProgressMessage.ifBlank { "Rendering HD MP4 Reel…" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(Icons.Outlined.MovieCreation, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Create HD Video Reel",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (promptText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        aiState.videoError?.let { errorMsg ->
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FlagshipCrimsonDarkBg,
                    border = BorderStroke(1.dp, browserColors.crimson.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = errorMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        if (aiState.selectedImageModel.isFirebase && promptText.isNotBlank()) {
                            Surface(
                                onClick = {
                                    onSelectFrameModel(AiImageModelOption.FLUX_ULTRA_FREE)
                                    onGenerate(promptText)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = "Use Free Flux Ultra Keyframes & Retry",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        aiState.generatedVideo?.let { video ->
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, browserColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${video.durationSeconds}s • ${video.resolutionLabel}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = browserColors.emerald
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        videoViewRef = this
                                        setVideoURI(Uri.fromFile(File(video.mp4FilePath)))
                                        setOnCompletionListener {
                                            isPlayingReel = false
                                        }
                                    }
                                },
                                update = { vv ->
                                    videoViewRef = vv
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (!isPlayingReel) {
                                Image(
                                    bitmap = video.thumbnail.asImageBitmap(),
                                    contentDescription = "Video reel preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        video.scenes.forEachIndexed { idx, scene ->
                            Text(
                                text = "Scene ${idx + 1}: ${scene.caption}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = {
                                    if (isPlayingReel) {
                                        runCatching { videoViewRef?.pause() }
                                        runCatching {
                                            audioPlayerRef?.stop()
                                            audioPlayerRef?.release()
                                        }
                                        audioPlayerRef = null
                                        isPlayingReel = false
                                    } else {
                                        runCatching {
                                            videoViewRef?.apply {
                                                setVideoURI(Uri.fromFile(File(video.mp4FilePath)))
                                                seekTo(0)
                                                start()
                                            }
                                            audioPlayerRef?.release()
                                            audioPlayerRef = video.companionAudioPath?.let { path ->
                                                MediaPlayer().apply {
                                                    setDataSource(path)
                                                    prepare()
                                                    start()
                                                }
                                            }
                                            isPlayingReel = true
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("ai_play_video_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Icon(
                                        imageVector = if (isPlayingReel) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isPlayingReel) "Stop Reel" else "Play Reel",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Surface(
                                onClick = onSave,
                                shape = RoundedCornerShape(12.dp),
                                color = FlagshipEmeraldDarkBg,
                                border = BorderStroke(1.dp, browserColors.emerald.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("ai_save_video_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, tint = browserColors.emerald, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (video.savedLocation != null) "Saved MP4" else "Save MP4",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = browserColors.emerald
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiQuickTile(
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, browserColors.cardBorder),
        modifier = modifier.height(60.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiChatBubble(
    msg: AiChatMessage,
    onQuickAction: (AiQuickNavigationAction) -> Unit
) {
    val browserColors = LocalBrowserColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                msg.isError -> FlagshipCrimsonDarkBg
                msg.isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                1.dp,
                when {
                    msg.isError -> browserColors.crimson.copy(alpha = 0.5f)
                    msg.isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else -> browserColors.cardBorder
                }
            ),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!msg.isUser && msg.engineUsed.isNotBlank()) {
                    Text(
                        text = msg.engineUsed,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (msg.isError) browserColors.crimson else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!msg.isUser && msg.suggestedActions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        msg.suggestedActions.forEach { action ->
                            Surface(
                                onClick = { onQuickAction(action) },
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                                modifier = Modifier.testTag("ai_action_${action.tagCode.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = action.buttonLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PageTranslatorSheet(
    bilingualMode: Boolean,
    isTranslating: Boolean,
    activeLanguage: TranslateLanguage?,
    onToggleBilingual: (Boolean) -> Unit,
    onSelectLanguage: (TranslateLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    ModalSheetScrim(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = null,
                tint = browserColors.cyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Page Translator",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Free instant translation — no API key needed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isTranslating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("translator_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bilingual Side-by-Side Mode checkbox card
            Surface(
                onClick = { onToggleBilingual(!bilingualMode) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, browserColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = bilingualMode,
                        onCheckedChange = onToggleBilingual,
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Bilingual Side-by-Side Mode",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Keep English & add translated text below each block",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.height(360.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TranslateLanguage.entries.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { lang ->
                            val selected = lang == activeLanguage
                            val badgeColor = Color(lang.badgeColorHex)
                            Surface(
                                onClick = { if (!isTranslating) onSelectLanguage(lang) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("translate_lang_${lang.code}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(badgeColor.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lang.badgeCode,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = badgeColor
                                        )
                                    }
                                    Text(
                                        text = lang.nativeName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
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
    }
}
