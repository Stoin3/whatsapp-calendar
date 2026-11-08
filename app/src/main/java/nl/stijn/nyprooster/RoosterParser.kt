package nl.stijn.nyprooster

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.IsoFields

/** Eén dienst uit het rooster. */
data class Dienst(val start: LocalDateTime, val eind: LocalDateTime)

/** Resultaat van het uitlezen van één rooster-PDF voor één persoon. */
data class Rooster(
    val weeknummer: Int?,
    /** Maandag van de roosterweek. */
    val maandag: LocalDate,
    val diensten: List<Dienst>,
)

class RoosterFout(message: String) : Exception(message)

/**
 * Leest de tekst van een NYP-rooster ("Rooster NYP  Week: 46") uit.
 *
 * Verwachte opbouw (tekst zoals PDFBox die met sortering op positie teruggeeft):
 * ```
 * Rooster NYP Week: 46 (1/2)
 * Maandag Dinsdag Woensdag Donderdag Vrijdag Zaterdag Zondag
 * 09 nov '26 10 nov '26 11 nov '26 12 nov '26 13 nov '26 14 nov '26 15 nov '26 Dagen Uren
 * 58 Stijn - - - - - 16,00 - 22,50 17,00 - 19,00 2 8,00
 * ```
 * Elke rij heeft 7 cellen (ma t/m zo): "-" (vrij) of "begin - eind".
 * Tijden zijn decimaal genoteerd: "22,50" = 22:30 (zie [parseTijd]).
 */
object RoosterParser {

    private val MAANDEN = mapOf(
        "jan" to 1, "feb" to 2, "mrt" to 3, "maa" to 3, "apr" to 4, "mei" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    // "09 nov '26", "9 nov. '26", "09 nov 2026"
    private val DATUM = Regex(
        """(\d{1,2})\s+([a-zA-Z]{3})[a-zA-Z]*\.?\s+(?:['’`´]\s?(\d{2})\b|(\d{4})\b)"""
    )
    private val WEEK = Regex("""Week\s*:?\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
    private val TIJD = """(\d{1,2})[,.:](\d{2})"""
    private val CEL = Regex("""\G\s*(?:$TIJD\s*[-–]\s*$TIJD|[-–])(?=\s|$)""")

    /** Ziet de tekst eruit als een NYP-rooster? */
    fun isRooster(tekst: String): Boolean =
        tekst.contains("Rooster", ignoreCase = true) && WEEK.containsMatchIn(tekst)

    fun parse(tekst: String, naam: String, vandaag: LocalDate = LocalDate.now()): Rooster {
        if (!isRooster(tekst)) throw RoosterFout("Dit lijkt geen NYP-rooster (geen 'Rooster' / 'Week:' gevonden).")
        val weeknummer = WEEK.find(tekst)?.groupValues?.get(1)?.toInt()
        val maandag = bepaalMaandag(tekst, weeknummer, vandaag)
        val cellen = vindRij(tekst, naam)
            ?: throw RoosterFout("Naam '$naam' niet gevonden in het rooster van week ${weeknummer ?: "?"}.")

        val diensten = cellen.mapIndexedNotNull { i, cel ->
            if (cel == null) return@mapIndexedNotNull null
            val dag = maandag.plusDays(i.toLong())
            val start = dag.atTime(cel.first)
            var eind = dag.atTime(cel.second)
            if (!eind.isAfter(start)) eind = eind.plusDays(1) // dienst tot na middernacht
            Dienst(start, eind)
        }
        return Rooster(weeknummer, maandag, diensten)
    }

    /** Maandag van de roosterweek: uit de datums in de kop, anders uit het weeknummer. */
    internal fun bepaalMaandag(tekst: String, weeknummer: Int?, vandaag: LocalDate): LocalDate {
        val datums = DATUM.findAll(tekst).mapNotNull { m ->
            val dag = m.groupValues[1].toInt()
            val maand = MAANDEN[m.groupValues[2].lowercase()] ?: return@mapNotNull null
            val jaar = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { 2000 + it.toInt() }
                ?: m.groupValues[4].toInt()
            runCatching { LocalDate.of(jaar, maand, dag) }.getOrNull()
        }.toList()

        if (datums.isNotEmpty()) {
            // Neem de maandag van de vroegste datum (de kop loopt ma t/m zo).
            val eerste = datums.min()
            val maandag = eerste.with(DayOfWeek.MONDAY)
            if (weeknummer != null && maandag.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != weeknummer) {
                throw RoosterFout(
                    "Datums in het rooster (${eerste}) passen niet bij week $weeknummer; rooster niet verwerkt."
                )
            }
            return maandag
        }
        if (weeknummer == null) throw RoosterFout("Geen datums en geen weeknummer gevonden in het rooster.")
        // Geen datums leesbaar: kies het jaar waarin deze week het dichtst bij vandaag ligt.
        return listOf(vandaag.year - 1, vandaag.year, vandaag.year + 1)
            .map { jaar ->
                LocalDate.of(jaar, 1, 4)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, weeknummer.toLong())
                    .with(DayOfWeek.MONDAY)
            }
            .minBy { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(vandaag, it)) }
    }

    /** Zoekt de rij van [naam] en geeft 7 cellen terug (null = vrij). */
    internal fun vindRij(tekst: String, naam: String): List<Pair<LocalTime, LocalTime>?>? {
        val rij = Regex("""^\s*\d*\s*${Regex.escape(naam.trim())}\s+(.*)$""", RegexOption.IGNORE_CASE)
        for (regel in tekst.lines()) {
            val m = rij.find(regel) ?: continue
            val cellen = leesCellen(m.groupValues[1]) ?: continue
            return cellen
        }
        return null
    }

    /** Leest precies 7 cellen vanaf het begin van [rest]; null als dat niet lukt. */
    internal fun leesCellen(rest: String): List<Pair<LocalTime, LocalTime>?>? {
        val cellen = mutableListOf<Pair<LocalTime, LocalTime>?>()
        var pos = 0
        while (cellen.size < 7) {
            val m = CEL.find(rest, pos) ?: return null
            if (m.range.first != pos) return null
            pos = m.range.last + 1
            val g = m.groupValues
            cellen += if (g[1].isEmpty()) null else parseTijd(g[1], g[2]) to parseTijd(g[3], g[4])
        }
        return cellen
    }

    /**
     * Het rooster gebruikt decimale uren: ",50" = half (16,00 - 22,50 is 16:00–22:30).
     * ,25 / ,50 / ,75 worden als kwartieren gelezen; andere waarden (bv. ,30 of ,45) als minuten.
     */
    internal fun parseTijd(uur: String, deel: String): LocalTime {
        val u = uur.toInt() % 24
        val minuten = when (deel) {
            "25" -> 15
            "50", "5" -> 30
            "75" -> 45
            else -> deel.toInt().coerceIn(0, 59)
        }
        return LocalTime.of(u, minuten)
    }
}
