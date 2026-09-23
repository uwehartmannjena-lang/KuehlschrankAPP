# Gemini Agent Profile & Behavioral Rules

## 1. Sprache und Kommunikation
- **Sprachvorgabe:** Antworte IMMER auf Deutsch. Alle Erklärungen, Code-Kommentare, Fehleranalysen und UI-Texte müssen fehlerfreies Deutsch sein.
- **Tonfall:** Sei direkt, lösungsorientiert und technisch präzise. Erkläre bei Code-Änderungen kurz, *warum* du etwas änderst.

## 2. Code-Vollständigkeit & Qualität (Spezifisch für Kühlschrank Profi)
- **App-Name:** Kühlschrank Profi 🍏
- **Struktur:** Die App nutzt bereits ein komplexes Dashboard mit Filtern ("< 3 Tage", "< 7 Tage"), Produktkarten (mit MHD, Preis, Kalorien, Lagerort wie "Vorratskammer") und Buttons für "Verbraucht" / "Müll".
- **Integration:** Neue Funktionen (wie der Barcode-Scanner) dürfen bestehende UI-Elemente nicht überschreiben, sondern müssen sich nahtlos in die obere TopAppBar (Scanner-Icon) oder den grünen schwimmenden Plus-Button (+) integrieren.
- **Daten-Erweiterung:** Wenn ein Barcode gescannt wird, muss dieser an das bestehende Datenmodell übergeben werden, um Preis, Name oder Kalorien falls möglich automatisiert zu ergänzen.

## 3. Automatisierter Entwicklungs-Workflow
- **Proaktive Fehlerbehebung:** Wenn ich dir eine Fehlermeldung oder einen Logcat-Auszug zeige, analysiere sofort die Ursache, korrigiere den betroffenen Code und liefere die korrigierte Datei ohne weitere Nachfragen.
- **Syntax-Check:** Bevor du mir Code vorschlägst, überprüfe ihn mental auf korrekte Imports und Kotlin-Syntax, um Gradle-Build-Fehler im Keim zu ersticken.
- **Abhängigkeiten (Dependencies):** Wenn für deinen Code neue Bibliotheken benötigt werden (z. B. Room, Ktor, Coil), nenne mir direkt die genaue Zeile für die `build.gradle.kts (Module :app)` und fordere mich zum Gradle-Sync auf.

## 4. Testing & Smartphone-Deployment
- **Unit-Tests:** Generiere für jede neue logische Funktion oder jedes ViewModel automatisch passende JUnit- oder MockK-Tests in einem separaten Code-Block.
- **Vorbereitung für das Handy:** Sobald ein Feature fehlerfrei implementiert ist, bereite automatisch den Terminal-Befehl oder den IDE-Prozess vor, um die App auf dem angeschlossenen physischen Android-Smartphone (oder Emulator) zu installieren.
- **Logcat-Überwachung:** Weise mich nach der Installation an, die App zu starten, und halte dich bereit, Abstürze aus dem Logcat sofort abzufangen.

## 5. Push-Benachrichtigungen & Angebots-Tracking
- **MHD-Warnung:** Nutze den Android `WorkManager`, um einmal täglich im Hintergrund zu prüfen, ob Produkte im Kühlschrank in weniger als 3 Tagen ablaufen. Löse dann eine lokale Push-Benachrichtigung aus.
- **Angebots-Checker:** Ermögliche es mir, in der App bestimmte Lebensmittel als "Lieblingsprodukt" zu markieren und einen Wunsch-Händler (z.B. REWE, EDEKA) zu hinterlegen.
- **Hintergrund-Suche:** Der `WorkManager` soll in regelmäßigen Abständen im Hintergrund eine Schnittstelle (z.B. Bring! oder eine offene Prospekt-API) abfragen. Wenn das Lieblingsprodukt im Angebot ist, löse sofort eine Push-Benachrichtigung mit dem Spar-Preis aus.

## 7. Strukturierte Beleg-Erkennung (OCR & KI)
- **Extraktion:** Bei der Analyse von Kassenbons (OCR-Text oder PDF) müssen neben den Produkten IMMER auch folgende Metadaten extrahiert werden:
    1. **Supermarkt-Name:** (z.B. REWE, LIDL, ALDI, EDEKA, KAUFLAND).
    2. **Kaufdatum:** Im Format `DD.MM.YYYY`.
- **Datenstruktur:** Diese Informationen müssen im `FridgeViewModel` verarbeitet und in den `FridgeItem`-Notes oder dedizierten Feldern (falls vorhanden) gespeichert werden, um die Preishistorie und Frische-Vorhersage zu verbessern.

## 6. Dynamische Favoriten-Symbole (Settings)
- **Symbol-Speicherung:** Speichere das vom Nutzer gewählte Favoriten-Symbol als String in den App-Einstellungen (SharedPreferences oder Jetpack DataStore). Standardwert ist das bestehende Sternchen ("star").
- **15 Auswahl-Optionen:** Stelle genau 15 moderne Material Design Icons zur Auswahl bereit:
    1. Star (Sternchen - Standard)
    2. Heart (Herz)
    3. Favorite (Gefüllt)
    4. Bookmark (Lesezeichen)
    5. PushPin (Pin)
    6. WorkspacePremium (Krone/Premium)
    7. LocalFireDepartment (Flamme)
    8. Lightbulb (Glühbirne)
    9. CheckCircle (Haken)
    10. ShoppingBag (Einkaufstasche)
    11. LocalMall (Einkaufstüte)
    12. Loyalty (Preisschild)
    13. Diamond (Diamant)
    14. Celebration (Party/Feier)
    15. Eco (Blatt/Bio)
- **UI-Anpassung:** Die Produktkarten im Dashboard müssen das ausgewählte Symbol dynamisch aus den Einstellungen laden und anzeigen, anstatt das Sternchen fest zu verbauen.
