package com.fieldscanner.app.data.finding

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FindingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertFinding(finding: FindingEntity): Long

    @Query("UPDATE findings SET audioUri = :audioUri WHERE id = :findingId")
    fun updateAudioUri(findingId: String, audioUri: String): Int

    @Query("SELECT * FROM findings ORDER BY foundAt DESC")
    fun getAllFindings(): List<FindingEntity>

    @Query("DELETE FROM findings WHERE id = :findingId")
    fun deleteFinding(findingId: String): Int
}
