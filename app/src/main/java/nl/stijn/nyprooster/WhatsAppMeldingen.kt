package nl.stijn.nyprooster

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Leest meldingen van WhatsApp mee. Komt er een bericht in de ingestelde groep binnen,
 * dan wordt de WhatsApp-documentenmap een paar keer gecontroleerd op een nieuw rooster.
 */
class WhatsAppMeldingen : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val groep = Instellingen(this).groep
        if (groep.isBlank()) return
        val teksten = alleTeksten(sbn.notification.extras)
        if (teksten.any { it.contains(groep, ignoreCase = true) }) {
            ScanWorker.planNaMelding(this)
        }
    }

    private fun alleTeksten(e: Bundle): List<String> {
        val lijst = mutableListOf<String>()
        listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG, Notification.EXTRA_CONVERSATION_TITLE,
            "android.hiddenConversationTitle", Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT,
        ).forEach { k -> e.getCharSequence(k)?.let { lijst += it.toString() } }
        e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { lijst += it.toString() }
        @Suppress("DEPRECATION")
        val berichten: Array<Parcelable>? = e.getParcelableArray(Notification.EXTRA_MESSAGES)
        berichten?.forEach { b -> (b as? Bundle)?.getCharSequence("text")?.let { lijst += it.toString() } }
        return lijst
    }
}
