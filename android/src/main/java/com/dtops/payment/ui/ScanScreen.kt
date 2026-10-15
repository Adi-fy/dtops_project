package com.dtops.payment.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.dtops.payment.data.AppDatabase
import com.dtops.payment.crypto.KeystoreManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun ScanScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scannedResult by remember { mutableStateOf<String?>(null) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            if (scannedResult == null) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                        val qrCode = decodeQRCode(imageProxy)
                                        if (qrCode != null) {
                                            scannedResult = qrCode
                                        }
                                        imageProxy.close()
                                    }
                                }
                                
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalyzer
                                )
                            } catch (e: Exception) {
                                Log.e("ScanScreen", "Camera bind error", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        
                        previewView
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Scanned Payload:")
                    Text(scannedResult ?: "")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = {
                        coroutineScope.launch {
                            try {
                                val txData = Gson().fromJson(scannedResult, com.google.gson.JsonObject::class.java)
                                // Validate JSON payload structure (normally would also verify cryptographically)
                                val txId = txData.get("tx_id")?.asString ?: return@launch
                                val senderId = txData.get("sender_id")?.asString ?: return@launch
                                val qrReceiverId = txData.get("receiver_id")?.asString ?: return@launch
                                val amount = txData.get("amount")?.asDouble ?: return@launch
                                val sequenceCounter = txData.get("sequence_counter")?.asInt ?: return@launch
                                val nonce = txData.get("nonce")?.asString ?: return@launch
                                val timestamp = txData.get("timestamp")?.asLong ?: return@launch
                                val signature = txData.get("signature")?.asString ?: return@launch
                                val pubKey = txData.get("pub_key")?.asString ?: return@launch
                                
                                val db = AppDatabase.getDatabase(context)
                                val wallet = db.walletDao().getWallet()
                                val myUserId = wallet?.userId ?: "Unknown"

                                // Verify the transaction is meant for me
                                if (qrReceiverId != myUserId && qrReceiverId != "device_bob") {
                                    android.widget.Toast.makeText(context, "QR is addressed to '$qrReceiverId', not you ('$myUserId')!", android.widget.Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                
                                val payloadStr = "$txId|$senderId|$qrReceiverId|$amount|$sequenceCounter|$nonce|$timestamp"
                                
                                // Verify signature
                                val keystoreManager = KeystoreManager()
                                val isValid = keystoreManager.verifySignature(payloadStr, signature, pubKey)
                                
                                if (!isValid) {
                                    android.widget.Toast.makeText(context, "Invalid Signature! Payment rejected.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                
                                // If valid: insert using qrReceiverId so the signature matches during backend sync!
                                db.insertTransactionSafely(
                                    txId, senderId, qrReceiverId, amount, sequenceCounter, nonce, timestamp, signature, "PENDING_SYNC"
                                )
                                db.walletDao().addFunds(amount) // Credit local wallet
                                navController.popBackStack()
                            } catch (e: Exception) {
                                Log.e("ScanScreen", "Error processing QR", e)
                            }
                        }
                    }) {
                        Text("Accept Payment")
                    }
                }
            }
        } else {
            Text("Camera permission is required.")
        }
    }
}

fun decodeQRCode(imageProxy: ImageProxy): String? {
    if (imageProxy.format == android.graphics.ImageFormat.YUV_420_888) {
        val planes = imageProxy.planes
        val yBuffer = planes[0].buffer
        val ySize = yBuffer.remaining()
        val yData = ByteArray(ySize)
        yBuffer.get(yData, 0, ySize)
        
        val source = PlanarYUVLuminanceSource(
            yData,
            imageProxy.width,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false
        )
        val binarizer = HybridBinarizer(source)
        val binaryBitmap = BinaryBitmap(binarizer)
        
        try {
            val result = MultiFormatReader().decode(binaryBitmap)
            return result.text
        } catch (e: Exception) {
            // Not found
        }
    }
    return null
}

