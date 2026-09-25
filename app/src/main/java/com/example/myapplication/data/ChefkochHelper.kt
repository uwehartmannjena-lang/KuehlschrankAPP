package com.example.myapplication.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object ChefkochHelper {

    fun buildChefkochSearchUrl(ingredients: List<String>): String {
        if (ingredients.isEmpty()) return "https://www.chefkoch.de/rezepte/"
        
        val brandsToRemove = listOf("K-classic", "Purland", "Gutfried")
        val unitsToRemove = listOf("Kg", "Stk", "Becher", "Bund")
        
        val cleanedIngredients = ingredients.map { ingredient ->
            var cleaned = ingredient
            brandsToRemove.forEach { brand ->
                cleaned = cleaned.replace(Regex("(?i)\\b$brand\\b"), "")
            }
            unitsToRemove.forEach { unit ->
                cleaned = cleaned.replace(Regex("(?i)\\b$unit\\b"), "")
            }
            cleaned.replace(Regex("\\s+"), " ").trim()
        }.filter { it.isNotEmpty() }

        if (cleanedIngredients.isEmpty()) return "https://www.chefkoch.de/rezepte/"

        val query = cleanedIngredients.joinToString(",")
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
