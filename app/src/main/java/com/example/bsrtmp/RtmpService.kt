package com.example.bsrtmp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
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
import com.pedro.encoder.input.video.CameraHelper

class RtmpService : Service(), ConnectChecker {
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

        // 1. CPU가 잠들지 않도록 WakeLock 설정
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bsRTMP::StreamWakeLock")
        wakeLock?.acquire()

        // 2. Wi-Fi가 끊기지 않도록 WifiLock 설정
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock =
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "bsRTMP::WifiLock")
        wifiLock?.acquire()

        startForeground(1, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        // 서비스 종료 시 잠금 해제
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
    }

    // 카메라 객체 초기화 (액티비티에서 호출)
    fun initCamera(openGlView: OpenGlView) {
        if (rtmpCamera == null) {
            rtmpCamera = RtmpCamera1(openGlView, this)
        } else {
            rtmpCamera?.replaceView(openGlView)
        }
    }

    fun getRtmpCamera() = rtmpCamera

    fun switchCamera() {
        rtmpCamera?.switchCamera()
    }

    fun startStream(url: String): Boolean {
        // PublishActivity에 있던 설정 로직을 서비스로 이동
        if (rtmpCamera?.prepareVideo(
                1280,
                720,
                10,
                1000 * 1024,
                90
            ) == true && rtmpCamera?.prepareAudio() == true
        ) {
            rtmpCamera?.startStream(url)
            return true
        }
        return false
    }

    fun stopStream() {
        rtmpCamera?.stopStream()
        rtmpCamera?.stopPreview()
    }

    private fun createNotification(): Notification {
        val channelId = "rtmp_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "RTMP Stream Service",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("실시간 방송 중")
            .setContentText("앱이 백그라운드에서도 송출을 유지합니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    // ConnectChecker 구현 (필요 시 액티비티로 콜백 전달 가능)
    private fun showToast(message: String) {
        // Looper.getMainLooper()를 사용하여 UI 스레드에서 Toast를 띄움
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ConnectChecker 구현부에서 사용 예시
    override fun onConnectionStarted(url: String) {
        showToast("연결 시작: $url")
    }

    override fun onConnectionSuccess() {
        showToast("연결 성공!")
    }

    override fun onConnectionFailed(reason: String) {
        showToast("연결 실패: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {}
    override fun onAuthError() {}
    override fun onAuthSuccess() {}
}