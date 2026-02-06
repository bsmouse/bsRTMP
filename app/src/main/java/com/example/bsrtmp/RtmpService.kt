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

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        }
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
            // 전면 카메라를 기본으로 설정
            rtmpCamera?.switchCamera()
        } else {
            rtmpCamera?.replaceView(openGlView)
        }

        // 초기화 시점에 미리보기가 꺼져있으면 안전하게 시작
/*        if (rtmpCamera?.isOnPreview == false && rtmpCamera?.isStreaming == false) {
            startPreviewSafe()
        }*/
    }

    private fun startPreviewSafe() {
        try {
            // prepare 호출 후 startPreview 실행
            if (rtmpCamera?.prepareVideo(1280, 720, 10, 1000 * 1024, 90) == true &&
                rtmpCamera?.prepareAudio() == true
            ) {
                rtmpCamera?.startPreview()
            }
        } catch (e: Exception) {
            Log.e(tag, "미리보기 시작 중 예외 발생: ${e.message}")
        }
    }

    fun getRtmpCamera() = rtmpCamera

    fun switchCamera() {
        rtmpCamera?.switchCamera()
    }

    fun startStream(url: String): Boolean {
        val camera = rtmpCamera ?: return false
        if (camera.isStreaming) return true

        // 이미 미리보기 중이라면 설정을 다시 하지 않고 바로 송출
        if (camera.isOnPreview) {
            camera.startStream(url)
            return true
        }

        // 미리보기가 꺼져 있다면 준비 후 미리보기와 송출을 함께 시작
        if (camera.prepareVideo(1280, 720, 10, 1000 * 1024, 90) && camera.prepareAudio()) {
            camera.startPreview()
            camera.startStream(url)
            return true
        }

        return false
    }

    fun stopStream() {
        rtmpCamera?.stopStream()
    }

    fun setBackgroundMode() {
        Log.d(tag, "setBackgroundMode")
        if (rtmpCamera?.isStreaming == true) {
            rtmpCamera?.replaceView(this)
        } else {
            rtmpCamera?.stopPreview()
        }
    }

    fun setForegroundMode(openGlView: OpenGlView) {
        Log.d(tag, "setForegroundMode")
        rtmpCamera?.replaceView(openGlView)
        // 화면으로 돌아왔을 때 스트리밍 중이 아니면 자동으로 미리보기를 켜지 않음
    }

    private fun createNotification(): Notification {
        val channelId = "rtmp_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "RTMP Stream Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("실시간 방송 중")
            .setContentText("앱이 백그라운드에서도 송출을 유지합니다.")
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
        Log.d(tag, "연결 시작: $url")
    }

    override fun onConnectionSuccess() {
        showToast("연결 성공!")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(tag, "연결 실패: $reason")
        showToast("연결 실패: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {
        Log.d(tag, "연결 종료")
    }

    override fun onAuthError() {}
    override fun onAuthSuccess() {}
}
