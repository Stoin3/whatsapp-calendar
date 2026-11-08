package nl.stijn.nyprooster

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Van PDF-bytes naar afspraken in de agenda. */
class Verwerker(private val context: Context) {
    private val inst = Instellingen(context)

    sealed class Uitkomst {
        data class Toegevoegd(val samenvatting: String) : Uitkomst()
        object AlVerwerkt : Uitkomst()
        object GeenRooster : Uitkomst()
        data class Fout(val melding: String) : Uitkomst()
    }

    /**
     * @param bron korte omschrijving voor het logboek (bestandsnaam)
     * @param forceer ook verwerken als precies deze PDF al eerder verwerkt is
     */
    fun verwerk(bytes: ByteArray, bron: String, forceer: Boolean): Uitkomst {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (!forceer && inst.isVerwerkt(hash)) return Uitkomst.AlVerwerkt

        val uitkomst = try {
            val tekst = PdfTekst.lees(context, bytes)
            if (!RoosterParser.isRooster(tekst)) {
                Uitkomst.GeenRooster
            } else {
                val rooster = RoosterParser.parse(tekst, inst.naam)
                if (!heeftAgendaRechten()) throw RoosterFout("Geen toestemming voor de agenda. Open de app.")
                val agenda = Agenda(context)
                val doel = agenda.zoek(inst.agenda)
                val oud = agenda.zetWeek(doel, inst.titel, rooster)
                inst.markeerVerwerkt(hash)
                Uitkomst.Toegevoegd(samenvatting(rooster, oud))
            }
        } catch (e: RoosterFout) {
            Uitkomst.Fout(e.message ?: "Onbekende fout")
        } catch (e: Exception) {
            Uitkomst.Fout("${e.javaClass.simpleName}: ${e.message}")
        }

        when (uitkomst) {
            is Uitkomst.Toegevoegd -> {
                inst.log("✅ $bron: ${uitkomst.samenvatting}")
                meld("Rooster in ${inst.agenda} gezet", uitkomst.samenvatting)
            }
            is Uitkomst.Fout -> {
                inst.log("❌ $bron: ${uitkomst.melding}")
                meld("Rooster niet verwerkt", uitkomst.melding)
            }
            Uitkomst.GeenRooster -> if (forceer) inst.log("ℹ️ $bron is geen NYP-rooster")
            Uitkomst.AlVerwerkt -> {}
        }
        return uitkomst
    }

    private fun samenvatting(r: Rooster, oud: Int): String {
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale("nl", "NL"))
        val uur = DateTimeFormatter.ofPattern("HH:mm")
        val week = "Week ${r.weeknummer ?: "?"}"
        val vervangen = if (oud > 0) " ($oud oude vervangen)" else ""
        if (r.diensten.isEmpty()) return "$week: geen diensten voor ${inst.naam}$vervangen"
        return "$week: " + r.diensten.joinToString(", ") { "${it.start.format(fmt)}–${it.eind.format(uur)}" } + vervangen
    }

    private fun heeftAgendaRechten() =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun meld(titel: String, tekst: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAAL, "Rooster verwerkt", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, KANAAL)
            .setSmallIcon(R.drawable.ic_melding)
            .setContentTitle(titel)
            .setContentText(tekst)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tekst))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }

    companion object {
        const val KANAAL = "rooster"
    }
}
