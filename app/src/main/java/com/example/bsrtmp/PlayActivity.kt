package com.example.bsrtmp

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

class PlayActivity : AppCompatActivity() {
    private val REQUEST_CODE_SCREEN = 1001
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var onClickListener: () -> Unit
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        playerView = findViewById(R.id.playerView)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnRotate = findViewById<Button>(R.id.btnRotate)
        val btnScreen = findViewById<Button>(R.id.btnScreen)

        btnRotate.setOnClickListener {
            // 현재 화면 방향 확인 후 반대로 전환
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE // 가로로 변경
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT  // 세로로 변경
            }
        }

        // 버튼 클릭 시 뒤로가기
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed() // 또는 finish()
        }
        initializePlayer()
    }
    
    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        // Media3에서는 StyledPlayerView 대신 PlayerView를 사용합니다.
        //val playerView = findViewById<PlayerView>(R.id.playerView)

        // player 준비 로직 내부
        val pref = getSharedPreferences("DCCL_CONFIG", MODE_PRIVATE)
        val savedUrl = pref.getString("play_url", "")

        if (!savedUrl.isNullOrEmpty()) {
            // 1. 버퍼 설정을 위한 LoadControl 정의 (최소 버퍼량 조절)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    500,   // 최소 버퍼 (기본 15000ms -> 0.5초로 변경)
                    1000,  // 최대 버퍼 (기본 50000ms -> 1초로 변경)
                    500,   // 재생 시작 전 필수 버퍼 (기본 2500ms -> 0.5초로 변경)
                    500    // 리버퍼링 후 재생 시작 전 필수 버퍼 (기본 5000ms -> 0.5초로 변경)
                )
                .build()

            val dataSourceFactory = RtmpDataSource.Factory()
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(savedUrl))

            // 2. 플레이어 생성 시 loadControl 적용
            player = ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true
                }

            playerView.player = player

        } else {
            Toast.makeText(this, "설정에서 재생 URL을 입력해주세요.", Toast.LENGTH_SHORT).show()
        }

    }


    // 재생 중 화면을 나가면 플레이어를 확실히 정지/해제해야 합니다.
    private fun releasePlayer() {
        player?.let {
            it.stop()
            it.release()
            player = null
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 화면이 회전되었을 때 라이브러리에 알림 (선택 사항)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 가로 모드 최적화 로직
        } else {
            // 세로 모드 최적화 로직
        }
    }
}