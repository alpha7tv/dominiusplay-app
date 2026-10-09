package top.dominiusplay.app

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.net.URLEncoder

/** Cliente da API Xtream (player_api.php) do servidor da lista do cliente. */
class Xtream(server: String, private val user: String, private val pass: String) {
    private val base = server.trim().trimEnd('/')

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String, extra: String = ""): String =
        "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=$action$extra"

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", DominiusApp.USER_AGENT).build()
        return DominiusApp.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP " + r.code)
            r.body?.string() ?: ""
        }
    }

    /**
     * Express: listas e categorias ficam guardadas no aparelho. A tela abre na hora com o que está guardado; se o que está
     * guardado tem mais de 10 minutos, o app atualiza em segundo plano para a próxima vez.
     */
    private fun cachedFetch(url: String): String {
        val dir = DominiusApp.app.cacheDir
        val f = File(dir, CACHE_PREFIX + md5(url) + ".json")
        if (f.exists()) {
            val age = System.currentTimeMillis() - f.lastModified()
            if (age < 24 * 3_600_000L) {
                val text = f.readText()
                if (text.isNotEmpty()) {
                    if (age > 10 * 60_000L) {
                        Thread {
                            try {
                                val fresh = fetch(url)
                                if (fresh.trim().startsWith("[")) f.writeText(fresh)
                            } catch (e: Exception) {
                                // continua com o que está guardado
                            }
                        }.start()
                    }
                    return text
                }
            }
        }
        val fresh = fetch(url)
        if (fresh.trim().startsWith("[")) f.writeText(fresh)
        return fresh
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun array(url: String): JSONArray {
        val t = (if (BuildConfig.EXPRESS) cachedFetch(url) else fetch(url)).trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONArray()
    }

    private fun categories(action: String): List<Category> {
        val a = array(api(action))
        val out = ArrayList<Category>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Category(o.str("category_id"), o.str("category_name")))
        }
        return out
    }

    fun liveCategories(): List<Category> = categories("get_live_categories")
    fun movieCategories(): List<Category> = categories("get_vod_categories")
    fun seriesCategories(): List<Category> = categories("get_series_categories")

    fun liveStreams(category: String): List<Entry> {
        val a = array(api("get_live_streams", "&category_id=" + enc(category)))
        val out = ArrayList<Entry>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Entry(o.str("stream_id"), o.str("name"), o.str("stream_icon"), "", o.str("epg_channel_id")))
        }
        return out
    }

    fun movies(category: String): List<Entry> {
        val a = array(api("get_vod_streams", "&category_id=" + enc(category)))
        val out = ArrayList<Entry>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Entry(o.str("stream_id"), o.str("name"), o.str("stream_icon"), o.str("container_extension")))
        }
        return out
    }

    fun shows(category: String): List<Entry> {
        val a = array(api("get_series", "&category_id=" + enc(category)))
        val out = ArrayList<Entry>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Entry(o.str("series_id"), o.str("name"), o.str("cover"), ""))
        }
        return out
    }

    /** Últimos filmes e séries adicionados (mistura os mais recentes dos dois catálogos). */
    fun latest(limit: Int): List<Featured> {
        val out = ArrayList<Featured>()
        val movies = array(api("get_vod_streams"))
        val mv = ArrayList<Featured>()
        for (i in 0 until movies.length()) {
            val o = movies.optJSONObject(i) ?: continue
            val ts = o.optString("added", "0").toLongOrNull() ?: 0L
            mv.add(Featured(Entry(o.str("stream_id"), o.str("name"), o.str("stream_icon"), o.str("container_extension")), false, ts))
        }
        mv.sortByDescending { it.ts }
        out.addAll(mv.take(limit))
        try {
            val shows = array(api("get_series"))
            val sv = ArrayList<Featured>()
            for (i in 0 until shows.length()) {
                val o = shows.optJSONObject(i) ?: continue
                val ts = o.optString("last_modified", "0").toLongOrNull() ?: 0L
                sv.add(Featured(Entry(o.str("series_id"), o.str("name"), o.str("cover"), ""), true, ts))
            }
            sv.sortByDescending { it.ts }
            out.addAll(sv.take(limit))
        } catch (e: Exception) {
            // sem séries: mostra só os filmes
        }
        out.sortByDescending { it.ts }
        return out.filter { it.entry.title.isNotBlank() }.take(limit)
    }

    fun episodes(seriesId: String): List<Episode> {
        val text = fetch(api("get_series_info", "&series_id=" + enc(seriesId))).trim()
        val out = ArrayList<Episode>()
        if (!text.startsWith("{")) return out
        val root = JSONObject(text)
        val eps = root.opt("episodes")
        if (eps is JSONObject) {
            val keys = eps.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                collect(eps.optJSONArray(key), key.toIntOrNull() ?: 1, out)
            }
        } else if (eps is JSONArray) {
            for (i in 0 until eps.length()) {
                collect(eps.optJSONArray(i), i + 1, out)
            }
        }
        out.sortWith(compareBy<Episode>({ it.season }, { it.number }))
        return out
    }

    private fun collect(arr: JSONArray?, defaultSeason: Int, out: MutableList<Episode>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val season = o.optInt("season", defaultSeason)
            val number = o.optInt("episode_num", i + 1)
            val title = o.str("title").ifEmpty { "Episódio $number" }
            out.add(Episode(o.str("id"), title, if (season > 0) season else defaultSeason, number, o.str("container_extension")))
        }
    }

    companion object {
        private const val CACHE_PREFIX = "cat-"

        /** Apaga as listas guardadas (ao sair da conta). */
        fun clearCache() {
            try {
                DominiusApp.app.cacheDir.listFiles()?.forEach { if (it.name.startsWith(CACHE_PREFIX)) it.delete() }
            } catch (e: Exception) {
                // sem importância
            }
        }
    }

    fun liveUrl(id: String): String = "$base/live/${enc(user)}/${enc(pass)}/$id.m3u8"
    fun liveTsUrl(id: String): String = "$base/live/${enc(user)}/${enc(pass)}/$id.ts"
    fun movieUrl(id: String, ext: String): String = "$base/movie/${enc(user)}/${enc(pass)}/$id.${ext.ifEmpty { "mp4" }}"
    fun episodeUrl(id: String, ext: String): String = "$base/series/${enc(user)}/${enc(pass)}/$id.${ext.ifEmpty { "mp4" }}"
}
