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
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.SurfaceHolder


class PublishActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private val TAG = "PublishActivity"
    private var rtmpService: RtmpService? = null
    private var isBound = false
    private lateinit var openGlView: OpenGlView
    private lateinit var btnStartStop: Button
    private lateinit var btnGoToPlay: Button
    private lateinit var btnSwitch: Button
    private lateinit var btnSettings: Button

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected")
            val binder = service as RtmpService.LocalBinder
            rtmpService = binder.getService()
            isBound = true
            
            // 서비스 연결 시점에 Surface가 이미 유효하다면 카메라 초기화
            if (openGlView.holder.surface.isValid) {
                rtmpService?.initCamera(openGlView)
                updateUI()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
            isBound = false
        }
    }

    private fun updateUI() {
        val isStreaming = rtmpService?.getRtmpCamera()?.isStreaming ?: false
        Log.d(TAG, "updateUI - isStreaming: $isStreaming")
        if (isStreaming) {
            btnStartStop.text = getString(R.string.stop_stream)
            openGlView.setBackgroundColor(Color.TRANSPARENT)
        } else {
            btnStartStop.text = getString(R.string.start_stream)
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

        Log.d(TAG, "onCreate: 새 액티비티 인스턴스가 생성되었습니다.")
        Toast.makeText(this, "새 액티비티 생성됨 (onCreate)", Toast.LENGTH_SHORT).show()

        checkPermissions()
        requestIgnoreBatteryOptimizations()
        openGlView = findViewById(R.id.surfaceView)
        openGlView.holder.addCallback(this) // Callback 추가
        
        btnStartStop = findViewById(R.id.btnStartStop)
        btnGoToPlay = findViewById(R.id.btnGoToPlay)
        btnSwitch = findViewById(R.id.btnSwitch)
        btnSettings = findViewById(R.id.btnSettings)

        val intent = Intent(this, RtmpService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, BIND_AUTO_CREATE)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnStartStop.setOnClickListener {
            val service = rtmpService ?: return@setOnClickListener
            val camera = service.getRtmpCamera() ?: return@setOnClickListener

            if (!camera.isStreaming) {
                val pref = getSharedPreferences("DCCL_CONFIG", MODE_PRIVATE)
                val savedUrl = pref.getString("publish_url", "")

                if (!savedUrl.isNullOrEmpty()) {
                    if (service.startStream(savedUrl)) {
                        openGlView.setBackgroundColor(Color.TRANSPARENT)
                        btnStartStop.text = getString(R.string.stop_stream)
                    }
                } else {
                    Toast.makeText(this, "설정에서 송출 URL을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            } else {
                service.stopStream()
                openGlView.setBackgroundColor(Color.BLACK)
                btnStartStop.text = getString(R.string.start_stream)
            }
        }

        btnGoToPlay.setOnClickListener {
            startActivity(Intent(this, PlayActivity::class.java))
        }

        btnSwitch.setOnClickListener {
            rtmpService?.switchCamera()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG, "onNewIntent: 기존 액티비티가 재사용되었습니다.")
        Toast.makeText(this, "기존 액티비티 재사용됨 (onNewIntent)", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        if (!isFinishing) {
            rtmpService?.setBackgroundMode()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // Surface가 유효한 경우에만 setForegroundMode 호출하여 IllegalArgumentException 방지
        if (openGlView.holder.surface.isValid) {
            rtmpService?.let { service ->
                service.setForegroundMode(openGlView)
                updateUI()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    // SurfaceHolder.Callback 구현
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")
        rtmpService?.let { service ->
            service.setForegroundMode(openGlView)
            updateUI()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: $width x $height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
    }

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
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "모든 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "권한이 거부되었습니다.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
