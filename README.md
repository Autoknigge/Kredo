# Kredo

Rechner für Immobilienkäufer: Kaufnebenkosten (Grunderwerbsteuer nach Bundesland, Makler, Notar und Grundbuch) und Annuitätendarlehen mit Sonderzahlungen, pauschal oder einzeln. Alles lokal auf dem Gerät; Google AdMob zeigt Werbung (Einwilligungsabfrage inklusive).

Paketname: `de.autoknigge.kredo`

## Bauen
Unter **Actions → Build** läuft der Build bei jedem Push.
- Mit hinterlegten Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`): Artefakt **Kredo** mit `Kredo.apk` und `Kredo.aab` (identischer Stand, signiert).
- Ohne Secrets: Artefakt **Kredo-Test** mit einer Test-APK.

## Werbung
Die AdMob-IDs stehen in `app/build.gradle.kts` (öffentliche Kennungen) und lassen sich per Secrets `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_OPEN_ID` überschreiben.

Datenschutzerklärung: https://autoknigge.de/kredo/datenschutz.html
