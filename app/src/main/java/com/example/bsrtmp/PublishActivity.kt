package com.example.bsrtmp

import androidx.core.content.ContextCompat
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.pedro.library.view.OpenGlView
import android.content.Intent // Intent를 사용하기 위해 추가 필요
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings


class PublishActivity : AppCompatActivity() {

    private var rtmpService: RtmpService? = null
    private var isBound = false
    private lateinit var openGlView: OpenGlView
    private lateinit var btnStartStop: Button
    private lateinit var btnGoToPlay: Button
    private val rtmpUrl = "rtmp://192.168.0.116/stream/u2"

    // 서비스 연결 콜백
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RtmpService.LocalBinder
            rtmpService = binder.getService()
            isBound = true
            // 서비스에 있는 카메라 엔진에 현재 화면(View)을 연결
            rtmpService?.initCamera(openGlView)

            // 만약 이미 서비스에서 송출 중이라면 버튼 텍스트 변경
            if (rtmpService?.getRtmpCamera()?.isStreaming == true) {
                btnStartStop.text = getString(R.string.stop_stream)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publish)

        checkPermissions()
        requestIgnoreBatteryOptimizations()
        openGlView = findViewById(R.id.surfaceView)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnGoToPlay = findViewById(R.id.btnGoToPlay)

        // 서비스 시작 및 바인딩
        val intent = Intent(this, RtmpService::class.java)
        startService(intent) // 앱이 꺼져도 서비스가 살 수 있게 startService 호출
        bindService(intent, connection, BIND_AUTO_CREATE)

        btnStartStop.setOnClickListener {
            val service = rtmpService ?: return@setOnClickListener
            val camera = service.getRtmpCamera() ?: return@setOnClickListener

            if (!camera.isStreaming) {
                if (service.startStream(rtmpUrl)) {
                    openGlView.setBackgroundColor(Color.TRANSPARENT)
                    btnStartStop.text = getString(R.string.stop_stream)
                }
            } else {
                service.stopStream()
                openGlView.setBackgroundColor(Color.BLACK)
                btnStartStop.text = getString(R.string.start_stream)
            }
        }

        btnGoToPlay.setOnClickListener {
            val service = rtmpService ?: return@setOnClickListener
//            // 스트리밍 중이라면 안전하게 종료하고 이동하는 것이 좋습니다.
//            service.stopStream()
//            openGlView.setBackgroundColor(Color.BLACK)
//            btnStartStop.text = getString(R.string.start_stream)

            // PlayActivity로 이동하는 의도(Intent) 생성
//            val intent = Intent(this, PlayActivity::class.java)
//            startActivity(intent)
            service.switchCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            // 화면이 꺼질 때 미리보기만 해제 (송출은 서비스에서 계속됨)
            rtmpService?.getRtmpCamera()?.replaceView(this) // Context만 전달하여 View 연결 해제
            unbindService(connection)
            isBound = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 화면 회전 시 OpenGlView의 크기를 다시 계산하도록 알림
        // val surfaceView = findViewById<OpenGlView>(R.id.surfaceView)

        // 현재 기기의 방향에 따라 인코더의 회전 값을 변경할 수 있습니다.
        // 하지만 대부분의 RTMP 라이브러리는 스트림 도중 해상도 변경(가로/세로 전환)을
        // 서버가 허용하지 않는 경우가 많으므로, 미리보기 화면만 맞추는 것이 안정적입니다.
    }

    // 간단한 권한 체크 함수
    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            // 권한이 없으면 사용자에게 요청
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    // 사용자가 권한 요청 팝업에서 응답했을 때 호출되는 콜백
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "모든 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "권한이 거부되었습니다. 앱 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                finish() // 권한이 없으면 앱 종료 혹은 기능 제한
            }
        }
    }
}