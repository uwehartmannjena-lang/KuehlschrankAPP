package com.example.myapplication.ui.help

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
            title = "Kassenbon-Scan & KI-Import",
            category = "Erfassung",
            summary = "Gescannte Belege, PDFs oder Fotos werden automatisch ausgelesen und als Vorrat angelegt.",
            detailedSteps = listOf(
                "Tippe in der oberen Leiste auf das Kassenbon-Symbol.",
                "Wähle aus: Foto aufnehmen, PDF/Bild aus Galerie laden oder Text teilen.",
                "Die KI erkennt Artikel, Menge und Preis. Du kannst Namen vor der Übernahme anpassen.",
                "Tippe auf 'Speichern', um die Artikel im Inventar abzulegen."
            ),
            faqs = listOf(
                FAQItem("Was passiert, wenn ein Bon falsch gelesen wird?", "Du kannst den Namen im Vorschaudialog korrigieren. Die App lernt deine Korrekturen für das nächste Mal!"),
                FAQItem("Werden Pfand und Steuerzeilen ignoriert?", "Ja, Pfand, Rabattzeilen und EC-Terminaldaten werden automatisch ausgefiltert.")
            ),
            icon = Icons.Default.ReceiptLong,
            accentColor = Color(0xFF2196F3),
            targetKey = "scan_btn"
        ),
        HelpTopic(
            id = "mhd_tracking",
            title = "MHD & Haltbarkeits-Tracker",
            category = "Frische",
            summary = "Farben signalisieren Frischestand: Grün für frisch, Gelb für kritisch, Rot für abgelaufen.",
            detailedSteps = listOf(
                "Jedes Produkt berechnet anhand seiner Kategorie automatisch das Ablaufdatum.",
                "Auf der Startseite zeigen Indikatoren (Grün / Gelb / Rot) den Reifegrad an.",
                "Unter Einstellungen kannst du festlegen, wie viele Tage vorher du gewarnt werden möchtest."
            ),
            faqs = listOf(
                FAQItem("Wie berechnet die App das Ablaufdatum?", "Basierend auf Warengruppe (z. B. Hackfleisch 2 Tage, Milch 10 Tage) ab Kaufdatum."),
                FAQItem("Kann ich geöffnete Produkte markieren?", "Ja! Wische nach unten oder nutze das Kontextmenü, um ein Produkt als 'Geöffnet' zu kennzeichnen.")
            ),
            icon = Icons.Default.Warning,
            accentColor = Color(0xFFFF9800),
            targetKey = "expiry_filter"
        ),
        HelpTopic(
            id = "inventory_storage",
            title = "Vorratskammer, Kühlschrank & Gefrierfach",
            category = "Organisation",
            summary = "Artikel werden automatisch dem passenden Lagerort und der richtigen Kategorie zugewiesen.",
            detailedSteps = listOf(
                "Beim Import ordnet die App Produkte automatisch Kühlschrank, Gefrierfach oder Vorratskammer zu.",
                "Nutze die Filterchips oben, um gezielt nur Vorräte an bestimmten Lagerorten anzuzeigen.",
                "Sollte ein Lagerort nicht stimmen, kannst du ihn beim Bearbeiten einfach ändern."
            ),
            faqs = listOf(
                FAQItem("Kann ich nach Kategorien filtern?", "Ja! Über das Filtermenü kannst du z. B. nur Milchprodukte oder Fleisch anzeigen."),
                FAQItem("Gibt es eine Ansicht als Baum oder Kalender?", "Ja! Über das Menü 'Ansicht' kannst du zwischen Liste, Raster, Baum und Kalender wechseln.")
            ),
            icon = Icons.Default.Kitchen,
            accentColor = Color(0xFF4CAF50),
            targetKey = "storage_tiles"
        ),
        HelpTopic(
            id = "gestures_swipe",
            title = "Wischgesten (Swipe-Aktionen)",
            category = "Bedienung",
            summary = "Verwalte Artikel blitzschnell mit Gesten in alle vier Richtungen.",
            detailedSteps = listOf(
                "Wische nach rechts ➡️ für 'Verbraucht' (Eintrag wandert in Statistik & Wiederverbrauchs-Liste).",
                "Wische nach links ⬅️ für 'Müll' (Eintrag landet in Abfall-Statistik).",
                "Wische nach oben ⬆️ für 'Favorit' (fügt Produkt der Watchlist hinzu).",
                "Wische nach unten ⬇️ für 'Geöffnet' (verkürzt die Haltbarkeit)."
            ),
            faqs = listOf(
                FAQItem("Kann ich die Wischgesten anpassen?", "Ja! In den Einstellungen kannst du alle 4 Richtungen individuell belegen."),
                FAQItem("Wie verhindere ich versehentliches Wischen beim Scrollen?", "Beginne die Wischgeste leicht horizontal, damit die App Scrollen von Wischen unterscheidet.")
            ),
            icon = Icons.Default.Swipe,
            accentColor = Color(0xFF9C27B0),
            targetKey = "item_card"
        ),
        HelpTopic(
            id = "favorites_deals",
            title = "Favoriten-Pool & Angebots-Alarm",
            category = "Sparen",
            summary = "Setze Lieblingsprodukte auf die Watchlist und erhalte automatische Pop-up-Angebote lokaler Händler.",
            detailedSteps = listOf(
                "Tippe oben auf das Herz-Symbol, um deine dauerhafte Watchlist zu öffnen.",
                "Füge Lieblingsprodukte hinzu (z. B. 'Butter', 'Nutella', 'Kaffee').",
                "Die App gleicht Watchlist & Einkaufsliste automatisch mit Prospekten von Kaufland, Lidl, Rewe & Edeka ab.",
                "Sobald ein Angebot existiert, erscheint ein Pop-up mit der Direktübernahme auf deine Einkaufsliste."
            ),
            faqs = listOf(
                FAQItem("Bleiben Favoriten auch ohne Vorrat auf der Liste?", "Ja, die Watchlist ist unabhängig vom aktuellen Kühlschrank-Inhalt."),
                FAQItem("Wie oft werden Angebote geprüft?", "Automatisch im Hintergrund oder manuell per Klick in der Watchlist.")
            ),
            icon = Icons.Default.Favorite,
            accentColor = Color(0xFFE91E63),
            targetKey = "watchlist_btn"
        )
    )

    val onboardingSteps: List<OnboardingStep> = listOf(
        OnboardingStep(
            id = "welcome",
            title = "Willkommen beim FrischeRadar!",
            description = "Dein intelligenter Begleiter für Kühlschrank, Vorrat, Mindesthaltbarkeit und Schnäppchen-Jagd.",
            icon = Icons.Default.Celebration
        ),
        OnboardingStep(
            id = "scan",
            title = "Bon- & Barcode-Scanner 🧾",
            description = "Scanne Kassenbons oder Barcodes. Die KI erkennt Namen, Preise und Mengen vollautomatisch.",
            targetKey = "scan_btn",
            icon = Icons.Default.QrCodeScanner
        ),
        OnboardingStep(
            id = "watchlist",
            title = "Angebots-Watchlist 🏷️",
            description = "Speichere deine Lieblingsprodukte. Die App schlägt Alarm, sobald sie bei Händlern im Angebot sind.",
            targetKey = "watchlist_btn",
            icon = Icons.Default.Favorite
        ),
        OnboardingStep(
            id = "fridge",
            title = "Frische- & MHD-Tracker 📦",
            description = "Farbindikatoren zeigen dir auf einen Blick, welche Vorräte bald verbraucht werden sollten.",
            targetKey = "storage_tiles",
            icon = Icons.Default.Kitchen
        ),
        OnboardingStep(
            id = "gestures",
            title = "Kompakte Wischgesten 👆",
            description = "Wische Karten nach rechts (Verbraucht), links (Müll) oder oben (Favorit), um Vorräte blitzschnell zu verwalten.",
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
