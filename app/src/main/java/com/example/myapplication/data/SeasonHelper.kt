package com.example.myapplication.data

import java.util.Calendar

object SeasonHelper {

    val seasonalProduce = mapOf(
        1 to listOf("Apfel", "Birne", "Feldsalat", "Grünkohl", "Lauch", "Pastinake", "Rosenkohl"),
        2 to listOf("Apfel", "Feldsalat", "Grünkohl", "Lauch", "Pastinake", "Rosenkohl"),
        3 to listOf("Apfel", "Bärlauch", "Feldsalat", "Lauch", "Spinat"),
        4 to listOf("Bärlauch", "Radieschen", "Rhabarber", "Spinat", "Spargel"),
        5 to listOf("Erdbeere", "Radieschen", "Rhabarber", "Spargel", "Spinat", "Spitzkohl"),
        6 to listOf("Aprikose", "Blaubeere", "Erdbeere", "Gurke", "Himbeere", "Kartoffel", "Kirsche", "Salat", "Spargel", "Tomate"),
        7 to listOf("Aprikose", "Blaubeere", "Brombeere", "Gurke", "Himbeere", "Johannisbeere", "Kartoffel", "Kirsche", "Salat", "Tomate", "Zucchini"),
        8 to listOf("Apfel", "Birne", "Blaubeere", "Brombeere", "Gurke", "Kartoffel", "Pflaume", "Salat", "Tomate", "Zucchini"),
        9 to listOf("Apfel", "Birne", "Brombeere", "Kürbis", "Kartoffel", "Pflaume", "Salat", "Tomate", "Weintraube"),
        10 to listOf("Apfel", "Birne", "Feldsalat", "Kürbis", "Kartoffel", "Lauch", "Pastinake", "Walnuss"),
        11 to listOf("Apfel", "Birne", "Feldsalat", "Grünkohl", "Lauch", "Pastinake", "Rosenkohl"),
        12 to listOf("Apfel", "Feldsalat", "Grünkohl", "Lauch", "Pastinake", "Rosenkohl")
    )

    fun getCurrentSeasonalProduce(): List<String> {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-based
        return seasonalProduce[currentMonth] ?: emptyList()
    }

    fun getStorageTip(produceName: String): String? {
        val nameLower = produceName.lowercase()
        return when {
            nameLower.contains("tomate") -> "Nicht kühlen – verliert sonst an Aroma. Am besten bei Zimmertemperatur lagern."
            nameLower.contains("banane") -> "Reift stark nach und lässt anderes Obst schneller reifen. Separat und nicht im Kühlschrank lagern."
            nameLower.contains("apfel") -> "Äpfel produzieren viel Reifegas (Ethylen). Im Kühlschrank halten sie lange, sollten aber abseits von anderem Obst liegen."
            nameLower.contains("brot") -> "Brot bleibt im Tontopf oder Brotkasten frisch. Im Kühlschrank wird es schneller altbacken."
            nameLower.contains("kartoffel") -> "Kühl, dunkel und trocken lagern, am besten im Keller oder einer Papiertüte."
            nameLower.contains("zwiebel") || nameLower.contains("knoblauch") -> "Dunkel und trocken lagern. Nicht neben Kartoffeln legen."
            nameLower.contains("pilze") || nameLower.contains("champignon") -> "In einer Papiertüte im Kühlschrank lagern, nicht in Plastik (Schimmelgefahr)."
            nameLower.contains("gurke") -> "Gurken sind kälteempfindlich. Nicht ganz unten im Kühlschrank, sondern eher weiter oben lagern."
            nameLower.contains("beeren") || nameLower.contains("erdbeer") -> "Sehr empfindlich! Flach nebeneinander legen und im Kühlschrank aufbewahren. Erst kurz vor dem Verzehr waschen."
            nameLower.contains("salat") -> "In ein feuchtes Küchentuch wickeln und ins Gemüsefach legen."
            else -> null
        }
    }
}
