package com.asn.rifterlog.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parking_records")
data class ParkingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val title: String = "",
    val note: String = ""
)
