package com.example.bsrtmp

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

class PlayActivity : AppCompatActivity() {
    private val TAG = "PlayActivity"
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var tvPlayStatus: TextView

    // 상태 업데이트를 위한 핸들러
    private val statsHandler = Handler(Looper.getMainLooper())
    private val updateStatsRunnable = object : Runnable {
        override fun run() {
            updatePlayStats()
            statsHandler.postDelayed(this, 1000) // 1초마다 업데이트
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        playerView = findViewById(R.id.playerView)
        tvPlayStatus = findViewById(R.id.tvPlayStatus)
        val btnRotate = findViewById<ImageButton>(R.id.btnRotate)

        btnRotate?.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        initializePlayer()
    }

    private var lastFrameCount = 0

    @UnstableApi
    private fun updatePlayStats() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_READY) {
            val format = p.videoFormat
            val counters = p.videoDecoderCounters // 디코더 카운터 참조

            if (format != null && counters != null) {
                // 실제 렌더링된 프레임 차이로 FPS 계산
                val currentFrameCount = counters.renderedOutputBufferCount
                val fps = currentFrameCount - lastFrameCount
                lastFrameCount = currentFrameCount

                val bitrate = format.bitrate
                val bitrateStr = if (bitrate > 0) "${bitrate / 1000} kbps" else "--- kbps"

                val status = "PLAY | ${format.width}x${format.height} | $fps FPS | $bitrateStr"
                tvPlayStatus.text = status
            }
        }
    }


    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        val pref = getSharedPreferences("DCCL_CONFIG", MODE_PRIVATE)
        val savedUrl = pref.getString("play_url", "")

        if (!savedUrl.isNullOrEmpty()) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2000, 5000, 1000, 1000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val dataSourceFactory = RtmpDataSource.Factory()
            val mediaItem = MediaItem.Builder()
                .setUri(savedUrl)
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(1000).build())
                .build()

            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)

            player = ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true

                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            tvPlayStatus.text = "Error: ${error.message}"
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                statsHandler.post(updateStatsRunnable)
                            } else if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                                statsHandler.removeCallbacks(updateStatsRunnable)
                            }
                        }
                    })
                }
            playerView.player = player
        }
    }

    override fun onStop() {
        super.onStop()
        statsHandler.removeCallbacks(updateStatsRunnable)
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        statsHandler.removeCallbacks(updateStatsRunnable)
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let {
            it.release()
            player = null
        }
    }
}