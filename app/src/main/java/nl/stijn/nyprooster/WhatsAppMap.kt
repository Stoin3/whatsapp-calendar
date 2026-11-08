package nl.stijn.nyprooster

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/** Zoekt nieuwe PDF's in de map waar WhatsApp ontvangen documenten opslaat. */
object WhatsAppMap {
    private const val MAX_LEEFTIJD_MS = 14L * 24 * 60 * 60 * 1000

    fun mappen(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        return listOf("com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business").flatMap { (pkg, naam) ->
            listOf(
                File(root, "Android/media/$pkg/$naam/Media/$naam Documents"), // Android 11+
                File(root, "$naam/Media/$naam Documents"),                     // oudere WhatsApp
            )
        }.filter { it.isDirectory }
    }

    fun heeftToegang(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        else true // READ_EXTERNAL_STORAGE wordt in de app gevraagd

    /** Ontvangen PDF's van de laatste 14 dagen (niet de map "Sent"). */
    fun recentePdfs(): List<File> {
        val grens = System.currentTimeMillis() - MAX_LEEFTIJD_MS
        val result = mutableListOf<File>()
        fun loop(map: File, diepte: Int) {
            for (f in map.listFiles() ?: return) {
                when {
                    f.isDirectory && diepte < 2 && !f.name.equals("Sent", true) && !f.name.startsWith(".") -> loop(f, diepte + 1)
                    f.isFile && f.name.endsWith(".pdf", true) && f.lastModified() >= grens -> result += f
                }
            }
        }
        mappen().forEach { loop(it, 0) }
        return result.sortedBy { it.lastModified() }
    }

    /** Verwerkt alle nog niet bekeken PDF's. @return aantal toegevoegde roosters */
    fun scan(context: Context, alles: Boolean = false): Int {
        if (!heeftToegang()) {
            Instellingen(context).log("⚠️ Geen toegang tot bestanden; kan WhatsApp-map niet lezen")
            return 0
        }
        val inst = Instellingen(context)
        if (alles) inst.vergeetBekeken()
        val verwerker = Verwerker(context)
        var toegevoegd = 0
        for (f in recentePdfs()) {
            val sleutel = "${f.absolutePath}|${f.length()}|${f.lastModified()}"
            if (inst.isBekeken(sleutel)) continue
            val bytes = try { f.readBytes() } catch (e: Exception) { continue } // nog bezig met downloaden?
            if (bytes.size < 5 || String(bytes, 0, 5) != "%PDF-") continue
            val uitkomst = verwerker.verwerk(bytes, f.name, forceer = false)
            // Ook bij een fout als bekeken markeren (anders elk uur dezelfde foutmelding).
            // "Nu scannen" in de app probeert alles opnieuw.
            inst.markeerBekeken(sleutel)
            if (uitkomst is Verwerker.Uitkomst.Toegevoegd) toegevoegd++
        }
        return toegevoegd
    }
}
