package com.dtops.payment.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 1, // Single row for POC
    val deviceId: String,
    val localBalance: Double
)

