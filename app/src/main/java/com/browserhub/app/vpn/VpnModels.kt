package com.browserhub.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.Locale

enum class VpnProvider(
    val displayName: String,
    val subtitle: String,
    val dohUrl: String,
    val serverIp: String,
    val badge: String
) {
    CLOUDFLARE(
        displayName = "Cloudflare 1.1.1.1",
        subtitle = "Ultra-fast encrypted DoH • No-logs privacy",
        dohUrl = "https://1.1.1.1/dns-query",
        serverIp = "1.1.1.1",
        badge = "FAST"
    ),
    ADGUARD(
        displayName = "AdGuard Privacy DNS",
        subtitle = "Encrypted DoH + built-in ad & tracker filter",
        dohUrl = "https://94.140.14.14/dns-query",
        serverIp = "94.140.14.14",
        badge = "FILTER"
    ),
    QUAD9(
        displayName = "Quad9 9.9.9.9",
        subtitle = "Encrypted DoH + malware & phishing block",
        dohUrl = "https://9.9.9.9/dns-query",
        serverIp = "9.9.9.9",
        badge = "SEC"
    )
}

enum class VirtualRegion(
    val shortLabel: String,
    val displayName: String,
    val countryCode: String,
    val timezoneId: String,
    val tzOffsetMinutesWest: Int,
    val latitude: Double,
    val longitude: Double
) {
    OFF(
        shortLabel = "Off",
        displayName = "Device Real Location",
        countryCode = "LOCAL",
        timezoneId = "",
        tzOffsetMinutesWest = 0,
        latitude = 0.0,
        longitude = 0.0
    ),
    SWITZERLAND(
        shortLabel = "Zurich CH",
        displayName = "Zurich, Switzerland",
        countryCode = "CH",
        timezoneId = "Europe/Zurich",
        tzOffsetMinutesWest = -60,
        latitude = 47.3769,
        longitude = 8.5417
    ),
    GERMANY(
        shortLabel = "Frankfurt DE",
        displayName = "Frankfurt, Germany",
        countryCode = "DE",
        timezoneId = "Europe/Berlin",
        tzOffsetMinutesWest = -60,
        latitude = 50.1109,
        longitude = 8.6821
    ),
    NETHERLANDS(
        shortLabel = "Amsterdam NL",
        displayName = "Amsterdam, Netherlands",
        countryCode = "NL",
        timezoneId = "Europe/Amsterdam",
        tzOffsetMinutesWest = -60,
        latitude = 52.3676,
        longitude = 4.9041
    ),
    UK(
        shortLabel = "London UK",
        displayName = "London, United Kingdom",
        countryCode = "GB",
        timezoneId = "Europe/London",
        tzOffsetMinutesWest = 0,
        latitude = 51.5074,
        longitude = -0.1278
    ),
    USA_EAST(
        shortLabel = "New York US",
        displayName = "New York, United States",
        countryCode = "US",
        timezoneId = "America/New_York",
        tzOffsetMinutesWest = 300,
        latitude = 40.7128,
        longitude = -74.0060
    ),
    SINGAPORE(
        shortLabel = "Singapore SG",
        displayName = "Singapore",
        countryCode = "SG",
        timezoneId = "Asia/Singapore",
        tzOffsetMinutesWest = -480,
        latitude = 1.3521,
        longitude = 103.8198
    ),
    JAPAN(
        shortLabel = "Tokyo JP",
        displayName = "Tokyo, Japan",
        countryCode = "JP",
        timezoneId = "Asia/Tokyo",
        tzOffsetMinutesWest = -540,
        latitude = 35.6762,
        longitude = 139.6503
    )
}

enum class ProxyExitMode(
    val displayName: String,
    val subtitle: String,
    val badge: String
) {
    DIRECT_DOH(
        displayName = "DoH Tunnel + Browser Location Mask",
        subtitle = "Encrypts DNS & masks GPS, Timezone & WebRTC at full speed (public IP stays ISP/Wi-Fi)",
        badge = "ZERO LAG"
    ),
    ORBOT_TOR(
        displayName = "Orbot / Tor Exit Proxy (127.0.0.1)",
        subtitle = "Routes WebView HTTP/HTTPS traffic via local Orbot app for a true external exit IP",
        badge = "TOR IP"
    ),
    CUSTOM_PROXY(
        displayName = "Custom SOCKS5 / HTTP Exit Proxy",
        subtitle = "Routes WebView traffic via your own SOCKS5 or HTTP proxy server to change exit IP & country",
        badge = "EXIT IP"
    )
}

data class ParsedProxySpec(
    val scheme: String,
    val host: String,
    val port: Int
) {
    val proxyConfigUrl: String
        get() = "$scheme://$host:$port"

    fun toJavaNetProxy(): Proxy {
        val type = if (scheme.startsWith("socks")) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress(host, port))
    }
}

object VpnProxyParser {
    fun parse(raw: String): ParsedProxySpec? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val normalized = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        return try {
            val uri = URI(normalized)
            val scheme = uri.scheme.orEmpty().lowercase(Locale.US).let {
                when (it) {
                    "socks", "socks5", "socks5h" -> "socks5"
                    "https" -> "https"
                    "http" -> "http"
                    else -> return null
                }
            }
            val host = uri.host?.trim().orEmpty()
            val port = uri.port
            if (host.isBlank() || port !in 1..65535) return null
            ParsedProxySpec(scheme = scheme, host = host, port = port)
        } catch (_: Exception) {
            null
        }
    }
}

data class NetworkCheckResult(
    val publicIp: String,
    val locationLabel: String,
    val ispLabel: String,
    val viaProxyLabel: String,
    val error: String? = null
)

enum class VpnConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class VpnStats(
    val connectionState: VpnConnectionState = VpnConnectionState.DISCONNECTED,
    val provider: VpnProvider = VpnProvider.CLOUDFLARE,
    val virtualRegion: VirtualRegion = VirtualRegion.SWITZERLAND,
    val proxyExitMode: ProxyExitMode = ProxyExitMode.DIRECT_DOH,
    val customProxyUrl: String = "",
    val activeProxyEndpoint: String? = null,
    val connectedSinceMs: Long? = null,
    val queriesEncrypted: Long = 0,
    val dnsTrackersBlocked: Long = 0,
    val webTrackersBlocked: Long = 0,
    val blockWebTrackers: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val webRtcLeakProtection: Boolean = true,
    val isCheckingNetwork: Boolean = false,
    val networkCheckResult: NetworkCheckResult? = null,
    val lastError: String? = null
) {
    val isConnected: Boolean
        get() = connectionState == VpnConnectionState.CONNECTED

    val totalTrackersBlocked: Long
        get() = dnsTrackersBlocked + webTrackersBlocked
}

object VpnStateHolder {
    private val _stats = MutableStateFlow(VpnStats())
    val stats: StateFlow<VpnStats> = _stats.asStateFlow()

    fun setState(state: VpnConnectionState, provider: VpnProvider? = null, error: String? = null) {
        _stats.update { current ->
            current.copy(
                connectionState = state,
                provider = provider ?: current.provider,
                connectedSinceMs = if (state == VpnConnectionState.CONNECTED) {
                    current.connectedSinceMs ?: System.currentTimeMillis()
                } else if (state == VpnConnectionState.DISCONNECTED) {
                    null
                } else {
                    current.connectedSinceMs
                },
                lastError = error
            )
        }
    }

    fun setProvider(provider: VpnProvider) {
        _stats.update { it.copy(provider = provider) }
    }

    fun setVirtualRegion(region: VirtualRegion) {
        _stats.update { it.copy(virtualRegion = region) }
    }

    fun setProxyExitConfig(mode: ProxyExitMode, customProxyUrl: String? = null, activeEndpoint: String? = null) {
        _stats.update { current ->
            current.copy(
                proxyExitMode = mode,
                customProxyUrl = customProxyUrl ?: current.customProxyUrl,
                activeProxyEndpoint = activeEndpoint
            )
        }
    }

    fun setNetworkChecking(checking: Boolean) {
        _stats.update { it.copy(isCheckingNetwork = checking) }
    }

    fun setNetworkCheckResult(result: NetworkCheckResult?) {
        _stats.update { it.copy(isCheckingNetwork = false, networkCheckResult = result) }
    }

    fun updatePrivacyToggles(
        blockWebTrackers: Boolean? = null,
        blockThirdPartyCookies: Boolean? = null,
        webRtcLeakProtection: Boolean? = null
    ) {
        _stats.update { current ->
            current.copy(
                blockWebTrackers = blockWebTrackers ?: current.blockWebTrackers,
                blockThirdPartyCookies = blockThirdPartyCookies ?: current.blockThirdPartyCookies,
                webRtcLeakProtection = webRtcLeakProtection ?: current.webRtcLeakProtection
            )
        }
    }

    fun incrementEncryptedQuery() {
        _stats.update { it.copy(queriesEncrypted = it.queriesEncrypted + 1) }
    }

    fun incrementDnsTrackerBlocked() {
        _stats.update { it.copy(dnsTrackersBlocked = it.dnsTrackersBlocked + 1) }
    }

    fun incrementWebTrackerBlocked() {
        _stats.update { it.copy(webTrackersBlocked = it.webTrackersBlocked + 1) }
    }
}
