package com.example.myapplication.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Definiert Lebensmittelkategorien mit Standard-Haltbarkeit und Icons.
 */
enum class FoodCategory(val expiryDays: Int, val icon: ImageVector, val storageTip: String) {
    FLEISCH_FISCH(3, Icons.Default.Warning, "Im kältesten Fach lagern und innerhalb von 48h verbrauchen."),
    MILCHPRODUKTE(10, Icons.Default.WaterDrop, "Käse in Pergamentpapier wickeln, damit er 'atmen' kann."),
    BROT_BACKWAREN(4, Icons.Default.Home, "Brot im Tontopf lagern, nicht im Kühlschrank."),
    OBST_GEMUESE(7, Icons.Default.Eco, "Bananen einzeln lagern, da sie andere Früchte schneller reifen lassen."),
    VORRAT_KONSERVEN(180, Icons.Default.Star, "Trocken und dunkel lagern. Einmal offen -> ab in den Kühlschrank."),
    HAUSTIER(365, Icons.Default.Favorite, "Geöffnete Dosen mit Deckel im Kühlschrank aufbewahren."),
    GETRAENKE(30, Icons.Default.WineBar, "Lichtgeschützt lagern, angebrochene Säfte stehend kühlen."),
    DROGERIE_HYGIENE(730, Icons.Default.Soap, "Kühl und trocken lagern, außerhalb der Reichweite von Kindern."),
    HAUSHALT(1095, Icons.Default.HomeRepairService, "In der Vorratskammer oder im Putzschrank aufbewahren."),
    SONSTIGES(7, Icons.Default.ShoppingCart, "Regelmäßig Bestand prüfen, um Verschwendung zu vermeiden.")
}

object CategoryDetector {
    fun detectCategory(productName: String): FoodCategory {
        val nameLower = productName.lowercase()

        return when {
            // Fleisch & Fisch
            nameLower.contains("hackfleisch") || nameLower.contains("geschnetzeltes") || 
            nameLower.contains("fleischwurst") || nameLower.contains("salami") || 
            nameLower.contains("hähnchen") || nameLower.contains("rinder") ||
            nameLower.contains("fleisch") || nameLower.contains("wurst") ||
            nameLower.contains("schinken") -> FoodCategory.FLEISCH_FISCH

            // Milchprodukte
            nameLower.contains("joghurt") || nameLower.contains("milch") || 
            nameLower.contains("käse") || nameLower.contains("quark") ||
            nameLower.contains("sahne") || nameLower.contains("butter") ||
            nameLower.contains("legere") -> FoodCategory.MILCHPRODUKTE

            // Haustier (Spezialfall Kaufland Bons: Gourmet Perle)
            (nameLower.contains("perle") && nameLower.contains("gourmet")) ||
            nameLower.contains("katzen") || nameLower.contains("hund") ||
            nameLower.contains("tier") -> FoodCategory.HAUSTIER

            // Brot & Backwaren
            nameLower.contains("churro") || nameLower.contains("bites") || 
            nameLower.contains("rustipani") || nameLower.contains("spaghetti") || 
            nameLower.contains("cookies") || nameLower.contains("weissb") ||
            nameLower.contains("brot") || nameLower.contains("brötchen") ||
            nameLower.contains("baguette") -> FoodCategory.BROT_BACKWAREN

            // Obst & Gemüse
            nameLower.contains("obst") || nameLower.contains("gemüse") || 
            nameLower.contains("apfel") || nameLower.contains("banane") ||
            nameLower.contains("tomate") || nameLower.contains("gurke") ||
            nameLower.contains("salat") -> FoodCategory.OBST_GEMUESE

            // Getränke
            nameLower.contains("bier") || nameLower.contains("saft") || 
            nameLower.contains("wasser") || nameLower.contains("limo") ||
            nameLower.contains("cola") || nameLower.contains("wein") -> FoodCategory.GETRAENKE

            // Konserven & Vorrat
            nameLower.contains("reis") || nameLower.contains("nudeln") || 
            nameLower.contains("mehl") || nameLower.contains("zucker") ||
            nameLower.contains("konserve") || nameLower.contains("erdnüsse") || 
            nameLower.contains("fixe") || nameLower.contains("tom") -> FoodCategory.VORRAT_KONSERVEN

            // Drogerie & Hygiene
            nameLower.contains("spüler") || nameLower.contains("wasch") || 
            nameLower.contains("seife") || nameLower.contains("shampoo") ||
            nameLower.contains("pasta") || nameLower.contains("deo") ||
            nameLower.contains("pflege") || nameLower.contains("dusch") ||
            nameLower.contains("wc") || nameLower.contains("papier") ||
            nameLower.contains("creme") || nameLower.contains("lotion") ||
            nameLower.contains("windel") || nameLower.contains("rasier") ||
            nameLower.contains("bürste") || nameLower.contains("kosmetik") -> FoodCategory.DROGERIE_HYGIENE

            // Haushalt & Non-Food
            nameLower.contains("rollen") || nameLower.contains("beutel") || 
            nameLower.contains("tücher") || nameLower.contains("batterie") ||
            nameLower.contains("licht") || nameLower.contains("folie") ||
            nameLower.contains("reiniger") || nameLower.contains("schwamm") ||
            nameLower.contains("kerze") || nameLower.contains("werkzeug") ||
            nameLower.contains("kabel") || nameLower.contains("stecker") -> FoodCategory.HAUSHALT

            else -> FoodCategory.SONSTIGES
        }
    }
}
