package com.dtops.payment.network

import com.dtops.payment.data.TransactionEntity
import retrofit2.http.Body
import retrofit2.http.POST

data class RegisterRequest(
    val device_id: String,
    val public_key_pem: String
)

data class RegisterResponse(
    val status: String,
    val balance: Double
)

data class TransactionSchema(
    val tx_id: String,
    val sender_id: String,
    val receiver_id: String,
    val amount: Double,
    val sequence_counter: Int,
    val nonce: String,
    val timestamp: Long,
    val signature: String
)

data class SyncRequest(
    val transactions: List<TransactionSchema>
)

data class SyncReportItem(
    val tx_id: String,
    val status: String,
    val message: String?
)

data class SyncResponse(
    val report: List<SyncReportItem>
)

interface BackendService {
    @POST("/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("/sync")
    suspend fun syncTransactions(@Body request: SyncRequest): SyncResponse
}

