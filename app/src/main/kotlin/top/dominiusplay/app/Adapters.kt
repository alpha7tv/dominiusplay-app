package top.dominiusplay.app

import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class CategoryAdapter(
    private val horizontal: Boolean,
    private val onPick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    var items: List<Category> = emptyList()
    var selectedId: String = ""

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val tv = TextView(ctx)
        tv.textSize = 15f
        tv.maxLines = 2
        tv.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12))
        tv.isFocusable = true
        tv.isClickable = true
        val lp = if (horizontal) RecyclerView.LayoutParams(Ui.WRAP, Ui.WRAP) else RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP)
        lp.setMargins(Ui.dp(ctx, 4), Ui.dp(ctx, 3), Ui.dp(ctx, 4), Ui.dp(ctx, 3))
        tv.layoutParams = lp
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        val selected = c.id == selectedId
        holder.tv.text = c.name
        holder.tv.setTextColor(if (selected) Ui.YELLOW else Ui.TEXT)
        holder.tv.background = Ui.focusable(holder.tv.context, if (selected) Ui.CARD2 else Ui.CARD, 10)
        holder.tv.setOnClickListener { onPick(c) }
    }

    override fun getItemCount(): Int = items.size
}

class EntryAdapter(
    private val poster: Boolean,
    private val onPick: (Entry, Int) -> Unit
) : RecyclerView.Adapter<EntryAdapter.VH>() {

    var items: List<Entry> = emptyList()

    class VH(val root: LinearLayout, val img: ImageView, val title: TextView) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = LinearLayout(ctx)
        root.isFocusable = true
        root.isClickable = true
        root.background = Ui.focusable(ctx, Ui.CARD, 12)
        val img = ImageView(ctx)
        val title = TextView(ctx)
        title.setTextColor(Ui.TEXT)
        title.maxLines = 2
        if (poster) {
            root.orientation = LinearLayout.VERTICAL
            root.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 8))
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            root.addView(img, LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(ctx, 190)))
            title.textSize = 12f
            title.gravity = Gravity.CENTER_HORIZONTAL
            root.addView(title, Ui.vlp(ctx, 6, 0))
            root.layoutParams = RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP).apply {
                setMargins(Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4))
            }
        } else {
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
            img.scaleType = ImageView.ScaleType.FIT_CENTER
            root.addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 40)))
            title.textSize = 16f
            val lp = LinearLayout.LayoutParams(0, Ui.WRAP, 1f)
            lp.setMargins(Ui.dp(ctx, 12), 0, 0, 0)
            root.addView(title, lp)
            root.layoutParams = RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP).apply {
                setMargins(Ui.dp(ctx, 4), Ui.dp(ctx, 3), Ui.dp(ctx, 4), Ui.dp(ctx, 3))
            }
        }
        return VH(root, img, title)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.title.text = e.title
        if (e.image.isNotBlank()) {
            holder.img.load(e.image) { crossfade(true) }
        } else {
            holder.img.setImageDrawable(null)
        }
        holder.root.setOnClickListener { onPick(e, holder.adapterPosition) }
    }

    override fun getItemCount(): Int = items.size
}
