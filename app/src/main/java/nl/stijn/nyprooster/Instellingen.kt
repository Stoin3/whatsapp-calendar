package nl.stijn.nyprooster

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Instellingen(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("instellingen", Context.MODE_PRIVATE)

    var naam: String
        get() = p.getString("naam", "Stijn")!!
        set(v) = p.edit().putString("naam", v.trim()).apply()

    var agenda: String
        get() = p.getString("agenda", "Gezinsagenda")!!
        set(v) = p.edit().putString("agenda", v.trim()).apply()

    var titel: String
        get() = p.getString("titel", "Stijn NYP Werk")!!
        set(v) = p.edit().putString("titel", v.trim()).apply()

    /** Bestanden die al bekeken zijn ("pad|grootte|gewijzigd"). */
    fun isBekeken(sleutel: String) = p.getStringSet("bekeken", emptySet())!!.contains(sleutel)
    fun markeerBekeken(sleutel: String) = voegToe("bekeken", sleutel, 300)
    fun vergeetBekeken() = p.edit().remove("bekeken").apply()

    /** SHA-256 van PDF's die al in de agenda staan. */
    fun isVerwerkt(hash: String) = p.getStringSet("verwerkt", emptySet())!!.contains(hash)
    fun markeerVerwerkt(hash: String) = voegToe("verwerkt", hash, 200)

    private fun voegToe(key: String, waarde: String, max: Int) {
        val lijst = LinkedHashSet(p.getStringSet(key, emptySet())!!)
        lijst += waarde
        while (lijst.size > max) lijst.remove(lijst.first())
        p.edit().putStringSet(key, lijst).apply()
    }

    fun log(regel: String) {
        val tijd = SimpleDateFormat("dd-MM HH:mm", Locale("nl", "NL")).format(Date())
        val oud = p.getString("log", "")!!.lines().filter { it.isNotBlank() }.take(60)
        p.edit().putString("log", (listOf("$tijd  $regel") + oud).joinToString("\n")).apply()
    }

    val logTekst: String get() = p.getString("log", "")!!

    fun registreer(l: SharedPreferences.OnSharedPreferenceChangeListener) = p.registerOnSharedPreferenceChangeListener(l)
    fun afmelden(l: SharedPreferences.OnSharedPreferenceChangeListener) = p.unregisterOnSharedPreferenceChangeListener(l)
}
