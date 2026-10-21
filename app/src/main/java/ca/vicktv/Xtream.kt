package ca.vicktv

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

object CatalogRules {
    fun normalize(s: String): String = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
    private val adultCategory = Regex("(^|[^a-z0-9])adults?(?:e|es)?([^a-z0-9]|$)")
    private val adultMarker = Regex("(^|[^a-z0-9])(?:xxx|porn(?:o|os|ography|ographique|ographiques)?|erotic(?:a)?|erotiques?|18\\s*(?:\\+|plus)|playboy|penthouse|hustler|brazzers)(?=$|[^a-z0-9])")
    fun isAdult(category: String, title: String, marked: Boolean = false): Boolean {
        if (marked) return true
        val group = normalize(category).replace(Regex("\\badult\\s+swim\\b"), "")
        return adultCategory.containsMatchIn(group) || adultMarker.containsMatchIn("$group ${normalize(title)}")
    }
    fun matches(section: String, category: String, title: String, markedAdult: Boolean = false): Boolean {
        val text = normalize("$category $title")
        return when (section) {
            "Adultes" -> isAdult(category, title, markedAdult)
            "Sports" -> Regex("sport|rds|tsn|espn|bein|dazn|nhl|nfl|nba|mlb|hockey|football").containsMatchIn(text)
            "Français" -> Regex("(^|[^a-z])(fr|fra|fre|vf|vff|vfq|vostfr|qc|quebec|france|francais|french)([^a-z]|$)|radio.?canada|tele.?quebec|tva|noovo|rds").containsMatchIn(text)
            "Québec" -> Regex("(^|[^a-z])(qc|quebec|quebecois|quebecoise|quebecoises|vfq)([^a-z]|$)|radio.?canada|tele.?quebec|tva|noovo").containsMatchIn(text)
            else -> true
        }
    }
    fun safeExtension(s: String, fallback: String) = s.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: fallback
}

data class Category(val id: String, val name: String)
data class Entry(val id: String, val name: String, val category: String, val kind: String, val extension: String = "ts", val number: Int = 0, val adult: Boolean = false) {
    val key get() = "$kind:$id"
}
class Xtream(private val account: Account) {
    private val root = account.server.trimEnd('/')
    private fun api(action: String? = null, extra: Pair<String, String>? = null, epg: Boolean = false): String {
        val b = Uri.parse("$root/player_api.php").buildUpon().appendQueryParameter("username", account.username).appendQueryParameter("password", account.password)
        action?.let { b.appendQueryParameter("action", it) }; extra?.let { b.appendQueryParameter(it.first, it.second) }
        if (action == "get_short_epg") b.appendQueryParameter("limit", "64")
        return Network.text(b.build().toString(), if (epg) 5000 else 15000, if (epg) 8000 else 25000, if (epg) 2 * 1024 * 1024 else 32 * 1024 * 1024)
    }
    fun authenticate() {
        val o = JSONObject(api()).optJSONObject("user_info") ?: error("Réponse Xtream non reconnue.")
        if (o.optInt("auth") != 1 || o.optString("status").equals("Disabled", true) || o.optString("status").equals("Expired", true))
            error("Connexion refusée ou abonnement expiré.")
    }
    fun categories(kind: String): List<Category> {
        val a = JSONArray(api("get_${kind}_categories"))
        return (0 until a.length()).map { a.getJSONObject(it) }.map { Category(it.optString("category_id"), it.optString("category_name", "Autres")) }
    }
    fun entries(kind: String, category: String? = null): List<Entry> {
        val action = if (kind == "series") "get_series" else "get_${kind}_streams"
        val a = JSONArray(api(action, category?.let { "category_id" to it }))
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Entry(it.optString(if (kind == "series") "series_id" else "stream_id"), it.optString("name", "Sans titre"),
                it.optString("category_id"), kind, CatalogRules.safeExtension(it.optString("container_extension"), if (kind == "live") "ts" else "mp4"), it.optInt("num"), it.optInt("is_adult") == 1 || it.optBoolean("is_adult"))
        }.filter { it.id.matches(Regex("\\d+")) }
    }
    fun episodes(series: Entry): List<Entry> {
        val all = JSONObject(api("get_series_info", "series_id" to series.id)).optJSONObject("episodes") ?: return emptyList()
        return all.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }.flatMap { season ->
            val a = all.optJSONArray(season) ?: JSONArray()
            (0 until a.length()).map { a.getJSONObject(it) }.sortedBy { it.optInt("episode_num") }.map {
                Entry(it.optString("id"), "S$season · ${it.optString("title", "Épisode ${it.optInt("episode_num")}")}", series.category, "episode", CatalogRules.safeExtension(it.optString("container_extension"), "mp4"))
            }
        }.filter { it.id.matches(Regex("\\d+")) }
    }
    @Volatile private var zoneLoaded = false
    private var serverZone: java.time.ZoneId? = null
    @Synchronized private fun zone(): java.time.ZoneId? {
        if (!zoneLoaded) {
            serverZone = runCatching { java.time.ZoneId.of(JSONObject(api(epg = true)).getJSONObject("server_info").getString("timezone")) }.getOrNull()
            zoneLoaded = true
        }
        return serverZone
    }
    fun guide(channel: Entry): List<Programme> {
        fun parse(raw: String): List<Programme> {
            val parsed = Guide.parse(raw)
            return if (parsed.isEmpty() && (JSONObject(raw).optJSONArray("epg_listings")?.length() ?: 0) > 0) Guide.parse(raw, zone()) else parsed
        }
        val full = runCatching { parse(api("get_simple_data_table", "stream_id" to channel.id, true)) }
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
        if (full.getOrNull()?.any { it.end > System.currentTimeMillis() } == true) return full.getOrThrow()
        return parse(api("get_short_epg", "stream_id" to channel.id, true))
    }
    fun stream(e: Entry, hls: Boolean): String {
        val path = when (e.kind) { "live" -> "live"; "vod" -> "movie"; else -> "series" }
        val ext = if (e.kind == "live" && hls) "m3u8" else e.extension
        return "$root/$path/${Uri.encode(account.username)}/${Uri.encode(account.password)}/${e.id}.$ext"
    }
}

object Network {
    fun connection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 25000
        setRequestProperty("User-Agent", "VickTV/${BuildConfig.VERSION_NAME}")
    }
    fun text(url: String, connectMs: Int = 15000, readMs: Int = 25000, maxChars: Int = 32 * 1024 * 1024): String {
        val c = connection(url).apply { connectTimeout = connectMs; readTimeout = readMs }
        return try {
            if (c.responseCode !in 200..299) error("Le serveur ne répond pas correctement (HTTP ${c.responseCode}).")
            c.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val result = StringBuilder(); val buffer = CharArray(8192)
                while (true) { if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException(); val n = reader.read(buffer); if (n < 0) break; result.append(buffer, 0, n); if (result.length > maxChars) error("Réponse trop volumineuse pour cette version.") }
                result.toString()
            }
        } finally { c.disconnect() }
    }
}
