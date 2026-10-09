package top.dominiusplay.app

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Um programa do guia (horários em segundos Unix). */
data class Prog(val start: Long, val end: Long, val title: String, val desc: String)

/**
 * Guia de programação (EPG). Vem do servidor do Web Player, que baixa o guia, guarda e casa os canais
 * da lista pelo epg_channel_id ou pelo nome. Chamar fora da thread principal.
 */
object Guide {
    private const val BASE = "https://player.dominiusplay.top"
    private val nowNext = HashMap<String, List<Prog>>()
    private val fetchedAt = HashMap<String, Long>()
    private val hm = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    @Synchronized
    fun cached(id: String): List<Prog>? = nowNext[id]

    /** Busca "agora / a seguir" dos canais que ainda não estão no cache (até [max] canais). */
    fun load(entries: List<Entry>, max: Int = 200): Boolean {
        val t = System.currentTimeMillis()
        val need = synchronized(this) { entries.filter { t - (fetchedAt[it.id] ?: 0L) > 300_000 }.take(max) }
        if (need.isEmpty()) return false
        var changed = false
        for (chunk in need.chunked(40)) {
            val arr = JSONArray()
            for (e in chunk) arr.put(JSONArray().put(e.id).put(e.epg).put(e.title))
            val json = get("$BASE/api/guia/agora?c=" + URLEncoder.encode(arr.toString(), "UTF-8")) ?: return changed
            val ready = json.optBoolean("ready")
            val data = json.optJSONObject("data") ?: JSONObject()
            synchronized(this) {
                for (e in chunk) {
                    if (ready) fetchedAt[e.id] = t
                    val a = data.optJSONArray(e.id) ?: continue
                    nowNext[e.id] = parse(a)
                    changed = true
                }
            }
        }
        return changed
    }

    /** Programação de hoje de um canal. */
    fun channel(e: Entry): List<Prog> {
        val url = "$BASE/api/guia/canal?e=" + URLEncoder.encode(e.epg, "UTF-8") + "&n=" + URLEncoder.encode(e.title, "UTF-8")
        val json = get(url) ?: return emptyList()
        return parse(json.optJSONArray("data") ?: JSONArray())
    }

    fun time(sec: Long): String = synchronized(hm) { hm.format(Date(sec * 1000)) }

    /** Linha curta para a lista de canais: "Agora: Jornal Nacional". */
    fun nowLine(list: List<Prog>?): String {
        if (list.isNullOrEmpty()) return ""
        val t = System.currentTimeMillis() / 1000
        for (p in list) {
            if (p.start <= t && p.end > t) return "Agora: " + p.title
            if (p.start > t) return time(p.start) + "  " + p.title
        }
        return ""
    }

    /** Linha do player: "Agora: 20:30 Jornal Nacional  •  Depois: 21:15 Novela". */
    fun nowNextLine(list: List<Prog>?): String {
        if (list.isNullOrEmpty()) return ""
        val t = System.currentTimeMillis() / 1000
        val parts = ArrayList<String>()
        for (p in list) {
            if (p.end <= t || parts.size >= 2) continue
            parts.add((if (p.start <= t) "Agora: " else "Depois: ") + time(p.start) + " " + p.title)
        }
        return parts.joinToString("   •   ")
    }

    private fun parse(a: JSONArray): List<Prog> {
        val out = ArrayList<Prog>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Prog(o.optLong("s"), o.optLong("e"), o.str("t"), o.str("d")))
        }
        return out
    }

    private fun get(url: String): JSONObject? {
        return try {
            val req = Request.Builder().url(url).header("User-Agent", DominiusApp.USER_AGENT).build()
            DominiusApp.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val j = JSONObject(r.body?.string() ?: "")
                if (j.optBoolean("ok")) j else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
