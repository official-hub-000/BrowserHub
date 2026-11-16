package com.browserhub.app.vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

public class HubVpnService extends VpnService {
    public static final String ACTION_CONNECT = "com.browserhub.app.vpn.CONNECT";
    public static final String ACTION_DISCONNECT = "com.browserhub.app.vpn.DISCONNECT";
    public static final String EXTRA_NODE_ID = "node_id";

    private ParcelFileDescriptor vpnInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            teardown();
            stopSelf();
            return START_NOT_STICKY;
        }
        String nodeId = intent != null && intent.getStringExtra(EXTRA_NODE_ID) != null
                ? intent.getStringExtra(EXTRA_NODE_ID)
                : "ch-zurich";
        establishTunnel(nodeId);
        return START_STICKY;
    }

    private void establishTunnel(String nodeId) {
        teardown();
        try {
            Builder builder = new Builder()
                    .setSession("BrowserHub Zero-Log Tunnel [" + nodeId + "]")
                    .setMtu(1420)
                    .addAddress("10.88.0.2", 24)
                    .addRoute("10.88.0.0", 24);
            vpnInterface = builder.establish();
        } catch (Throwable ignored) {
        }
    }

    private void teardown() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Throwable ignored) {
            }
            vpnInterface = null;
        }
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }
}
