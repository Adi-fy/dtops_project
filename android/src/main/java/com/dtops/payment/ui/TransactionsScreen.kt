package com.dtops.payment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtops.payment.data.AppDatabase
import com.dtops.payment.data.TransactionEntity
import com.dtops.payment.ui.theme.EmeraldGreen
import com.dtops.payment.ui.theme.ErrorRed
import com.dtops.payment.ui.theme.WarningYellow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionsScreen() {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    var transactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    
    var currentUserId by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val wallet = db.walletDao().getWallet()
        currentUserId = wallet?.userId ?: ""
        transactions = db.transactionDao().getAllTransactions().reversed()
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Transaction History", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(transactions) { tx ->
                    TransactionItem(tx, currentUserId)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(tx: TransactionEntity, currentUserId: String) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(tx.timestamp))
    
    val statusColor = when (tx.status) {
        "COMMITTED", "SYNCED" -> EmeraldGreen
        "REJECTED" -> ErrorRed
        else -> WarningYellow
    }
    
    val isOutbound = tx.senderId == currentUserId
    val amountPrefix = if (isOutbound) "-" else "+"
    val amountColor = if (isOutbound) MaterialTheme.colorScheme.onSurface else EmeraldGreen
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(if (isOutbound) "To: ${tx.receiverId}" else "From: ${tx.senderId}", fontWeight = FontWeight.Bold)
                Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(tx.status, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold)
                }
            }
            Text("$amountPrefix₹${tx.amount}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = amountColor)
        }
    }
}

