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
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Visual do Dominius Play: fundo escuro, laranja/amarelo/rosa, cantos arredondados e foco bem visível para controle remoto. */
object Ui {
    val BG = Color.parseColor(BuildConfig.C_BG)
    val CARD = Color.parseColor(BuildConfig.C_CARD)
    val CARD2 = Color.parseColor(BuildConfig.C_CARD2)
    val BORDER = Color.parseColor(BuildConfig.C_BORDER)
    val TEXT = Color.parseColor(BuildConfig.C_TEXT)
    val MUTED = Color.parseColor(BuildConfig.C_MUTED)
    val ORANGE = Color.parseColor(BuildConfig.C_ACCENT)
    val YELLOW = Color.parseColor(BuildConfig.C_YELLOW)
    val PINK = Color.parseColor(BuildConfig.C_PINK)
    val GREEN = Color.parseColor("#25D366")
    val DARK_TEXT = Color.parseColor(BuildConfig.C_ON_ACCENT)

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
        val s = SpannableString(BuildConfig.BRAND)
        s.setSpan(ForegroundColorSpan(ORANGE), minOf(BuildConfig.BRAND.indexOf(' ') + 1, BuildConfig.BRAND.length), BuildConfig.BRAND.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        t.text = s
        t.setTextColor(Color.WHITE)
        t.textSize = sizeSp
        t.typeface = Typeface.DEFAULT_BOLD
        t.letterSpacing = 0.08f
        return t
    }

    /** Logo Dominius Play (imagem). Na edição Express aparece "P2P EXPRESS" logo abaixo. */
    fun logoView(ctx: Context, heightDp: Int, centered: Boolean = true): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
        val img = ImageView(ctx)
        img.setImageResource(R.drawable.logo_full)
        img.adjustViewBounds = true
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        box.addView(img, LinearLayout.LayoutParams(WRAP, dp(ctx, heightDp)))
        if (BuildConfig.EXPRESS) {
            val t = text(ctx, "P2P EXPRESS", if (heightDp >= 100) 15f else 11f, YELLOW, true)
            t.letterSpacing = 0.2f
            box.addView(t, vlp(ctx, 2, 0, WRAP, WRAP))
        }
        return box
    }

    fun vlp(ctx: Context, topDp: Int = 0, bottomDp: Int = 0, w: Int = MATCH, h: Int = WRAP): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(w, h)
        lp.setMargins(0, dp(ctx, topDp), 0, dp(ctx, bottomDp))
        return lp
    }
}
