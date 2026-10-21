package ca.vicktv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Owns the guide's view, transient navigation state and a bounded in-memory schedule cache. */
class TvGuideScreen(private val activity: Activity, val account: Account?, private val store: Store,
    private val watch: (Entry) -> Unit, private val connect: () -> Unit) {
    val view = LinearLayout(activity)
    private val grid = TvGuideGrid(activity)
    private val main = Handler(Looper.getMainLooper())
    private val pool = ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    private val api = account?.let { Xtream(it) }
    private val pending = mutableMapOf<String, Future<*>>()
    private val tickets = mutableMapOf<String, Int>()
    private var nextTicket = 0
    private var catalogue: Future<*>? = null
    private var active = false
    private var generation = 0
    private var all = emptyList<Entry>()
    private var categories = emptyList<Category>()
    private var filter = "Français"
    private var category = ""
    private var query = ""
    private var focusId: String? = null
    private var initialFilter = true
    private var loadedAt = 0L
    private data class Cached(val programmes: List<Programme>, val loaded: Long, val failed: Boolean)
    private val cache = object : LinkedHashMap<String, Cached>(96, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > 96
    }
    private val demoFavourites = mutableSetOf<String>()
    private val bg = Color.rgb(16, 26, 34)
    private val panel = Color.rgb(30, 46, 57)
    private val mint = Color.rgb(100, 224, 180)
    private val muted = Color.rgb(166, 184, 194)
    private val time = SimpleDateFormat("HH:mm", Locale.CANADA_FRENCH)
    private val date = SimpleDateFormat("EEE d MMM · HH:mm", Locale.CANADA_FRENCH)
    private val heading: TextView
    private val title: TextView
    private val description: TextView
    private val clock: TextView
    private val notice: TextView
    private val categoryButton: Button
    private val searchButton: Button
    private val favouriteButton: Button
    private val filters = mutableMapOf<String, Button>()
    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            updateNotice()
            if (grid.windowStart + GuideWindow.SPAN <= System.currentTimeMillis()) grid.now()
            grid.refresh(); loadVisible(); main.postDelayed(this, 60000)
        }
    }
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private fun text(size: Float, color: Int = Color.WHITE) = TextView(activity).apply {
        textSize = size; setTextColor(color); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL
    }
    private fun shape(color: Int, border: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(8).toFloat(); if(border) setStroke(dp(2), mint)
    }
    private fun button(label: String, action: () -> Unit) = Button(activity).apply {
        id = View.generateViewId(); text = label; textSize = 14f; isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(8), 0, dp(8), 0); setTextColor(Color.WHITE); background = shape(panel)
        defaultFocusHighlightEnabled = false
        setOnFocusChangeListener { _, focused ->
            background = shape(if(focused) mint else panel, isSelected)
            setTextColor(if(focused) bg else Color.WHITE)
        }
        setOnClickListener { action() }
    }
    private fun row(height: Int) = LinearLayout(activity).also { view.addView(it, LinearLayout.LayoutParams(-1, dp(height))) }
    private fun add(row: LinearLayout, button: Button, weight: Float = 1f) {
        row.addView(button, LinearLayout.LayoutParams(0, -1, weight).apply { setMargins(0, dp(3), dp(6), dp(3)) })
    }
    init {
        view.orientation = LinearLayout.VERTICAL; view.setBackgroundColor(bg); view.setPadding(dp(24), dp(12), dp(24), dp(10))
        val top = row(42)
        top.addView(text(27f, mint).apply { setText(R.string.guide_title); setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -1, 1f))
        clock = text(14f, muted).apply { text = date.format(Date()) }; top.addView(clock)
        val tools = row(46)
        listOf("Français", "★ Favoris", "Toutes").forEach { label ->
            val b = button(label) { filter = label; category = ""; applyFilters(); grid.requestFocus() }
            filters[label] = b; add(tools, b)
        }
        categoryButton = button("Catégories") { chooseCategory() }; add(tools, categoryButton, 1.15f)
        searchButton = button("Rechercher") { search() }; add(tools, searchButton, 1.15f)
        add(tools, button("? Aide") { help() }, .75f)
        val detail = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(5), 0, dp(4)) }
        heading = text(12f, mint); title = text(22f).apply { setTypeface(null, Typeface.BOLD) }; description = text(14f, muted)
        detail.addView(heading, LinearLayout.LayoutParams(-1, dp(20)))
        detail.addView(title, LinearLayout.LayoutParams(-1, dp(30)))
        detail.addView(description, LinearLayout.LayoutParams(-1, dp(24)))
        view.addView(detail, LinearLayout.LayoutParams(-1, dp(84)))
        grid.id = View.generateViewId()
        grid.programmes = { cache[it.id]?.programmes ?: emptyList() }
        grid.favourite = { isFavourite(it) }
        grid.status = { entry -> when {
            cache[entry.id]?.failed == true -> "Horaire indisponible · OK pour regarder"
            cache.containsKey(entry.id) -> "Horaire non fourni · OK pour regarder"
            else -> "Chargement de l’horaire…"
        } }
        grid.onSelection = { selection(); loadVisible() }
        grid.onWindow = { updateNotice() }
        grid.onOpen = { entry, programme -> open(entry, programme) }
        grid.onFavourite = { options() }
        view.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = row(42)
        add(actions, button("← Plus tôt") { grid.shift(-1); grid.requestFocus() })
        add(actions, button("Maintenant") { grid.now(); grid.requestFocus() })
        add(actions, button("Plus tard →") { grid.shift(1); grid.requestFocus() })
        favouriteButton = button("☆ Favori") { toggleFavourite() }; add(actions, favouriteButton)
        notice = text(12f, muted)
        view.addView(notice, LinearLayout.LayoutParams(-1, dp(22)))
        for (i in 0 until tools.childCount) tools.getChildAt(i).nextFocusDownId = grid.id
        for (i in 0 until actions.childCount) actions.getChildAt(i).nextFocusUpId = grid.id
        grid.nextFocusUpId = filters.getValue("Français").id
        grid.nextFocusDownId = actions.getChildAt(1).id
        updateNotice()
    }
    fun resume(channelId: String? = null) {
        active = true
        if (channelId != null) { focusId = channelId; filter = "Toutes"; category = ""; query = "" }
        if (all.isEmpty() || System.currentTimeMillis() - loadedAt > 300000) loadCatalogue()
        else { applyFilters(); grid.refresh(); loadVisible() }
        main.removeCallbacks(ticker); main.postDelayed(ticker, 60000)
        grid.post { grid.requestFocus() }
    }
    fun pause() {
        active = false; generation++; main.removeCallbacks(ticker)
        catalogue?.cancel(true); catalogue = null
        pending.values.forEach { it.cancel(true) }; pending.clear(); tickets.clear(); pool.purge()
    }
    fun close() { pause(); pool.shutdownNow(); main.removeCallbacksAndMessages(null) }
    private fun isFavourite(entry: Entry) = if (account == null) entry.key in demoFavourites else entry.key in store.favourites()
    private fun loadCatalogue() {
        if (!active || catalogue != null) return
        heading.setText(if(account == null) R.string.guide_demo_label else R.string.guide_tagline)
        title.setText(R.string.guide_loading); description.setText(R.string.guide_loading_hint)
        val token = generation
        catalogue = pool.submit {
            val result = runCatching { if(api == null) demoCatalogue() else api.categories("live") to api.entries("live").distinctBy { it.id } }
            main.post {
                if (!active || generation != token) return@post
                catalogue = null
                result.onSuccess { (cats, entries) ->
                    categories = cats; all = entries; loadedAt = System.currentTimeMillis()
                    if (initialFilter) {
                        filter = when {
                            focusId != null -> "Toutes"
                            all.any { isFavourite(it) } -> "★ Favoris"
                            all.any { e -> CatalogRules.matches("Français", categoryName(e), e.name) } -> "Français"
                            else -> "Toutes"
                        }; initialFilter = false
                    }
                    applyFilters()
                }.onFailure {
                    title.setText(R.string.guide_connection_failed)
                    description.setText(R.string.guide_connection_retry)
                    AlertDialog.Builder(activity).setTitle("Connexion au guide").setMessage("Le fournisseur ne répond pas. Tes favoris et tes réglages sont conservés.")
                        .setPositiveButton("Réessayer") { _, _ -> loadCatalogue() }.setNegativeButton("Fermer", null).show()
                }
            }
        }
    }
    private fun categoryName(e: Entry) = categories.firstOrNull { it.id == e.category }?.name.orEmpty()
    private fun applyFilters() {
        val terms = CatalogRules.normalize(query)
        val shown = all.filter { e ->
            (category.isEmpty() || e.category == category) && when(filter) {
                "★ Favoris" -> isFavourite(e)
                "Français" -> CatalogRules.matches("Français", categoryName(e), e.name)
                else -> true
            } && (terms.isEmpty() || CatalogRules.normalize("${e.name} ${e.number} ${categoryName(e)}").contains(terms))
        }
        filters.forEach { (label, b) -> b.isSelected = label == filter && category.isEmpty(); b.background = shape(panel, b.isSelected) }
        categoryButton.text = if(category.isEmpty()) "Catégories" else "Catégorie ✓"
        searchButton.text = if(query.isBlank()) "Rechercher" else "Recherche ✓"
        grid.setChannels(shown, focusId); focusId = null; updateNotice()
    }
    private fun loadVisible() {
        if (!active || all.isEmpty()) return
        val visible = grid.visible().map { it.id }.toSet()
        pending.keys.filter { it !in visible }.toList().forEach { pending.remove(it)?.cancel(true); tickets.remove(it) }; pool.purge()
        grid.visible().forEach { entry ->
            val cached = cache[entry.id]
            if (pending.containsKey(entry.id) || cached != null && System.currentTimeMillis() - cached.loaded < 180000) return@forEach
            val token = generation
            val ticket = ++nextTicket
            tickets[entry.id] = ticket
            pending[entry.id] = pool.submit {
                val now = System.currentTimeMillis()
                val result = runCatching { if(api == null) demoProgrammes(entry, now) else api.guide(entry) }
                if (Thread.currentThread().isInterrupted) return@submit
                main.post {
                    if (!active || token != generation || tickets[entry.id] != ticket) return@post
                    pending.remove(entry.id); tickets.remove(entry.id)
                    cache[entry.id] = Cached(result.getOrDefault(emptyList()).filter { it.end > now - GuideWindow.SPAN && it.start < now + 24 * 60 * 60 * 1000 }.take(256), now, result.isFailure)
                    grid.refresh()
                }
            }
        }
    }
    private fun selection() {
        val entry = grid.selectedEntry(); val p = grid.selectedProgramme(); val now = System.currentTimeMillis()
        heading.text = if(account == null) "DÉMO · EXEMPLE D’HORAIRE" else entry?.let { "${it.name}  ·  ${categoryName(it)}" } ?: "GUIDE TV"
        title.text = p?.title ?: if (entry == null) "Aucune chaîne dans cette sélection" else entry.name
        description.text = p?.let {
            "${time.format(Date(it.start))} – ${time.format(Date(it.end))} · ${if(it.isNow(now)) "En cours" else if(it.start > now) "À venir" else "Terminé"} · ${it.description.ifBlank { "OK pour les détails" }}"
        } ?: if(entry == null) "Choisis Toutes, change de catégorie ou efface la recherche." else grid.status(entry)
        favouriteButton.text = if(entry?.let { isFavourite(it) } == true) "★ Favori" else "☆ Ajouter favori"
    }
    private fun updateNotice() {
        notice.setText(if(account == null) R.string.guide_hint_demo else R.string.guide_hint)
        clock.text = date.format(Date(grid.windowStart))
    }
    private fun toggleFavourite() {
        val entry = grid.selectedEntry() ?: return
        if(account == null) { if(!demoFavourites.remove(entry.key)) demoFavourites.add(entry.key) } else store.toggle(entry)
        if(filter == "★ Favoris") applyFilters() else grid.refresh()
    }
    private fun chooseCategory() {
        val choices = listOf(Category("", "Toutes les catégories")) + categories
        AlertDialog.Builder(activity).setTitle("Choisir une catégorie").setItems(choices.map { it.name }.toTypedArray()) { _, which ->
            category = choices[which].id; filter = "Toutes"; applyFilters(); grid.requestFocus()
        }.setNegativeButton("Retour", null).show()
    }
    private fun search() {
        val input = EditText(activity).apply { setSingleLine(); hint = "Nom ou numéro de chaîne"; setText(query); selectAll() }
        AlertDialog.Builder(activity).setTitle("Trouver une chaîne").setView(input)
            .setPositiveButton("Rechercher") { _, _ -> query = input.text.toString().trim(); applyFilters(); grid.requestFocus() }
            .setNeutralButton("Tout effacer") { _, _ -> query = ""; applyFilters(); grid.requestFocus() }
            .setNegativeButton("Retour", null).show()
    }
    private fun options() {
        val entry = grid.selectedEntry() ?: return
        AlertDialog.Builder(activity).setTitle(entry.name).setItems(arrayOf("Regarder la chaîne en direct", "Détails de l’émission", if(isFavourite(entry)) "★ Retirer des favoris" else "☆ Ajouter aux favoris", "Revenir à maintenant", "Français / Favoris / Toutes", "Choisir une catégorie", "Rechercher une chaîne", "Comment utiliser le guide")) { _, which ->
            when(which) {
                0 -> play(entry)
                1 -> grid.selectedProgramme()?.let { details(entry, it) }
                2 -> toggleFavourite()
                3 -> grid.now()
                4 -> AlertDialog.Builder(activity).setTitle("Quelles chaînes ?").setItems(arrayOf("Français", "★ Favoris", "Toutes")) { _, i ->
                    filter = listOf("Français", "★ Favoris", "Toutes")[i]; category = ""; applyFilters(); grid.requestFocus()
                }.setNegativeButton("Retour", null).show()
                5 -> chooseCategory()
                6 -> search()
                7 -> help()
            }
        }.setNegativeButton("Retour au guide", null).show()
    }
    private fun open(entry: Entry, p: Programme?) {
        if(p == null || p.isNow(System.currentTimeMillis())) play(entry)
        else details(entry, p)
    }
    private fun details(entry: Entry, p: Programme) {
        AlertDialog.Builder(activity).setTitle(p.title)
            .setMessage("${entry.name}\n${time.format(Date(p.start))} – ${time.format(Date(p.end))}\n\n${p.description.ifBlank { "Aucune description fournie." }}\n\nLa chaîne démarre toujours en direct. Cette émission n’est pas une reprise.")
            .setPositiveButton("Regarder la chaîne") { _, _ -> play(entry) }.setNegativeButton("Retour au guide", null).show()
    }
    private fun play(entry: Entry) {
        if(account == null) AlertDialog.Builder(activity).setTitle("Tu explores le mode démo")
            .setMessage("Ces émissions sont des exemples. Connecte ton compte pour voir tes vraies chaînes et leurs horaires.")
            .setPositiveButton("Connecter mon compte") { _, _ -> connect() }.setNegativeButton("Continuer la démo", null).show()
        else watch(entry)
    }
    private fun help() {
        AlertDialog.Builder(activity).setTitle("Le guide en 3 gestes")
            .setMessage("1. ↑ ↓ : choisis une chaîne.\n2. ← → : regarde ce qui joue plus tard.\n3. OK : regarde l’émission en cours ou ouvre les détails d’une émission à venir.\n\nMenu ouvre les options : favoris, recherche et retour à maintenant.\n\n★ Favoris retrouve tes chaînes préférées. Catégories et Rechercher t’aident à trouver une chaîne.\n\nRetour ramène à l’accueil. Après la lecture, Retour retrouve ta place dans le guide.\n\nL’horaire vient de ton fournisseur. Une chaîne reste accessible même sans horaire. Jusqu’à 12 h à venir, selon les données reçues.")
            .setPositiveButton("C’est compris", null).show()
    }
    private fun demoCatalogue(): Pair<List<Category>, List<Entry>> {
        val cats = listOf(Category("qc", "Québec · Français"), Category("sport", "Sports · Français"), Category("en", "English"))
        val names = listOf("Télé-Québec", "ICI Télé", "TVA", "Noovo", "RDS", "TV5", "ICI RDI", "English TV")
        return cats to names.mapIndexed { i, name -> Entry("demo$i", name, if(i == 7) "en" else if(i == 4) "sport" else "qc", "live", number = i + 1) }
    }
    private fun demoProgrammes(e: Entry, now: Long): List<Programme> {
        val start = GuideWindow.floor(now) - GuideWindow.HALF_HOUR
        val titles = listOf("Le rendez-vous du soir", "Un été au Québec", "Les nouvelles", "La grande escapade", "Cinéma en famille", "À table !")
        val offset = e.number % titles.size
        return (0..27).map { i -> Programme(titles[(i + offset) % titles.size], "Exemple d’émission pour essayer le guide. Aucun horaire réel ni flux inclus.", start + i * GuideWindow.HALF_HOUR, start + (i + 1) * GuideWindow.HALF_HOUR) }
    }
}
