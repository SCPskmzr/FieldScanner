package com.fieldscanner.app.data.finding

/** Domain model used by journal and scanner workflows. */
data class Finding(
    val id: String,
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

internal fun FindingEntity.toDomain() = Finding(
    id, photoUri, title, description, confidence, visualId, audioUri, latitude, longitude, accuracyMeters, foundAt
)

internal fun Finding.toEntity() = FindingEntity(
    id, photoUri, title, description, confidence, visualId, audioUri, latitude, longitude, accuracyMeters, foundAt
)
