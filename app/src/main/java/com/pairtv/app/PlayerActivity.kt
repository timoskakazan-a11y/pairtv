package com.pairtv.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URLS = "extra_urls"
    }

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Не гасить экран и держать ТВ включённым, пока идёт показ.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val urls = intent.getStringArrayListExtra(EXTRA_URLS)
        val errorText = findViewById<TextView>(R.id.errorText)

        if (urls.isNullOrEmpty()) {
            errorText.visibility = View.VISIBLE
            errorText.text = "Нет видео для воспроизведения"
            return
        }

        val playerView = findViewById<PlayerView>(R.id.playerView)
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer

        val mediaItems = urls.map { MediaItem.fromUri(it) }
        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        exoPlayer.playWhenReady = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorText.visibility = View.VISIBLE
                errorText.text = "Ошибка воспроизведения: ${error.message}\nПроверьте, что ПК в сети и сервер запущен."
            }
        })

        exoPlayer.prepare()
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
