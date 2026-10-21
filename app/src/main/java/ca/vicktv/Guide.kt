package ca.vicktv

import org.json.JSONObject
import java.util.Base64

data class Programme(val title: String, val description: String, val start: Long, val end: Long) {
    fun isNow(now: Long) = now in start until end
}

object Guide {
    private fun decode(value: String): String = runCatching {
        val decoded = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
        if ('\uFFFD' in decoded || decoded.any { it.isISOControl() && it !in "\n\r\t" }) value else decoded
    }.getOrDefault(value)

    fun parse(raw: String): List<Programme> {
        val rows = JSONObject(raw).optJSONArray("epg_listings") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val start = row.optLong("start_timestamp") * 1000L
            val end = row.optLong("stop_timestamp", row.optLong("end_timestamp")) * 1000L
            if (start <= 0 || end <= start) null else Programme(
                decode(row.optString("title")).ifBlank { "Émission sans titre" },
                decode(row.optString("description")), start, end
            )
        }.distinctBy { it.start to it.title }.sortedBy { it.start }
    }
}
