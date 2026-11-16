package com.browserhub.app.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class HubVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_CONNECT = "com.browserhub.app.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.browserhub.app.vpn.DISCONNECT"
        const val EXTRA_NODE_ID = "node_id"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_DOH_PROVIDER = "doh_provider"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            teardownTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        val nodeId = intent?.getStringExtra(EXTRA_NODE_ID) ?: "ch-zurich"
        establishTunnel(nodeId)
        return START_STICKY
    }

    private fun establishTunnel(nodeId: String) {
        teardownTunnel()
        try {
            val builder = Builder()
                .setSession("BrowserHub Zero-Log Tunnel [$nodeId]")
                .setMtu(1420)
                .addAddress("10.88.0.2", 24)
                .addRoute("10.88.0.0", 24)
            vpnInterface = builder.establish()
        } catch (_: Throwable) {
        }
    }

    private fun teardownTunnel() {
        try {
            vpnInterface?.close()
        } catch (_: Throwable) {
        } finally {
            vpnInterface = null
        }
    }

    override fun onDestroy() {
        teardownTunnel()
        super.onDestroy()
    }
}
