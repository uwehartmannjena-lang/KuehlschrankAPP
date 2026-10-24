package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserLearnedDao {
    @Query("SELECT * FROM UserLearnedCorrections")
    suspend fun getAllLearnedCorrections(): List<UserLearnedCorrection>

    @Query("SELECT * FROM UserLearnedCorrections WHERE receipt_raw_text = :rawText LIMIT 1")
    suspend fun getCorrection(rawText: String): UserLearnedCorrection?

    @Query("SELECT * FROM UserLearnedCorrections WHERE LOWER(receipt_raw_text) = LOWER(:rawText) LIMIT 1")
    suspend fun findCorrectionCaseInsensitive(rawText: String): UserLearnedCorrection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrection(correction: UserLearnedCorrection)

    @androidx.room.Update
    suspend fun updateCorrection(correction: UserLearnedCorrection)

    @androidx.room.Delete
    suspend fun deleteCorrection(correction: UserLearnedCorrection)

    @Query("DELETE FROM UserLearnedCorrections WHERE receipt_raw_text = :rawText")
    suspend fun deleteByRawText(rawText: String)
}
