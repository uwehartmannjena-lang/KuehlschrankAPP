package com.example.myapplication.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object ChefkochHelper {

    fun cleanIngredientForChefkoch(raw: String): String {
        var text = raw.replace(",", " ")

        // Gewichtsangaben und Einheiten entfernen (z. B. "1 Kg", "500g", "2,5 kg", "1,5 l", "10 Stk", "1 Becher")
        text = text.replace(Regex("""(?i)\b\d+([.,]\d+)?\s*(kg|g|ml|l|stück|stk|st|becher|dose|glas|flasche|bund|netz|schale|tafel|rolle|tüte|packung|er)\b"""), " ")
        text = text.replace(Regex("""(?i)\b(kg|g|ml|l|stück|stk|st|becher|dose|glas|flasche|bund|netz|schale|tafel|rolle|tüte|packung)\b"""), " ")
        text = text.replace(Regex("""\b\d+([.,]\d+)?\b"""), " ")

        // Markennamen & Kürzel entfernen ("K-classic", "Purland", "Gut & Günstig", "ja!", "K.", "Hä.", "Meg.", "Kn.", etc.)
        text = text.replace(Regex("""(?i)\b(k-classic|gut\s*&\s*günstig|gut&günstig|g&g|rewe\s*beste\s*wahl|rewe\s*bio|rewe\s*feine\s*welt|edeka\s*bio|edeka|gut\s*bio|milsani|milbona|dulano|biobio|gutes\s*land|naturgut|ja!|ja|purland|gutfried)\b"""), " ")
        text = text.replace(Regex("""(?i)\b(k|hä|meg|dit|kn|pres|bautz)\b[.-]?"""), " ")

        // Sonderzeichen & Mehrfach-Leerzeichen entfernen
        return text.replace(Regex("""[^\p{L}\s]"""), " ")
                   .replace(Regex("""\s+"""), " ")
                   .trim()
    }

    fun buildChefkochSearchUrl(ingredients: List<String>): String {
        if (ingredients.isEmpty()) return "https://www.chefkoch.de/rezepte/"

        val cleanedList = ingredients
            .map { cleanIngredientForChefkoch(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3) // Max 3 Hauptzutaten für optimale Trefferquote bei Chefkoch

        if (cleanedList.isEmpty()) return "https://www.chefkoch.de/rezepte/"

        val query = cleanedList.joinToString(" ")
        val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        return "https://www.chefkoch.de/rs/s0/$encodedQuery/Rezepte.html"
    }

    fun openChefkoch(context: Context, ingredients: List<String>) {
        val url = buildChefkochSearchUrl(ingredients)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openChefkochUrl(context: Context, recipeUrl: String) {
        val url = if (recipeUrl.startsWith("http")) recipeUrl else buildChefkochSearchUrl(listOf(recipeUrl))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
