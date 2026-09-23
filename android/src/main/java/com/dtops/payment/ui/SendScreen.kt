package com.dtops.payment.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Send Payment", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        TextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = {
            val amount = amountText.toDoubleOrNull()
            if (amount != null && amount > 0) {
                coroutineScope.launch {
                    val db = AppDatabase.getDatabase(context)
                    
                    // Enforce balance check
                    val hasFunds = db.walletDao().deductFunds(amount)
                    if (!hasFunds) {
                        errorMessage = "Insufficient local balance. Please top up."
                        return@launch
                    }
                    errorMessage = null
                    
                    val senderId = "device_alice" // Hardcoded for POC
                    val receiverId = "device_bob" // Normally obtained from QR or input
                    val txId = UUID.randomUUID().toString()
                    val nonce = UUID.randomUUID().toString()
                    val timestamp = System.currentTimeMillis()
                    
                    val highestCounter = db.transactionDao().getHighestSequenceCounter(senderId) ?: 0
                    val sequenceCounter = highestCounter + 1
                    
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
            }
        }) {
            Text("Generate QR")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        qrBitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(250.dp))
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

