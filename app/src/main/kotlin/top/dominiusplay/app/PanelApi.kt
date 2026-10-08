package top.dominiusplay.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** Conversa com a API do painel Dominius Play. */
object PanelApi {
    const val BASE = "https://dominiusplay.top"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    class Result(val httpStatus: Int, val json: JSONObject?, val networkError: Boolean)

    private fun call(request: Request): Result {
        return try {
            DominiusApp.http.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                val json = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    null
                }
                Result(resp.code, json, false)
            }
        } catch (e: IOException) {
            Result(0, null, true)
        }
    }

    private fun post(path: String, body: JSONObject): Result {
        val req = Request.Builder()
            .url(BASE + path)
            .header("User-Agent", DominiusApp.USER_AGENT)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        return call(req)
    }

    fun info(): Result {
        val req = Request.Builder().url("$BASE/api/app/info").header("User-Agent", DominiusApp.USER_AGENT).build()
        return call(req)
    }

    fun activate(code: String): Result {
        val b = JSONObject()
        b.put("code", code)
        b.put("device_key", Prefs.deviceKey)
        b.put("model", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
        b.put("app_version", BuildConfig.VERSION_NAME)
        return post("/api/app/ativar", b)
    }

    fun refresh(): Result {
        val b = JSONObject()
        b.put("token", Prefs.token)
        b.put("device_key", Prefs.deviceKey)
        b.put("app_version", BuildConfig.VERSION_NAME)
        return post("/api/app/atualizar", b)
    }

    /** Conta exibição ou clique de um aviso (sem bloquear a tela). */
    fun noticeEvent(id: Int, event: String) {
        Thread {
            try {
                val b = JSONObject()
                b.put("id", id)
                b.put("event", event)
                post("/api/app/aviso", b)
            } catch (e: Exception) {
                // sem importância
            }
        }.start()
    }
}
