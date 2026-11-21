package com.browserhub.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.browserhub.app.R
import com.browserhub.app.ui.theme.LocalBrowserColors
import com.browserhub.app.vpn.BrowserHubVpnService
import com.browserhub.app.vpn.ProxyExitMode
import com.browserhub.app.vpn.VirtualRegion
import com.browserhub.app.vpn.VpnConnectionState
import com.browserhub.app.vpn.VpnProvider
import com.browserhub.app.vpn.VpnStats
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun VpnOverlayScreen(
    vpnStats: VpnStats,
    onSelectProvider: (VpnProvider) -> Unit,
    onSelectVirtualRegion: (VirtualRegion) -> Unit,
    onConfigureProxyExit: (ProxyExitMode, String?) -> Unit,
    onRefreshNetworkCheck: () -> Unit,
    onUpdateToggles: (
        blockWebTrackers: Boolean?,
        blockThirdPartyCookies: Boolean?,
        webRtcLeakProtection: Boolean?
    ) -> Unit,
    onStopVpn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val browserColors = LocalBrowserColors.current

    var customProxyInput by rememberSaveable(vpnStats.customProxyUrl) {
        mutableStateOf(vpnStats.customProxyUrl)
    }
    var showCustomProxyField by rememberSaveable(vpnStats.proxyExitMode) {
        mutableStateOf(vpnStats.proxyExitMode == ProxyExitMode.CUSTOM_PROXY)
    }

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vpnStats.isConnected, vpnStats.connectedSinceMs) {
        val startedAt = vpnStats.connectedSinceMs
        if (!vpnStats.isConnected || startedAt == null) {
            elapsedSeconds = 0L
        } else {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            BrowserHubVpnService.start(context, vpnStats.provider)
        }
    }

    val requestVpnConnection: () -> Unit = {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnConsentLauncher.launch(prepareIntent)
        } else {
            BrowserHubVpnService.start(context, vpnStats.provider)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        requestVpnConnection()
    }

    val onConnectOrDisconnect: () -> Unit = {
        if (vpnStats.connectionState == VpnConnectionState.CONNECTED ||
            vpnStats.connectionState == VpnConnectionState.CONNECTING
        ) {
            onStopVpn()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestVpnConnection()
            }
        }
    }

    val isProtected = vpnStats.connectionState == VpnConnectionState.CONNECTED
    val isConnecting = vpnStats.connectionState == VpnConnectionState.CONNECTING

    val statusAccent by animateColorAsState(
        targetValue = when (vpnStats.connectionState) {
            VpnConnectionState.CONNECTED -> browserColors.secure
            VpnConnectionState.CONNECTING -> browserColors.warning
            VpnConnectionState.ERROR -> MaterialTheme.colorScheme.error
            VpnConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.primary
        },
        label = "vpnStatusAccent"
    )

    val orderedRegions = remember {
        listOf(
            VirtualRegion.SWITZERLAND,
            VirtualRegion.GERMANY,
            VirtualRegion.NETHERLANDS,
            VirtualRegion.UK,
            VirtualRegion.USA_EAST,
            VirtualRegion.SINGAPORE,
            VirtualRegion.JAPAN,
            VirtualRegion.OFF
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            LibraryTopBar(
                title = stringResource(R.string.vpn_title),
                onBack = onDismiss
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero VPN Shield Card
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.2.dp, statusAccent.copy(alpha = 0.45f)),
                            tonalElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(statusAccent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isConnecting) {
                                        CircularProgressIndicator(
                                            color = statusAccent,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isProtected) {
                                                Icons.Outlined.GppGood
                                            } else {
                                                Icons.Outlined.VpnLock
                                            },
                                            contentDescription = null,
                                            tint = statusAccent,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = when (vpnStats.connectionState) {
                                        VpnConnectionState.CONNECTED -> stringResource(R.string.vpn_status_connected)
                                        VpnConnectionState.CONNECTING -> stringResource(R.string.vpn_status_connecting)
                                        VpnConnectionState.ERROR -> vpnStats.lastError ?: "Tunnel Error"
                                        VpnConnectionState.DISCONNECTED -> stringResource(R.string.vpn_status_disconnected)
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(R.string.vpn_subtitle_free),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusAccent
                                )

                                if (isProtected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val routeSummary = buildString {
                                        if (vpnStats.virtualRegion != VirtualRegion.OFF) {
                                            append("Location Mask: ${vpnStats.virtualRegion.displayName}")
                                        } else {
                                            append("Location Mask: Off")
                                        }
                                        append(" • ")
                                        if (!vpnStats.activeProxyEndpoint.isNullOrBlank()) {
                                            append("Exit: ${vpnStats.activeProxyEndpoint}")
                                        } else {
                                            append("DoH: ${vpnStats.provider.serverIp}")
                                        }
                                    }
                                    Text(
                                        text = routeSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = onConnectOrDisconnect,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isProtected || isConnecting) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        contentColor = if (isProtected || isConnecting) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onPrimary
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("vpn_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PowerSettingsNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isProtected || isConnecting) {
                                            stringResource(R.string.vpn_action_disconnect)
                                        } else {
                                            stringResource(R.string.vpn_action_connect)
                                        },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }

                    // Live Telemetry Metrics
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VpnMetricTile(
                                label = "Encrypted DNS",
                                value = vpnStats.queriesEncrypted.toString(),
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            VpnMetricTile(
                                label = "Trackers Blocked",
                                value = vpnStats.totalTrackersBlocked.toString(),
                                accent = browserColors.secure,
                                modifier = Modifier.weight(1f)
                            )
                            VpnMetricTile(
                                label = "Uptime",
                                value = formatUptime(elapsedSeconds),
                                accent = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Live IP & Location Verifier Card
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Public,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Live IP & Location Check",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = onRefreshNetworkCheck,
                                        enabled = !vpnStats.isCheckingNetwork,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("vpn_check_ip_button")
                                    ) {
                                        if (vpnStats.isCheckingNetwork) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Checking…")
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Check Live IP")
                                        }
                                    }
                                }

                                val check = vpnStats.networkCheckResult
                                if (check != null) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Public IP: ${check.publicIp}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Network Country/City: ${check.locationLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (check.ispLabel.isNotBlank()) {
                                            Text(
                                                text = "Carrier / ASN: ${check.ispLabel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "Traffic Route: ${check.viaProxyLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val maskStatus = if (isProtected && vpnStats.virtualRegion != VirtualRegion.OFF) {
                                            "Browser GPS & Timezone Mask: Active (${vpnStats.virtualRegion.displayName} • ${vpnStats.virtualRegion.timezoneId})"
                                        } else if (vpnStats.virtualRegion != VirtualRegion.OFF) {
                                            "Browser GPS & Timezone Mask: Armed (${vpnStats.virtualRegion.displayName}) — Connect VPN to enforce"
                                        } else {
                                            "Browser GPS & Timezone Mask: Off"
                                        }
                                        Text(
                                            text = maskStatus,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isProtected && vpnStats.virtualRegion != VirtualRegion.OFF) {
                                                browserColors.secure
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Tap \"Check Live IP\" to inspect your real public exit IP, ASN, and active WebView location shield.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Virtual Location & Timezone Shield
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.MyLocation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Browser Location & Timezone Mask",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Blocks hardware GPS permission prompts and overrides Geolocation, Timezone, Intl locale, and WebRTC inside websites.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(orderedRegions, key = { it.name }) { region ->
                                    val selected = vpnStats.virtualRegion == region
                                    Surface(
                                        onClick = { onSelectVirtualRegion(region) },
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        border = BorderStroke(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                            }
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("vpn_region_${region.name.lowercase(Locale.US)}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Outlined.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Outlined.Language,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = region.shortLabel,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (vpnStats.virtualRegion != VirtualRegion.OFF) {
                                val r = vpnStats.virtualRegion
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Active coordinates: %.4f, %.4f • TZ: %s",
                                        r.latitude,
                                        r.longitude,
                                        r.timezoneId
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isProtected) browserColors.secure else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Traffic Exit Route (Public IP Relocation)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Route,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Public IP Exit Route",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            ProxyExitMode.entries.forEach { mode ->
                                val selected = vpnStats.proxyExitMode == mode
                                Surface(
                                    onClick = {
                                        if (mode == ProxyExitMode.CUSTOM_PROXY) {
                                            showCustomProxyField = true
                                        } else {
                                            showCustomProxyField = false
                                            onConfigureProxyExit(mode, null)
                                        }
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("vpn_exit_${mode.name.lowercase(Locale.US)}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.displayName,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = mode.badge,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = showCustomProxyField || vpnStats.proxyExitMode == ProxyExitMode.CUSTOM_PROXY) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customProxyInput,
                                        onValueChange = { customProxyInput = it },
                                        singleLine = true,
                                        label = { Text("Proxy Endpoint (socks5://host:port or http://host:port)") },
                                        placeholder = { Text("socks5://127.0.0.1:1080") },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Uri,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                onConfigureProxyExit(ProxyExitMode.CUSTOM_PROXY, customProxyInput)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("vpn_proxy_input")
                                    )
                                    Button(
                                        onClick = {
                                            onConfigureProxyExit(ProxyExitMode.CUSTOM_PROXY, customProxyInput)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("vpn_proxy_apply_button")
                                    ) {
                                        Text("Verify & Apply Exit Proxy")
                                    }
                                }
                            }
                        }
                    }

                    // DoH Provider Selector
                    item {
                        Text(
                            text = stringResource(R.string.vpn_provider_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VpnProvider.entries.forEach { provider ->
                                val selected = vpnStats.provider == provider
                                Surface(
                                    onClick = { onSelectProvider(provider) },
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("vpn_provider_${provider.name.lowercase(Locale.US)}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = provider.displayName,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = provider.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = provider.badge,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Browser Privacy Shield Switches
                    item {
                        Text(
                            text = stringResource(R.string.vpn_shield_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VpnToggleRow(
                                icon = Icons.Outlined.Shield,
                                title = stringResource(R.string.vpn_block_trackers),
                                subtitle = stringResource(R.string.vpn_block_trackers_desc),
                                checked = vpnStats.blockWebTrackers,
                                onCheckedChange = { enabled ->
                                    onUpdateToggles(enabled, null, null)
                                },
                                testTag = "vpn_switch_trackers"
                            )
                            VpnToggleRow(
                                icon = Icons.Outlined.Cookie,
                                title = stringResource(R.string.vpn_block_3p_cookies),
                                subtitle = stringResource(R.string.vpn_block_3p_cookies_desc),
                                checked = vpnStats.blockThirdPartyCookies,
                                onCheckedChange = { enabled ->
                                    onUpdateToggles(null, enabled, null)
                                },
                                testTag = "vpn_switch_cookies"
                            )
                            VpnToggleRow(
                                icon = Icons.Outlined.Lock,
                                title = stringResource(R.string.vpn_webrtc_guard),
                                subtitle = stringResource(R.string.vpn_webrtc_guard_desc),
                                checked = vpnStats.webRtcLeakProtection,
                                onCheckedChange = { enabled ->
                                    onUpdateToggles(null, null, enabled)
                                },
                                testTag = "vpn_switch_webrtc"
                            )
                        }
                    }

                    // Transparency / Architecture Note
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "How BrowserHub Privacy Routing works: (1) On-device Android VPN TUN encrypts DNS lookups via RFC 8484 DoH (${vpnStats.provider.serverIp}) and blocks tracker domains without throttling speed. (2) Browser Location Shield blocks GPS prompts and masks HTML5 geolocation & timezone to your selected region. (3) Because BrowserHub has no remote paid VPN servers, your external WAN IP only changes when you route WebView traffic through Orbot/Tor or a Custom SOCKS5/HTTP Exit Proxy above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnMetricTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VpnToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

private fun formatUptime(totalSeconds: Long): String {
    val hrs = totalSeconds / 3600L
    val mins = (totalSeconds % 3600L) / 60L
    val secs = totalSeconds % 60L
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
