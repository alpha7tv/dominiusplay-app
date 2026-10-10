package top.dominiusplay.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.setBackgroundColor(Ui.BG)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.addView(Ui.logoView(this, 150))
        val bar = ProgressBar(this)
        col.addView(bar, Ui.vlp(this, 24, 0, Ui.WRAP, Ui.WRAP))
        root.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        setContentView(root)
        lifecycleScope.launch { boot() }
    }

    private suspend fun boot() {
        if (Prefs.token.isEmpty()) {
            goActivation(null)
            return
        }
        Session.load()
        val res = withContext(Dispatchers.IO) { PanelApi.refresh() }
        val json = res.json
        when {
            json != null && json.optBoolean("ok") -> {
                Session.save(json)
                goHome()
            }
            json != null && (json.optString("error") == "invalid" || json.optString("error") == "blocked") -> {
                Session.clear()
                goActivation(json.optString("message"))
            }
            Session.current != null -> goHome()
            else -> goActivation("Sem conexão. Verifique a internet e abra o app de novo.")
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun goActivation(message: String?) {
        val i = Intent(this, ActivationActivity::class.java)
        if (!message.isNullOrEmpty()) i.putExtra("msg", message)
        startActivity(i)
        finish()
    }
}
