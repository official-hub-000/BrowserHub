package com.browserhub.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.browserhub.app.R
import com.browserhub.app.data.local.ExtensionEntity
import com.browserhub.app.extensions.ExtensionSourceBrowser
import com.browserhub.app.extensions.ExtensionStoreDirectory
import com.browserhub.app.extensions.ExtensionStoreLink
import com.browserhub.app.extensions.WebExtensionEngine
import com.browserhub.app.extensions.WebExtensionJsBridge
import com.browserhub.app.ui.theme.FlagshipCrimsonDarkBg
import com.browserhub.app.ui.theme.FlagshipEmeraldDarkBg
import com.browserhub.app.ui.theme.FlagshipVioletDarkBg
import com.browserhub.app.ui.theme.HubAccentList
import com.browserhub.app.ui.theme.LocalBrowserColors
import java.io.File

private enum class ExtensionHubTab(val label: String) {
    INSTALLED("Installed"),
    WORKING("Working"),
    STORES("Stores"),
    IMPORT("Import"),
    STUDIO("Studio")
}

@Composable
fun ExtensionsOverlayScreen(
    extensions: List<ExtensionEntity>,
    isInstalling: Boolean,
    onImportLocalPackage: (android.net.Uri) -> Unit,
    onInstallFromUrl: (String) -> Unit,
    onInstallStarterTemplate: (String) -> Unit,
    onInstallStudioScript: (
        name: String,
        version: String,
        matchPattern: String,
        runAt: String,
        jsCode: String,
        cssCode: String
    ) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onToggleIncognito: (String, Boolean) -> Unit,
    onOpenPopup: (String) -> Unit,
    onOpenOptions: (String) -> Unit,
    onUninstall: (ExtensionEntity) -> Unit,
    onOpenStoreUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    var selectedTab by remember { mutableStateOf(ExtensionHubTab.INSTALLED) }
    var urlInput by remember { mutableStateOf("") }

    // Studio editor states
    var studioName by remember { mutableStateOf("Custom Page Tweaker") }
    var studioVersion by remember { mutableStateOf("1.0.0") }
    var studioMatch by remember { mutableStateOf("<all_urls>") }
    var studioRunAtIndex by remember { mutableIntStateOf(2) }
    val runAtOptions = listOf("document_start", "document_end", "document_idle")
    var studioJs by remember {
        mutableStateOf("console.log('BrowserHub Studio script running on ' + location.hostname);")
    }
    var studioCss by remember { mutableStateOf("/* Optional CSS rules */\n") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLocalPackage(uri)
        }
    }

    val workingExtensions = remember(extensions) { extensions.filter { it.enabled } }

    ModalSheetScrim(onDismiss = onDismiss) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Universal World Extension Engine",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Chrome · Firefox · Edge · Opera · UserScript",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isInstalling) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("extensions_sheet_close")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 5 Pill Navigation Tabs
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ExtensionHubTab.entries, key = { it.name }) { tab ->
                val selected = tab == selectedTab
                val label = when (tab) {
                    ExtensionHubTab.INSTALLED -> "Installed (${extensions.size})"
                    ExtensionHubTab.WORKING -> "Working (${workingExtensions.size})"
                    else -> tab.label
                }
                Surface(
                    onClick = { selectedTab = tab },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("ext_tab_${tab.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            ExtensionHubTab.INSTALLED, ExtensionHubTab.WORKING -> {
                val list = if (selectedTab == ExtensionHubTab.INSTALLED) extensions else workingExtensions
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (list.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, browserColors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = if (selectedTab == ExtensionHubTab.WORKING) {
                                            "No extensions currently active"
                                        } else {
                                            stringResource(R.string.label_extensions_empty)
                                        },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Browse Stores or Import .crx / .xpi / .nex / .user.js / .zip files.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(list, key = { it.id }) { ext ->
                            UniversalExtensionCard(
                                extension = ext,
                                onToggleEnabled = { onToggleEnabled(ext.id, !ext.enabled) },
                                onToggleIncognito = { onToggleIncognito(ext.id, !ext.allowInIncognito) },
                                onOpenPopup = { onOpenPopup(ext.id) },
                                onOpenOptions = { onOpenOptions(ext.id) },
                                onUninstall = { onUninstall(ext) }
                            )
                        }
                    }

                    // Built-in Starter Templates section inside Installed/Working
                    val installedIds = extensions.map { it.id }.toSet()
                    val availableTemplates = WebExtensionEngine.builtInTemplates.filter { it.id !in installedIds }
                    if (availableTemplates.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "ONE-TAP BUNDLED EXTENSIONS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(availableTemplates, key = { it.id }) { tpl ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, browserColors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("starter_ext_${tpl.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SourceBadge(source = tpl.sourceBrowser)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tpl.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = tpl.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = { onInstallStarterTemplate(tpl.id) },
                                        enabled = !isInstalling,
                                        modifier = Modifier.testTag("install_starter_${tpl.id}")
                                    ) {
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ExtensionHubTab.STORES -> {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = FlagshipEmeraldDarkBg.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, browserColors.emerald.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Direct Desktop Store Installation",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = browserColors.emerald
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Stores open in Desktop Mode with Add-on shims enabled. Tap \"Add to Chrome / Firefox / Edge / Opera\" on any store page and BrowserHub downloads, unpacks, and activates it automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(".CRX3", ".XPI", ".NEX", ".USER.JS", ".ZIP").forEach { fmt ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = fmt,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(ExtensionStoreDirectory.stores, key = { it.id }) { store ->
                        StoreRowCard(
                            store = store,
                            onClick = { onOpenStoreUrl(store.storeUrl) }
                        )
                    }
                }
            }

            ExtensionHubTab.IMPORT -> {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, browserColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Import Local Extension File",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Supports Chrome (.crx), Firefox (.xpi), Opera (.nex / .oex), Edge packages, Tampermonkey (.user.js), and unpacked .zip bundles.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    enabled = !isInstalling,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("extensions_import_file_button")
                                ) {
                                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Choose .crx / .xpi / .nex / .user.js / .zip", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, browserColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Install from Store Page or Package URL",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    placeholder = { Text("https://addons.mozilla.org/en-US/firefox/addon/...") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("install_url_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (urlInput.isNotBlank()) {
                                                onInstallFromUrl(urlInput)
                                                urlInput = ""
                                            }
                                        },
                                        enabled = urlInput.isNotBlank() && !isInstalling,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("extensions_install_url_button")
                                    ) {
                                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Download & Install")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ExtensionHubTab.STUDIO -> {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, browserColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Code, null, tint = browserColors.violet, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "WebExtension & UserScript Studio",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                OutlinedTextField(
                                    value = studioName,
                                    onValueChange = { studioName = it },
                                    label = { Text("Extension Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = studioVersion,
                                        onValueChange = { studioVersion = it },
                                        label = { Text("Version") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.35f)
                                    )
                                    OutlinedTextField(
                                        value = studioMatch,
                                        onValueChange = { studioMatch = it },
                                        label = { Text("URL Match Pattern") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.65f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    runAtOptions.forEachIndexed { idx, opt ->
                                        val selected = idx == studioRunAtIndex
                                        Surface(
                                            onClick = { studioRunAtIndex = idx },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else browserColors.cardBorder),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = opt.removePrefix("document_"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = studioJs,
                                    onValueChange = { studioJs = it },
                                    label = { Text("JavaScript Content Script") },
                                    minLines = 3,
                                    maxLines = 6,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = studioCss,
                                    onValueChange = { studioCss = it },
                                    label = { Text("CSS Stylesheet") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        onInstallStudioScript(
                                            studioName,
                                            studioVersion,
                                            studioMatch,
                                            runAtOptions[studioRunAtIndex],
                                            studioJs,
                                            studioCss
                                        )
                                        selectedTab = ExtensionHubTab.INSTALLED
                                    },
                                    enabled = !isInstalling && studioName.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = browserColors.emerald,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("studio_build_install_button")
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Build & Install Manifest V3 Extension", fontWeight = FontWeight.Bold)
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
private fun UniversalExtensionCard(
    extension: ExtensionEntity,
    onToggleEnabled: () -> Unit,
    onToggleIncognito: () -> Unit,
    onOpenPopup: () -> Unit,
    onOpenOptions: () -> Unit,
    onUninstall: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    val source = remember(extension.sourceBrowser) {
        ExtensionSourceBrowser.entries.firstOrNull { it.name == extension.sourceBrowser }
            ?: ExtensionSourceBrowser.LOCAL
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (extension.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else browserColors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("installed_ext_${extension.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(source = source)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "v${extension.version} · MV${extension.manifestVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                text = source.badgeCode,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // ON / OFF status pill
                Surface(
                    onClick = onToggleEnabled,
                    shape = RoundedCornerShape(999.dp),
                    color = if (extension.enabled) FlagshipEmeraldDarkBg else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (extension.enabled) browserColors.emerald else browserColors.cardBorder
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("toggle_ext_${extension.id}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (extension.enabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (extension.enabled) browserColors.emerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (extension.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = extension.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Allowed in Incognito / Incognito Off pill
                Surface(
                    onClick = onToggleIncognito,
                    shape = RoundedCornerShape(10.dp),
                    color = if (extension.allowInIncognito) FlagshipVioletDarkBg else MaterialTheme.colorScheme.background,
                    border = BorderStroke(
                        1.dp,
                        if (extension.allowInIncognito) browserColors.violet.copy(alpha = 0.55f) else browserColors.cardBorder
                    ),
                    modifier = Modifier.testTag("incognito_ext_${extension.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = if (extension.allowInIncognito) browserColors.violet else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (extension.allowInIncognito) "Allowed in Incognito" else "Incognito Off",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (extension.allowInIncognito) browserColors.violet else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                if (!extension.optionsPath.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onOpenOptions,
                        enabled = extension.enabled,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("options_ext_${extension.id}")
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Options", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (!extension.popupPath.isNullOrBlank()) {
                    FilledTonalButton(
                        onClick = onOpenPopup,
                        enabled = extension.enabled,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("popup_ext_${extension.id}")
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Popup", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Surface(
                    onClick = onUninstall,
                    shape = RoundedCornerShape(10.dp),
                    color = FlagshipCrimsonDarkBg.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, browserColors.crimson.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("uninstall_ext_${extension.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove", tint = browserColors.crimson, modifier = Modifier.size(14.dp))
                        Text("Remove", style = MaterialTheme.typography.labelSmall, color = browserColors.crimson)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreRowCard(
    store: ExtensionStoreLink,
    onClick: () -> Unit
) {
    val browserColors = LocalBrowserColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, browserColors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store_card_${store.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceBadge(source = store.sourceBrowser)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = store.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = store.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open Store",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SourceBadge(
    source: ExtensionSourceBrowser,
    modifier: Modifier = Modifier
) {
    val accent = HubAccentList[source.ordinal % HubAccentList.size]
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = source.badgeCode,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ExtensionPageDialog(
    extension: ExtensionEntity,
    isOptionsPage: Boolean,
    activeTabUrl: String,
    activeTabTitle: String,
    engine: WebExtensionEngine,
    jsBridge: WebExtensionJsBridge,
    onDismiss: () -> Unit
) {
    val relativePath = if (isOptionsPage) extension.optionsPath else extension.popupPath
    val pageFile = remember(extension.installDir, relativePath) {
        relativePath?.let { File(extension.installDir, it.removePrefix("/")) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isOptionsPage) Icons.Outlined.Tune else Icons.Outlined.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isOptionsPage) "${extension.name} Options" else extension.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(32.dp)
                            .testTag("extension_popup_close")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                if (pageFile != null && pageFile.exists() && relativePath != null) {
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("extension_popup_webview"),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    allowFileAccess = true
                                    domStorageEnabled = true
                                }
                                addJavascriptInterface(jsBridge, "BrowserHubExtBridge")
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val reqUrl = request?.url?.toString() ?: return null
                                        return engine.serveVirtualExtensionResource(extension, reqUrl)
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?
                                    ) {
                                        val shim = engine.buildPopupRuntimeShim(
                                            extension = extension,
                                            activeTabUrl = activeTabUrl,
                                            activeTabTitle = activeTabTitle
                                        )
                                        view?.evaluateJavascript(shim, null)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        val shim = engine.buildPopupRuntimeShim(
                                            extension = extension,
                                            activeTabUrl = activeTabUrl,
                                            activeTabTitle = activeTabTitle
                                        )
                                        view?.evaluateJavascript(shim, null)
                                    }
                                }
                                loadUrl(engine.buildVirtualPageUrl(extension, relativePath))
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This extension does not provide a popup or options UI page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
