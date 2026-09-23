package com.dtops.payment.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet WHERE id = 1 LIMIT 1")
    suspend fun getWallet(): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(wallet: WalletEntity)

    @Transaction
    suspend fun addFunds(amount: Double) {
        val currentWallet = getWallet()
        if (currentWallet != null) {
            insertOrUpdate(currentWallet.copy(localBalance = currentWallet.localBalance + amount))
        } else {
            insertOrUpdate(WalletEntity(deviceId = "device_alice", localBalance = amount))
        }
    }

    @Transaction
    suspend fun deductFunds(amount: Double): Boolean {
        val currentWallet = getWallet()
        if (currentWallet != null && currentWallet.localBalance >= amount) {
            insertOrUpdate(currentWallet.copy(localBalance = currentWallet.localBalance - amount))
            return true
        }
        return false
    }
}

