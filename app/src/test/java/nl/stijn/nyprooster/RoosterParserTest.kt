package nl.stijn.nyprooster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class RoosterParserTest {

    // Zelfde opbouw als het echte rooster, met verzonnen collega's.
    private val gesorteerd = """
        Rooster NYP Week: 46 (1/2)
        Maandag Dinsdag Woensdag Donderdag Vrijdag Zaterdag Zondag
        09 nov '26 10 nov '26 11 nov '26 12 nov '26 13 nov '26 14 nov '26 15 nov '26 Dagen Uren
        1 Anna - - 17,00 - 19,00 - - 15,00 - 22,00 - 2 8,50
        2 Bram 15,00 - 22,00 17,00 - 19,00 15,00 - 22,00 - 15,00 - 22,50 - 15,00 - 22,00 5 28,50
        - - - - - - -
        44 Stijn (Auto) 16,00 - 22,00 16,00 - 19,00 - - 16,00 - 22,50 - - 3 14,50
        58 Stijn - - - - - 16,00 - 22,50 17,00 - 19,00 2 8,00
        61Hugo (Fiets/Scooter) 17,00 - 19,00 - 17,00 - 22,00 - - - - 2 7,00
    """.trimIndent()

    @Test
    fun leestDienstenVanStijn() {
        val r = RoosterParser.parse(gesorteerd, "Stijn")
        assertEquals(46, r.weeknummer)
        assertEquals(LocalDate.of(2026, 11, 9), r.maandag)
        assertEquals(
            listOf(
                Dienst(LocalDateTime.of(2026, 11, 14, 16, 0), LocalDateTime.of(2026, 11, 14, 22, 30)),
                Dienst(LocalDateTime.of(2026, 11, 15, 17, 0), LocalDateTime.of(2026, 11, 15, 19, 0)),
            ),
            r.diensten,
        )
    }

    @Test
    fun naamMetSpatieWordtNietVerward() {
        // "Stijn (Auto)" mag niet als "Stijn" gelezen worden (cellen beginnen dan met "(Auto)").
        val r = RoosterParser.parse(gesorteerd, "Stijn")
        assertEquals(2, r.diensten.size)
        val auto = RoosterParser.parse(gesorteerd, "Stijn (Auto)")
        assertEquals(3, auto.diensten.size)
    }

    @Test
    fun naamZonderSpatieNaNummer() {
        val r = RoosterParser.parse(gesorteerd, "Hugo (Fiets/Scooter)")
        assertEquals(LocalDateTime.of(2026, 11, 9, 17, 0), r.diensten[0].start)
        assertEquals(LocalDateTime.of(2026, 11, 11, 22, 0), r.diensten[1].eind)
    }

    @Test
    fun datumsInWillekeurigeVolgorde() {
        // Zonder sortering op positie komen de kopdatums door elkaar.
        val ongesorteerd = """
            58 Stijn - - - - - 16,00 - 22,50 17,00 - 19,00 2 8,00
            Maandag Dinsdag Donderdag
            Rooster NYP
            09 nov '26 12 nov '26 14 nov '26
            10 nov '26 11 nov '26 13 nov '26 15 nov '26
            Week: 46
        """.trimIndent()
        val r = RoosterParser.parse(ongesorteerd, "stijn")
        assertEquals(LocalDate.of(2026, 11, 9), r.maandag)
        assertEquals(2, r.diensten.size)
    }

    @Test
    fun jaarwisseling() {
        val tekst = """
            Rooster NYP Week: 1
            28 dec '26 29 dec '26 30 dec '26 31 dec '26 01 jan '27 02 jan '27 03 jan '27
            58 Stijn 17,00 - 19,00 - - - 16,00 - 22,50 - - 2 8,00
        """.trimIndent()
        // 28 dec 2026 is maandag van ISO-week 53 van 2026, dus week 1 klopt niet -> fout.
        // Met week 53 gaat het goed:
        val r = RoosterParser.parse(tekst.replace("Week: 1", "Week: 53"), "Stijn")
        assertEquals(LocalDate.of(2026, 12, 28), r.maandag)
        assertEquals(LocalDateTime.of(2027, 1, 1, 16, 0), r.diensten[1].start)
    }

    @Test
    fun alleenWeeknummerZonderDatums() {
        val tekst = "Rooster NYP Week: 46\n58 Stijn - - - - - 16,00 - 22,50 17,00 - 19,00 2 8,00"
        val r = RoosterParser.parse(tekst, "Stijn", vandaag = LocalDate.of(2026, 11, 8))
        assertEquals(LocalDate.of(2026, 11, 9), r.maandag)
    }

    @Test
    fun dienstTotNaMiddernacht() {
        val tekst = "Rooster NYP Week: 46\n09 nov '26\n58 Stijn 18,00 - 01,00 - - - - - - 1 7,00"
        val d = RoosterParser.parse(tekst, "Stijn").diensten.single()
        assertEquals(LocalDateTime.of(2026, 11, 10, 1, 0), d.eind)
    }

    @Test
    fun geenDienstenIsGeldig() {
        val tekst = "Rooster NYP Week: 46\n09 nov '26\n58 Stijn - - - - - - - 0 0,00"
        assertTrue(RoosterParser.parse(tekst, "Stijn").diensten.isEmpty())
    }

    @Test
    fun vasteSpatiesEnTabs() {
        val tekst = "Rooster NYP Week: 46\r\n09 nov '26\r\n58\u00A0Stijn\t-\u00A0- - - - 16,00\u00A0-\u00A022,50 17,00 - 19,00 2 8,00\r\n"
        assertEquals(2, RoosterParser.parse(tekst, "Stijn").diensten.size)
    }

    @Test
    fun cellenMoetenLosStaan() {
        // Geen 7 losse cellen -> geen rij.
        assertEquals(null, RoosterParser.leesCellen("- - - x - - - -"))
        assertEquals(null, RoosterParser.leesCellen("16,00-22,50- - - - - - -"))
        assertEquals(null, RoosterParser.leesCellen("- - - - - -"))
        assertEquals(7, RoosterParser.leesCellen("- - - - - 16,00 - 22,50 17,00 - 19,00 2 8,00")!!.size)
    }

    @Test(expected = RoosterFout::class)
    fun naamOntbreekt() {
        RoosterParser.parse(gesorteerd, "Piet")
    }

    @Test
    fun herkentGeenRooster() {
        assertFalse(RoosterParser.isRooster("Factuur 2026-11 bedrag 12,00"))
        assertTrue(RoosterParser.isRooster(gesorteerd))
    }

    @Test
    fun tijden() {
        assertEquals("22:30", RoosterParser.parseTijd("22", "50").toString())
        assertEquals("16:15", RoosterParser.parseTijd("16", "25").toString())
        assertEquals("17:45", RoosterParser.parseTijd("17", "75").toString())
        assertEquals("17:30", RoosterParser.parseTijd("17", "30").toString())
    }

    /**
     * Test op een echt rooster-PDF (niet in git, i.v.m. namen van collega's):
     *   ROOSTER_PDF=/pad/naar/rooster.pdf ./gradlew test
     */
    @Test
    fun echtePdf() {
        val pad = System.getenv("ROOSTER_PDF")
        assumeTrue(pad != null && File(pad).exists())
        val tekst = org.apache.pdfbox.pdmodel.PDDocument.load(File(pad)).use { doc ->
            org.apache.pdfbox.text.PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }
        val r = RoosterParser.parse(tekst, System.getenv("ROOSTER_NAAM") ?: "Stijn")
        println("Week ${r.weeknummer}, maandag ${r.maandag}")
        r.diensten.forEach { println("  ${it.start} - ${it.eind}") }
        assertTrue(r.weeknummer != null)
    }
}
