package com.example.bsrtmp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera1
import com.pedro.library.view.OpenGlView
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.content.pm.ServiceInfo
import android.util.Log

class RtmpService : Service(), ConnectChecker {
    private val tag = "RtmpService"
    private var rtmpCamera: RtmpCamera1? = null
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // Tracking if we are trying to stream or actually streaming
    private var wantToStream = false
    private var isBackgroundMode = false

    inner class LocalBinder : Binder() {
        fun getService(): RtmpService = this@RtmpService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "onCreate")

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bsRTMP::StreamWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)

        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "bsRTMP::WifiLock")
        wifiLock?.acquire()

        // Start as foreground service immediately
        startForegroundWithNotification()
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "onStartCommand")
        startForegroundWithNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "onDestroy")
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        stopStream()
        rtmpCamera?.stopPreview()
    }

    fun initCamera(openGlView: OpenGlView) {
        if (rtmpCamera == null) {
            rtmpCamera = RtmpCamera1(openGlView, this)
            rtmpCamera?.switchCamera()
        } else {
            rtmpCamera?.replaceView(openGlView)
        }

        if (rtmpCamera?.isOnPreview == false && rtmpCamera?.isStreaming == false) {
            startPreviewSafe()
        }
    }

    private fun startPreviewSafe() {
        try {
            if (rtmpCamera?.prepareVideo(1280, 720, 10, 1000 * 1024, 90) == true &&
                rtmpCamera?.prepareAudio() == true
            ) {
                rtmpCamera?.startPreview()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting preview: ${e.message}")
        }
    }

    fun getRtmpCamera() = rtmpCamera

    fun switchCamera() {
        rtmpCamera?.switchCamera()
    }

    fun startStream(url: String): Boolean {
        val camera = rtmpCamera ?: return false
        if (camera.isStreaming) return true

        // Ensure we are in foreground before starting stream
        startForegroundWithNotification()

        wantToStream = true
        if (camera.prepareVideo(1280, 720, 10, 1000 * 1024, 90) && camera.prepareAudio()) {
            if (!camera.isOnPreview) {
                camera.startPreview()
            }
            camera.startStream(url)
            return true
        }

        wantToStream = false
        return false
    }

    fun stopStream() {
        wantToStream = false
        rtmpCamera?.stopStream()
    }

    fun setBackgroundMode() {
        Log.d(tag, "setBackgroundMode")
        isBackgroundMode = true
        if (rtmpCamera?.isStreaming == true || wantToStream) {
            rtmpCamera?.replaceView(this)
        } else {
            Log.d(tag, "Not streaming, stopping service.")
            rtmpCamera?.stopPreview()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    fun setForegroundMode(openGlView: OpenGlView) {
        Log.d(tag, "setForegroundMode")
        isBackgroundMode = false
        startForegroundWithNotification()
        rtmpCamera?.replaceView(openGlView)
    }

    private fun createNotification(): Notification {
        val channelId = "rtmp_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "RTMP Stream Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Streaming Live")
            .setContentText("App is maintaining stream in background.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionStarted(url: String) {
        Log.d(tag, "Connection started: $url")
        showToast("연결 시작: $url")
    }

    override fun onConnectionSuccess() {
        Log.d(tag, "Connection success!")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(tag, "Connection failed: $reason")
        showToast("Connection failed: $reason")
        wantToStream = false
        if (isBackgroundMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {
        Log.d(tag, "Disconnected")
        showToast("연결 종료")
        wantToStream = false
        if (isBackgroundMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    override fun onAuthError() {}
    override fun onAuthSuccess() {}
}