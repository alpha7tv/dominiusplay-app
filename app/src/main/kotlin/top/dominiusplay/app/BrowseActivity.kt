package top.dominiusplay.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lista de categorias + itens para Canais ao vivo, Filmes e Séries. */
class BrowseActivity : BaseActivity() {
    private lateinit var mode: String
    private lateinit var xt: Xtream
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var catList: RecyclerView
    private lateinit var itemList: RecyclerView
    private lateinit var catAdapter: CategoryAdapter
    private lateinit var entryAdapter: EntryAdapter

    private val cache = HashMap<String, List<Entry>>()
    private var current: List<Entry> = emptyList()
    private var selectedCat: Category? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: "live"
        val s = Session.current
        if (s == null || s.expired || s.server.isEmpty()) {
            finish()
            return
        }
        xt = Xtream(s.server, s.username, s.password)

        val narrow = Ui.isNarrow(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        val pad = Ui.dp(this, 16)
        root.setPadding(pad, pad, pad, pad)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = when (mode) {
            "movies" -> "🎬  Filmes"
            "series" -> "🍿  Séries"
            else -> "📺  Canais ao vivo"
        }
        header.addView(Ui.text(this, title, 22f, Ui.TEXT, true), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        search = EditText(this)
        search.hint = "Buscar"
        search.setHintTextColor(Ui.MUTED)
        search.setTextColor(Ui.TEXT)
        search.textSize = 15f
        search.setSingleLine(true)
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.background = Ui.shape(this, Ui.CARD, 12, Ui.BORDER, 1)
        search.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
        })
        header.addView(search, LinearLayout.LayoutParams(Ui.dp(this, if (narrow) 150 else 280), Ui.WRAP))
        Cast.button(this)?.let { b -> header.addView(b, LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44))) }
        root.addView(header)

        status = Ui.text(this, "", 14f, Ui.MUTED)
        status.gravity = Gravity.CENTER
        root.addView(status, Ui.vlp(this, 8, 0))

        catAdapter = CategoryAdapter(narrow) { pick(it) }
        catList = RecyclerView(this)
        catList.layoutManager = LinearLayoutManager(this, if (narrow) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL, false)
        catList.adapter = catAdapter

        val poster = mode != "live"
        entryAdapter = EntryAdapter(poster) { e, index -> open(e, index) }
        itemList = RecyclerView(this)
        val span = if (!poster) 1 else (if (narrow) 3 else 5)
        itemList.layoutManager = if (poster) GridLayoutManager(this, span) else LinearLayoutManager(this)
        itemList.adapter = entryAdapter
        itemList.clipToPadding = false

        val body = LinearLayout(this)
        body.orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        if (narrow) {
            body.addView(catList, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 56)))
            body.addView(itemList, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        } else {
            body.addView(catList, LinearLayout.LayoutParams(Ui.dp(this, 250), Ui.MATCH))
            val lp = LinearLayout.LayoutParams(0, Ui.MATCH, 1f)
            lp.setMargins(Ui.dp(this, 12), 0, 0, 0)
            body.addView(itemList, lp)
        }
        root.addView(body, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        setContentView(root)
        loadCategories()
    }

    private fun setStatus(text: String) {
        status.text = text
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun loadCategories() {
        setStatus("Carregando categorias…")
        lifecycleScope.launch {
            try {
                val cats = withContext(Dispatchers.IO) {
                    when (mode) {
                        "movies" -> xt.movieCategories()
                        "series" -> xt.seriesCategories()
                        else -> xt.liveCategories()
                    }
                }
                catAdapter.items = cats
                catAdapter.notifyDataSetChanged()
                if (cats.isEmpty()) {
                    setStatus("Nenhuma categoria encontrada nesta lista.")
                } else {
                    setStatus("")
                    pick(cats[0])
                }
            } catch (e: Exception) {
                setStatus("Não foi possível carregar. Verifique a internet e tente de novo. (" + (e.message ?: "erro") + ")")
            }
        }
    }

    private fun pick(c: Category) {
        selectedCat = c
        catAdapter.selectedId = c.id
        catAdapter.notifyDataSetChanged()
        search.setText("")
        val cached = cache[c.id]
        if (cached != null) {
            current = cached
            applyFilter()
            return
        }
        setStatus("Carregando…")
        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    when (mode) {
                        "movies" -> xt.movies(c.id)
                        "series" -> xt.shows(c.id)
                        else -> xt.liveStreams(c.id)
                    }
                }
                cache[c.id] = list
                if (selectedCat?.id == c.id) {
                    current = list
                    applyFilter()
                }
            } catch (e: Exception) {
                setStatus("Não foi possível carregar esta categoria. (" + (e.message ?: "erro") + ")")
            }
        }
    }

    private fun applyFilter() {
        val q = search.text.toString().trim().lowercase()
        val list = if (q.isEmpty()) current else current.filter { it.title.lowercase().contains(q) }
        entryAdapter.items = list
        entryAdapter.notifyDataSetChanged()
        setStatus(if (list.isEmpty()) "Nada encontrado." else "")
        loadGuide(list)
    }

    private var guideJob: Job? = null

    /** Guia de programação dos canais da lista ("Agora: ..." em cada linha). */
    private fun loadGuide(list: List<Entry>) {
        if (mode != "live" || list.isEmpty()) return
        guideJob?.cancel()
        guideJob = lifecycleScope.launch {
            val changed = withContext(Dispatchers.IO) { Guide.load(list) }
            if (changed && entryAdapter.items === list) entryAdapter.notifyItemRangeChanged(0, list.size)
        }
    }

    private fun open(e: Entry, index: Int) {
        when (mode) {
            "live" -> {
                val list = entryAdapter.items
                // Express abre pelo fluxo direto (.ts), que começa bem antes; o HLS fica como reserva
                PlayQueue.items = list.map {
                    if (BuildConfig.EXPRESS) PlayItem(it.title, xt.liveTsUrl(it.id), xt.liveUrl(it.id), it)
                    else PlayItem(it.title, xt.liveUrl(it.id), xt.liveTsUrl(it.id), it)
                }
                PlayQueue.index = if (index in list.indices) index else 0
                PlayQueue.live = true
                startActivity(Intent(this, PlayerActivity::class.java))
            }
            "movies" -> {
                PlayQueue.items = listOf(PlayItem(e.title, xt.movieUrl(e.id, e.ext), null))
                PlayQueue.index = 0
                PlayQueue.live = false
                startActivity(Intent(this, PlayerActivity::class.java))
            }
            else -> openSeries(e)
        }
    }

    private fun openSeries(e: Entry) {
        setStatus("Carregando episódios…")
        lifecycleScope.launch {
            try {
                val eps = withContext(Dispatchers.IO) { xt.episodes(e.id) }
                setStatus("")
                if (eps.isEmpty()) {
                    setStatus("Nenhum episódio disponível para esta série.")
                } else {
                    chooseSeason(e, eps)
                }
            } catch (ex: Exception) {
                setStatus("Não foi possível carregar os episódios. (" + (ex.message ?: "erro") + ")")
            }
        }
    }

    private fun chooseSeason(show: Entry, eps: List<Episode>) {
        val seasons = eps.map { it.season }.distinct().sorted()
        if (seasons.size == 1) {
            chooseEpisode(show, eps.filter { it.season == seasons[0] }, seasons[0])
            return
        }
        val labels = seasons.map { s -> "Temporada " + s + "  (" + eps.count { it.season == s } + " episódios)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(show.title)
            .setItems(labels) { _, which ->
                val season = seasons[which]
                chooseEpisode(show, eps.filter { it.season == season }, season)
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun chooseEpisode(show: Entry, eps: List<Episode>, season: Int) {
        val labels = eps.map { "E" + String.format("%02d", it.number) + "  •  " + it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(show.title + " - Temporada " + season)
            .setItems(labels) { _, which ->
                PlayQueue.items = eps.map { PlayItem(show.title + " - T" + it.season + "E" + it.number, xt.episodeUrl(it.id, it.ext), null) }
                PlayQueue.index = which
                PlayQueue.live = false
                startActivity(Intent(this, PlayerActivity::class.java))
            }
            .setNegativeButton("Voltar", null)
            .show()
    }
}
