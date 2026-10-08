package top.dominiusplay.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivationActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var message: TextView
    private lateinit var button: TextView
    private lateinit var noticesBox: LinearLayout
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Ui.BG)
        scroll.isFillViewport = true

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val pad = Ui.dp(this, 24)
        col.setPadding(pad, Ui.dp(this, 40), pad, pad)

        col.addView(Ui.logo(this, 34f))
        col.addView(Ui.text(this, "Ative o seu acesso", 22f, Ui.TEXT, true), Ui.vlp(this, 28, 6))
        val sub = Ui.text(this, "Digite o código de 8 números que você recebeu no e-mail do teste ou pelo WhatsApp.", 14f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, Ui.vlp(this, 0, 18))

        input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(8))
        input.hint = "00000000"
        input.setHintTextColor(Ui.MUTED)
        input.setTextColor(Ui.YELLOW)
        input.textSize = 30f
        input.gravity = Gravity.CENTER
        input.letterSpacing = 0.2f
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.background = Ui.shape(this, Ui.CARD, 14, Ui.BORDER, 2)
        input.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14))
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                activate()
                true
            } else {
                false
            }
        }
        val boxWidth = if (Ui.isNarrow(this)) Ui.MATCH else Ui.dp(this, 420)
        col.addView(input, Ui.vlp(this, 0, 0, boxWidth, Ui.WRAP))

        message = Ui.text(this, "", 14f, Ui.MUTED)
        message.gravity = Gravity.CENTER
        col.addView(message, Ui.vlp(this, 12, 0, boxWidth, Ui.WRAP))

        button = Ui.primaryButton(this, "Ativar") { activate() }
        col.addView(button, Ui.vlp(this, 16, 0, boxWidth, Ui.WRAP))

        val help = Ui.ghostButton(this, "Preciso de ajuda (WhatsApp)") {
            NoticeUi.whatsapp(this, "Olá! Preciso de ajuda para ativar o aplicativo Dominius Play.")
        }
        col.addView(help, Ui.vlp(this, 12, 0, boxWidth, Ui.WRAP))

        noticesBox = LinearLayout(this)
        noticesBox.orientation = LinearLayout.VERTICAL
        col.addView(noticesBox, Ui.vlp(this, 24, 0, boxWidth, Ui.WRAP))

        scroll.addView(col, ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP))
        setContentView(scroll)

        val msg = intent.getStringExtra("msg")
        if (!msg.isNullOrEmpty()) setMessage(msg, true)
        input.requestFocus()
        loadInfo()
    }

    private fun setMessage(text: String, error: Boolean) {
        message.text = text
        message.setTextColor(if (error) Ui.PINK else Ui.MUTED)
    }

    /** Marca, suporte e avisos da tela de ativação. */
    private fun loadInfo() {
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { PanelApi.info() }
            val json = res.json ?: return@launch
            if (!json.optBoolean("ok")) return@launch
            val cfg = json.optJSONObject("config")
            val support = cfg?.optString("support_whatsapp").orEmpty()
            if (support.isNotEmpty()) Prefs.supportWhatsapp = support
            val arr = json.optJSONArray("notices") ?: return@launch
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val image = o.str("image")
                val link = o.str("link_url")
                val label = o.str("link_label")
                val n = Notice(
                    o.optInt("id"), o.str("kind"), o.str("placement"), o.str("title"), o.str("body"),
                    if (image.isEmpty()) null else image,
                    if (link.isEmpty()) null else link,
                    if (label.isEmpty()) "Saiba mais" else label
                )
                noticesBox.addView(NoticeUi.banner(this@ActivationActivity, n), Ui.vlp(this@ActivationActivity, 0, 12))
            }
        }
    }

    private fun activate() {
        if (busy) return
        val code = input.text.toString().filter { it.isDigit() }
        if (code.length != 8) {
            setMessage("Digite os 8 números do código.", true)
            return
        }
        busy = true
        button.alpha = 0.5f
        setMessage("Verificando…", false)
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { PanelApi.activate(code) }
            busy = false
            button.alpha = 1f
            val json = res.json
            if (res.networkError) {
                setMessage("Sem conexão com a internet. Verifique a rede e tente de novo.", true)
            } else if (json != null && json.optBoolean("ok")) {
                Prefs.token = json.optString("token")
                Session.save(json)
                startActivity(Intent(this@ActivationActivity, HomeActivity::class.java))
                finish()
            } else {
                val m = json?.optString("message").orEmpty()
                setMessage(if (m.isEmpty()) "Não foi possível ativar. Tente novamente." else m, true)
            }
        }
    }
}
