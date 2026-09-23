package com.dtops.payment.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val txId: String,
    val senderId: String,
    val receiverId: String,
    val amount: Double,
    val sequenceCounter: Int,
    val nonce: String,
    val timestamp: Long,
    val signature: String,
    val status: String, // PENDING_SYNC, SYNCED
    val previousHash: String,
    val currentHash: String
)

