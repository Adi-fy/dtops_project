package com.dtops.payment.network

import com.dtops.payment.data.TransactionEntity
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path

data class RegisterRequest(
    val device_id: String,
    val public_key_pem: String
)

data class RegisterResponse(
    val status: String,
    val balance: Double,
    val highest_sequence_counter: Int = 0
)

data class TopUpRequest(
    val user_id: String,
    val amount: Double
)

data class TopUpResponse(
    val message: String,
    val new_balance: Double
)

data class BalanceResponse(
    val balance: Double
)

data class UserStateResponse(
    val user_id: String,
    val balance: Double,
    val highest_sequence_counter: Int
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
    @GET("/health")
    suspend fun healthCheck(): Map<String, String>

    @POST("/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("/topup")
    suspend fun topUp(@Body request: TopUpRequest): TopUpResponse

    @GET("/balance/{userId}")
    suspend fun getBalance(@Path("userId") userId: String): BalanceResponse

    @GET("/user/{userId}/state")
    suspend fun getUserState(@Path("userId") userId: String): UserStateResponse

    @POST("/sync")
    suspend fun syncTransactions(@Body request: SyncRequest): SyncResponse
}

