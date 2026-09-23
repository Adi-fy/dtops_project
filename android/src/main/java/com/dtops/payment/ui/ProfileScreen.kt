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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    
    var balance by remember { mutableStateOf(0.0) }
    var topUpAmount by remember { mutableStateOf("") }
    
    // Fetch initial balance
    LaunchedEffect(Unit) {
        val wallet = db.walletDao().getWallet()
        balance = wallet?.localBalance ?: 0.0
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
                Text("Device ID: device_alice", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        db.walletDao().addFunds(amount)
                        balance = db.walletDao().getWallet()?.localBalance ?: 0.0
                        topUpAmount = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Top Up Wallet")
        }
    }
}

