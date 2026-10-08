package top.dominiusplay.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private var lastRefresh = 0L
    private var shownPopup = false
    private var shownUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Ui.BG)
        scroll.isFillViewport = true
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(this, 24)
        content.setPadding(pad, pad, pad, pad)
        scroll.addView(content, ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP))
        setContentView(scroll)

        if (Session.current == null) Session.load()
        if (Session.current == null) {
            goActivation(null)
            return
        }
        render()
        lastRefresh = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        if (Session.current != null && System.currentTimeMillis() - lastRefresh > 120_000) refresh()
    }

    private fun goActivation(message: String?) {
        val i = Intent(this, ActivationActivity::class.java)
        if (!message.isNullOrEmpty()) i.putExtra("msg", message)
        startActivity(i)
        finish()
    }

    private fun refresh() {
        lastRefresh = System.currentTimeMillis()
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { PanelApi.refresh() }
            val json = res.json ?: return@launch
            if (json.optBoolean("ok")) {
                Session.save(json)
                render()
            } else if (json.optString("error") == "invalid" || json.optString("error") == "blocked") {
                Session.clear()
                goActivation(json.optString("message"))
            }
        }
    }

    private fun statusText(s: SessionData): String {
        val left = s.secondsRemaining() ?: return if (s.isTrial) "Teste ativo" else "Acesso ativo"
        if (left <= 0) return "Acesso vencido"
        if (left < 86400) {
            val h = left / 3600
            val m = (left % 3600) / 60
            return "Restam " + h + "h" + String.format(Locale.US, "%02d", m) + "min"
        }
        val fmt = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        return "Acesso até " + fmt.format(Date(System.currentTimeMillis() + left * 1000))
    }

    private fun render() {
        val s = Session.current ?: return
        content.removeAllViews()
        content.addView(header(s))
        val upd = s.update
        if (Updater.available(upd)) content.addView(Updater.banner(this, upd!!), Ui.vlp(this, 16, 0))
        if (s.expired) {
            content.addView(expiredView(s), Ui.vlp(this, 24, 0))
            maybeShowUpdate(s)
            return
        }
        for (n in s.notices) {
            if (n.placement == "home") content.addView(NoticeUi.banner(this, n), Ui.vlp(this, 16, 0))
        }
        content.addView(cards(), Ui.vlp(this, 20, 0))
        content.addView(FeaturedRail.build(this, s), Ui.vlp(this, 20, 0))
        maybeShowPopup(s)
        maybeShowUpdate(s)
    }

    private fun header(s: SessionData): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(Ui.logo(this, 24f), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val st = Ui.text(this, statusText(s), 13f, Ui.YELLOW, true)
        st.setPadding(0, 0, Ui.dp(this, 12), 0)
        row.addView(st)
        val cfg = Ui.ghostButton(this, "Configurações") { showSettings() }
        cfg.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8))
        cfg.textSize = 13f
        row.addView(cfg)
        return row
    }

    private fun bigCard(emoji: String, title: String, subtitle: String, mode: String, weighted: Boolean): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24))
        c.background = Ui.focusable(this, Ui.CARD, 18)
        c.isFocusable = true
        c.isClickable = true
        c.minimumHeight = Ui.dp(this, 150)
        c.addView(Ui.text(this, emoji, 44f))
        val t = Ui.text(this, title, 20f, Ui.TEXT, true)
        t.gravity = Gravity.CENTER
        c.addView(t, Ui.vlp(this, 8, 0))
        val sub = Ui.text(this, subtitle, 13f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        c.addView(sub, Ui.vlp(this, 2, 0))
        c.setOnClickListener {
            startActivity(Intent(this, BrowseActivity::class.java).putExtra("mode", mode))
        }
        val lp = if (weighted) LinearLayout.LayoutParams(0, Ui.WRAP, 1f) else LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP)
        lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6))
        c.layoutParams = lp
        return c
    }

    private fun cards(): View {
        val narrow = Ui.isNarrow(this)
        val box = LinearLayout(this)
        box.orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val weighted = !narrow
        val live = bigCard("📺", "Canais ao vivo", "TV, esportes, notícias e mais", "live", weighted)
        box.addView(live)
        box.addView(bigCard("🎬", "Filmes", "Lançamentos e clássicos", "movies", weighted))
        box.addView(bigCard("🍿", "Séries", "Temporadas e episódios", "series", weighted))
        live.requestFocus()
        return box
    }

    private fun expiredView(s: SessionData): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        val pad = Ui.dp(this, 24)
        card.setPadding(pad, pad, pad, pad)
        card.background = Ui.shape(this, Ui.CARD, 18, Ui.PINK, 2)
        card.addView(Ui.text(this, "⏰", 44f))
        val t = Ui.text(this, s.expiredTitle, 22f, Ui.TEXT, true)
        t.gravity = Gravity.CENTER
        card.addView(t, Ui.vlp(this, 8, 0))
        val m = Ui.text(this, s.expiredMessage, 15f, Ui.MUTED)
        m.gravity = Gravity.CENTER
        card.addView(m, Ui.vlp(this, 8, 16))
        val other = Ui.ghostButton(this, "Entrar com outra conta") {
            Session.clear()
            goActivation(null)
        }
        card.addView(other, Ui.vlp(this, 4, 0, Ui.WRAP, Ui.WRAP))
        other.requestFocus()
        return card
    }

    private fun maybeShowPopup(s: SessionData) {
        if (shownPopup) return
        val n = s.notices.firstOrNull { it.placement == "popup" } ?: return
        shownPopup = true
        NoticeUi.popup(this, n)
    }

    private fun maybeShowUpdate(s: SessionData) {
        val u = s.update ?: return
        if (!Updater.available(u) || shownUpdate) return
        shownUpdate = true
        Updater.dialog(this, u)
    }

    private fun showSettings() {
        val s = Session.current ?: return
        val info = "Cliente: " + s.clientName + "\n" + statusText(s) + "\nVersão do app: " + BuildConfig.VERSION_NAME
        AlertDialog.Builder(this)
            .setTitle("Configurações")
            .setMessage(info)
            .setPositiveButton("Fechar", null)
            .setNegativeButton("Sair deste aparelho") { _, _ ->
                Session.clear()
                goActivation(null)
            }
            .show()
    }
}
