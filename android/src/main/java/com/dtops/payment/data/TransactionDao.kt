package com.dtops.payment.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY sequenceCounter DESC LIMIT 1")
    suspend fun getLastTransaction(): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY sequenceCounter ASC")
    suspend fun getAllTransactions(): List<TransactionEntity>
    
    @Query("SELECT MAX(sequenceCounter) FROM transactions WHERE senderId = :senderId")
    suspend fun getHighestSequenceCounter(senderId: String): Int?
    
    @Query("SELECT * FROM transactions WHERE status = 'PENDING_SYNC'")
    suspend fun getPendingTransactions(): List<TransactionEntity>
    
    @Query("UPDATE transactions SET status = :status WHERE txId = :txId")
    suspend fun updateTransactionStatus(txId: String, status: String)
}

