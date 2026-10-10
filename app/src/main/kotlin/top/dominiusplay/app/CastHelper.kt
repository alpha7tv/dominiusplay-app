package top.dominiusplay.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Transmissão para a Smart TV (Chromecast, Android TV e Smart TVs com Chromecast embutido).
 * Em aparelhos sem Google Play Services (a maioria das TV Box) o botão simplesmente não aparece.
 */
object Cast {
    fun context(ctx: Context): CastContext? {
        return try {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) != ConnectionResult.SUCCESS) null
            else CastContext.getSharedInstance(ctx)
        } catch (e: Exception) {
            null
        }
    }

    /** Ícone de transmissão (TV com ondas). Devolve null quando o aparelho não suporta. */
    fun button(activity: Activity): View? {
        if (context(activity) == null) return null
        return try {
            val b = MediaRouteButton(activity)
            CastButtonFactory.setUpMediaRouteButton(activity, b)
            b.isFocusable = true
            b.background = Ui.focusable(activity, Color.TRANSPARENT, 12)
            b
        } catch (e: Exception) {
            null
        }
    }

    fun session(ctx: Context): CastSession? =
        try { context(ctx)?.sessionManager?.currentCastSession?.takeIf { it.isConnected } } catch (e: Exception) { null }

    fun remote(ctx: Context): RemoteMediaClient? = session(ctx)?.remoteMediaClient

    fun deviceName(ctx: Context): String = session(ctx)?.castDevice?.friendlyName ?: "a TV"

    fun endSession(ctx: Context) {
        try { context(ctx)?.sessionManager?.endCurrentSession(true) } catch (e: Exception) { /* já encerrada */ }
    }

    /** Endereço que a TV vai abrir: canais ao vivo em HLS (a TV não toca o fluxo direto), filmes e episódios no arquivo. */
    fun urlFor(item: PlayItem, live: Boolean): String =
        if (live) listOfNotNull(item.url, item.altUrl).firstOrNull { it.contains(".m3u8") } ?: item.url else item.url

    private fun typeFor(url: String, live: Boolean): String = when {
        live || url.contains(".m3u8") -> "application/x-mpegURL"
        url.substringBefore('?').endsWith(".mkv") -> "video/x-matroska"
        else -> "video/mp4"
    }

    /** Já está tocando este mesmo endereço na TV? (evita reiniciar quando a tela do app é recriada) */
    fun isLoaded(ctx: Context, item: PlayItem, live: Boolean): Boolean {
        val rc = remote(ctx) ?: return false
        return rc.mediaInfo?.contentId == urlFor(item, live) && (rc.isPlaying || rc.isBuffering || rc.isPaused)
    }

    fun load(ctx: Context, item: PlayItem, live: Boolean, positionMs: Long): Boolean {
        val rc = remote(ctx) ?: return false
        val url = urlFor(item, live)
        val md = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
        md.putString(MediaMetadata.KEY_TITLE, item.title)
        val info = MediaInfo.Builder(url)
            .setStreamType(if (live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(typeFor(url, live))
            .setMetadata(md)
            .build()
        rc.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(true)
                .setCurrentTime(if (live) 0L else positionMs)
                .build()
        )
        return true
    }
}

/** Escutador de sessão com tudo opcional: quem usa só implementa o que precisa. */
open class CastSessionAdapter : SessionManagerListener<CastSession> {
    override fun onSessionStarting(session: CastSession) {}
    override fun onSessionStarted(session: CastSession, sessionId: String) {}
    override fun onSessionStartFailed(session: CastSession, error: Int) {}
    override fun onSessionEnding(session: CastSession) {}
    override fun onSessionEnded(session: CastSession, error: Int) {}
    override fun onSessionResuming(session: CastSession, sessionId: String) {}
    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
    override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    override fun onSessionSuspended(session: CastSession, reason: Int) {}
}
