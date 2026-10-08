package top.dominiusplay.app

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Dados guardados no aparelho: identificação do aparelho, sessão ativada e última resposta do painel. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("dominius", Context.MODE_PRIVATE)
        if (deviceKey.isEmpty()) {
            deviceKey = "dp-" + UUID.randomUUID().toString().replace("-", "")
        }
    }

    var deviceKey: String
        get() = sp.getString("device_key", "") ?: ""
        set(v) { sp.edit().putString("device_key", v).apply() }

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) { sp.edit().putString("token", v).apply() }

    var payload: String
        get() = sp.getString("payload", "") ?: ""
        set(v) { sp.edit().putString("payload", v).apply() }

    var supportWhatsapp: String
        get() = sp.getString("support", "5514988159045") ?: "5514988159045"
        set(v) { sp.edit().putString("support", v).apply() }

    fun clearSession() {
        sp.edit().remove("token").remove("payload").apply()
    }
}
