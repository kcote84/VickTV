package ca.vicktv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.io.File
import java.net.URI
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private val work = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var requestId = 0
    private var screen = "home"
    private var current: Entry? = null
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var playbackPosition = 0L
    private var stopped = false
    private var greetingPending = true
    private var audioRestored = false
    private var retries = 0
    private var retryTask: Runnable? = null
    private var busy = false
    private var pendingInstall: File? = null
    private var browseTitle = ""
    private var browseKind = "live"
    private var browseSection = ""
    private var browseCategory: String? = null
    private var categories = listOf<Category>()
    private var items = listOf<Entry>()
    private var list: ListView? = null
    private var search: EditText? = null
    private var languageFilter = "Tous"
    private var listTitle = ""
    private var listSection = ""
    private var listFilters = false
    private var shownEntries = emptyList<Entry>()
    private var lastListSelection = 0
    private data class ListState(val title: String, val section: String, val filters: Boolean, val screen: String,
        val categories: List<Category>, val entries: List<Entry>, val query: String, val selected: Int, val language: String)
    private var playerOrigin: ListState? = null
    private var seriesOrigin: ListState? = null
    private var guideScreen: TvGuideScreen? = null
    private var returnToGuide = false
    private val bg = TvTheme.background
    private val panel = TvTheme.panel
    private val accent = TvTheme.accent
    private val muted = TvTheme.muted
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = Store(this)
        home()
    }
    override fun onStart() {
        super.onStart()
        if (screen == "guide") guideScreen?.resume()
        if (screen == "player" && player == null && current != null) play(current!!, playbackPosition)
        if ((greetingPending || stopped) && screen == "home" && store.prefs.getBoolean("love", true)) {
            greetingPending = false
            main.post { if (!isFinishing && screen == "home") love() }
        }
        stopped = false
        if (screen == "settings" && pendingInstall != null) settings()
    }
    override fun onStop() {
        guideScreen?.pause()
        stopped = true
        playbackPosition = player?.currentPosition ?: playbackPosition
        releasePlayer()
        super.onStop()
    }
    override fun onDestroy() { requestId++; guideScreen?.close(); releasePlayer(); work.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroy() }
    @Deprecated("TV back navigation")
    override fun onBackPressed() {
        when (screen) {
            "home" -> finish()
            "player" -> leavePlayer()
            "episodes" -> seriesOrigin?.let { restoreList(it) } ?: home()
            else -> { releasePlayer(); current = null; home() }
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (screen == "player" && keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { player?.let { if (it.isPlaying) it.pause() else it.play() }; return true }
        if (screen == "player" && keyCode == KeyEvent.KEYCODE_MENU) { audioMenu(); return true }
        if (list?.hasFocus() == true && keyCode == KeyEvent.KEYCODE_MENU) {
            shownEntries.getOrNull(list!!.selectedItemPosition)?.let { entryMenu(it) }; return true
        }
        return super.onKeyDown(keyCode, event)
    }
    private fun shape(color: Int, border: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(12).toFloat(); if (border != null) setStroke(dp(2), border)
    }
    private fun label(value: String, size: Float = 18f, color: Int = Color.WHITE) = TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, dp(5), 0, dp(5)) }
    private fun button(title: String, action: () -> Unit): Button = Button(this).apply {
        text = title; textSize = 16f; isAllCaps = false; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        minHeight = dp(48); setPadding(dp(18), dp(8), dp(18), dp(8)); background = shape(panel)
        defaultFocusHighlightEnabled = false
        setOnFocusChangeListener { _, focus -> background = shape(if (focus) accent else panel); setTextColor(if (focus) bg else Color.WHITE) }
        setOnClickListener { action() }
    }
    private fun addButton(parent: LinearLayout, title: String, action: () -> Unit) = button(title, action).also {
        parent.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(8) })
    }
    private fun page(name: String, subtitle: String): LinearLayout {
        if (name != "guide") guideScreen?.pause()
        requestId++; busy = false; screen = name; list = null; search = null
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(34), dp(18), dp(34), dp(14)); setBackgroundColor(bg) }
        setContentView(root)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("VickTv", 28f, accent).apply { setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(label("POUR VICKY, AVEC AMOUR", 12f, muted))
        root.addView(header)
        root.addView(label(subtitle, 16f, muted))
        return root
    }
    private fun home() {
        playerOrigin = null; seriesOrigin = null; returnToGuide = false
        page("home", "Ta soirée, tout simplement.")
        root.addView(label("Qu’est-ce qu’on regarde ?", 26f).apply { setTypeface(null, Typeface.BOLD) })
        if (store.account() == null) root.addView(label("Bienvenue ! Connecte ton compte ou explore le mode démo.", 16f, muted))
        val guide = button("Guide TV   ·   Ce qui joue maintenant et plus tard") { openTvGuide() }.apply { textSize = 20f }
        root.addView(guide, LinearLayout.LayoutParams(-1, dp(62)).apply { setMargins(0, dp(8), dp(12), 0) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
        val sections = listOf("Français" to "CHAÎNES FRANCOPHONES", "Sports" to "LE DIRECT, EN GRAND", "Films" to "SOIRÉE CINÉMA", "Séries" to "ENCORE UN ÉPISODE")
        sections.forEach { (title, detail) ->
            val b = button("$title\n\n$detail") {
                if (store.account() == null) demo(title) else browse(title, when(title) { "Films" -> "vod"; "Séries" -> "series"; else -> "live" }, title)
            }.apply { textSize = 17f }
            row.addView(b, LinearLayout.LayoutParams(0, dp(100), 1f).apply { setMargins(0, dp(10), dp(12), dp(10)) })
        }
        root.addView(row)
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf<Pair<String, () -> Unit>>(
            "★ Favoris" to { if (store.account() == null) demo("Favoris") else favourites() },
            "Toutes les chaînes" to { if (store.account() == null) demo("Toutes les chaînes") else browse("Toutes les chaînes", "live", "") },
            "Adultes 18+" to { adults() },
            "Réglages" to { settings() }
        ).forEach { (t, f) -> footer.addView(button(t, f), LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(12) }) }
        root.addView(footer)
        root.addView(label("Flèches pour naviguer  ·  OK pour choisir  ·  Retour pour revenir", 12f, muted))
        guide.requestFocus()
    }
    private fun adults() {
        if (store.account() == null) { demo("Adultes 18+"); return }
        AlertDialog.Builder(this).setTitle("Adultes 18+")
            .setItems(arrayOf("Chaînes en direct", "Films", "Séries")) { _, choice ->
                browse("Adultes · ${listOf("Chaînes", "Films", "Séries")[choice]}", listOf("live", "vod", "series")[choice], "Adultes")
            }.setNegativeButton("Retour", null).show()
    }
    private fun openTvGuide(channelId: String? = null) {
        releasePlayer(); current = null
        val account = store.account()
        if (guideScreen == null || guideScreen?.account != account) {
            guideScreen?.close()
            guideScreen = TvGuideScreen(this, account, store, { entry -> returnToGuide = true; playerOrigin = null; play(entry) }, { login() })
        }
        page("guide", "")
        root.removeAllViews(); root.setPadding(0, 0, 0, 0)
        val guide = guideScreen!!
        (guide.view.parent as? ViewGroup)?.removeView(guide.view)
        root.addView(guide.view, LinearLayout.LayoutParams(-1, -1))
        guide.resume(channelId)
    }
    private fun love() {
        val index = store.prefs.getInt("love_index", 0)
        store.prefs.edit().putInt("love_index", (index + 1) % LoveNotes.messages.size).apply()
        AlertDialog.Builder(this).setTitle("Un petit mot pour Vicky ♡").setMessage(LoveNotes.at(index)).setPositiveButton("Bonne soirée ♡", null).show()
    }
    private fun demo(section: String) {
        page("demo", "$section · Mode démo")
        root.addView(label("Ton espace $section", 28f))
        root.addView(label("Les vrais contenus apparaîtront après la connexion à ton compte Xtream.\nAucune chaîne n’est fournie avec VickTv.", 18f, muted))
        addButton(root, "Connecter mon compte Xtream") { login() }
        addButton(root, "Retour à l’accueil") { home() }
    }
    private fun settings() {
        page("settings", "Une fois réglé, on profite.")
        val scroll = ScrollView(this); val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; scroll.addView(body); root.addView(scroll)
        addButton(body, if (store.account() == null) "Connecter mon compte Xtream" else "Modifier mon compte Xtream") { login() }
        addButton(body, "Mots d’amour : ${if(store.prefs.getBoolean("love", true)) "activés" else "désactivés"}") { store.prefs.edit().putBoolean("love", !store.prefs.getBoolean("love", true)).apply(); settings() }
        addButton(body, "Format du direct : ${if(store.prefs.getBoolean("hls", false)) "HLS" else "MPEG-TS"}") { store.prefs.edit().putBoolean("hls", !store.prefs.getBoolean("hls", false)).apply(); settings() }
        addButton(body, "Audio : ${audioModeLabel()}") {
            AlertDialog.Builder(this).setTitle("Préférence audio").setItems(arrayOf("Automatique", "Stéréo", "Cinéma / 5.1")) { _, which ->
                store.prefs.edit().putString("audio_mode", listOf("auto", "stereo", "surround")[which]).apply(); settings()
            }.show()
        }
        addButton(body, "Éviter l’audiodescription : ${if(store.prefs.getBoolean("avoid_description", true)) "oui" else "non"}") {
            store.prefs.edit().putBoolean("avoid_description", !store.prefs.getBoolean("avoid_description", true)).apply(); settings()
        }
        addButton(body, "Vérifier les mises à jour · ${BuildConfig.VERSION_NAME}") { checkUpdate() }
        if (pendingInstall != null) addButton(body, "Installer la mise à jour téléchargée") { installUpdate() }
        addButton(body, "Retour") { home() }
        body.addView(label("Stéréo et 5.1 préfèrent une piste disponible; la sortie dépend de ta télé et du Fire Stick.\nL’audiodescription est évitée par défaut si une piste normale est disponible.\nLes comptes restent sur ce Fire Stick. Android 8 / Fire OS 7 ou supérieur.", 14f, muted))
    }
    private fun audioMode() = store.prefs.getString("audio_mode", if(store.prefs.getBoolean("stereo", false)) "stereo" else "auto") ?: "auto"
    private fun audioModeLabel() = when(audioMode()) { "stereo" -> "Stéréo"; "surround" -> "Cinéma / 5.1"; else -> "Automatique" }
    private fun field(hintText: String, value: String = "", secret: Boolean = false) = EditText(this).apply {
        hint = hintText; setText(value); textSize = 16f; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(muted)
        inputType = if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setPadding(dp(12), dp(8), dp(12), dp(8)); background = shape(panel); contentDescription = hintText
    }
    private fun login() {
        page("login", "Un compte distinct sur chaque Fire Stick.")
        val account = store.account()
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(form) }; root.addView(scroll)
        val server = field("Serveur : https://exemple.com:port", account?.server ?: "")
        val user = field("Nom d’utilisateur", account?.username ?: "")
        val password = field("Mot de passe", account?.password ?: "", true)
        listOf(server, user, password).forEach { form.addView(it, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) }) }
        addButton(form, "Se connecter") {
            if (busy) return@addButton
            val url = server.text.toString().trim().trimEnd('/')
            val valid = runCatching { val uri = URI(url); uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/") }.getOrDefault(false)
            if (!valid || user.text.isBlank() || password.text.isEmpty()) { message("Connexion", "Entre l’adresse du serveur (sans player_api.php), ton utilisateur et ton mot de passe."); return@addButton }
            val a = Account(url, user.text.toString().trim(), password.text.toString())
            val connect = {
                async("Connexion en cours…", { Xtream(a).authenticate(); val old = store.account(); if(old?.server != a.server || old.username != a.username) store.clearFavourites(); store.save(a) }) { toast("Connexion réussie"); home() }
            }
            if (url.startsWith("http://")) AlertDialog.Builder(this).setTitle("Serveur sans HTTPS")
                .setMessage("Cette adresse transmet tes identifiants sans chiffrement réseau. Utilise HTTPS si ton fournisseur le permet.")
                .setNegativeButton("Modifier", null).setPositiveButton("Utiliser cette adresse") { _, _ -> connect() }.show() else connect()
        }
        addButton(form, "Retour") { home() }
        form.addView(label("Tes identifiants sont chiffrés sur cet appareil. Ils ne sont jamais envoyés à GitHub.", 12f, muted))
        server.requestFocus()
    }
    private fun favourites() {
        page("browse", "Tes favoris, réunis au même endroit.")
        items = store.favouriteEntries(); categories = emptyList()
        languageFilter = "Tous"
        buildList("Favoris", "Favoris", false)
    }
    private fun browse(title: String, kind: String, section: String, category: String? = null) {
        if (kind != browseKind || title != browseTitle) languageFilter = "Tous"
        browseTitle = title; browseKind = kind; browseSection = section; browseCategory = category
        page("browse", "$title · Choisis une catégorie ou cherche un titre.")
        async("Chargement du catalogue…", {
            val api = Xtream(store.account() ?: error("Connecte ton compte."))
            api.categories(kind) to api.entries(kind, category)
        }) { (cats, entries) -> categories = cats; items = entries; buildList(title, section, true) }
    }
    private fun buildList(title: String, section: String, filters: Boolean) {
        listTitle = title; listSection = section; listFilters = filters
        root.addView(label(title, 25f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val query = field("Rechercher…"); search = query
        controls.addView(query, LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(button("Chercher") { renderRows(section) }, LinearLayout.LayoutParams(dp(110), dp(46)).apply { leftMargin = dp(8) })
        if(filters) controls.addView(button("Catégories") { chooseCategory() }, LinearLayout.LayoutParams(dp(130), dp(46)).apply { leftMargin = dp(8) })
        root.addView(controls)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (filters && browseKind in listOf("vod", "series")) actions.addView(button("Langue : $languageFilter") {
            AlertDialog.Builder(this).setTitle("Trouver un contenu").setItems(arrayOf("Tous", "Français", "Québec")) { _, which ->
                val state = captureList()!!.copy(language = listOf("Tous", "Français", "Québec")[which], selected = 0)
                restoreList(state)
            }.show()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        actions.addView(button("Options / Favoris") { selectedEntry()?.let { entryMenu(it) } ?: toast("Choisis d’abord un titre dans la liste.") }, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(actions)
        val hint = label("OK : ouvrir  ·  Menu : favoris et horaire  ·  Appui long OK : favori", 13f, muted); root.addView(hint)
        list = ListView(this).apply { dividerHeight = dp(6); isFocusable = true; setPadding(0, dp(4), 0, 0); clipToPadding = false }
        list?.selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        lastListSelection = 0
        list?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { lastListSelection = position }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        renderRows(section); list?.requestFocus()
    }
    private fun renderRows(section: String) {
        val query = CatalogRules.normalize(search?.text.toString())
        val names = categories.associate { it.id to it.name }
        val favourites = store.favourites()
        val shown = items.filter { (section != "Favoris" || it.key in favourites) && CatalogRules.matches(section, names[it.category] ?: "", it.name, it.adult) &&
            (!listFilters || CatalogRules.matches(languageFilter, names[it.category] ?: "", it.name)) && CatalogRules.normalize(it.name).contains(query) }
        shownEntries = shown
        val labels = shown.map { "${if(it.key in favourites) "★  " else ""}${it.name}\n${names[it.category] ?: when(it.kind) { "live" -> "Chaîne en direct"; "vod" -> "Film"; "series" -> "Série"; else -> "Épisode" }}" }
        list?.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = (super.getView(position, convertView, parent) as TextView).apply {
                textSize = 18f; setTextColor(Color.WHITE); setPadding(dp(16), dp(8), dp(16), dp(8)); minHeight = dp(68)
                background = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_selected), shape(TvTheme.selected, accent))
                    addState(intArrayOf(android.R.attr.state_pressed), shape(TvTheme.selected, accent))
                    addState(intArrayOf(), shape(panel))
                }
            }
        }
        list?.emptyView = null
        if (shown.isEmpty()) { list?.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listOf(if(section == "Favoris") "Tes favoris apparaîtront ici. Utilise Options / Favoris sur un titre." else "Aucun résultat. Essaie une autre langue ou catégorie.")) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = (super.getView(position, convertView, parent) as TextView).apply { setTextColor(muted); textSize = 17f }
        }; list?.setOnItemClickListener(null); list?.setOnItemLongClickListener(null) }
        else {
            list?.setOnItemClickListener { _, _, index, _ -> lastListSelection = index; val entry = shown[index]; if(entry.kind == "series") episodes(entry) else play(entry) }
            list?.setOnItemLongClickListener { _, _, index, _ -> toast(if(store.toggle(shown[index])) "Ajouté aux favoris" else "Retiré des favoris"); renderRows(section); list?.setSelection(index); true }
        }
    }
    private fun selectedEntry() = shownEntries.getOrNull(list?.selectedItemPosition?.takeIf { it >= 0 } ?: lastListSelection)
    private fun captureList(): ListState? = if (screen in listOf("browse", "episodes") && list != null)
        ListState(listTitle, listSection, listFilters, screen, categories, items, search?.text.toString(), lastListSelection, languageFilter) else null
    private fun restoreList(state: ListState) {
        page(state.screen, state.title); categories = state.categories; items = state.entries; languageFilter = state.language
        buildList(state.title, state.section, state.filters); search?.setText(state.query); renderRows(state.section)
        lastListSelection = state.selected.coerceAtMost((shownEntries.size - 1).coerceAtLeast(0))
        list?.setSelection(lastListSelection); list?.requestFocus()
    }
    private fun leavePlayer() {
        releasePlayer(); current = null
        if(returnToGuide) openTvGuide() else playerOrigin?.let { restoreList(it) } ?: home()
    }
    private fun entryMenu(entry: Entry) {
        val options = mutableListOf(if(entry.kind == "series") "Voir les épisodes" else "Regarder", if(entry.key in store.favourites()) "Retirer des favoris" else "Ajouter aux favoris")
        if(entry.kind == "live") options.add("Guide TV · cette chaîne")
        AlertDialog.Builder(this).setTitle(entry.name).setItems(options.toTypedArray()) { _, which -> when(which) {
            0 -> if(entry.kind == "series") episodes(entry) else play(entry)
            1 -> { val selection = shownEntries.indexOfFirst { it.key == entry.key }.coerceAtLeast(0); toast(if(store.toggle(entry)) "Ajouté aux favoris" else "Retiré des favoris"); renderRows(listSection); list?.setSelection(selection.coerceAtMost((shownEntries.size - 1).coerceAtLeast(0))) }
            2 -> openTvGuide(entry.id)
        } }.setNegativeButton("Fermer", null).show()
    }
    private fun chooseCategory() {
        val options = listOf(Category("", "Toutes les catégories")) + categories
        AlertDialog.Builder(this).setTitle("Catégories du fournisseur").setItems(options.map { it.name }.toTypedArray()) { _, which -> browse(browseTitle, browseKind, if(browseSection == "Adultes") "Adultes" else "", options[which].id.ifEmpty { null }) }.setNegativeButton("Annuler", null).show()
    }
    private fun episodes(series: Entry) {
        seriesOrigin = captureList()
        categories = emptyList()
        page("episodes", series.name)
        async("Chargement des épisodes…", { Xtream(store.account()!!).episodes(series) }) { entries -> items = entries; buildList(series.name, "", false) }
    }
    private fun releasePlayer() { retryTask?.let { main.removeCallbacks(it) }; retryTask = null; playerView?.player = null; player?.release(); player = null; playerView = null }
    private fun play(entry: Entry, position: Long = 0L) {
        captureList()?.let { playerOrigin = it }
        releasePlayer(); current = entry; playbackPosition = position; audioRestored = false; retries = 0
        page("player", entry.name)
        root.removeAllViews(); root.setPadding(0, 0, 0, 0)
        val heading = label(entry.name, 20f, accent).apply {
            setPadding(dp(24), dp(8), dp(24), dp(8)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(heading, LinearLayout.LayoutParams(-1, dp(48)))
        val video = PlayerView(this).apply { useController = true; controllerShowTimeoutMs = 5000; setShowSubtitleButton(true); setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) }
        root.addView(video, LinearLayout.LayoutParams(-1, 0, 1f)); playerView = video
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val playbackActions = mutableListOf<Pair<String, () -> Unit>>("Audio / Son" to { audioMenu() }, "Infos" to { streamInfo() }, "★ Favori" to { toast(if(store.toggle(entry)) "Ajouté aux favoris" else "Retiré des favoris") }, "Reprendre" to { retryTask?.let { main.removeCallbacks(it) }; retries = 0; player?.prepare(); player?.play() })
        if(entry.kind == "live") playbackActions.add("Guide TV" to { openTvGuide(entry.id) })
        playbackActions.add("Retour" to { leavePlayer() })
        playbackActions.forEach { (t, f) -> row.addView(button(t, f).apply { textSize = 14f; setPadding(dp(6), 0, dp(6), 0) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) }) }
        root.addView(row)
        video.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            val chrome = if(visibility == View.VISIBLE || row.hasFocus()) View.VISIBLE else View.GONE
            heading.visibility = chrome; row.visibility = chrome
        })
        val useHls = entry.kind == "live" && store.prefs.getBoolean("hls", false)
        val factory = DefaultMediaSourceFactory(DefaultHttpDataSource.Factory().setUserAgent("VickTv/${BuildConfig.VERSION_NAME}").setConnectTimeoutMs(15000).setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(false))
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(factory).build(); player = p; video.player = p
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setPreferredAudioLanguage("fr").setMaxAudioChannelCount(if(audioMode() == "stereo") 2 else Int.MAX_VALUE).build()
        p.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { restoreAudio(tracks) }
            override fun onPlaybackStateChanged(state: Int) { if(state == Player.STATE_READY) retries = 0 }
            override fun onPlayerError(error: PlaybackException) {
                if (screen != "player" || player !== p) return
                if (retries < 3 && PlaybackErrors.shouldRetry(PlaybackErrors.httpStatus(error))) {
                    retries++; toast("Reconnexion $retries/3…")
                    retryTask = Runnable { if(player === p && screen == "player") { p.prepare(); p.play() } }.also { main.postDelayed(it, 1500L * retries) }
                } else playbackError(entry, useHls, error)
            }
        })
        val url = Xtream(store.account()!!).stream(entry, useHls)
        p.setMediaItem(MediaItem.fromUri(url)); if(entry.kind != "live") p.seekTo(position); p.prepare(); p.play(); video.requestFocus()
    }
    private fun playbackError(entry: Entry, hls: Boolean, error: PlaybackException) {
        val status = PlaybackErrors.httpStatus(error)
        val details = buildString {
            append(PlaybackErrors.explanation(status))
            if(entry.kind == "live") append("\nFormat du direct : ${if(hls) "HLS" else "MPEG-TS"}")
            status?.let { append("\nHTTP : $it") }
            append("\nCode : ${error.errorCodeName}")
            if(hls) append("\n\nPasser en MPEG-TS change le format pour toutes les chaînes en direct.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("Lecture interrompue").setMessage(details)
            .setNegativeButton("Fermer", null)
        if(hls) dialog.setPositiveButton("Passer en MPEG-TS") { _, _ ->
            store.prefs.edit().putBoolean("hls", false).apply()
            play(entry)
        }
        dialog.show()
    }
    private fun fingerprint(f: Format): String = listOf(f.language ?: "", f.label ?: "", f.sampleMimeType ?: "", f.channelCount.toString(), f.roleFlags.toString()).joinToString("|")
    private fun restoreAudio(tracks: Tracks) {
        if(audioRestored) return
        val p = player ?: return; val key = current?.key ?: return
        val options = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }
        if(options.isEmpty()) return
        val saved = store.prefs.getString("audio:$key", null)
        val choices = options.map { (g, i) -> val f = g.getTrackFormat(i); AudioChoice(fingerprint(f), f.language, f.label, f.channelCount,
            f.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0 || AudioRules.isDescription(f.label)) }
        val chosen = AudioRules.choose(choices, saved, audioMode(), store.prefs.getBoolean("avoid_description", true))?.let { options[it] }
        audioRestored = true
        if(choices.all { it.described } && store.prefs.getBoolean("avoid_description", true)) toast("Le fournisseur propose seulement des pistes avec audiodescription.")
        chosen?.let { p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(it.first.mediaTrackGroup, it.second)).build() }
    }
    private fun audioMenu() {
        val p = player ?: return
        val options = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).map { g to it } }
        if(options.isEmpty()) { message("Audio", "Aucune piste détectée pour le moment. Attends le démarrage de la chaîne."); return }
        val titles = options.map { (g,i) -> val f = g.getTrackFormat(i); "${if(g.isTrackSelected(i)) "✓ " else ""}${f.label ?: f.language ?: "Piste ${i+1}"} · ${f.channelCount.takeIf { it > 0 } ?: "?"} canaux · ${f.sampleMimeType ?: "audio"}${if(f.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0 || AudioRules.isDescription(f.label)) " · Description" else ""}${if(!g.isTrackSupported(i)) " · non prise en charge" else ""}" }
        AlertDialog.Builder(this).setTitle("Choisir le son").setItems(titles.toTypedArray()) { _, which ->
            val (g,i) = options[which]
            if(!g.isTrackSupported(i)) { toast("Ce format n’est pas pris en charge par cet appareil."); return@setItems }
            store.prefs.edit().putString("audio:${current!!.key}", fingerprint(g.getTrackFormat(i))).apply()
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        }.setNeutralButton("Choix automatique") { _, _ ->
            store.prefs.edit().remove("audio:${current!!.key}").apply()
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
            audioRestored = false; restoreAudio(p.currentTracks)
        }.setNegativeButton("Fermer", null).show()
    }
    private fun streamInfo() {
        val p = player ?: return
        val info = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).filter { g.isTrackSelected(it) }.map { g.getTrackFormat(it) } }.joinToString("\n") { "${it.sampleMimeType} · ${it.channelCount} canaux · ${it.language ?: "langue inconnue"}" }
        message("Infos du flux", "${current?.name}\n$info\nVidéo : ${p.videoFormat?.width ?: "?"} × ${p.videoFormat?.height ?: "?"}\nCes données décrivent le flux reçu, pas la sortie HDMI réelle.")
    }
    private fun checkUpdate() {
        if(busy) return
        val updater = Updater(this)
        async("Recherche d’une mise à jour…", { updater.check() }) { update ->
            if(update == null) message("VickTv ${BuildConfig.VERSION_NAME}", "Tu as la dernière version publiée.")
            else AlertDialog.Builder(this).setTitle("VickTv ${update.version}").setMessage(update.notes).setNegativeButton("Plus tard", null).setPositiveButton("Télécharger") { _, _ ->
                async("Téléchargement et vérification…", { updater.download(update) }) { file -> pendingInstall = file; installUpdate() }
            }.show()
        }
    }
    private fun installUpdate() {
        val file = pendingInstall ?: return
        if(!file.isFile) {
            pendingInstall = null
            settings()
            message("Téléchargement à reprendre", "Le fichier de mise à jour n’est plus disponible. Choisis Vérifier les mises à jour pour le télécharger de nouveau.")
            return
        }
        val updater = Updater(this)
        if(!updater.canInstall()) AlertDialog.Builder(this).setTitle("Autoriser la mise à jour")
            .setMessage("Autorise VickTv à installer des applications. Reviens ensuite dans Réglages et choisis Installer la mise à jour téléchargée.")
            .setNegativeButton("Plus tard", null).setPositiveButton("Ouvrir les réglages") { _, _ -> runCatching { updater.requestPermission() }.onFailure { message("Autorisation", "Va dans Ma Fire TV → Options développeur → Installer des applications inconnues, puis autorise VickTv.") } }.show()
        else runCatching { updater.install(file) }.onFailure { message("Installation", "L’installateur n’a pas pu s’ouvrir sur cet appareil.") }
    }
    private fun <T> async(status: String, task: () -> T, success: (T) -> Unit) {
        val token = requestId; busy = true
        val progress = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)))
        val statusView = label(status, 14f, muted); root.addView(statusView)
        work.execute {
            val result = runCatching { task() }
            main.post {
                if(isFinishing || isDestroyed || token != requestId) return@post
                busy = false; root.removeView(progress); root.removeView(statusView)
                result.onSuccess(success).onFailure { error ->
                    val text = when {
                        error is java.net.SocketTimeoutException -> "Le serveur met trop de temps à répondre. Réessaie."
                        error is java.net.UnknownHostException -> "Adresse introuvable. Vérifie le serveur et la connexion Internet."
                        error is org.json.JSONException -> "Réponse du serveur non reconnue. Vérifie l’adresse Xtream."
                        error is java.io.IOException -> "Connexion impossible. Vérifie Internet et le serveur. Pour une mise à jour, vérifie qu’une release GitHub existe."
                        else -> error.message?.takeIf { !it.contains("http") && !it.contains("password") } ?: "L’opération n’a pas pu être terminée."
                    }
                    message("VickTv", text)
                    if(screen == "browse" || screen == "episodes") addButton(root, "Retour à l’accueil") { home() }
                }
            }
        }
    }
    private fun message(title: String, text: String) { if(!isFinishing) AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show() }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
}
