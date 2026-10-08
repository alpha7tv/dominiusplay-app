package top.dominiusplay.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Aviso de versão nova + download e instalação da atualização dentro do próprio app. */
object Updater {
    fun parse(upd: JSONObject?): UpdateInfo? {
        if (upd == null) return null
        return UpdateInfo(upd.optInt("version_code"), upd.str("version_name"), upd.str("url"), upd.optBoolean("mandatory"), upd.str("notes"))
    }

    fun available(u: UpdateInfo?): Boolean = u != null && u.versionCode > BuildConfig.VERSION_CODE && u.url.isNotEmpty()

    /** Cartão fixo "Nova versão disponível" com o botão Atualizar. */
    fun banner(activity: AppCompatActivity, u: UpdateInfo): View {
        val card = LinearLayout(activity)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.START
        val pad = Ui.dp(activity, 16)
        card.setPadding(pad, pad, pad, pad)
        card.background = Ui.shape(activity, Ui.CARD, 16, Ui.YELLOW, 2)
        card.addView(Ui.text(activity, "Nova versão disponível (" + u.versionName + ")", 17f, Ui.YELLOW, true))
        val what = if (u.notes.isNotEmpty()) u.notes else if (u.mandatory) "Esta atualização é necessária para continuar usando o app." else "Atualize para ter as últimas melhorias."
        card.addView(Ui.text(activity, what, 14f, Ui.MUTED), Ui.vlp(activity, 4, 0))
        card.addView(Ui.primaryButton(activity, "Atualizar") { start(activity, u) }, Ui.vlp(activity, 12, 0, Ui.WRAP, Ui.WRAP))
        return card
    }

    /** Janela de aviso (uma vez por abertura). Obrigatória não pode ser dispensada. */
    fun dialog(activity: AppCompatActivity, u: UpdateInfo) {
        val b = AlertDialog.Builder(activity)
            .setTitle("Nova versão disponível (" + u.versionName + ")")
            .setMessage(
                (if (u.mandatory) "Esta atualização é necessária para continuar usando o app." else "Atualize para ter as últimas melhorias.") +
                    (if (u.notes.isNotEmpty()) "\n\n" + u.notes else "")
            )
            .setPositiveButton("Atualizar", null)
        if (u.mandatory) b.setCancelable(false) else b.setNegativeButton("Depois", null)
        val d = b.create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { start(activity, u); if (!u.mandatory) d.dismiss() } }
        d.show()
    }

    fun start(activity: AppCompatActivity, u: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, "Permita instalar apps desta fonte e toque em Atualizar de novo.", Toast.LENGTH_LONG).show()
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
            } catch (e: Exception) {
                NoticeUi.open(activity, u.url)
            }
            return
        }
        val progress = AlertDialog.Builder(activity).setTitle("Atualizando").setMessage("Baixando a nova versão…").setCancelable(false).create()
        progress.show()
        activity.lifecycleScope.launch {
            val file = try {
                withContext(Dispatchers.IO) { download(activity, u.url) }
            } catch (e: Exception) {
                null
            }
            progress.dismiss()
            if (file == null) {
                Toast.makeText(activity, "Não foi possível baixar. Abrindo o link para baixar manualmente.", Toast.LENGTH_LONG).show()
                NoticeUi.open(activity, u.url)
                return@launch
            }
            try {
                install(activity, file)
            } catch (e: Exception) {
                NoticeUi.open(activity, u.url)
            }
        }
    }

    private fun download(ctx: Context, url: String): File {
        val dir = File(ctx.cacheDir, "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "dominiusplay.apk")
        val req = Request.Builder().url(url).header("User-Agent", DominiusApp.USER_AGENT).build()
        val client = DominiusApp.http.newBuilder().readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).callTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP " + r.code)
            val body = r.body ?: throw IOException("vazio")
            out.outputStream().use { o -> body.byteStream().copyTo(o) }
        }
        if (out.length() < 100_000) throw IOException("arquivo inválido")
        return out
    }

    private fun install(activity: AppCompatActivity, file: File) {
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, "application/vnd.android.package-archive")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(i)
    }
}
