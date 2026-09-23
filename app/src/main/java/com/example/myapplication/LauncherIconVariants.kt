package com.example.myapplication

import java.util.Locale

/**
 * Zentrale Definition aller auswählbaren Launcher-Icons.
 * Die Namen müssen mit den Activity-Alias-Einträgen im Manifest übereinstimmen.
 */
object LauncherIconVariants {
    const val count = 25

    fun isValid(index: Int): Boolean = index in 1..count

    fun resourceName(index: Int): String {
        require(isValid(index)) { "Ungültige App-Icon-Variante: $index" }
        return "ic_launcher_variant_%02d".format(index)
    }

    fun aliasClassName(packageName: String, index: Int): String {
        require(isValid(index)) { "Ungültige App-Icon-Variante: $index" }
        return "$packageName.LauncherVariant%02d".format(Locale.US, index)
    }
}
