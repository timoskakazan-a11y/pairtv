package com.pairtv.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URLS = "extra_urls"
        const val EXTRA_SCALE = "extra_scale"
        const val EXTRA_PC_IP = "extra_pc_ip"
        const val EXTRA_PC_PORT = "extra_pc_port"
        const val EXTRA_TV_NAME = "extra_tv_name"

        private const val HEARTBEAT_INTERVAL_MS = 4000L
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var errorText: TextView

    private var pcIp: String = ""
    private var pcPort: Int = 8765
    private var tvName: String = "AndroidTV"

    private val bgExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var heartbeatRunning = false
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            val current = player?.currentMediaItem?.localConfiguration?.uri?.lastPathSegment ?: "—"
            bgExecutor.execute { PlaylistClient.sendHeartbeat(pcIp, pcPort, tvName, current) }
            if (heartbeatRunning) mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    @Volatile private var reloadListenerRunning = false
    private var reloadSocket: DatagramSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val urls = intent.getStringArrayListExtra(EXTRA_URLS)
        val scale = intent.getStringExtra(EXTRA_SCALE) ?: "fill"
        pcIp = intent.getStringExtra(EXTRA_PC_IP) ?: ""
        pcPort = intent.getIntExtra(EXTRA_PC_PORT, 8765)
        tvName = intent.getStringExtra(EXTRA_TV_NAME) ?: "AndroidTV"

        playerView = findViewById(R.id.playerView)
        errorText = findViewById(R.id.errorText)

        if (urls.isNullOrEmpty()) {
            showError("Нет видео для воспроизведения")
            return
        }

        applyScaleMode(scale)
        startPlayback(urls)
        startHeartbeat()
        startReloadListener()
    }

    private fun applyScaleMode(scale: String) {
        playerView.resizeMode = if (scale == "fit") {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    private fun startPlayback(urls: List<String>) {
        errorText.visibility = View.GONE

        val exoPlayer = player ?: ExoPlayer.Builder(this).build().also { player = it }
        playerView.player = exoPlayer

        val mediaItems = urls.map { MediaItem.fromUri(it) }
        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        exoPlayer.playWhenReady = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                showError("Ошибка воспроизведения: ${error.message}\nПроверьте, что ПК в сети и сервер запущен.")
            }
        })

        exoPlayer.prepare()
    }

    private fun showError(message: String) {
        errorText.visibility = View.VISIBLE
        errorText.text = message
    }

    private fun startHeartbeat() {
        heartbeatRunning = true
        mainHandler.post(heartbeatRunnable)
    }

    private fun stopHeartbeat() {
        heartbeatRunning = false
        mainHandler.removeCallbacks(heartbeatRunnable)
    }

    /** Слушает UDP-команду "обновить экран" от ПК и перезагружает плейлист без выхода из приложения. */
    private fun startReloadListener() {
        reloadListenerRunning = true
        bgExecutor.execute {
            try {
                val socket = DatagramSocket(null)
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress(Net.CONTROL_PORT))
                reloadSocket = socket
                val buffer = ByteArray(512)
                while (reloadListenerRunning) {
                    socket.soTimeout = 2000
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length)
                    if (text.startsWith(Net.RELOAD_MAGIC) && pcIp.isNotEmpty()) {
                        reloadFromPc()
                    }
                }
            } catch (_: Exception) {
                // порт занят или сеть недоступна — молча выходим, обычное обновление приложением всё ещё работает
            }
        }
    }

    private fun reloadFromPc() {
        val playlist = PlaylistClient.fetch(pcIp, pcPort) ?: return
        mainHandler.post {
            applyScaleMode(playlist.scale)
            val exoPlayer = player ?: return@post
            val currentUrl = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            val mediaItems = playlist.urls.map { MediaItem.fromUri(it) }
            exoPlayer.setMediaItems(mediaItems)
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            // Пытаемся продолжить с того же ролика, если он остался в новом плейлисте.
            val keepIndex = playlist.urls.indexOf(currentUrl)
            if (keepIndex >= 0) {
                exoPlayer.seekTo(keepIndex, 0)
            }
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
        }
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
        stopHeartbeat()
        reloadListenerRunning = false
        reloadSocket?.close()
        bgExecutor.shutdownNow()
        player?.release()
        player = null
        super.onDestroy()
    }
}
