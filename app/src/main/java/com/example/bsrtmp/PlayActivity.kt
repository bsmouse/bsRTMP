package com.example.bsrtmp

import android.os.Bundle
import android.widget.Button
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

    private lateinit var onClickListener: () -> Unit
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    // 테스트할 RTMP 주소
    private val playUrl = "rtmp://192.168.0.116/stream/t1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        playerView = findViewById(R.id.playerView)
        val btnBack = findViewById<Button>(R.id.btnBack)

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
        .createMediaSource(MediaItem.fromUri(playUrl))

    // 2. 플레이어 생성 시 loadControl 적용
    player = ExoPlayer.Builder(this)
        .setLoadControl(loadControl)
        .build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }

    playerView.player = player
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
}