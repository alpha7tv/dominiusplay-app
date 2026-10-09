package top.dominiusplay.app

import android.app.Application
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DominiusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        app = this
        Prefs.init(this)
    }

    /** Guarda o resultado do DNS por alguns minutos: abrir outro canal no mesmo servidor não espera uma nova consulta. */
    private object CachedDns : Dns {
        private class Entry(val list: List<InetAddress>, val at: Long)
        private val cache = ConcurrentHashMap<String, Entry>()

        override fun lookup(hostname: String): List<InetAddress> {
            val e = cache[hostname]
            if (e != null && System.currentTimeMillis() - e.at < 10 * 60_000) return e.list
            return try {
                val list = Dns.SYSTEM.lookup(hostname)
                cache[hostname] = Entry(list, System.currentTimeMillis())
                list
            } catch (ex: Exception) {
                if (e != null) e.list else throw ex // sem rede agora: usa o último endereço conhecido
            }
        }
    }

    companion object {
        lateinit var app: Application
            private set

        const val USER_AGENT = "DominiusPlay/1.0 (Android)"

        val http: OkHttpClient by lazy {
            val b = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true)
            if (BuildConfig.EXPRESS) {
                // Express: conexões mantidas abertas e DNS em cache, para trocar de canal sem recomeçar do zero
                b.connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES)).dns(CachedDns)
            }
            b.build()
        }

        /** Mesmo cliente (mesmas conexões), mas sem seguir redirecionamentos: serve para descobrir o endereço final de um canal. */
        val httpNoRedirect: OkHttpClient by lazy {
            http.newBuilder().followRedirects(false).followSslRedirects(false).callTimeout(8, TimeUnit.SECONDS).build()
        }
    }
}
