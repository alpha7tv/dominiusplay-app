package top.dominiusplay.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Menu lateral de canais, por cima do vídeo em tela cheia. Abre com OK no controle (ou toque na tela), mostra o canal
 * no ar, as categorias e a lista de canais com o que está passando agora. O vídeo continua tocando por trás, e dá para
 * trocar de canal e de categoria sem sair do player. VOLTAR (ou ficar sem mexer por alguns segundos) fecha.
 */
class ChannelDrawer(
    private val act: AppCompatActivity,
    private val onChannelChosen: () -> Unit,
    private val onVisibility: (Boolean) -> Unit
) {
    val host = FrameLayout(act)
    var isOpen = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { close() }
    private val panel = LinearLayout(act)
    private val scrim = View(act)
    private val chips = RecyclerView(act)
    private val rows = RecyclerView(act)
    private val chipAdapter = ChipAdapter()
    private val rowAdapter = RowAdapter()
    private val nowName = Ui.text(act, "", 18f, Ui.TEXT, true)
    private val nowSub = Ui.text(act, "", 12.5f, Ui.MUTED)
    private val nowNext = Ui.text(act, "", 12f, Ui.MUTED)
    private val nowBar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
    private val status = Ui.text(act, "", 14f, Ui.MUTED)
    private val titleView = Ui.text(act, "Canais ao vivo", 17f, Ui.TEXT, true)

    private var xt: Xtream? = null
    private var cats: List<Category> = emptyList()
    private val lists = HashMap<String, List<Entry>>()
    private var shown: List<Entry> = emptyList()
    private var shownCat = ""
    private var loadingCats = false
    private val panelWidth: Int

    init {
        val dm = act.resources.displayMetrics
        panelWidth = if (Ui.isNarrow(act)) minOf((dm.widthPixels * 0.88f).toInt(), Ui.dp(act, 380)) else Ui.dp(act, 440)
        host.visibility = View.GONE
        host.isFocusable = false

        // sombra escura que sai da esquerda e deixa o menu legível sobre qualquer imagem
        val sg = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xE6000000.toInt(), 0x99000000.toInt(), 0x00000000))
        scrim.background = sg
        scrim.isClickable = true
        scrim.setOnClickListener { close() }
        host.addView(scrim, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        val r = Ui.dp(act, 22).toFloat()
        val bg = GradientDrawable()
        bg.setColor((Ui.BG and 0xFFFFFF) or (0xF2 shl 24))
        bg.cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        bg.setStroke(Ui.dp(act, 1), Ui.BORDER)
        panel.background = bg
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(Ui.dp(act, 16), Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 12))
        panel.isClickable = true // toque no menu não passa para o vídeo
        host.addView(panel, FrameLayout.LayoutParams(panelWidth, Ui.MATCH, Gravity.START))

        // cabeçalho
        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(act)
        logo.setImageResource(R.drawable.logo_wm)
        logo.adjustViewBounds = true
        head.addView(logo, LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(act, 26)))
        val tlp = LinearLayout.LayoutParams(0, Ui.WRAP, 1f)
        tlp.setMargins(Ui.dp(act, 12), 0, 0, 0)
        head.addView(titleView, tlp)
        val x = Ui.text(act, "✕", 18f, Ui.MUTED, true)
        x.gravity = Gravity.CENTER
        x.isClickable = true
        x.setOnClickListener { close() }
        head.addView(x, LinearLayout.LayoutParams(Ui.dp(act, 40), Ui.dp(act, 40)))
        panel.addView(head)

        // canal no ar agora
        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.shape(act, Ui.CARD2, 16, Ui.BORDER, 1)
        card.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12))
        val live = Ui.text(act, "●  NO AR", 11f, Ui.ORANGE, true)
        live.letterSpacing = 0.14f
        card.addView(live)
        nowName.maxLines = 1
        nowName.ellipsize = TextUtils.TruncateAt.END
        card.addView(nowName, Ui.vlp(act, 4, 0))
        nowSub.maxLines = 1
        nowSub.ellipsize = TextUtils.TruncateAt.END
        card.addView(nowSub, Ui.vlp(act, 2, 0))
        nowBar.max = 100
        nowBar.progressDrawable = progressDrawable()
        nowBar.visibility = View.GONE
        card.addView(nowBar, Ui.vlp(act, 8, 0, Ui.MATCH, Ui.dp(act, 4)))
        nowNext.maxLines = 1
        nowNext.ellipsize = TextUtils.TruncateAt.END
        card.addView(nowNext, Ui.vlp(act, 6, 0))
        panel.addView(card, Ui.vlp(act, 12, 10))

        // categorias
        chips.layoutManager = LinearLayoutManager(act, RecyclerView.HORIZONTAL, false)
        chips.adapter = chipAdapter
        chips.clipToPadding = false
        chips.overScrollMode = View.OVER_SCROLL_NEVER
        panel.addView(chips, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(act, 48)))

        status.gravity = Gravity.CENTER
        status.visibility = View.GONE
        panel.addView(status, Ui.vlp(act, 10, 0))

        // canais
        rows.layoutManager = LinearLayoutManager(act)
        rows.adapter = rowAdapter
        rows.clipToPadding = false
        rows.setPadding(0, Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4))
        rows.itemAnimator = null
        panel.addView(rows, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        if (!Ui.isNarrow(act)) {
            val hint = Ui.text(act, "▲ ▼ canais    ◀ ▶ categorias    OK assistir    VOLTAR fechar", 11f, Ui.MUTED)
            hint.gravity = Gravity.CENTER
            hint.maxLines = 1
            panel.addView(hint, Ui.vlp(act, 8, 0))
        }
    }

    private fun progressDrawable(): android.graphics.drawable.Drawable {
        val track = Ui.shape(act, Ui.BORDER, 2)
        val fill = android.graphics.drawable.ClipDrawable(Ui.shape(act, Ui.ORANGE, 2), Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
        val ld = android.graphics.drawable.LayerDrawable(arrayOf(track, fill))
        ld.setId(0, android.R.id.background)
        ld.setId(1, android.R.id.progress)
        return ld
    }

    // ------------------------------------------------------------------ abrir e fechar

    fun open() {
        if (isOpen || PlayQueue.items.isEmpty() || !PlayQueue.live) return
        isOpen = true
        if (xt == null) Session.current?.let { xt = Xtream(it.server, it.username, it.password) }
        // começa mostrando a categoria que está tocando (já está na memória)
        shownCat = PlayQueue.categoryId
        shown = PlayQueue.items.mapNotNull { it.entry }
        if (shownCat.isNotEmpty()) lists[shownCat] = shown
        titleView.text = PlayQueue.categoryName.ifEmpty { "Canais ao vivo" }
        chipAdapter.selectedId = shownCat
        chipAdapter.notifyDataSetChanged()
        rowAdapter.items = shown
        rowAdapter.notifyDataSetChanged()
        refreshNow()
        loadCategories()
        loadGuide(shown)

        host.visibility = View.VISIBLE
        panel.translationX = -panelWidth.toFloat()
        scrim.alpha = 0f
        panel.animate().translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        scrim.animate().alpha(1f).setDuration(220).start()
        onVisibility(true)
        focusPlaying()
        touch()
    }

    fun close(immediate: Boolean = false) {
        if (!isOpen) return
        isOpen = false
        handler.removeCallbacks(autoClose)
        onVisibility(false)
        if (immediate) {
            host.visibility = View.GONE
            return
        }
        panel.animate().translationX(-panelWidth.toFloat()).setDuration(180).start()
        scrim.animate().alpha(0f).setDuration(180).withEndAction { if (!isOpen) host.visibility = View.GONE }.start()
    }

    /** Reinicia o tempo de espera: o menu fecha sozinho depois de alguns segundos sem uso. */
    fun touch() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, 20_000)
    }

    private fun focusPlaying() {
        val id = playingId()
        val pos = shown.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }
        (rows.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, Ui.dp(act, 110))
        rows.postDelayed({
            if (!isOpen) return@postDelayed
            val v = rows.findViewHolderForAdapterPosition(pos)?.itemView
            if (v != null) v.requestFocus() else rows.requestFocus()
        }, 90)
    }

    private fun playingId(): String = PlayQueue.items.getOrNull(PlayQueue.index)?.entry?.id ?: ""

    // ------------------------------------------------------------------ dados

    private fun loadCategories() {
        if (cats.isNotEmpty() || loadingCats) return
        val x = xt ?: return
        loadingCats = true
        act.lifecycleScope.launch {
            val list = try { withContext(Dispatchers.IO) { x.liveCategories() } } catch (e: Exception) { emptyList() }
            loadingCats = false
            cats = list
            chipAdapter.items = list
            chipAdapter.selectedId = shownCat
            chipAdapter.notifyDataSetChanged()
            val i = list.indexOfFirst { it.id == shownCat }
            if (i > 0) chips.scrollToPosition(i)
        }
    }

    private fun pickCategory(c: Category) {
        touch()
        if (c.id == shownCat) return
        shownCat = c.id
        titleView.text = c.name
        chipAdapter.selectedId = c.id
        chipAdapter.notifyDataSetChanged()
        val cached = lists[c.id]
        if (cached != null) {
            showList(cached)
            return
        }
        val x = xt ?: return
        status.text = "Carregando…"
        status.visibility = View.VISIBLE
        rowAdapter.items = emptyList()
        rowAdapter.notifyDataSetChanged()
        act.lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { x.liveStreams(c.id) }
                lists[c.id] = list
                if (shownCat == c.id) showList(list)
            } catch (e: Exception) {
                if (shownCat == c.id) {
                    status.text = "Não foi possível carregar esta categoria."
                }
            }
        }
    }

    private fun showList(list: List<Entry>) {
        shown = list
        status.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        if (list.isEmpty()) status.text = "Nenhum canal nesta categoria."
        rowAdapter.items = list
        rowAdapter.notifyDataSetChanged()
        (rows.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
        loadGuide(list)
    }

    private fun loadGuide(list: List<Entry>) {
        if (list.isEmpty()) return
        act.lifecycleScope.launch {
            val changed = withContext(Dispatchers.IO) { Guide.load(list.take(120)) }
            if (changed && shown === list) {
                rowAdapter.notifyDataSetChanged()
                refreshNow()
            }
        }
    }

    private fun choose(position: Int) {
        touch()
        val x = xt
        if (shownCat == PlayQueue.categoryId || x == null) {
            if (shownCat == PlayQueue.categoryId) PlayQueue.index = position else return
        } else {
            val name = cats.firstOrNull { it.id == shownCat }?.name ?: ""
            PlayQueue.setLive(x, shown, position, shownCat, name)
        }
        onChannelChosen()
        rowAdapter.notifyDataSetChanged()
        refreshNow()
    }

    /** Atualiza o cartão "no ar" (nome, programa de agora, barra de progresso, próximo). */
    fun refreshNow() {
        val item = PlayQueue.items.getOrNull(PlayQueue.index) ?: return
        nowName.text = item.title
        val progs = item.entry?.let { Guide.cached(it.id) }
        val t = System.currentTimeMillis() / 1000
        val cur = progs?.firstOrNull { it.start <= t && it.end > t }
        val next = progs?.firstOrNull { it.start > t }
        if (cur != null) {
            nowSub.text = Guide.time(cur.start) + " – " + Guide.time(cur.end) + "   " + cur.title
            val span = (cur.end - cur.start).coerceAtLeast(1)
            nowBar.progress = (((t - cur.start) * 100) / span).toInt().coerceIn(0, 100)
            nowBar.visibility = View.VISIBLE
        } else {
            nowSub.text = if (progs.isNullOrEmpty()) "Sem programação disponível" else ""
            nowBar.visibility = View.GONE
        }
        nowNext.text = if (next != null) "Depois: " + Guide.time(next.start) + "  " + next.title else ""
        nowNext.visibility = if (next != null) View.VISIBLE else View.GONE
        if (isOpen) rowAdapter.notifyDataSetChanged()
    }

    // ------------------------------------------------------------------ categorias (botões em formato de pílula)

    private inner class ChipAdapter : RecyclerView.Adapter<ChipAdapter.VH>() {
        var items: List<Category> = emptyList()
        var selectedId: String = ""

        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(act)
            tv.textSize = 13.5f
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.maxLines = 1
            tv.gravity = Gravity.CENTER
            tv.setPadding(Ui.dp(act, 16), 0, Ui.dp(act, 16), 0)
            tv.isFocusable = true
            tv.isClickable = true
            val lp = RecyclerView.LayoutParams(Ui.WRAP, Ui.dp(act, 38))
            lp.setMargins(0, Ui.dp(act, 5), Ui.dp(act, 8), Ui.dp(act, 5))
            tv.layoutParams = lp
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            val on = c.id == selectedId
            holder.tv.text = c.name
            holder.tv.setTextColor(if (on) Ui.DARK_TEXT else Ui.TEXT)
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_focused), Ui.shape(act, if (on) Ui.ORANGE else Ui.CARD2, 19, Color.WHITE, 3))
            sl.addState(intArrayOf(), Ui.shape(act, if (on) Ui.ORANGE else Ui.CARD, 19, if (on) Ui.ORANGE else Ui.BORDER, 1))
            holder.tv.background = sl
            holder.tv.setOnClickListener { pickCategory(c) }
        }

        override fun getItemCount(): Int = items.size
    }

    // ------------------------------------------------------------------ canais

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {
        var items: List<Entry> = emptyList()

        inner class VH(val root: LinearLayout, val bar: View, val num: TextView, val img: ImageView, val name: TextView, val sub: TextView, val badge: TextView) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val root = LinearLayout(act)
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.isFocusable = true
            root.isClickable = true
            root.setPadding(0, Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8))
            val lp = RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP)
            lp.setMargins(0, Ui.dp(act, 2), 0, Ui.dp(act, 2))
            root.layoutParams = lp

            val bar = View(act)
            root.addView(bar, LinearLayout.LayoutParams(Ui.dp(act, 4), Ui.dp(act, 38)).also { it.setMargins(Ui.dp(act, 3), 0, Ui.dp(act, 8), 0) })
            val num = Ui.text(act, "", 12.5f, Ui.MUTED, true)
            num.gravity = Gravity.CENTER
            root.addView(num, LinearLayout.LayoutParams(Ui.dp(act, 34), Ui.WRAP))
            val img = ImageView(act)
            img.scaleType = ImageView.ScaleType.FIT_CENTER
            root.addView(img, LinearLayout.LayoutParams(Ui.dp(act, 54), Ui.dp(act, 38)).also { it.setMargins(Ui.dp(act, 4), 0, Ui.dp(act, 12), 0) })
            val col = LinearLayout(act)
            col.orientation = LinearLayout.VERTICAL
            val name = Ui.text(act, "", 16f, Ui.TEXT, true)
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            col.addView(name)
            val sub = Ui.text(act, "", 12f, Ui.MUTED)
            sub.maxLines = 1
            sub.ellipsize = TextUtils.TruncateAt.END
            col.addView(sub, Ui.vlp(act, 2, 0))
            root.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            val badge = Ui.text(act, "AO VIVO", 9.5f, Ui.DARK_TEXT, true)
            badge.letterSpacing = 0.08f
            badge.setPadding(Ui.dp(act, 7), Ui.dp(act, 3), Ui.dp(act, 7), Ui.dp(act, 3))
            badge.background = Ui.shape(act, Ui.ORANGE, 8)
            root.addView(badge, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).also { it.setMargins(Ui.dp(act, 8), 0, 0, 0) })

            // ◀ volta para as categorias
            root.setOnKeyListener { _, code, ev ->
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && ev.action == KeyEvent.ACTION_DOWN) {
                    val i = chipAdapter.items.indexOfFirst { it.id == shownCat }.coerceAtLeast(0)
                    chips.scrollToPosition(i)
                    chips.postDelayed({ (chips.findViewHolderForAdapterPosition(i)?.itemView ?: chips.getChildAt(0))?.requestFocus() }, 30)
                    true
                } else false
            }
            return VH(root, bar, num, img, name, sub, badge)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            val playing = e.id == playingId() && shownCat == PlayQueue.categoryId
            holder.num.text = (position + 1).toString()
            holder.name.text = e.title
            holder.name.setTextColor(if (playing) Ui.ORANGE else Ui.TEXT)
            val line = Guide.nowLine(Guide.cached(e.id))
            holder.sub.text = line
            holder.sub.visibility = if (line.isEmpty()) View.GONE else View.VISIBLE
            holder.bar.background = if (playing) Ui.shape(act, Ui.ORANGE, 2) else null
            holder.badge.visibility = if (playing) View.VISIBLE else View.GONE
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_focused), Ui.shape(act, Ui.CARD2, 14, Ui.ORANGE, 3))
            sl.addState(intArrayOf(android.R.attr.state_pressed), Ui.shape(act, Ui.CARD2, 14, Ui.YELLOW, 2))
            sl.addState(intArrayOf(), Ui.shape(act, if (playing) Ui.CARD else Color.TRANSPARENT, 14))
            holder.root.background = sl
            if (e.image.isNotBlank()) holder.img.load(e.image) { crossfade(true) } else holder.img.setImageDrawable(null)
            holder.root.setOnClickListener { choose(holder.adapterPosition) }
        }

        override fun getItemCount(): Int = items.size
    }
}
