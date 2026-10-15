package com.dtops.payment.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtops.payment.data.AppDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.dtops.payment.network.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    
    var balance by remember { mutableStateOf(0.0) }
    var topUpAmount by remember { mutableStateOf("") }
    
    var userId by remember { mutableStateOf("") }
    
    // Fetch initial balance
    LaunchedEffect(Unit) {
        val wallet = db.walletDao().getWallet()
        balance = wallet?.localBalance ?: 0.0
        userId = wallet?.userId ?: "Unknown"
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Profile & Wallet", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("User ID: $userId", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Current Balance", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("₹$balance", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = topUpAmount,
            onValueChange = { topUpAmount = it },
            label = { Text("Add Arbitrary Funds (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                val amount = topUpAmount.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    coroutineScope.launch {
                        try {
                            val wallet = db.walletDao().getWallet()
                            val userId = wallet?.userId ?: return@launch
                            
                            // 1. Call Backend
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.apiService.topUp(com.dtops.payment.network.TopUpRequest(userId, amount))
                            }
                            
                            // 2. If successful, add funds locally
                            db.walletDao().addFunds(amount)
                            balance = db.walletDao().getWallet()?.localBalance ?: 0.0
                            topUpAmount = ""
                            android.widget.Toast.makeText(context, "Top-up successful! New online balance: ₹${response.new_balance}", android.widget.Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Top-up failed. Ensure you are online and server is running.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Top Up via Bank Network")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val wallet = db.walletDao().getWallet()
                        val currentUserId = wallet?.userId ?: return@launch
                        
                        val response = withContext(Dispatchers.IO) {
                            ApiClient.apiService.getUserState(currentUserId)
                        }
                        
                        db.walletDao().updateBalance(response.balance)
                        val prefs = context.getSharedPreferences("dtops_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putInt("highest_seq_${currentUserId}", response.highest_sequence_counter).apply()

                        balance = db.walletDao().getWallet()?.localBalance ?: 0.0
                        android.widget.Toast.makeText(context, "State synchronized! Online balance: ₹${response.balance}", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Sync failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Sync True Global Balance")
        }
    }
}

