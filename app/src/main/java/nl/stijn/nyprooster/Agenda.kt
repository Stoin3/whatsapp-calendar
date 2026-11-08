package nl.stijn.nyprooster

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import java.time.ZoneId

data class AgendaInfo(val id: Long, val naam: String, val account: String)

/** Schrijft diensten via de Android-agenda; die synchroniseert zelf met Google Agenda. */
class Agenda(private val context: Context) {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val markering = "Automatisch toegevoegd door de NYP Rooster-app"

    /** Alle agenda's waarin geschreven mag worden. */
    fun beschrijfbareAgendas(): List<AgendaInfo> {
        val lijst = mutableListOf<AgendaInfo>()
        context.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.CALENDAR_ACCESS_LEVEL),
            "${Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                lijst += AgendaInfo(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")
            }
        }
        return lijst
    }

    fun zoek(naam: String): AgendaInfo {
        val alle = beschrijfbareAgendas()
        val treffers = alle.filter { it.naam.equals(naam, ignoreCase = true) }
        // Bij voorkeur een Google-account (synchroniseert naar Google Agenda).
        return treffers.firstOrNull { "@" in it.account }
            ?: treffers.firstOrNull()
            ?: throw RoosterFout(
                "Agenda '$naam' niet gevonden op de telefoon. Beschikbaar: " +
                    alle.joinToString { it.naam }.ifEmpty { "(geen)" } +
                    ". Staat agenda-synchronisatie aan voor dit Google-account?"
            )
    }

    /**
     * Vervangt de diensten van deze week: eerder door de app geplaatste afspraken in die week
     * worden verwijderd en de nieuwe toegevoegd. Zo geeft een bijgewerkt rooster geen dubbele afspraken.
     * @return aantal verwijderde oude afspraken
     */
    fun zetWeek(agenda: AgendaInfo, titel: String, rooster: Rooster): Int {
        val van = rooster.maandag.atStartOfDay(zone).toInstant().toEpochMilli()
        val tot = rooster.maandag.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val cr = context.contentResolver

        var verwijderd = 0
        cr.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID),
            "${Events.CALENDAR_ID} = ? AND ${Events.DTSTART} >= ? AND ${Events.DTSTART} < ? " +
                "AND ${Events.DESCRIPTION} LIKE ? AND ${Events.DELETED} = 0",
            arrayOf(agenda.id.toString(), van.toString(), tot.toString(), "%$markering%"),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                verwijderd += cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, c.getLong(0)), null, null)
            }
        }

        for (d in rooster.diensten) {
            val waarden = ContentValues().apply {
                put(Events.CALENDAR_ID, agenda.id)
                put(Events.TITLE, titel)
                put(Events.DESCRIPTION, "NYP rooster week ${rooster.weeknummer ?: "?"}\n$markering")
                put(Events.DTSTART, d.start.atZone(zone).toInstant().toEpochMilli())
                put(Events.DTEND, d.eind.atZone(zone).toInstant().toEpochMilli())
                put(Events.EVENT_TIMEZONE, zone.id)
                put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
            }
            cr.insert(Events.CONTENT_URI, waarden) ?: throw RoosterFout("Afspraak toevoegen mislukt.")
        }
        return verwijderd
    }
}
