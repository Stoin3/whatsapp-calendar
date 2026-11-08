package nl.stijn.nyprooster

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var inst: Instellingen
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private val velden = mutableMapOf<String, EditText>()
    private val logLuisteraar = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "log") runOnUiThread { logView.text = inst.logTekst.ifEmpty { "(nog niets)" } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inst = Instellingen(this)
        setContentView(bouwScherm())
        ScanWorker.planPeriodiek(this)
        verwerkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        verwerkIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        inst.registreer(logLuisteraar)
        logView.text = inst.logTekst.ifEmpty { "(nog niets)" }
        toonStatus()
    }

    override fun onPause() {
        super.onPause()
        inst.afmelden(logLuisteraar)
    }

    // ---------- scherm ----------

    private fun bouwScherm(): View {
        val d = resources.displayMetrics.density
        val kolom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (32 * d).toInt())
        }
        fun kop(t: String) = kolom.addView(TextView(this).apply {
            text = t; textSize = 18f; setPadding(0, (20 * d).toInt(), 0, (6 * d).toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        fun tekst(t: String) = kolom.addView(TextView(this).apply { text = t; textSize = 14f })
        fun knop(t: String, actie: () -> Unit) = kolom.addView(Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { actie() }
        })

        kolom.addView(TextView(this).apply { text = "NYP Rooster → Agenda"; textSize = 22f })
        tekst("Zet het weekrooster uit de WhatsApp-groep automatisch in je Google Agenda.")

        kop("1. Toestemmingen")
        status = TextView(this).apply { textSize = 14f; setLineSpacing(0f, 1.3f) }
        kolom.addView(status)
        knop("Agenda-toegang geven") { vraagAgenda() }
        knop("Toegang tot bestanden geven (WhatsApp-map)") { vraagBestanden() }
        knop("Batterijbeperking uitzetten") { vraagBatterij() }
        tekst(
            "Zet in WhatsApp → Instellingen → Opslag en gegevens → Automatisch downloaden: " +
                "\"Documenten\" aan (wifi én mobiel), anders wordt de PDF niet automatisch opgeslagen."
        )

        kop("2. Instellingen")
        fun veld(key: String, label: String, waarde: String) {
            tekst(label)
            kolom.addView(EditText(this).apply {
                setText(waarde); inputType = InputType.TYPE_CLASS_TEXT; isSingleLine = true
            }.also { velden[key] = it })
        }
        veld("naam", "Naam in het rooster", inst.naam)
        veld("agenda", "Agenda", inst.agenda)
        veld("titel", "Titel van de afspraak", inst.titel)
        knop("Agenda kiezen uit lijst…") { kiesAgenda() }
        knop("Instellingen opslaan") { slaOp(); Toast.makeText(this, "Opgeslagen", Toast.LENGTH_SHORT).show() }

        kop("3. Rooster verwerken")
        tekst(
            "Automatisch: elke 15 minuten wordt de WhatsApp-documentenmap gecontroleerd. Alleen PDF's " +
                "die een NYP-rooster zijn worden verwerkt.\nHandmatig: deel de PDF vanuit WhatsApp met \"NYP Rooster\", of:"
        )
        knop("Rooster-PDF kiezen…") {
            slaOp()
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf"),
                KIES_PDF,
            )
        }
        knop("WhatsApp-map nu scannen") {
            slaOp()
            opAchtergrond("Scannen…") {
                val n = WhatsAppMap.scan(this, alles = true)
                if (n == 0) "Geen nieuw rooster gevonden in ${WhatsAppMap.mappen().size} map(pen)." else "$n rooster(s) toegevoegd."
            }
        }

        kop("Logboek")
        logView = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        kolom.addView(logView)

        return ScrollView(this).apply { fitsSystemWindows = true; addView(kolom) }
    }

    private fun slaOp() {
        velden["naam"]?.text?.toString()?.takeIf { it.isNotBlank() }?.let { inst.naam = it }
        velden["agenda"]?.text?.toString()?.takeIf { it.isNotBlank() }?.let { inst.agenda = it }
        velden["titel"]?.text?.toString()?.takeIf { it.isNotBlank() }?.let { inst.titel = it }
    }

    private fun toonStatus() {
        fun r(ok: Boolean, t: String) = (if (ok) "✅ " else "❌ ") + t
        val agendaOk = heeft(Manifest.permission.READ_CALENDAR) && heeft(Manifest.permission.WRITE_CALENDAR)
        val bestandOk = if (Build.VERSION.SDK_INT >= 30) WhatsAppMap.heeftToegang()
        else heeft(Manifest.permission.READ_EXTERNAL_STORAGE)
        val batterijOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val mappen = if (bestandOk) WhatsAppMap.mappen().size else 0
        status.text = listOf(
            r(agendaOk, "Agenda"),
            r(bestandOk, "Bestanden" + if (bestandOk) " ($mappen WhatsApp-documentenmap gevonden)" else ""),
            r(batterijOk, "Geen batterijbeperking"),
        ).joinToString("\n")
    }

    // ---------- toestemmingen ----------

    private fun heeft(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun vraagAgenda() {
        val p = mutableListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(p.toTypedArray(), 1)
    }

    private fun vraagBestanden() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun vraagBatterij() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        toonStatus()
    }

    private fun kiesAgenda() {
        if (!heeft(Manifest.permission.READ_CALENDAR)) { vraagAgenda(); return }
        val lijst = Agenda(this).beschrijfbareAgendas()
        if (lijst.isEmpty()) {
            Toast.makeText(this, "Geen agenda's gevonden", Toast.LENGTH_LONG).show(); return
        }
        AlertDialog.Builder(this)
            .setTitle("Kies agenda")
            .setItems(lijst.map { "${it.naam}\n${it.account}" }.toTypedArray()) { _, i ->
                velden["agenda"]?.setText(lijst[i].naam)
                slaOp()
            }
            .show()
    }

    // ---------- PDF verwerken ----------

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == KIES_PDF && resultCode == RESULT_OK) data?.data?.let { verwerkUri(it) }
    }

    private fun verwerkIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        uri?.let { verwerkUri(it) }
        setIntent(Intent(this, MainActivity::class.java)) // niet opnieuw verwerken bij draaien scherm
    }

    private fun verwerkUri(uri: Uri) {
        opAchtergrond("Rooster lezen…") {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@opAchtergrond "Kon het bestand niet openen."
            when (val u = Verwerker(this).verwerk(bytes, uri.lastPathSegment ?: "gedeelde PDF", forceer = true)) {
                is Verwerker.Uitkomst.Toegevoegd -> u.samenvatting
                is Verwerker.Uitkomst.Fout -> "Mislukt: ${u.melding}"
                Verwerker.Uitkomst.GeenRooster -> "Dit is geen NYP-rooster."
                Verwerker.Uitkomst.AlVerwerkt -> "Al verwerkt."
            }
        }
    }

    private fun opAchtergrond(bezig: String, werk: () -> String) {
        Toast.makeText(this, bezig, Toast.LENGTH_SHORT).show()
        Thread {
            val resultaat = try { werk() } catch (e: Exception) { "Fout: ${e.message}" }
            runOnUiThread {
                AlertDialog.Builder(this).setMessage(resultaat).setPositiveButton("OK", null).show()
                toonStatus()
            }
        }.start()
    }

    companion object {
        private const val KIES_PDF = 10
    }
}
