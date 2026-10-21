package ca.vicktv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Only five channel rows are drawn, regardless of the size of the supplier's catalogue. */
class TvGuideGrid(context: Context) : View(context) {
    var channels = emptyList<Entry>(); private set
    var selected = 0; private set
    var first = 0; private set
    var windowStart = GuideWindow.floor(System.currentTimeMillis()); private set
    private var cursor = System.currentTimeMillis()
    private var followingNow = true
    var programmes: (Entry) -> List<Programme> = { emptyList() }
    var status: (Entry) -> String = { "Chargement de l’horaire…" }
    var favourite: (Entry) -> Boolean = { false }
    var onSelection: () -> Unit = {}
    var onOpen: (Entry, Programme?) -> Unit = { _, _ -> }
    var onFavourite: () -> Unit = {}
    var onWindow: () -> Unit = {}
    private val density = resources.displayMetrics.density
    private fun px(n: Float) = n * density
    private val ink = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = TvTheme.accent
    private val muted = TvTheme.muted
    private val clock = SimpleDateFormat("HH:mm", Locale.CANADA_FRENCH)
    private val dateScratch = Date(0)
    private val rectangle = RectF()
    private fun stamp(value: Long): String { dateScratch.time = value; return clock.format(dateScratch) }
    private fun rounded(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        rectangle.set(left, top, right, bottom); c.drawRoundRect(rectangle, px(7f), px(7f), fill)
    }
    private val column get() = minOf(px(184f), width * .25f)
    private val header get() = px(30f)
    private val rowHeight get() = (height - header) / 5f
    init {
        isFocusable = true; isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setOnClickListener { channels.getOrNull(selected)?.let { onOpen(it, selectedProgramme()) } }
        setOnLongClickListener { onFavourite(); true }
    }
    fun setChannels(values: List<Entry>, focusId: String? = null) {
        val same = values == channels && focusId == null
        val old = focusId ?: channels.getOrNull(selected)?.id
        channels = values; selected = values.indexOfFirst { it.id == old }.coerceAtLeast(0)
        if (!same) first = (selected - 2).coerceIn(0, maxOf(0, values.size - 5))
        changed()
    }
    fun visible() = channels.drop(first).take(5)
    private fun slots(entry: Entry) = GuideWindow.slots(programmes(entry), windowStart, windowStart + GuideWindow.SPAN)
    fun selectedProgramme(): Programme? = channels.getOrNull(selected)?.let { GuideWindow.at(slots(it), cursor)?.programme }
    fun selectedEntry() = channels.getOrNull(selected)
    fun refresh() {
        if (followingNow) {
            cursor = System.currentTimeMillis()
            if (cursor !in windowStart until windowStart + GuideWindow.SPAN) { windowStart = GuideWindow.floor(cursor); onWindow() }
        }
        updateDescription(); invalidate(); onSelection()
    }
    fun now() { followingNow = true; windowStart = GuideWindow.floor(System.currentTimeMillis()); cursor = System.currentTimeMillis(); changed(); onWindow() }
    fun shift(direction: Int) {
        val base = GuideWindow.floor(System.currentTimeMillis())
        val next = (windowStart + direction * GuideWindow.SPAN).coerceIn(base, base + 5 * GuideWindow.SPAN)
        if (next != windowStart) { followingNow = false; windowStart = next; cursor = next; changed(); onWindow() }
    }
    private fun changed() { updateDescription(); invalidate(); onSelection() }
    private fun updateDescription() {
        val entry = selectedEntry()
        val p = selectedProgramme()
        contentDescription = if (entry == null) "Guide TV, aucune chaîne" else
            "${selected + 1} sur ${channels.size}. ${entry.name}. ${p?.let { "${it.title}, ${clock.format(Date(it.start))} à ${clock.format(Date(it.end))}" } ?: status(entry)}. OK pour regarder ou ouvrir les détails. Menu pour les options."
        if (hasFocus()) sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
    }
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect); changed()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (channels.isEmpty()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP) -1 else 1
                val step = if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) 5 else 1
                val next = (selected + direction * step).coerceIn(0, channels.lastIndex)
                if (next == selected) return super.onKeyDown(keyCode, event)
                selected = next
                if (selected < first) first = selected
                if (selected >= first + 5) first = selected - 4
                changed(); return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                followingNow = false
                val slots = slots(channels[selected]); val i = slots.indexOfFirst { cursor >= it.start && cursor < it.end }.coerceAtLeast(0)
                val next = i + if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                if (next in slots.indices) { cursor = slots[next].start; changed() }
                else shift(if (next < 0) -1 else 1)
                return true
            }
            KeyEvent.KEYCODE_MENU -> { onFavourite(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && event.y >= header && rowHeight > 0) {
            val index = first + ((event.y - header) / rowHeight).toInt()
            if (index in channels.indices) {
                selected = index
                followingNow = event.x <= column
                cursor = if (event.x <= column) System.currentTimeMillis().coerceIn(windowStart, windowStart + GuideWindow.SPAN - 1)
                else (windowStart + (event.x - column) / (width - column) * GuideWindow.SPAN).toLong().coerceIn(windowStart, windowStart + GuideWindow.SPAN - 1)
                requestFocus(); changed(); performClick()
            }
            return true
        }
        return event.action == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
    }
    override fun performClick(): Boolean = super.performClick()
    private fun text(c: Canvas, value: String, x: Float, y: Float, available: Float, size: Float, color: Int, bold: Boolean = false) {
        if (available <= px(8f)) return
        ink.color = color; ink.textSize = px(size) * resources.configuration.fontScale.coerceAtMost(1.3f)
        ink.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(TextUtils.ellipsize(value.replace('\n', ' '), ink, available, TextUtils.TruncateAt.END).toString(), x, y, ink)
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width <= column || rowHeight <= 0) return
        val timeline = width - column
        text(c, "CHAÎNES  ·  ${if(channels.isEmpty()) 0 else selected + 1}/${channels.size}", px(10f), px(20f), column - px(16f), 11f, muted, true)
        for (i in 0..3) text(c, stamp(windowStart + i * GuideWindow.HALF_HOUR), column + timeline * i / 4 + px(8f), px(20f), timeline / 4 - px(12f), 13f, muted)
        visible().forEachIndexed { row, entry ->
            val top = header + row * rowHeight; val bottom = top + rowHeight - px(4f)
            val active = first + row == selected
            fill.color = if (active) TvTheme.selectedChannel else TvTheme.row
            rounded(c, 0f, top, column - px(5f), bottom)
            val ordinal = entry.number.takeIf { it > 0 } ?: (first + row + 1)
            text(c, "${if(favourite(entry)) "★ " else ""}$ordinal", px(10f), top + rowHeight * .34f, column - px(20f), 11f, accent, true)
            text(c, entry.name, px(10f), top + rowHeight * .73f, column - px(20f), 16f, Color.WHITE, active)
            slots(entry).forEach { slot ->
                val left = column + timeline * (slot.start - windowStart).toFloat() / GuideWindow.SPAN
                val right = column + timeline * (slot.end - windowStart).toFloat() / GuideWindow.SPAN - px(4f)
                if (right <= left) return@forEach
                val focused = active && cursor >= slot.start && cursor < slot.end
                val live = slot.programme?.isNow(System.currentTimeMillis()) == true
                fill.style = Paint.Style.FILL
                fill.color = when { focused && hasFocus() -> TvTheme.selected; live -> TvTheme.live; else -> TvTheme.panel }
                rounded(c, left, top, right, bottom)
                if (focused) {
                    fill.style = Paint.Style.STROKE; fill.strokeWidth = px(if(hasFocus()) 2.5f else 1f); fill.color = if(hasFocus()) accent else muted
                    rounded(c, left + px(1.5f), top + px(1.5f), right - px(1.5f), bottom - px(1.5f)); fill.style = Paint.Style.FILL
                }
                text(c, slot.programme?.title ?: status(entry), left + px(10f), top + rowHeight * .43f, right - left - px(18f), 16f, if(slot.programme != null) Color.WHITE else muted, focused)
                slot.programme?.let { text(c, "${stamp(it.start)} – ${stamp(it.end)}", left + px(10f), top + rowHeight * .78f, right - left - px(18f), 11f, muted) }
            }
        }
        val now = System.currentTimeMillis()
        if (now >= windowStart && now < windowStart + GuideWindow.SPAN) {
            val x = column + timeline * (now - windowStart).toFloat() / GuideWindow.SPAN
            fill.color = accent; fill.strokeWidth = px(1f); c.drawLine(x, header, x, header + rowHeight * minOf(5, channels.size), fill)
        }
    }
}
