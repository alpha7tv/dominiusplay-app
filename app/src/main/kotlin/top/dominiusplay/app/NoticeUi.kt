package top.dominiusplay.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import coil.load

/** Avisos e publicidade cadastrados no painel (tela inicial, janela ao abrir, tela de ativação). */
object NoticeUi {
    private val counted = HashSet<Int>()

    fun open(ctx: Context, url: String?) {
        if (url.isNullOrEmpty()) return
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Não foi possível abrir o link neste aparelho.", Toast.LENGTH_LONG).show()
        }
    }

    private fun countView(n: Notice) {
        if (counted.add(n.id)) PanelApi.noticeEvent(n.id, "view")
    }

    private fun content(ctx: Context, n: Notice, withButton: Boolean): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        if (n.image != null) {
            val img = ImageView(ctx)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.adjustViewBounds = true
            box.addView(img, Ui.vlp(ctx, 0, 10, Ui.MATCH, Ui.dp(ctx, 150)))
            img.load(n.image) { crossfade(true) }
        }
        box.addView(Ui.text(ctx, n.title, 18f, Ui.TEXT, true))
        if (n.body.isNotEmpty()) {
            box.addView(Ui.text(ctx, n.body, 14f, Ui.MUTED), Ui.vlp(ctx, 6, 0))
        }
        if (withButton && n.linkUrl != null) {
            val b = Ui.primaryButton(ctx, n.linkLabel) {
                PanelApi.noticeEvent(n.id, "click")
                open(ctx, n.linkUrl)
            }
            box.addView(b, Ui.vlp(ctx, 12, 0, Ui.WRAP, Ui.WRAP))
        }
        return box
    }

    /** Cartão de aviso para a tela inicial ou de ativação. */
    fun banner(ctx: Context, n: Notice): View {
        countView(n)
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.START
        val pad = Ui.dp(ctx, 16)
        card.setPadding(pad, pad, pad, pad)
        card.background = Ui.shape(ctx, Ui.CARD, 16, Ui.BORDER, 1)
        if (n.kind == "ad") {
            card.addView(Ui.text(ctx, "PUBLICIDADE", 10f, Ui.MUTED, true), Ui.vlp(ctx, 0, 6))
        }
        card.addView(content(ctx, n, true))
        return card
    }

    /** Janela de aviso ao abrir o app. */
    fun popup(ctx: Context, n: Notice) {
        countView(n)
        val pad = Ui.dp(ctx, 20)
        val box = content(ctx, n, false)
        box.setPadding(pad, pad, pad, 0)
        val b = AlertDialog.Builder(ctx).setView(box).setNegativeButton("Fechar", null)
        if (n.linkUrl != null) {
            b.setPositiveButton(n.linkLabel) { _, _ ->
                PanelApi.noticeEvent(n.id, "click")
                open(ctx, n.linkUrl)
            }
        }
        b.show()
    }
}
