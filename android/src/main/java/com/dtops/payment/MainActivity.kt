package com.dtops.payment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.produceState
import com.dtops.payment.data.AppDatabase
import com.dtops.payment.ui.LoginScreen
import com.dtops.payment.ui.Dashboard
import com.dtops.payment.ui.ProfileScreen
import com.dtops.payment.ui.ScanScreen
import com.dtops.payment.ui.SendScreen
import com.dtops.payment.ui.TransactionsScreen
import com.dtops.payment.ui.theme.DtopsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DtopsTheme {
                val context = this
                val db = AppDatabase.getDatabase(context)
                val navController = rememberNavController()
                
                val startDestState = produceState<String?>(initialValue = null) {
                    val wallet = db.walletDao().getWallet()
                    value = if (wallet != null) "dashboard" else "login"
                }

                val startDestination = startDestState.value
                if (startDestination == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    
                    Scaffold(
                        bottomBar = {
                            if (currentRoute != "login") {
                                NavigationBar {
                                    val currentDestination = navBackStackEntry?.destination
                                    
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                        label = { Text("Home") },
                                        selected = currentDestination?.hierarchy?.any { it.route == "dashboard" } == true,
                                        onClick = { navController.navigate("dashboard") { popUpTo(navController.graph.startDestinationId) } }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.List, contentDescription = "History") },
                                        label = { Text("History") },
                                        selected = currentDestination?.hierarchy?.any { it.route == "history" } == true,
                                        onClick = { navController.navigate("history") { popUpTo(navController.graph.startDestinationId) } }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                        label = { Text("Profile") },
                                        selected = currentDestination?.hierarchy?.any { it.route == "profile" } == true,
                                        onClick = { navController.navigate("profile") { popUpTo(navController.graph.startDestinationId) } }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController, 
                            startDestination = startDestination,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("login") { LoginScreen(navController) }
                            composable("dashboard") { Dashboard(navController) }
                            composable("send") { SendScreen(navController) }
                            composable("scan") { ScanScreen(navController) }
                            composable("history") { TransactionsScreen() }
                            composable("profile") { ProfileScreen(navController) }
                        }
                    }
                }
            }
        }
    }
}
