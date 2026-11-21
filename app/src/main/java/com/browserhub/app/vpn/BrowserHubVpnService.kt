package com.browserhub.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.browserhub.app.MainActivity
import com.browserhub.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

class BrowserHubVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelInterface: ParcelFileDescriptor? = null
    private var tunnelJob: Job? = null
    private val writeLock = Any()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpnTunnel()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val providerName = intent?.getStringExtra(EXTRA_PROVIDER)
                val provider = providerName?.let { name ->
                    VpnProvider.entries.firstOrNull { it.name == name }
                } ?: VpnStateHolder.stats.value.provider
                startVpnTunnel(provider)
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun startVpnTunnel(provider: VpnProvider) {
        stopVpnTunnel(updateState = false)
        VpnStateHolder.setState(VpnConnectionState.CONNECTING, provider = provider)

        createNotificationChannel()
        val notification = buildNotification(provider)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val builder = Builder()
                .setSession("BrowserHub Privacy VPN")
                .addAddress(VIRTUAL_CLIENT_IP, 32)
                .addDnsServer(VIRTUAL_DNS_IP)
                .addRoute(VIRTUAL_DNS_IP, 32)
                .setMtu(1500)
                .setBlocking(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val pfd = builder.establish()
                ?: throw IllegalStateException("Android VPN permission was not granted.")
            tunnelInterface = pfd
            VpnStateHolder.setState(VpnConnectionState.CONNECTED, provider = provider)

            tunnelJob = serviceScope.launch {
                runDnsTunnelLoop(pfd, provider)
            }
        } catch (e: Exception) {
            VpnStateHolder.setState(
                VpnConnectionState.ERROR,
                provider = provider,
                error = e.message ?: "Unable to establish VPN interface"
            )
            stopSelf()
        }
    }

    private fun runDnsTunnelLoop(pfd: ParcelFileDescriptor, provider: VpnProvider) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (serviceScope.isActive) {
            val length = try {
                input.read(buffer)
            } catch (_: Exception) {
                break
            }
            if (length <= 0) continue

            val parsed = DnsPacketCodec.parseIpv4UdpDns(buffer, length) ?: continue
            serviceScope.launch {
                try {
                    val dnsResp = if (
                        VpnStateHolder.stats.value.blockWebTrackers &&
                        TrackerBlocklist.isBlockedDomain(parsed.queriedDomain)
                    ) {
                        VpnStateHolder.incrementDnsTrackerBlocked()
                        DnsPacketCodec.buildNxDomainDnsResponse(parsed.dnsPayload)
                    } else {
                        val resolved = queryDohServer(provider.dohUrl, parsed.dnsPayload)
                        VpnStateHolder.incrementEncryptedQuery()
                        resolved
                    }

                    if (dnsResp != null) {
                        val responsePacket = DnsPacketCodec.buildIpv4UdpResponsePacket(parsed, dnsResp)
                        synchronized(writeLock) {
                            output.write(responsePacket)
                            output.flush()
                        }
                    }
                } catch (_: Exception) {
                    // Ignore transient DNS query failures
                }
            }
        }
    }

    private fun queryDohServer(dohUrl: String, dnsQuery: ByteArray): ByteArray? {
        val conn = (URL(dohUrl).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Content-Type", "application/dns-message")
            setRequestProperty("Accept", "application/dns-message")
            sslSocketFactory = ProtectedSslSocketFactory(
                delegate = sslSocketFactory,
                protectSocket = { socket -> protect(socket) }
            )
        }
        return try {
            conn.outputStream.use { it.write(dnsQuery) }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { it.readBytes() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun stopVpnTunnel(updateState: Boolean = true) {
        tunnelJob?.cancel()
        tunnelJob = null
        try {
            tunnelInterface?.close()
        } catch (_: Exception) {
        }
        tunnelInterface = null
        if (updateState) {
            VpnStateHolder.setState(VpnConnectionState.DISCONNECTED)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        stopVpnTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BrowserHub Built-in VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Encrypted DNS-over-HTTPS privacy tunnel"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(provider: VpnProvider): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BrowserHubVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_shield)
            .setContentTitle("BrowserHub VPN Protected")
            .setContentText("Encrypted tunnel active • ${provider.displayName}")
            .setContentIntent(launchIntent)
            .addAction(0, "Disconnect", stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.browserhub.app.vpn.START"
        const val ACTION_STOP = "com.browserhub.app.vpn.STOP"
        const val EXTRA_PROVIDER = "extra_provider"
        private const val CHANNEL_ID = "browserhub_vpn_channel"
        private const val NOTIFICATION_ID = 4101
        private const val VIRTUAL_CLIENT_IP = "10.111.0.1"
        private const val VIRTUAL_DNS_IP = "10.111.0.2"

        fun start(context: Context, provider: VpnProvider) {
            val intent = Intent(context, BrowserHubVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROVIDER, provider.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BrowserHubVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

private class ProtectedSslSocketFactory(
    private val delegate: SSLSocketFactory,
    private val protectSocket: (Socket) -> Boolean
) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        if (s != null) protectSocket(s)
        return delegate.createSocket(s, host, port, autoClose).also { protectSocket(it) }
    }

    override fun createSocket(host: String?, port: Int): Socket {
        return delegate.createSocket(host, port).also { protectSocket(it) }
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        return delegate.createSocket(host, port, localHost, localPort).also { protectSocket(it) }
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        return delegate.createSocket(host, port).also { protectSocket(it) }
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        return delegate.createSocket(address, port, localAddress, localPort).also { protectSocket(it) }
    }
}
