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
import android.content.pm.ServiceInfo
import android.util.Log

class RtmpService : Service(), ConnectChecker {
    private val tag = "RtmpService"
    private var rtmpCamera: RtmpCamera1? = null
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var wantToStream = false
    private var isBackgroundMode = false

    // 상태 업데이트를 위한 핸들러
    private val statsHandler = Handler(Looper.getMainLooper())
    private var lastBitrate: Long = 0

    // These variables store the configuration used to start the stream
    private var currentWidth = 1280
    private var currentHeight = 720
    private var currentFps = 10

    interface StreamListener {
        fun onStatusChanged(status: String)
    }
    private var listener: StreamListener? = null

    fun setStreamListener(listener: StreamListener?) {
        this.listener = listener
    }

    inner class LocalBinder : Binder() {
        fun getService(): RtmpService = this@RtmpService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bsRTMP::StreamWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)

        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "bsRTMP::WifiLock")
        wifiLock?.acquire()

        startForegroundWithNotification()
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        statsHandler.removeCallbacksAndMessages(null)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        stopStream()
        rtmpCamera?.stopPreview()
    }

    private val updateStatsRunnable = object : Runnable {
        override fun run() {
            val camera = rtmpCamera
            if (camera != null && camera.isStreaming) {
                val bitRateKbps = lastBitrate / 1000
                
                val statusMsg = "Live: $currentWidth x $currentHeight | $currentFps FPS | $bitRateKbps kbps"
                listener?.onStatusChanged(statusMsg)
                
                statsHandler.postDelayed(this, 1000)
            }
        }
    }

    fun initCamera(openGlView: OpenGlView) {
        if (rtmpCamera == null) {
            rtmpCamera = RtmpCamera1(openGlView, this)
            // rtmpCamera?.switchCamera()
        } else {
            rtmpCamera?.replaceView(openGlView)
        }
        if (rtmpCamera?.isOnPreview == false && rtmpCamera?.isStreaming == false) {
             startPreviewSafe()
        }
    }

    private fun startPreviewSafe() {
        try {
            if (rtmpCamera?.prepareVideo(currentWidth, currentHeight, currentFps, 1000 * 1024, 90) == true &&
                rtmpCamera?.prepareAudio() == true
            ) {
                rtmpCamera?.startPreview()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error: ${e.message}")
        }
    }

    fun getRtmpCamera() = rtmpCamera

    fun switchCamera() {
        rtmpCamera?.switchCamera()
    }

    fun startStream(url: String): Boolean {
        val camera = rtmpCamera ?: return false
        if (camera.isStreaming) return true

        startForegroundWithNotification()
        wantToStream = true
        if (camera.prepareVideo(currentWidth, currentHeight, currentFps, 1000 * 1024, 90) && camera.prepareAudio()) {
            if (!camera.isOnPreview) camera.startPreview()
            camera.startStream(url)
            return true
        }
        wantToStream = false
        return false
    }

    fun stopStream() {
        wantToStream = false
        rtmpCamera?.stopStream()
        statsHandler.removeCallbacks(updateStatsRunnable)
        listener?.onStatusChanged("Status: Stopped")
    }

    fun setBackgroundMode() {
        isBackgroundMode = true
        if (rtmpCamera?.isStreaming == true || wantToStream) {
            rtmpCamera?.replaceView(this)
        } else {
            rtmpCamera?.stopPreview()
            stopForeground(true)
            stopSelf()
        }
    }

    fun setForegroundMode(openGlView: OpenGlView) {
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
            .setContentTitle("실시간 방송 중")
            .setContentText("백그라운드 송출 유지 중")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onConnectionStarted(url: String) {
        listener?.onStatusChanged("Status: Connecting...")
    }

    override fun onConnectionSuccess() {
        statsHandler.post(updateStatsRunnable)
    }

    override fun onConnectionFailed(reason: String) {
        listener?.onStatusChanged("Error: $reason")
        wantToStream = false
        statsHandler.removeCallbacks(updateStatsRunnable)
        if (isBackgroundMode) {
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        lastBitrate = bitrate
    }

    override fun onDisconnect() {
        listener?.onStatusChanged("Status: Disconnected")
        wantToStream = false
        statsHandler.removeCallbacks(updateStatsRunnable)
        if (isBackgroundMode) {
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onAuthError() { listener?.onStatusChanged("Auth Error") }
    override fun onAuthSuccess() { listener?.onStatusChanged("Auth Success") }
}
