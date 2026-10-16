package com.example.myapplication.ui.help

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class FAQItem(
    val question: String,
    val answer: String
)

data class HelpTopic(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val detailedSteps: List<String>,
    val faqs: List<FAQItem>,
    val icon: ImageVector,
    val accentColor: Color,
    val targetKey: String? = null
)

data class OnboardingStep(
    val id: String,
    val title: String,
    val description: String,
    val targetKey: String? = null,
    val icon: ImageVector = Icons.Default.Info
)

object AppHelpManager {

    val topics: List<HelpTopic> = listOf(
        HelpTopic(
            id = "receipt_scan",
            title = "Kassenbon-Scan & KI-Lernfunktion",
            category = "Erfassung & OCR",
            summary = "Gescannte Belege, PDFs oder Galeriefotos werden automatisch analysiert, bereinigt und dauerhaft gelernt.",
            detailedSteps = listOf(
                "Schritt 1: Tippe in der oberen Leiste auf das Kassenbon-Symbol.",
                "Schritt 2: Wähle Kamera-Scan, PDF-Import oder Foto aus der Galerie.",
                "Schritt 3: Die KI extrahiert Supermarkt, Kaufdatum, Produkte, Mengen und Preise.",
                "Schritt 4: Passe Abkürzungen bei Bedarf an – die Korrektur wird lokal und im Community-Lernmodell gespeichert.",
                "Schritt 5: Tippe auf 'Speichern', um alle Artikel direkt in den Vorrat einzubuchen."
            ),
            faqs = listOf(
                FAQItem("Was passiert bei unverständlichen Kassenkürzeln?", "Du kannst den Artikel im Dialog korrigieren. FrischeRadar lernt den Klarnamen und wendet ihn bei künftigen Bons sofort an."),
                FAQItem("Werden Pfand und Rabatte berücksichtigt?", "Pfand wird automatisch auf dem virtuellen Pfandkonto verbucht, Rabatte und Pfandrückgaben werden abgezogen."),
                FAQItem("Funktioniert das auch offline?", "Ja! Die integrierte Offline-Engine verarbeitet Belege jederzeit lokal ohne Cloud-Zwang.")
            ),
            icon = Icons.Default.ReceiptLong,
            accentColor = Color(0xFF2196F3),
            targetKey = "scan_btn"
        ),
        HelpTopic(
            id = "offers_radar",
            title = "Angebotsradar & Online-Prospekte",
            category = "Sparen & Angebote",
            summary = "Gleiche Vorräte und Favoriten mit aktuellen Aktionen ab. Ein Klick öffnet den Online-Prospekt des Marktes.",
            detailedSteps = listOf(
                "Schritt 1: Setze Lieblingsartikel auf deine dauerhafte Watchlist (Herz-Symbol oben).",
                "Schritt 2: FrischeRadar scannt automatisch Aktionsangebote von Kaufland, Netto, REWE, Lidl und Edeka.",
                "Schritt 3: Erscheint ein Treffer im Angebots-Radar, tippe einfach auf die Angebotskarte.",
                "Schritt 4: Der Browser öffnet direkt die Aktionsseite bzw. den Prospekt beim gesuchten Artikel.",
                "Schritt 5: Mit dem Warenkorb-Symbol übernimmst du das Angebot direkt auf die Einkaufsliste."
            ),
            faqs = listOf(
                FAQItem("Welche Supermärkte werden unterstützt?", "Aktuell Kaufland, Netto, REWE, Lidl, Edeka sowie Aldi und Penny."),
                FAQItem("Muss ein Artikel im Vorrat sein?", "Nein, Artikel auf der Watchlist werden auch geprüft, wenn der Vorrat aktuell leer ist.")
            ),
            icon = Icons.Default.LocalOffer,
            accentColor = Color(0xFFE91E63),
            targetKey = "watchlist_btn"
        ),
        HelpTopic(
            id = "inventory_management",
            title = "Vorratsverwaltung & Lagerorte",
            category = "Organisation",
            summary = "Artikel flexibel nach Kühlschrank, Vorratskammer und Gefrierfach verwalten und filtern.",
            detailedSteps = listOf(
                "Schritt 1: Nutze die oberen Lagerort-Filter (Kühlschrank, Gefrierfach, Vorratskammer).",
                "Schritt 2: Tippe auf einen Artikel, um die Mengenangabe per +/- anzupassen.",
                "Schritt 3: Über das Stift-Symbol öffnet sich das native Bottom-Sheet für alle Detailfelder.",
                "Schritt 4: Wechsle über das Ansicht-Menü zwischen Liste, Raster, Regal- und Kalender-Ansicht."
            ),
            faqs = listOf(
                FAQItem("Kann ich nach Kategorien filtern?", "Ja, über die Kategorie-Chips filterst du blitzschnell nach Milchprodukten, Gemüse, Fleisch oder Vorrat."),
                FAQItem("Wie funktioniert der Vorratsschutz?", "Unterschreitet ein Artikel den Mindestbestand, schlägt FrischeRadar automatisch einen Nachkauf vor.")
            ),
            icon = Icons.Default.Kitchen,
            accentColor = Color(0xFF4CAF50),
            targetKey = "storage_tiles"
        ),
        HelpTopic(
            id = "mhd_freshness",
            title = "MHD-Ampel & Frische-Tracker",
            category = "Frische & Haltbarkeit",
            summary = "Farbindikatoren signalisieren den Zustand: Grün = frisch, Gelb = bald verbrauchen, Rot = abgelaufen.",
            detailedSteps = listOf(
                "Schritt 1: FrischeRadar schätzt oder übernimmt das Mindesthaltbarkeitsdatum automatisch.",
                "Schritt 2: Der Farbstreifen links zeigt die Dringlichkeit (Grün > 3 Tage, Gelb < 3 Tage, Rot abgelaufen).",
                "Schritt 3: Markiere angebrochene Packungen als 'Geöffnet', um die Verzehrzeit realistisch zu verkürzen.",
                "Schritt 4: In den Einstellungen kannst du festlegen, wie viele Tage im Voraus du gewarnt werden möchtest."
            ),
            faqs = listOf(
                FAQItem("Gibt es Push-Benachrichtigungen?", "Ja, der Hintergrund-Dienst erinnert dich morgens rechtzeitig an ablaufende Lebensmittel."),
                FAQItem("Wie werden geöffnete Produkte berechnet?", "Milch und Saft erhalten z. B. automatisch 3–4 Tage Frischezeit ab Öffnungsdatum.")
            ),
            icon = Icons.Default.Warning,
            accentColor = Color(0xFFFF9800),
            targetKey = "expiry_filter"
        ),
        HelpTopic(
            id = "share_and_sync",
            title = "Teilen & Haushalts-Synchronisation",
            category = "Vernetzung",
            summary = "Kassenbons teilen, Haushaltsmitglieder per QR-Code koppeln und Daten mit dem PC abgleichen.",
            detailedSteps = listOf(
                "Schritt 1: Kassenbons und Einkaufslisten können jederzeit per WhatsApp, E-Mail oder Messenger geteilt werden.",
                "Schritt 2: Erstelle einen Haushaltscode oder scanne den QR-Code eines Partners, um Vorräte live zu teilen.",
                "Schritt 3: Aktiviere in den Einstellungen 'Mit PC verbinden', um Vorräte bequem am Browser im WLAN zu verwalten.",
                "Schritt 4: Manuelle Belegkorrekturen werden geräuschlos mit dem Server synchronisiert."
            ),
            faqs = listOf(
                FAQItem("Werden meine Daten ins Internet hochgeladen?", "Nein, der Sync erfolgt direkt im lokalen WLAN oder anonymisiert bei Community-Korrekturen."),
                FAQItem("Kann ich Backups exportieren?", "Ja, in den Einstellungen findest du vollständige JSON-Backup- und Restore-Funktionen.")
            ),
            icon = Icons.Default.Share,
            accentColor = Color(0xFF009688),
            targetKey = "nav_bar"
        ),
        HelpTopic(
            id = "gestures_swipe",
            title = "Wischgesten (Swipe-Aktionen)",
            category = "Bedienung",
            summary = "Verwalte Vorratskarten blitzschnell mit Gesten in alle vier Himmelsrichtungen.",
            detailedSteps = listOf(
                "Wische nach rechts: Markiert den Artikel als 'Verbraucht' (Statistik wird gepflegt).",
                "Wische nach links: Markiert den Artikel als 'Müll / Verdorbener Abfall'.",
                "Wische nach oben: Fügt das Produkt deiner dauerhaften Favoriten-Watchlist hinzu.",
                "Wische nach unten: Kennzeichnet den Artikel als angebrochen / geöffnet."
            ),
            faqs = listOf(
                FAQItem("Kann ich die Aktionen umbelegen?", "Ja! In den Einstellungen unter 'Erscheinungsbild & Wischgesten' lässt sich jede Richtung frei belegen.")
            ),
            icon = Icons.Default.Swipe,
            accentColor = Color(0xFF9C27B0),
            targetKey = "item_card"
        )
    )

    val onboardingSteps: List<OnboardingStep> = listOf(
        OnboardingStep(
            id = "welcome",
            title = "Willkommen beim FrischeRadar!",
            description = "Dein intelligenter Begleiter für Kühlschrank, Vorrat, Mindesthaltbarkeit und Prospekt-Angebote.",
            icon = Icons.Default.Sensors
        ),
        OnboardingStep(
            id = "scan",
            title = "Kassenbon- & Barcode-Scan",
            description = "Scanne Belege per Kamera, PDF oder Galerie. Die KI erkennt Artikel, Preise und Einheiten automatisch.",
            targetKey = "scan_btn",
            icon = Icons.Default.ReceiptLong
        ),
        OnboardingStep(
            id = "deals",
            title = "Angebots-Radar & Watchlist",
            description = "Füge Lieblingsartikel hinzu. Sobald Kaufland, Netto, REWE, Lidl oder Edeka ein Angebot haben, führt ein Klick direkt zum Prospekt.",
            targetKey = "watchlist_btn",
            icon = Icons.Default.LocalOffer
        ),
        OnboardingStep(
            id = "fridge",
            title = "Vorratsfilter & MHD-Ampel",
            description = "Filtere nach Lagerort (Kühlschrank, Vorrat, Gefrierfach) und behalte ablaufende Lebensmittel per Farb-Ampel im Blick.",
            targetKey = "storage_tiles",
            icon = Icons.Default.Kitchen
        ),
        OnboardingStep(
            id = "gestures",
            title = "Schnelle Wischgesten",
            description = "Wische Karten nach rechts für 'Verbraucht', links für 'Müll' oder nach oben für 'Favorit'. Vollständig konfigurierbar!",
            targetKey = "item_card",
            icon = Icons.Default.Swipe
        )
    )

    fun searchHelp(query: String): List<HelpTopic> {
        if (query.isBlank()) return topics
        val q = query.trim().lowercase()
        return topics.filter { topic ->
            topic.title.lowercase().contains(q) ||
            topic.summary.lowercase().contains(q) ||
            topic.category.lowercase().contains(q) ||
            topic.faqs.any { it.question.lowercase().contains(q) || it.answer.lowercase().contains(q) }
        }
    }
}
