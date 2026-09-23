package com.example.myapplication.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Definiert Lebensmittelkategorien mit Standard-Haltbarkeit und Icons.
 */
enum class FoodCategory(val displayName: String, val expiryDays: Int, val icon: ImageVector, val storageTip: String) {
    FLEISCH_FISCH("Fleisch & Fisch", 3, Icons.Default.SetMeal, "Im kältesten Fach lagern und innerhalb von 48h verbrauchen."),
    MILCHPRODUKTE("Milchprodukte", 10, Icons.Default.WaterDrop, "Käse in Pergamentpapier wickeln, damit er 'atmen' kann."),
    BROT_BACKWAREN("Brot & Backwaren", 4, Icons.Default.Home, "Brot im Tontopf lagern, nicht im Kühlschrank."),
    OBST_GEMUESE("Obst & Gemüse", 7, Icons.Default.Eco, "Bananen einzeln lagern, da sie andere Früchte schneller reifen lassen."),
    VORRAT_KONSERVEN("Vorrat & Konserven", 180, Icons.Default.Star, "Trocken und dunkel lagern. Einmal offen -> ab in den Kühlschrank."),
    HAUSTIER("Haustier", 365, Icons.Default.Favorite, "Geöffnete Dosen mit Deckel im Kühlschrank aufbewahren."),
    GETRAENKE("Getränke", 180, Icons.Default.WineBar, "Lichtgeschützt lagern, angebrochene Säfte stehend kühlen."),
    DROGERIE_HYGIENE("Drogerie & Hygiene", 730, Icons.Default.Soap, "Kühl und trocken lagern, außerhalb der Reichweite von Kindern."),
    HAUSHALT("Haushalt", 1095, Icons.Default.HomeRepairService, "In der Vorratskammer oder im Putzschrank aufbewahren."),
    SONSTIGES("Sonstiges", 7, Icons.Default.ShoppingCart, "Regelmäßig Bestand prüfen, um Verschwendung zu vermeiden.")
}

object CategoryDetector {
    fun detectCategory(productName: String): FoodCategory {
        val nameLower = productName.lowercase()

        return when {
            // Haustier (Muss ganz oben stehen, da Katzenfutter oft 'Lachs', 'Fleisch' oder 'Huhn' enthält!)
            nameLower.contains("perle") || nameLower.contains("gourmet") ||
            nameLower.contains("katzen") || nameLower.contains("hund") ||
            nameLower.contains("futter") || nameLower.contains("sheba") ||
            nameLower.contains("felix") || nameLower.contains("whiskas") ||
            nameLower.contains("tier") -> FoodCategory.HAUSTIER

            // Fleisch & Fisch
            nameLower.contains("hackfleisch") || nameLower.contains("geschnetzeltes") || 
            nameLower.contains("fleischwurst") || nameLower.contains("salami") || 
            nameLower.contains("hähnchen") || nameLower.contains("rinder") ||
            nameLower.contains("fleisch") || nameLower.contains("wurst") ||
            nameLower.contains("schinken") -> FoodCategory.FLEISCH_FISCH

            // Brot & Backwaren
            nameLower.contains("churro") || nameLower.contains("bites") || 
            nameLower.contains("rustipani") || nameLower.contains("cookies") ||
            nameLower.contains("spekulatius") || nameLower.contains("keks") ||
            nameLower.contains("gebäck") || nameLower.contains("kuchen") ||
            nameLower.contains("weissb") || nameLower.contains("brot") ||
            nameLower.contains("brötchen") || nameLower.contains("baguette") -> FoodCategory.BROT_BACKWAREN

            // Obst & Gemüse
            nameLower.contains("obst") || nameLower.contains("gemüse") || 
            nameLower.contains("apfel") || nameLower.contains("banane") ||
            nameLower.contains("tomate") || nameLower.contains("gurke") || nameLower.contains("gurken") ||
            nameLower.contains("kohlrabi") || nameLower.contains("salat") ||
            nameLower.contains("möhre") || nameLower.contains("karotte") ||
            nameLower.contains("zucchini") || nameLower.contains("paprika") ||
            nameLower.contains("brokkoli") || nameLower.contains("blumenkohl") ||
            nameLower.contains("radieschen") -> FoodCategory.OBST_GEMUESE

            // Getränke
            nameLower.contains("bier") || nameLower.contains("mönchshof") ||
            nameLower.contains("zwickel") || nameLower.contains("pils") ||
            nameLower.contains("weizen") || nameLower.contains("radler") ||
            nameLower.contains("saft") || nameLower.contains("wasser") ||
            nameLower.contains("limo") || nameLower.contains("cola") ||
            nameLower.contains("wein") || nameLower.contains("sekt") -> FoodCategory.GETRAENKE

            // Konserven & Vorrat
            nameLower.contains("reis") || nameLower.contains("nudeln") || 
            nameLower.contains("spaghetti") || nameLower.contains("mehl") ||
            nameLower.contains("zucker") || nameLower.contains("konserve") ||
            nameLower.contains("erdnüsse") || nameLower.contains("fixe") ||
            nameLower.contains("tom") -> FoodCategory.VORRAT_KONSERVEN

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
