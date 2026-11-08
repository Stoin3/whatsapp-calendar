package nl.stijn.nyprooster

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppTest {

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun hoofdschermStartZonderFouten() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertTrue(!activity.isFinishing)
    }

    /** Echte pdfbox-android tekstextractie (zoals op de telefoon) op een rooster-PDF uit ROOSTER_PDF. */
    @Test
    fun pdfboxAndroidLeestEchtRooster() {
        val pad = System.getenv("ROOSTER_PDF")
        assumeTrue(pad != null && File(pad).exists())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tekst = PdfTekst.lees(context, File(pad).readBytes())
        val r = RoosterParser.parse(tekst, System.getenv("ROOSTER_NAAM") ?: "Stijn")
        println("pdfbox-android: week ${r.weeknummer}, ${r.diensten}")
        assertEquals(46, r.weeknummer)
        assertEquals(2, r.diensten.size)
    }
}
