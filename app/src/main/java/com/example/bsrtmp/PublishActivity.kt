package com.example.bsrtmp

import androidx.core.content.ContextCompat
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.pedro.library.view.OpenGlView
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
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
    private lateinit var btnSwitch: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var tvStatus: TextView

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected")
            val binder = service as RtmpService.LocalBinder
            val s = binder.getService()
            rtmpService = s
            isBound = true
            
            s.setStreamListener(object : RtmpService.StreamListener {
                override fun onStatusChanged(status: String) {
                    runOnUiThread {
                        tvStatus.text = status
                    }
                }
            })

            // 서비스 연결 시점에 설정값 동기화
            syncAudioConfig()

            if (openGlView.holder.surface.isValid) {
                s.initCamera(openGlView)
                updateUI()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
            isBound = false
            rtmpService = null
        }
    }

    private fun syncAudioConfig() {
        val pref = getSharedPreferences("DCCL_CONFIG", MODE_PRIVATE)
        val allowAudio = pref.getBoolean("enable_audio", true)
        rtmpService?.setAudioEnabled(allowAudio)
    }

    private fun updateUI() {
        val isStreaming = rtmpService?.getRtmpCamera()?.isStreaming ?: false
        Log.d(TAG, "updateUI - isStreaming: $isStreaming")
        if (isStreaming) {
            btnStartStop.text = getString(R.string.stop_stream)
            btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336")) // Red
        } else {
            btnStartStop.text = getString(R.string.start_stream)
            btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")) // Green
            tvStatus.text = "Status: Stopped"
        }
        openGlView.setBackgroundColor(Color.TRANSPARENT)
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

        Log.d(TAG, "onCreate")

        checkPermissions()
        requestIgnoreBatteryOptimizations()
        
        openGlView = findViewById(R.id.surfaceView)
        openGlView.holder.addCallback(this)
        
        tvStatus = findViewById(R.id.tvStatus)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnGoToPlay = findViewById(R.id.btnGoToPlay)
        btnSwitch = findViewById(R.id.btnSwitch)
        btnSettings = findViewById(R.id.btnSettings)

        startAndBindRtmpService()

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnStartStop.setOnClickListener {
            val service = rtmpService ?: run {
                startAndBindRtmpService()
                Toast.makeText(this, "서비스를 다시 시작합니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 스트리밍 시작 전 최신 설정값 확인
            syncAudioConfig()
            
            val camera = service.getRtmpCamera() ?: return@setOnClickListener

            if (!camera.isStreaming) {
                val pref = getSharedPreferences("DCCL_CONFIG", MODE_PRIVATE)
                val savedUrl = pref.getString("publish_url", "")
  
                if (!savedUrl.isNullOrEmpty()) {
                    if (service.startStream(savedUrl)) {
                        updateUI()
                    }
                } else {
                    Toast.makeText(this, "설정에서 송출 URL을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            } else {
                service.stopStream()
                updateUI()
            }
        }

        btnGoToPlay.setOnClickListener {
            startActivity(Intent(this, PlayActivity::class.java))
        }

        btnSwitch.setOnClickListener {
            rtmpService?.switchCamera()
        }
    }

    private fun startAndBindRtmpService() {
        val intent = Intent(this, RtmpService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        
        val pref = getSharedPreferences("DCCL_CONFIG", MODE_PRIVATE)
        val allowBackground = pref.getBoolean("allow_background", true)

        if (!isFinishing) {
            if (allowBackground) {
                rtmpService?.setBackgroundMode()
            } else {
                rtmpService?.stopStream()
                rtmpService?.getRtmpCamera()?.stopPreview()
                updateUI()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        
        if (rtmpService == null || !isBound) {
            startAndBindRtmpService()
        } else {
            // 화면 복귀 시 설정값 다시 동기화
            syncAudioConfig()
            if (openGlView.holder.surface.isValid) {
                rtmpService?.initCamera(openGlView)
                updateUI()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        if (isBound) {
            rtmpService?.setStreamListener(null)
            unbindService(connection)
            isBound = false
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")
        rtmpService?.let { service ->
            syncAudioConfig()
            service.initCamera(openGlView)
            updateUI()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

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
