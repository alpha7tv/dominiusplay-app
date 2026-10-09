package top.dominiusplay.app

import org.json.JSONObject

/** Texto de um campo JSON, vazio quando ausente ou nulo. */
fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

data class Notice(
    val id: Int,
    val kind: String,
    val placement: String,
    val title: String,
    val body: String,
    val image: String?,
    val linkUrl: String?,
    val linkLabel: String
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val mandatory: Boolean,
    val notes: String
)

data class SessionData(
    val status: String,
    val clientName: String,
    val isTrial: Boolean,
    val secondsLeft: Long?,
    val fetchedAt: Long,
    val server: String,
    val username: String,
    val password: String,
    val appName: String,
    val supportWhatsapp: String,
    val expiredTitle: String,
    val expiredMessage: String,
    val notices: List<Notice>,
    val update: UpdateInfo?
) {
    val expired: Boolean get() = status == "expired"

    /** Segundos que faltam para vencer (null = sem vencimento). */
    fun secondsRemaining(): Long? {
        val left = secondsLeft ?: return null
        return left - (System.currentTimeMillis() - fetchedAt) / 1000
    }
}

/** Sessão atual do aparelho (última resposta do painel). */
object Session {
    var current: SessionData? = null

    fun load() {
        val raw = Prefs.payload
        if (raw.isEmpty()) return
        try {
            current = parse(JSONObject(raw), System.currentTimeMillis())
        } catch (e: Exception) {
            current = null
        }
    }

    fun save(json: JSONObject) {
        Prefs.payload = json.toString()
        current = parse(json, System.currentTimeMillis())
        val support = current?.supportWhatsapp.orEmpty()
        if (support.isNotEmpty()) Prefs.supportWhatsapp = support
    }

    fun clear() {
        Prefs.clearSession()
        current = null
        Resolver.clear()
        Xtream.clearCache()
    }

    fun parse(j: JSONObject, fetchedAt: Long): SessionData {
        val client = j.optJSONObject("client") ?: JSONObject()
        val cfg = j.optJSONObject("config") ?: JSONObject()
        val xt = j.optJSONObject("xtream")
        val upd = j.optJSONObject("update")

        val notices = ArrayList<Notice>()
        val arr = j.optJSONArray("notices")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val image = o.str("image")
                val link = o.str("link_url")
                val label = o.str("link_label")
                notices.add(
                    Notice(
                        o.optInt("id"),
                        o.str("kind"),
                        o.str("placement"),
                        o.str("title"),
                        o.str("body"),
                        if (image.isEmpty()) null else image,
                        if (link.isEmpty()) null else link,
                        if (label.isEmpty()) "Saiba mais" else label
                    )
                )
            }
        }

        var update: UpdateInfo? = null
        if (upd != null) {
            update = UpdateInfo(
                upd.optInt("version_code"),
                upd.str("version_name"),
                upd.str("url"),
                upd.optBoolean("mandatory"),
                upd.str("notes")
            )
        }

        return SessionData(
            status = j.str("status").ifEmpty { "active" },
            clientName = client.str("name"),
            isTrial = client.optBoolean("is_trial"),
            secondsLeft = if (client.isNull("seconds_left")) null else client.optLong("seconds_left"),
            fetchedAt = fetchedAt,
            server = xt?.str("server") ?: "",
            username = xt?.str("username") ?: "",
            password = xt?.str("password") ?: "",
            appName = cfg.str("app_name").ifEmpty { "Dominius Play" },
            supportWhatsapp = cfg.str("support_whatsapp"),
            expiredTitle = cfg.str("expired_title").ifEmpty { "Seu acesso venceu" },
            expiredMessage = cfg.str("expired_message").ifEmpty { "Renove o seu acesso com o seu fornecedor para voltar a assistir." },
            notices = notices,
            update = update
        )
    }
}

/** Item de lista (canal, filme ou série). */
data class Entry(val id: String, val title: String, val image: String, val ext: String, val epg: String = "")

/** Destaque da tela inicial: filme ou série recém-adicionado. */
data class Featured(val entry: Entry, val series: Boolean, val ts: Long)

data class Category(val id: String, val name: String)

data class Episode(val id: String, val title: String, val season: Int, val number: Int, val ext: String)

data class PlayItem(val title: String, val url: String, val altUrl: String?, val entry: Entry? = null)

/** Fila de reprodução compartilhada entre a lista e o player (zapping de canais, próximos episódios). */
object PlayQueue {
    var items: List<PlayItem> = emptyList()
    var index: Int = 0
    var live: Boolean = false
}
