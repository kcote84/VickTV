package ca.vicktv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Account(val server: String, val username: String, val password: String)

class Store(context: Context) {
    val prefs = context.getSharedPreferences("vicktv", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("vicktv.account", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("vicktv.account", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(account: Account) {
        val raw = JSONObject().put("server", account.server).put("username", account.username).put("password", account.password).toString()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(raw.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString("account", enc).apply()
    }
    fun account(): Account? = runCatching {
        val parts = (prefs.getString("account", null) ?: return null).split(":")
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))) }
        val o = JSONObject(String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        Account(o.getString("server"), o.getString("username"), o.getString("password"))
    }.getOrNull()
    fun clearAccount() { prefs.edit().remove("account").apply() }
    fun favourites(): Set<String> = prefs.getStringSet("favourites", emptySet())!!.toSet()
    fun clearFavourites() { prefs.edit().remove("favourites").remove("favourite_details").apply() }
    fun favouriteEntries(): List<Entry> = runCatching {
        val all = JSONObject(prefs.getString("favourite_details", "{}")!!)
        favourites().mapNotNull { id -> all.optJSONObject(id)?.let { Entry(it.getString("id"), it.getString("name"), it.getString("category"), it.getString("kind"), it.getString("extension"), it.optInt("number")) } }.sortedBy { it.name }
    }.getOrDefault(emptyList())
    fun toggle(entry: Entry): Boolean {
        val values = favourites().toMutableSet()
        val details = JSONObject(prefs.getString("favourite_details", "{}")!!)
        val added = if (values.remove(entry.key)) { details.remove(entry.key); false } else {
            values.add(entry.key)
            details.put(entry.key, JSONObject().put("id", entry.id).put("name", entry.name).put("category", entry.category).put("kind", entry.kind).put("extension", entry.extension).put("number", entry.number))
            true
        }
        prefs.edit().putStringSet("favourites", values).putString("favourite_details", details.toString()).apply()
        return added
    }
}
