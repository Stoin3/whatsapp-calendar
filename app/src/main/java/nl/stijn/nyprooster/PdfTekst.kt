package nl.stijn.nyprooster

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfTekst {
    @Volatile private var klaar = false

    fun lees(context: Context, bytes: ByteArray): String {
        if (!klaar) {
            PDFBoxResourceLoader.init(context.applicationContext)
            klaar = true
        }
        PDDocument.load(bytes).use { doc ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            return stripper.getText(doc)
        }
    }
}
