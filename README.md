# NYP Rooster → Google Agenda

Android-app die het weekrooster van New York Pizza uit de WhatsApp-groep
**Info NYP Egelantierlaan** leest en jouw diensten automatisch in de agenda
**Gezinsagenda** zet als **Stijn NYP Werk**.

## Hoe het werkt

1. WhatsApp downloadt het rooster-PDF automatisch naar
   `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/`.
2. De app leest WhatsApp-meldingen mee. Komt er een bericht in de groep binnen,
   dan kijkt de app (na 20 s, 1, 3 en 10 min) of er een nieuw rooster-PDF in die map staat.
   Daarnaast kijkt de app elk uur, voor het geval een melding gemist is.
3. De PDF wordt op de telefoon zelf uitgelezen (geen internet nodig). De rij met jouw naam
   geeft per dag de begin- en eindtijd. Tijden zijn decimaal: `22,50` = 22:30.
4. De diensten worden via de Android-agenda in **Gezinsagenda** gezet; die synchroniseert
   vanzelf naar Google Agenda. Komt er een bijgewerkt rooster voor dezelfde week, dan worden
   de eerder door de app geplaatste afspraken van die week vervangen (geen dubbele afspraken).
   Zelf gemaakte afspraken worden nooit aangeraakt.

Handmatig kan ook: in WhatsApp de PDF openen → **Delen** → **NYP Rooster**,
of in de app **Rooster-PDF kiezen…**.

## Installeren

1. Download `app-release.apk` (bij *Actions* → laatste run → *NYP-Rooster-apk*) en open hem
   op de telefoon. Sta "installeren uit onbekende bronnen" toe.
2. Open **NYP Rooster** en geef in stap 1 alle toestemmingen:
   - **Agenda** (en meldingen, om te laten weten dat het rooster erin staat)
   - **Toegang tot bestanden** – om de WhatsApp-documentenmap te lezen
   - **Meldingtoegang** – om berichten in de groep op te merken.
     Is dit grijs met "Beperkte instelling"? Instellingen → Apps → NYP Rooster →
     ⋮ (rechtsboven) → **Beperkte instellingen toestaan**, en probeer opnieuw.
   - **Batterijbeperking uitzetten** – anders stopt o.a. Samsung/Xiaomi de app op de achtergrond.
3. WhatsApp → Instellingen → Opslag en gegevens → **Automatisch downloaden media**:
   zet **Documenten** aan bij wifi én mobiele data.
4. Controleer bij stap 2 de instellingen (naam `Stijn`, agenda `Gezinsagenda`, titel
   `Stijn NYP Werk`, groep `Info NYP Egelantierlaan`). Met **Agenda kiezen uit lijst…**
   zie je welke agenda's op de telefoon beschikbaar zijn.
5. Test: open het rooster in WhatsApp → Delen → NYP Rooster. In het logboek zie je het resultaat.

## Ontwikkelen

```
./gradlew testDebugUnitTest      # tests
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

Testen op een echt rooster (PDF's staan niet in git i.v.m. namen van collega's):

```
ROOSTER_PDF=/pad/naar/rooster.pdf ./gradlew testDebugUnitTest -i
```

De APK wordt ondertekend met `app/nyp-rooster.jks` (bewust in de repo), zodat elke nieuwe
versie over de vorige heen geïnstalleerd kan worden.
