package top.dominiusplay.app

import android.app.Application
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DominiusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        app = this
        Prefs.init(this)
    }

    companion object {
        lateinit var app: Application
            private set

        const val USER_AGENT = "DominiusPlay/1.0 (Android)"

        val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
}
