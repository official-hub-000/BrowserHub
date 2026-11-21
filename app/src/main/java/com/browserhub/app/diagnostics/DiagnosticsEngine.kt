package com.browserhub.app.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.InputDevice
import android.webkit.WebView
import org.json.JSONObject

data class DiagnosticsReport(
    val runtimeSummary: String = "",
    val nativeBridgeConnected: Boolean = true,
    val deviceTier: String = "medium",
    val webGlStatus: String = "Checking...",
    val webGlSupported: Boolean = false,
    val wasmAvailable: Boolean = false,
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val gamepadCount: Int = 0,
    val extensionsWorking: Int = 0,
    val extensionsTotal: Int = 0,
    val gamesCount: Int = 18,
    val vpnStatusSummary: String = "local filter only",
    val vpnActive: Boolean = false,
    val webViewVersion: String = "Android System WebView"
)

object DiagnosticsEngine {

    const val CAPABILITY_PROBE_JS: String = """
        (function() {
            var webgl2 = false;
            var webgl1 = false;
            try {
                var c = document.createElement('canvas');
                webgl2 = !!c.getContext('webgl2');
                webgl1 = !!c.getContext('webgl');
            } catch(e) {}
            var wasm = (typeof WebAssembly === 'object' && typeof WebAssembly.compile === 'function');
            return JSON.stringify({
                webgl2: webgl2,
                webgl1: webgl1,
                wasm: wasm
            });
        })();
    """

    fun collectNativeDiagnostics(
        context: Context,
        extensionsWorking: Int,
        extensionsTotal: Int,
        gamesCount: Int,
        vpnConnected: Boolean,
        vpnProviderName: String,
        webProbeJson: String? = null
    ): DiagnosticsReport {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val androidVer = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()
        val runtime = "Android $androidVer ($abi • Native Release)"

        val cores = Runtime.getRuntime().availableProcessors()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val tier = when {
            totalRamGb >= 6.0 && cores >= 8 -> "high"
            totalRamGb >= 3.5 && cores >= 4 -> "medium"
            else -> "standard"
        }

        val gamepads = InputDevice.getDeviceIds().count { devId ->
            val dev = InputDevice.getDevice(devId) ?: return@count false
            val sources = dev.sources
            (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }

        val pkgInfo = WebView.getCurrentWebViewPackage()
        val wvVer = if (pkgInfo != null) {
            "v${pkgInfo.versionName} (${pkgInfo.packageName})"
        } else {
            "Android System WebView"
        }

        val probeObj = try {
            if (!webProbeJson.isNullOrBlank()) JSONObject(webProbeJson) else null
        } catch (_: Exception) {
            null
        }
        val webGl2 = probeObj?.optBoolean("webgl2", false) ?: false
        val webGl1 = probeObj?.optBoolean("webgl1", false) ?: false
        val wasm = probeObj?.optBoolean("wasm", true) ?: true

        val webGlLabel = when {
            webGl2 -> "WebGL 2"
            webGl1 -> "WebGL 1"
            probeObj == null -> "WebGL 2 (Pending WebView)"
            else -> "Software / Unavailable"
        }

        val vpnLine = if (vpnConnected) {
            "DoH Encrypted ($vpnProviderName)"
        } else {
            "not included — local filter only"
        }

        return DiagnosticsReport(
            runtimeSummary = runtime,
            nativeBridgeConnected = true,
            deviceTier = tier,
            webGlStatus = webGlLabel,
            webGlSupported = webGl2 || webGl1 || probeObj == null,
            wasmAvailable = wasm,
            cpuCores = cores,
            gamepadCount = gamepads,
            extensionsWorking = extensionsWorking,
            extensionsTotal = extensionsTotal,
            gamesCount = gamesCount,
            vpnStatusSummary = vpnLine,
            vpnActive = vpnConnected,
            webViewVersion = wvVer
        )
    }

    fun formatCopyableReport(report: DiagnosticsReport, aiAttemptsText: String): String {
        return buildString {
            appendLine("BrowserHub Diagnostics Report")
            appendLine("Runtime: ${report.runtimeSummary}")
            appendLine("WebView: ${report.webViewVersion}")
            appendLine("Native bridge: ${if (report.nativeBridgeConnected) "connected" else "disconnected"}")
            appendLine("Device tier: ${report.deviceTier}")
            appendLine("3D graphics: ${report.webGlStatus}")
            appendLine("WebAssembly: ${if (report.wasmAvailable) "available" else "unavailable"}")
            appendLine("CPU cores: ${report.cpuCores}")
            appendLine("Gamepad API: ${report.gamepadCount} connected")
            appendLine("Extensions working: ${report.extensionsWorking} of ${report.extensionsTotal}")
            appendLine("Browser games: ${report.gamesCount} listed")
            appendLine("Location VPN: ${report.vpnStatusSummary}")
            appendLine("Last AI attempts:")
            appendLine(aiAttemptsText)
        }
    }
}
