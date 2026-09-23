package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconVariantsTest {
    @Test
    fun alleKonfiguriertenVariantenSindGueltig() {
        assertTrue(LauncherIconVariants.isValid(1))
        assertTrue(LauncherIconVariants.isValid(25))
        assertFalse(LauncherIconVariants.isValid(0))
        assertFalse(LauncherIconVariants.isValid(26))
    }

    @Test
    fun erzeugtStabileRessourcenUndAliasNamen() {
        assertEquals("ic_launcher_variant_04", LauncherIconVariants.resourceName(4))
        assertEquals(
            "com.example.myapplication.LauncherVariant25",
            LauncherIconVariants.aliasClassName("com.example.myapplication", 25)
        )
    }
}
