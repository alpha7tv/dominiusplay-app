package top.dominiusplay.app

import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Edição Express: o servidor da lista redireciona cada canal para outro endereço (com chave). Descobrir esse endereço
 * antes de você clicar tira uma volta de rede da abertura do canal. Se o endereço antecipado falhar, o player volta ao normal.
 */
object Resolver {
    private class Hit(val url: String, val at: Long)

    private val cache = ConcurrentHashMap<String, Hit>()
    private val inflight = ConcurrentHashMap<String, Boolean>()
    private const val TTL_MS = 90_000L

    /** Endereço final já descoberto, ou null. Cada endereço é entregue uma única vez. */
    fun take(url: String): String? {
        if (!BuildConfig.EXPRESS) return null
        val h = cache.remove(url) ?: return null
        return if (System.currentTimeMillis() - h.at < TTL_MS) h.url else null
    }

    /** Descobre em segundo plano (só para o canal vizinho). Não abre vídeo: só lê o redirecionamento. */
    fun prefetch(url: String) {
        if (!BuildConfig.EXPRESS || cache.containsKey(url) || inflight.putIfAbsent(url, true) != null) return
        Thread {
            try {
                val req = Request.Builder().url(url).header("User-Agent", DominiusApp.USER_AGENT).header("Range", "bytes=0-0").build()
                DominiusApp.httpNoRedirect.newCall(req).execute().use { r ->
                    if (r.isRedirect) {
                        val loc = r.header("Location")
                        val abs = if (loc != null) r.request.url.resolve(loc)?.toString() else null
                        if (abs != null) cache[url] = Hit(abs, System.currentTimeMillis())
                    }
                }
            } catch (e: Exception) {
                // sem importância: o canal abre pelo caminho normal
            } finally {
                inflight.remove(url)
            }
        }.start()
    }

    fun clear() {
        cache.clear()
    }
}
