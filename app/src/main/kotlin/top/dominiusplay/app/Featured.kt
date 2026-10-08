package top.dominiusplay.app

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Faixa "Últimos adicionados" da tela inicial (filmes e séries novos). */
object FeaturedRail {
    private var cache: List<Featured> = emptyList()
    private var cacheKey = ""
    private var cacheAt = 0L

    /** Cria a seção; ela só aparece quando os destaques chegam. */
    fun build(activity: AppCompatActivity, s: SessionData): View {
        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.visibility = View.GONE

        box.addView(Ui.text(activity, "Últimos adicionados", 18f, Ui.TEXT, true), Ui.vlp(activity, 0, 8))
        val list = RecyclerView(activity)
        list.layoutManager = LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)
        list.clipToPadding = false
        val adapter = Adapter { openItem(activity, s, it) }
        list.adapter = adapter
        box.addView(list, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(activity, 258)))

        val key = s.server + "|" + s.username
        val fresh = cacheKey == key && cache.isNotEmpty() && System.currentTimeMillis() - cacheAt < 600_000
        if (fresh) {
            adapter.items = cache
            adapter.notifyDataSetChanged()
            box.visibility = View.VISIBLE
        } else if (s.server.isNotEmpty()) {
            activity.lifecycleScope.launch {
                val items = try {
                    withContext(Dispatchers.IO) { Xtream(s.server, s.username, s.password).latest(20) }
                } catch (e: Exception) {
                    emptyList()
                }
                if (items.isNotEmpty()) {
                    cache = items
                    cacheKey = key
                    cacheAt = System.currentTimeMillis()
                    adapter.items = items
                    adapter.notifyDataSetChanged()
                    box.visibility = View.VISIBLE
                }
            }
        }
        return box
    }

    private fun openItem(activity: AppCompatActivity, s: SessionData, f: Featured) {
        val xt = Xtream(s.server, s.username, s.password)
        if (!f.series) {
            PlayQueue.items = listOf(PlayItem(f.entry.title, xt.movieUrl(f.entry.id, f.entry.ext), null))
            PlayQueue.index = 0
            PlayQueue.live = false
            activity.startActivity(Intent(activity, PlayerActivity::class.java))
            return
        }
        activity.lifecycleScope.launch {
            val eps = try {
                withContext(Dispatchers.IO) { xt.episodes(f.entry.id) }
            } catch (e: Exception) {
                null
            }
            if (eps == null || eps.isEmpty()) {
                AlertDialog.Builder(activity).setMessage("Não foi possível carregar os episódios desta série.").setPositiveButton("OK", null).show()
            } else {
                chooseSeason(activity, xt, f.entry, eps)
            }
        }
    }

    private fun chooseSeason(a: AppCompatActivity, xt: Xtream, show: Entry, eps: List<Episode>) {
        val seasons = eps.map { it.season }.distinct().sorted()
        if (seasons.size == 1) {
            chooseEpisode(a, xt, show, eps.filter { it.season == seasons[0] }, seasons[0])
            return
        }
        val labels = seasons.map { n -> "Temporada " + n + "  (" + eps.count { it.season == n } + " episódios)" }.toTypedArray()
        AlertDialog.Builder(a)
            .setTitle(show.title)
            .setItems(labels) { _, which ->
                val season = seasons[which]
                chooseEpisode(a, xt, show, eps.filter { it.season == season }, season)
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun chooseEpisode(a: AppCompatActivity, xt: Xtream, show: Entry, eps: List<Episode>, season: Int) {
        val labels = eps.map { "E" + String.format("%02d", it.number) + "  •  " + it.title }.toTypedArray()
        AlertDialog.Builder(a)
            .setTitle(show.title + " - Temporada " + season)
            .setItems(labels) { _, which ->
                PlayQueue.items = eps.map { PlayItem(show.title + " - T" + it.season + "E" + it.number, xt.episodeUrl(it.id, it.ext), null) }
                PlayQueue.index = which
                PlayQueue.live = false
                a.startActivity(Intent(a, PlayerActivity::class.java))
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private class Adapter(private val onPick: (Featured) -> Unit) : RecyclerView.Adapter<Adapter.VH>() {
        var items: List<Featured> = emptyList()

        class VH(val root: LinearLayout, val img: ImageView, val title: TextView, val badge: TextView) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val root = LinearLayout(ctx)
            root.orientation = LinearLayout.VERTICAL
            root.isFocusable = true
            root.isClickable = true
            root.background = Ui.focusable(ctx, Ui.CARD, 12)
            root.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 8))

            val frame = FrameLayout(ctx)
            val img = ImageView(ctx)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            frame.addView(img, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            val badge = TextView(ctx)
            badge.textSize = 10f
            badge.setTextColor(Ui.BG)
            badge.setTypeface(badge.typeface, android.graphics.Typeface.BOLD)
            badge.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 3), Ui.dp(ctx, 8), Ui.dp(ctx, 3))
            val blp = FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP or Gravity.START)
            blp.setMargins(Ui.dp(ctx, 6), Ui.dp(ctx, 6), 0, 0)
            frame.addView(badge, blp)
            root.addView(frame, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(ctx, 190)))

            val title = TextView(ctx)
            title.setTextColor(Ui.TEXT)
            title.textSize = 12f
            title.maxLines = 2
            title.gravity = Gravity.CENTER_HORIZONTAL
            root.addView(title, Ui.vlp(ctx, 6, 0))

            root.layoutParams = RecyclerView.LayoutParams(Ui.dp(ctx, 132), Ui.WRAP).apply {
                setMargins(Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4))
            }
            return VH(root, img, title, badge)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = items[position]
            holder.title.text = f.entry.title
            holder.badge.text = if (f.series) "SÉRIE" else "FILME"
            holder.badge.background = Ui.shape(holder.root.context, if (f.series) Ui.PINK else Ui.YELLOW, 6)
            if (f.entry.image.isNotBlank()) {
                holder.img.load(f.entry.image) { crossfade(true) }
            } else {
                holder.img.setImageDrawable(null)
            }
            holder.root.setOnClickListener { onPick(f) }
        }

        override fun getItemCount(): Int = items.size
    }
}
