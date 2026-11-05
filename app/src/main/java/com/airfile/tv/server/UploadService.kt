package com.airfile.tv.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.airfile.tv.AirFileState
import com.airfile.tv.MainActivity
import com.airfile.tv.R
import com.airfile.tv.net.NetworkInfo
import com.airfile.tv.net.UploadUrl
import com.airfile.tv.storage.FileSink
import java.io.IOException
import java.security.SecureRandom

/** Keeps the upload server alive while AirFile is open and publishes the QR URL. */
class UploadService : Service() {

    private var server: UploadServer? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var port = 0
    private var token = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server == null) startServer()
        return START_NOT_STICKY
    }

    private fun startServer() {
        token = newToken()
        val sink = FileSink(this)
        for (candidate in PORTS) {
            val s = UploadServer(this, candidate, token, sink)
            try {
                s.start(SOCKET_TIMEOUT_MS, false)
                server = s
                port = candidate
                break
            } catch (_: IOException) {
                // Port busy; try the next one.
            }
        }
        if (server == null) {
            AirFileState.error.value = "Could not start the receiver (no free port)."
            return
        }
        AirFileState.error.value = null
        refreshUrl()

        val cm = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshUrl()
            override fun onLost(network: Network) = refreshUrl()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                refreshUrl()
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun refreshUrl() {
        val ip = NetworkInfo.findLanIpv4(this)
        AirFileState.url.value = ip?.let { UploadUrl.build(it, port, token) }
    }

    override fun onDestroy() {
        networkCallback?.let {
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        }
        server?.stop()
        server = null
        AirFileState.url.value = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.notification_title))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun newToken(): String {
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private companion object {
        const val CHANNEL_ID = "airfile_receiver"
        const val NOTIFICATION_ID = 1
        const val SOCKET_TIMEOUT_MS = 60_000
        val PORTS = 8080..8099
    }
}
