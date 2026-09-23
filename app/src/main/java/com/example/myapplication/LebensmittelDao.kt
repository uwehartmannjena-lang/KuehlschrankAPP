package com.example.myapplication

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LebensmittelDao {

    // Holt alle Lebensmittel, sortiert nach dem nächsten Ablaufdatum
    @Query("SELECT * FROM kuehlschrank_tabelle ORDER BY ablaufdatum ASC")
    fun getAllLebensmittel(): Flow<List<Lebensmittel>>

    // Fügt ein neues Lebensmittel hinzu (oder ignoriert es, falls es existiert - Duplikatschutz)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLebensmittel(lebensmittel: Lebensmittel)

    // Löscht ein bestimmtes Produkt aus dem Kühlschrank
    @Delete
    suspend fun deleteLebensmittel(lebensmittel: Lebensmittel)
}
