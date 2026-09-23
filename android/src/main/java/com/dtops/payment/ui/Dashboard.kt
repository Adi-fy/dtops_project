package com.dtops.payment.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dtops.payment.data.AppDatabase

import android.widget.Toast
import com.dtops.payment.network.ApiClient
import com.dtops.payment.network.SyncRequest
import com.dtops.payment.network.TransactionSchema
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Dashboard(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val coroutineScope = rememberCoroutineScope()
    var balance by remember { mutableStateOf(0.0) }
    var isSyncing by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val wallet = db.walletDao().getWallet()
        balance = wallet?.localBalance ?: 0.0
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("DTOPS", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Offline Payment Mesh", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Available Balance", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("₹$balance", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DashboardActionButton(
                icon = Icons.Default.QrCode,
                text = "Send",
                onClick = { navController.navigate("send") }
            )
            DashboardActionButton(
                icon = Icons.Default.QrCodeScanner,
                text = "Receive",
                onClick = { navController.navigate("scan") }
            )
            DashboardActionButton(
                icon = Icons.Default.Sync,
                text = if (isSyncing) "Syncing..." else "Sync",
                onClick = {
                    if (isSyncing) return@DashboardActionButton
                    isSyncing = true
                    coroutineScope.launch {
                        try {
                            val pending = db.transactionDao().getPendingTransactions()
                            if (pending.isEmpty()) {
                                Toast.makeText(context, "All caught up! No pending transactions.", Toast.LENGTH_SHORT).show()
                                isSyncing = false
                                return@launch
                            }
                            
                            val schemas = pending.map {
                                TransactionSchema(
                                    tx_id = it.txId,
                                    sender_id = it.senderId,
                                    receiver_id = it.receiverId,
                                    amount = it.amount,
                                    sequence_counter = it.sequenceCounter,
                                    nonce = it.nonce,
                                    timestamp = it.timestamp,
                                    signature = it.signature
                                )
                            }
                            
                            val response = withContext(Dispatchers.IO) {
                                ApiClient.apiService.syncTransactions(SyncRequest(schemas))
                            }
                            
                            response.report.forEach { reportItem ->
                                val finalStatus = if (reportItem.status == "COMMITTED") "SYNCED" else reportItem.status
                                db.transactionDao().updateTransactionStatus(reportItem.tx_id, finalStatus)
                            }
                            Toast.makeText(context, "Successfully synced ${response.report.size} transactions!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isSyncing = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun DashboardActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text, fontWeight = FontWeight.Medium)
    }
}
