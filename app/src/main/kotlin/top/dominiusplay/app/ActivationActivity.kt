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
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var loginBox: LinearLayout
    private lateinit var codeBox: LinearLayout
    private lateinit var sub: TextView
    private lateinit var title: TextView
    private lateinit var toggle: TextView
    private var codeMode = false
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
        title = Ui.text(this, "Entre com o seu acesso", 22f, Ui.TEXT, true)
        col.addView(title, Ui.vlp(this, 28, 6))
        sub = Ui.text(this, "Digite o usuário e a senha que você recebeu no e-mail do teste ou pelo WhatsApp.", 14f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, Ui.vlp(this, 0, 18))
        val boxWidth = if (Ui.isNarrow(this)) Ui.MATCH else Ui.dp(this, 420)

        fun field(hint: String, password: Boolean): EditText {
            val e = EditText(this)
            e.inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            e.hint = hint
            e.setHintTextColor(Ui.MUTED)
            e.setTextColor(Ui.TEXT)
            e.textSize = 18f
            e.setSingleLine(true)
            e.background = Ui.shape(this, Ui.CARD, 14, Ui.BORDER, 2)
            e.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14))
            return e
        }
        loginBox = LinearLayout(this)
        loginBox.orientation = LinearLayout.VERTICAL
        userInput = field("Usuário", false)
        userInput.imeOptions = EditorInfo.IME_ACTION_NEXT
        passInput = field("Senha", true)
        passInput.imeOptions = EditorInfo.IME_ACTION_DONE
        passInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        loginBox.addView(userInput, Ui.vlp(this, 0, 0, Ui.MATCH, Ui.WRAP))
        loginBox.addView(passInput, Ui.vlp(this, 12, 0, Ui.MATCH, Ui.WRAP))
        col.addView(loginBox, Ui.vlp(this, 0, 0, boxWidth, Ui.WRAP))

        codeBox = LinearLayout(this)
        codeBox.orientation = LinearLayout.VERTICAL
        codeBox.visibility = android.view.View.GONE
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
                submit()
                true
            } else {
                false
            }
        }
        codeBox.addView(input, Ui.vlp(this, 0, 0, Ui.MATCH, Ui.WRAP))
        col.addView(codeBox, Ui.vlp(this, 0, 0, boxWidth, Ui.WRAP))

        message = Ui.text(this, "", 14f, Ui.MUTED)
        message.gravity = Gravity.CENTER
        col.addView(message, Ui.vlp(this, 12, 0, boxWidth, Ui.WRAP))

        button = Ui.primaryButton(this, "Entrar") { submit() }
        col.addView(button, Ui.vlp(this, 16, 0, boxWidth, Ui.WRAP))

        toggle = Ui.ghostButton(this, "Tenho um código de ativação") { setMode(!codeMode) }
        col.addView(toggle, Ui.vlp(this, 12, 0, boxWidth, Ui.WRAP))

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
        userInput.requestFocus()
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

    private fun setMode(code: Boolean) {
        codeMode = code
        loginBox.visibility = if (code) android.view.View.GONE else android.view.View.VISIBLE
        codeBox.visibility = if (code) android.view.View.VISIBLE else android.view.View.GONE
        title.text = if (code) "Ative o seu acesso" else "Entre com o seu acesso"
        sub.text = if (code) "Digite o código de 8 números que você recebeu no e-mail do teste ou pelo WhatsApp."
        else "Digite o usuário e a senha que você recebeu no e-mail do teste ou pelo WhatsApp."
        button.text = if (code) "Ativar" else "Entrar"
        toggle.text = if (code) "Entrar com usuário e senha" else "Tenho um código de ativação"
        setMessage("", false)
        (if (code) input else userInput).requestFocus()
    }

    private fun submit() {
        if (codeMode) activate() else login()
    }

    private fun login() {
        if (busy) return
        val user = userInput.text.toString().trim()
        val pass = passInput.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            setMessage("Digite o usuário e a senha.", true)
            return
        }
        busy = true
        button.alpha = 0.5f
        setMessage("Verificando…", false)
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { PanelApi.login(user, pass) }
            finishAuth(res)
        }
    }

    private fun finishAuth(res: PanelApi.Result) {
        busy = false
        button.alpha = 1f
        val json = res.json
        if (res.networkError) {
            setMessage("Sem conexão com a internet. Verifique a rede e tente de novo.", true)
        } else if (json != null && json.optBoolean("ok")) {
            Prefs.token = json.optString("token")
            Session.save(json)
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        } else {
            val m = json?.optString("message").orEmpty()
            setMessage(if (m.isEmpty()) "Não foi possível entrar. Tente novamente." else m, true)
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
            finishAuth(res)
        }
    }
}
