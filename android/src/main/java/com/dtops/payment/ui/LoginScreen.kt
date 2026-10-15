package com.dtops.payment.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dtops.payment.crypto.KeystoreManager
import com.dtops.payment.data.AppDatabase
import com.dtops.payment.data.WalletEntity
import com.dtops.payment.network.ApiClient
import com.dtops.payment.network.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val coroutineScope = rememberCoroutineScope()
    
    var userId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to DTOPS", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Register your device to continue", color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it },
            label = { Text("Choose a User ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (userId.isNotBlank()) {
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val keystore = KeystoreManager()
                            val pubKey = keystore.getPublicKeyPem()
                            
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.apiService.register(RegisterRequest(userId, pubKey))
                            }
                            
                            val prefs = context.getSharedPreferences("dtops_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putInt("highest_seq_${userId}", response.highest_sequence_counter).apply()

                            db.walletDao().insertOrUpdate(WalletEntity(userId = userId, localBalance = response.balance))
                            navController.navigate("dashboard") {
                                popUpTo("login") { inclusive = true }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading && userId.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("Register / Login")
            }
        }
    }
}

