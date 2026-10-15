package com.dtops.payment.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.navigation.NavController
import com.dtops.payment.crypto.KeystoreManager
import com.dtops.payment.data.AppDatabase
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SendScreen(navController: NavController) {
    var amountText by remember { mutableStateOf("") }
    var receiverIdText by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentTxId by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Send Payment", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        TextField(
            value = receiverIdText,
            onValueChange = { receiverIdText = it },
            label = { Text("Recipient User ID") },
            placeholder = { Text("e.g. bob") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = {
            val amount = amountText.toDoubleOrNull()
            val recipient = receiverIdText.trim()
            
            if (recipient.isBlank()) {
                errorMessage = "Please enter the Recipient User ID"
                return@Button
            }
            if (amount == null || amount <= 0) {
                errorMessage = "Please enter a valid amount"
                return@Button
            }
            
            coroutineScope.launch {
                val db = AppDatabase.getDatabase(context)
                
                val wallet = db.walletDao().getWallet()
                val senderId = wallet?.userId ?: "Unknown"
                
                if (recipient == senderId) {
                    errorMessage = "Cannot send payment to yourself"
                    return@launch
                }
                
                // Enforce balance check
                val hasFunds = db.walletDao().deductFunds(amount)
                if (!hasFunds) {
                    errorMessage = "Insufficient local balance. Please top up."
                    return@launch
                }
                errorMessage = null
                
                val receiverId = recipient
                val txId = UUID.randomUUID().toString()
                currentTxId = txId
                val nonce = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                
                val prefs = context.getSharedPreferences("dtops_prefs", android.content.Context.MODE_PRIVATE)
                val baseCounter = prefs.getInt("highest_seq_${senderId}", 0)
                val localHighest = db.transactionDao().getHighestSequenceCounter(senderId) ?: 0
                val sequenceCounter = maxOf(baseCounter, localHighest) + 1
                prefs.edit().putInt("highest_seq_${senderId}", sequenceCounter).apply()
                
                val payloadStr = "$txId|$senderId|$receiverId|$amount|$sequenceCounter|$nonce|$timestamp"
                
                val keystoreManager = KeystoreManager()
                val signature = keystoreManager.sign(payloadStr)
                
                val txData = mapOf(
                    "tx_id" to txId,
                    "sender_id" to senderId,
                    "receiver_id" to receiverId,
                    "amount" to amount,
                    "sequence_counter" to sequenceCounter,
                    "nonce" to nonce,
                    "timestamp" to timestamp,
                    "signature" to signature,
                    "pub_key" to keystoreManager.getPublicKeyPem() // Include for POC verification
                )
                
                val jsonPayload = Gson().toJson(txData)
                qrBitmap = generateQrCode(jsonPayload)
                
                // Insert into local DB as pending
                db.insertTransactionSafely(
                    txId, senderId, receiverId, amount, sequenceCounter, nonce, timestamp, signature, "PENDING_SYNC"
                )
            }
        }) {
            Text("Generate QR")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        qrBitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(250.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0 && currentTxId != null) {
                            val db = AppDatabase.getDatabase(context)
                            // Refund the wallet
                            db.walletDao().addFunds(amount)
                            db.transactionDao().deletePendingTransaction(currentTxId!!)
                        }
                        qrBitmap = null
                        amountText = ""
                        currentTxId = null
                        errorMessage = "Transaction Cancelled & Refunded"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Cancel & Refund")
            }
        }
    }
}

fun generateQrCode(text: String): Bitmap {
    val size = 512
    val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

