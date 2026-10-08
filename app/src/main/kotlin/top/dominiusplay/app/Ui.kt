package top.dominiusplay.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Visual do Dominius Play: fundo escuro, laranja/amarelo/rosa, cantos arredondados e foco bem visível para controle remoto. */
object Ui {
    val BG = Color.parseColor("#070A12")
    val CARD = Color.parseColor("#0F1628")
    val CARD2 = Color.parseColor("#151E36")
    val BORDER = Color.parseColor("#1E2842")
    val TEXT = Color.parseColor("#EAEEF7")
    val MUTED = Color.parseColor("#8E98B0")
    val ORANGE = Color.parseColor("#FF7A2F")
    val YELLOW = Color.parseColor("#FFC94D")
    val PINK = Color.parseColor("#FF4D6D")
    val GREEN = Color.parseColor("#25D366")
    val DARK_TEXT = Color.parseColor("#1A0E05")

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun isNarrow(ctx: Context): Boolean = ctx.resources.configuration.screenWidthDp < 600

    fun shape(ctx: Context, fill: Int, radiusDp: Int, strokeColor: Int = Color.TRANSPARENT, strokeDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.setColor(fill)
        g.cornerRadius = dp(ctx, radiusDp).toFloat()
        if (strokeDp > 0) g.setStroke(dp(ctx, strokeDp), strokeColor)
        return g
    }

    /** Fundo de item selecionável: borda laranja quando recebe o foco do controle remoto. */
    fun focusable(ctx: Context, fill: Int = CARD, radiusDp: Int = 14): StateListDrawable {
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_focused), shape(ctx, CARD2, radiusDp, ORANGE, 3))
        s.addState(intArrayOf(android.R.attr.state_pressed), shape(ctx, CARD2, radiusDp, YELLOW, 2))
        s.addState(intArrayOf(), shape(ctx, fill, radiusDp, BORDER, 1))
        return s
    }

    private fun gradient(ctx: Context, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(YELLOW, ORANGE, PINK))
        g.cornerRadius = dp(ctx, radiusDp).toFloat()
        return g
    }

    private fun solid(ctx: Context, color: Int, radiusDp: Int): GradientDrawable = shape(ctx, color, radiusDp)

    private fun styledButton(ctx: Context, text: String, textColor: Int, normal: GradientDrawable, focused: GradientDrawable, onClick: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text
        t.setTextColor(textColor)
        t.textSize = 16f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setPadding(dp(ctx, 26), dp(ctx, 14), dp(ctx, 26), dp(ctx, 14))
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_focused), focused)
        sl.addState(intArrayOf(android.R.attr.state_pressed), focused)
        sl.addState(intArrayOf(), normal)
        t.background = sl
        t.isFocusable = true
        t.isClickable = true
        t.setOnClickListener { onClick() }
        return t
    }

    fun primaryButton(ctx: Context, text: String, onClick: () -> Unit): TextView {
        val focused = gradient(ctx, 14)
        focused.setStroke(dp(ctx, 3), Color.WHITE)
        return styledButton(ctx, text, DARK_TEXT, gradient(ctx, 14), focused, onClick)
    }

    fun whatsappButton(ctx: Context, text: String, onClick: () -> Unit): TextView {
        val focused = solid(ctx, GREEN, 14)
        focused.setStroke(dp(ctx, 3), Color.WHITE)
        return styledButton(ctx, text, Color.parseColor("#04230F"), solid(ctx, GREEN, 14), focused, onClick)
    }

    fun ghostButton(ctx: Context, text: String, onClick: () -> Unit): TextView {
        val normal = shape(ctx, Color.TRANSPARENT, 14, BORDER, 2)
        val focused = shape(ctx, CARD2, 14, ORANGE, 3)
        return styledButton(ctx, text, TEXT, normal, focused, onClick)
    }

    fun text(ctx: Context, value: String, sizeSp: Float, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = value
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    fun logo(ctx: Context, sizeSp: Float = 26f): TextView {
        val t = TextView(ctx)
        val s = SpannableString("DOMINIUS PLAY")
        s.setSpan(ForegroundColorSpan(ORANGE), 9, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        t.text = s
        t.setTextColor(Color.WHITE)
        t.textSize = sizeSp
        t.typeface = Typeface.DEFAULT_BOLD
        t.letterSpacing = 0.08f
        return t
    }

    fun vlp(ctx: Context, topDp: Int = 0, bottomDp: Int = 0, w: Int = MATCH, h: Int = WRAP): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(w, h)
        lp.setMargins(0, dp(ctx, topDp), 0, dp(ctx, bottomDp))
        return lp
    }
}
