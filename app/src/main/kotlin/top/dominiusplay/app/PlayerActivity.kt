package top.dominiusplay.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reprodução de canais (com troca por cima/baixo no controle), filmes e episódios. */
class PlayerActivity : BaseActivity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var overlay: TextView
    private lateinit var message: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }
    private var triedAlt = false
    private var usedFast = false

    // transmissão para a Smart TV
    private lateinit var castBar: LinearLayout
    private lateinit var castText: TextView
    private var casting = false
    private var castSessionListener: CastSessionAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (PlayQueue.items.isEmpty()) {
            finish()
            return
        }
        val root = FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.BLACK)
        view = PlayerView(this)
        view.useController = !PlayQueue.live
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        root.addView(view, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        if (PlayQueue.live) {
            // celular: toque mostra o canal e o guia; toque longo abre a programação do dia
            view.setOnClickListener { showTitle(titleWithGuide(currentItem())) }
            view.setOnLongClickListener { showGuide(); true }
        }

        overlay = Ui.text(this, "", 18f, android.graphics.Color.WHITE, true)
        overlay.background = Ui.shape(this, android.graphics.Color.parseColor("#B3070A12"), 12)
        overlay.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10))
        val olp = FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP or Gravity.START)
        olp.setMargins(Ui.dp(this, 24), Ui.dp(this, 24), 0, 0)
        root.addView(overlay, olp)

        message = Ui.text(this, "", 16f, android.graphics.Color.WHITE, true)
        message.gravity = Gravity.CENTER
        message.visibility = View.GONE
        message.background = Ui.shape(this, android.graphics.Color.parseColor("#D9070A12"), 14)
        message.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14))
        root.addView(message, FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER))

        // ícone de transmissão (aparece só em aparelhos com Google Play Services)
        Cast.button(this)?.let { b ->
            val lp = FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.TOP or Gravity.END)
            lp.setMargins(0, Ui.dp(this, 20), Ui.dp(this, 24), 0)
            root.addView(b, lp)
        }
        castBar = LinearLayout(this)
        castBar.orientation = LinearLayout.VERTICAL
        castBar.gravity = Gravity.CENTER
        castBar.visibility = View.GONE
        castBar.setBackgroundColor(android.graphics.Color.parseColor("#F2070A12"))
        castText = Ui.text(this, "", 22f, android.graphics.Color.WHITE, true)
        castText.gravity = Gravity.CENTER
        castBar.addView(castText)
        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.CENTER
        if (!PlayQueue.live) {
            buttons.addView(Ui.ghostButton(this, "Pausar / Continuar") {
                val rc = Cast.remote(this)
                if (rc != null) { if (rc.isPlaying) rc.pause() else rc.play() }
            }, Ui.vlp(this, 0, 0, Ui.WRAP, Ui.WRAP))
        }
        buttons.addView(Ui.primaryButton(this, "Parar transmissão") { Cast.endSession(this) }, Ui.vlp(this, 0, 0, Ui.WRAP, Ui.WRAP))
        castBar.addView(buttons, Ui.vlp(this, 16, 0, Ui.WRAP, Ui.WRAP))
        root.addView(castBar, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        if (PlayQueue.items.isEmpty()) return
        val data = OkHttpDataSource.Factory(DominiusApp.http).setUserAgent(DominiusApp.USER_AGENT)
        val builder = ExoPlayer.Builder(this)
        if (BuildConfig.EXPRESS) {
            // Express: começa a tocar com meio segundo de vídeo, procura menos no começo do fluxo e aceita o primeiro quadro-chave
            val extractors = DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
                .setTsExtractorTimestampSearchBytes(188 * 150)
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(data, extractors))
            builder.setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2500, 20_000, 500, 1000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
        } else {
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(data))
        }
        val p = builder.build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) message.visibility = View.GONE
                if (state == Player.STATE_ENDED && !PlayQueue.live) next(1)
            }
        })
        view.player = p
        player = p
        registerCastListener()
        play(false)
    }

    override fun onStop() {
        super.onStop()
        unregisterCastListener()
        handler.removeCallbacks(hideOverlay)
        view.player = null
        player?.release()
        player = null
    }

    private fun currentItem(): PlayItem = PlayQueue.items[PlayQueue.index]

    private fun play(useAlt: Boolean, raw: Boolean = false) {
        val item = currentItem()
        if (Cast.session(this) != null) {  // há uma TV conectada: o vídeo vai para ela, não para esta tela
            castCurrent()
            return
        }
        val url = if (useAlt && item.altUrl != null) item.altUrl else item.url
        triedAlt = useAlt
        // Express: usa o endereço final descoberto antes do clique (se houver); se falhar, onError() refaz pelo caminho normal
        val fast = if (!raw && PlayQueue.live) Resolver.take(url) else null
        usedFast = fast != null
        message.visibility = View.GONE
        player?.setMediaItem(MediaItem.fromUri(fast ?: url))
        player?.prepare()
        player?.playWhenReady = true
        if (PlayQueue.live) prefetchNeighbors()
        showTitle(titleWithGuide(item))
        val entry = item.entry
        if (PlayQueue.live && entry != null) {
            val index = PlayQueue.index
            lifecycleScope.launch {
                val changed = withContext(Dispatchers.IO) { Guide.load(listOf(entry)) }
                if (changed && index == PlayQueue.index && overlay.visibility == View.VISIBLE) overlay.text = titleWithGuide(item)
            }
        }
    }

    /** Nome do canal com "Agora / Depois" do guia, quando houver. */
    private fun titleWithGuide(item: PlayItem): String {
        val e = item.entry ?: return item.title
        val line = Guide.nowNextLine(Guide.cached(e.id))
        return if (line.isEmpty()) item.title else item.title + "\n" + line
    }

    /** Programação de hoje do canal atual. */
    private fun showGuide() {
        val e = currentItem().entry ?: return
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { Guide.channel(e) }
            if (isFinishing) return@launch
            if (list.isEmpty()) {
                showTitle(e.title + "\nProgramação não disponível para este canal.")
                return@launch
            }
            val t = System.currentTimeMillis() / 1000
            var current = 0
            val labels = list.mapIndexed { i, p ->
                val on = p.start <= t && p.end > t
                if (on) current = i
                Guide.time(p.start) + " - " + Guide.time(p.end) + (if (on) "   ▶ AGORA   " else "   ") + p.title
            }.toTypedArray()
            val dialog = AlertDialog.Builder(this@PlayerActivity)
                .setTitle("Programação - " + e.title)
                .setItems(labels) { _, which ->
                    val p = list[which]
                    if (p.desc.isNotEmpty()) {
                        AlertDialog.Builder(this@PlayerActivity).setTitle(p.title).setMessage(p.desc).setPositiveButton("OK", null).show()
                    }
                }
                .setNegativeButton("Fechar", null)
                .create()
            dialog.show()
            dialog.listView?.setSelection(current)
        }
    }

    private fun showTitle(text: String) {
        overlay.text = text
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, 5000)
    }

    // ------------------------------------------------------------------ transmissão para a Smart TV

    private fun registerCastListener() {
        val sm = Cast.context(this)?.sessionManager ?: return
        val l = object : CastSessionAdapter() {
            override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) { runOnUiThread { castCurrent() } }
            override fun onSessionResumed(session: com.google.android.gms.cast.framework.CastSession, wasSuspended: Boolean) { runOnUiThread { castCurrent() } }
            override fun onSessionEnded(session: com.google.android.gms.cast.framework.CastSession, error: Int) { runOnUiThread { backToDevice() } }
        }
        castSessionListener = l
        sm.addSessionManagerListener(l, com.google.android.gms.cast.framework.CastSession::class.java)
    }

    private fun unregisterCastListener() {
        val l = castSessionListener ?: return
        try {
            Cast.context(this)?.sessionManager?.removeSessionManagerListener(l, com.google.android.gms.cast.framework.CastSession::class.java)
        } catch (e: Exception) { /* sem importância */ }
        castSessionListener = null
    }

    /** Envia o canal/filme atual para a TV e para de tocar neste aparelho. */
    private fun castCurrent() {
        if (PlayQueue.items.isEmpty()) return
        val item = currentItem()
        val live = PlayQueue.live
        val pos = player?.currentPosition ?: 0L
        player?.stop()
        casting = true
        castBar.visibility = View.VISIBLE
        castText.text = "Transmitindo para " + Cast.deviceName(this) + "\n" + item.title
        if (!Cast.isLoaded(this, item, live)) {
            if (!Cast.load(this, item, live, pos)) {
                backToDevice()
                Toast.makeText(this, "Não foi possível transmitir. Tente de novo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** A transmissão acabou: volta a tocar neste aparelho. */
    private fun backToDevice() {
        if (!casting) return
        casting = false
        castBar.visibility = View.GONE
        if (!isFinishing && player != null) play(false)
    }

    /** Deixa pronto o endereço dos canais de cima e de baixo, para a troca de canal ser mais rápida. */
    private fun prefetchNeighbors() {
        val size = PlayQueue.items.size
        if (size < 2) return
        val i = PlayQueue.index
        for (d in intArrayOf(1, -1)) {
            val it = PlayQueue.items[((i + d) % size + size) % size]
            Resolver.prefetch(if (triedAlt && it.altUrl != null) it.altUrl else it.url)
        }
    }

    private fun onError() {
        val item = currentItem()
        if (usedFast) { // o endereço antecipado não funcionou: tenta o normal antes de desistir
            usedFast = false
            play(triedAlt, raw = true)
            return
        }
        if (!triedAlt && item.altUrl != null) {
            play(true)
            return
        }
        message.text = if (PlayQueue.live) "Não foi possível abrir este canal.\nPressione OK para tentar de novo ou troque de canal." else "Não foi possível reproduzir.\nPressione OK para tentar de novo."
        message.visibility = View.VISIBLE
    }

    private fun next(step: Int) {
        val size = PlayQueue.items.size
        if (size <= 1) return
        val n = PlayQueue.index + step
        if (!PlayQueue.live && (n < 0 || n >= size)) return
        PlayQueue.index = ((n % size) + size) % size
        play(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (message.visibility == View.VISIBLE && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            play(false)
            return true
        }
        if (PlayQueue.live) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    next(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    next(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    showTitle(titleWithGuide(currentItem()))
                    return true
                }
                KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU -> {
                    showGuide()
                    return true
                }
            }
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    next(1)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    next(-1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
