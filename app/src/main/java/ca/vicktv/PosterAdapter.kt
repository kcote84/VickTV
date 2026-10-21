package ca.vicktv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class PosterAdapter(private val context: Context, private val entries: List<Entry>, private val favourites: Set<String>,
    private val loader: PosterLoader, private val posterHeight: Int) : BaseAdapter() {
    private data class Holder(val image: ImageView, val title: TextView)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    override fun getCount() = entries.size
    override fun getItem(position: Int) = entries[position]
    override fun getItemId(position: Int) = position.toLong()
    private fun shape(color: Int, border: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(10).toFloat()
        if(border) setStroke(dp(3), TvTheme.accent)
    }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val card = convertView as? LinearLayout ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4))
            val image = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            val title = TextView(context).apply {
                textSize = 16f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                minLines = 2; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(6), dp(7), dp(6), dp(3)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(image, LinearLayout.LayoutParams(-1, posterHeight)); addView(title, LinearLayout.LayoutParams(-1, -2))
            tag = Holder(image, title)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_selected), shape(TvTheme.selected, true))
                addState(intArrayOf(android.R.attr.state_pressed), shape(TvTheme.selected, true))
                addState(intArrayOf(), shape(TvTheme.panel))
            }
        }
        val holder = card.tag as Holder; val entry = entries[position]
        holder.title.text = "${if(entry.key in favourites) "★ " else ""}${entry.name}"
        card.contentDescription = "${entry.name}${if(entry.key in favourites) ", favori" else ""}"
        holder.image.scaleType = ImageView.ScaleType.CENTER_INSIDE
        holder.image.setImageResource(if(entry.kind == "series") R.drawable.home_series else R.drawable.home_films)
        holder.image.setColorFilter(TvTheme.muted); holder.image.setBackgroundColor(TvTheme.row)
        loader.bind(holder.image, entry.poster)
        return card
    }
}
