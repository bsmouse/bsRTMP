package com.example.bsrtmp

import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera1
import com.pedro.library.view.OpenGlView
import android.content.Intent // Intent를 사용하기 위해 추가 필요
import android.content.res.Configuration
import android.graphics.Color


class PublishActivity : AppCompatActivity(), ConnectChecker {

    private lateinit var rtmpCamera: RtmpCamera1
    private lateinit var btnStartStop: Button
    private lateinit var btnGoToPlay: Button // 새 버튼 변수 추가
    // 테스트용 주소 (실제 서버 주소로 변경 필요)
    private val rtmpUrl = "rtmp://192.168.0.116/stream/u2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publish)

        // 1. 카메라 권한 확인
        checkPermissions()

        val openGlView = findViewById<OpenGlView>(R.id.surfaceView)

        btnStartStop = findViewById(R.id.btnStartStop)
        btnGoToPlay = findViewById(R.id.btnGoToPlay) // 버튼 연결

        // 2. RtmpCamera1 초기화
        rtmpCamera = RtmpCamera1(openGlView, this)

        // 3. 시작/종료 버튼 리스너
        btnStartStop.setOnClickListener {
            if (!rtmpCamera.isStreaming) {
                // 다시 시작할 때
                openGlView.setBackgroundColor(Color.TRANSPARENT)

                // 방법 B: 프레임과 회전값까지 지정 (폭, 높이, FPS, 비트레이트, 회전값)
                // rotation은 보통 0(가로) 또는 90(세로)을 넣습니다.
                if (rtmpCamera.prepareVideo(640, 480, 10, 1000 * 1024, 90) && rtmpCamera.prepareAudio()) {
                    rtmpCamera.startStream(rtmpUrl)
                    btnStartStop.text = getString(R.string.stop_stream)
                } else {
                    Toast.makeText(this, "Error preparing stream", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 1. 스트리밍 중단
                rtmpCamera.stopStream()
                // stopStream 호출 후
                openGlView.setBackgroundColor(Color.BLACK)

                // 2. 카메라 미리보기 중단 (화면 멈춤 현상 해결의 핵심)
                rtmpCamera.stopPreview()

                // 3. 다시 깨끗한 상태로 미리보기 시작 (원할 경우)
                // 만약 아예 검은 화면으로 두고 싶다면 stopPreview()만 호출하세요.
                // 다시 카메라를 켜서 대기 상태로 만들고 싶다면 아래 줄을 추가하세요.
                // rtmpCamera.startPreview()

                btnStartStop.text = getString(R.string.start_stream)
            }
        }

        // 2. 재생 화면으로 이동 버튼
        btnGoToPlay.setOnClickListener {
            // 스트리밍 중이라면 안전하게 종료하고 이동하는 것이 좋습니다.
            if (rtmpCamera.isStreaming) {
                rtmpCamera.stopStream()
                rtmpCamera.stopPreview()
                btnStartStop.text = getString(R.string.start_stream)
            }

            // PlayActivity로 이동하는 의도(Intent) 생성
            val intent = Intent(this, PlayActivity::class.java)
            startActivity(intent)
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

    // --- ConnectChecker 콜백 구현 부분 ---

    // 새로 추가해야 하는 메서드입니다.
    override fun onConnectionStarted(url: String) {
        runOnUiThread {
            // 연결을 시도하기 시작했을 때 사용자에게 알림을 줄 수 있습니다.
            Toast.makeText(this, "연결 시작: $url", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionSuccess() {
        runOnUiThread { Toast.makeText(this, "연결 성공!", Toast.LENGTH_SHORT).show() }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "연결 실패: $reason", Toast.LENGTH_SHORT).show()
            rtmpCamera.stopStream()
            btnStartStop.text = getString(R.string.start_stream)
        }
    }

    // 나머지 메서드들도 동일하게 유지합니다.
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {
        runOnUiThread { Toast.makeText(this, "연결 끊김", Toast.LENGTH_SHORT).show() }
    }
    override fun onAuthError() {}
    override fun onAuthSuccess() {}
}