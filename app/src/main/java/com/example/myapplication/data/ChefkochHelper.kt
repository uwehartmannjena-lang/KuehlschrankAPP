package com.example.myapplication.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object ChefkochHelper {

    fun buildChefkochSearchUrl(ingredients: List<String>): String {
        if (ingredients.isEmpty()) return "https://www.chefkoch.de/rezepte/"
        val query = ingredients.joinToString(",") { it.trim() }
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
