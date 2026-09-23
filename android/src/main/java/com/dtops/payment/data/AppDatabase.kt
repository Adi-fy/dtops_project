package com.dtops.payment.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.security.MessageDigest

@Database(entities = [TransactionEntity::class, WalletEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun walletDao(): WalletDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dtops_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
    
    suspend fun verifyChainIntegrity(): Boolean {
        val transactions = transactionDao().getAllTransactions()
        var prevHash = "GENESIS"
        
        for (tx in transactions) {
            if (tx.previousHash != prevHash) return false
            
            val payloadToHash = "${tx.previousHash}${tx.txId}${tx.senderId}${tx.receiverId}${tx.amount}${tx.sequenceCounter}${tx.nonce}${tx.signature}"
            val calculatedHash = hashString(payloadToHash)
            
            if (calculatedHash != tx.currentHash) return false
            prevHash = tx.currentHash
        }
        return true
    }
    
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    suspend fun insertTransactionSafely(
        txId: String, senderId: String, receiverId: String, amount: Double,
        sequenceCounter: Int, nonce: String, timestamp: Long, signature: String, status: String
    ): TransactionEntity {
        val lastTx = transactionDao().getLastTransaction()
        val prevHash = lastTx?.currentHash ?: "GENESIS"
        
        val payloadToHash = "$prevHash$txId$senderId$receiverId$amount$sequenceCounter$nonce$signature"
        val currentHash = hashString(payloadToHash)
        
        val newEntity = TransactionEntity(
            txId, senderId, receiverId, amount, sequenceCounter, nonce, timestamp, signature, status, prevHash, currentHash
        )
        transactionDao().insert(newEntity)
        return newEntity
    }
}

