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
                                val type = object : TypeToken<Map<String, Any>>() {}.type
                                val txData: Map<String, Any> = Gson().fromJson(scannedResult, type)
                                
                                val txId = txData["tx_id"] as String
                                val senderId = txData["sender_id"] as String
                                val receiverId = txData["receiver_id"] as String
                                val amount = (txData["amount"] as Double)
                                val sequenceCounter = (txData["sequence_counter"] as Double).toInt()
                                val nonce = txData["nonce"] as String
                                val timestamp = (txData["timestamp"] as Double).toLong()
                                val signature = txData["signature"] as String
                                
                                val db = AppDatabase.getDatabase(context)
                                db.insertTransactionSafely(
                                    txId, senderId, receiverId, amount, sequenceCounter, nonce, timestamp, signature, "PENDING_SYNC"
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

