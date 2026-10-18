package com.fieldscanner.app.data.finding

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "findings")
data class FindingEntity(
    @PrimaryKey val id: String,
    val photoUri: String,
    val title: String,
    val description: String,
    val confidence: Double,
    val visualId: String,
    val audioUri: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val foundAt: Long
)
