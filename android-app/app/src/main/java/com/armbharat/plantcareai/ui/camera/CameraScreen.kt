
package com.armbharat.plantcareai.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import androidx.compose.ui.res.stringResource
import com.armbharat.plantcareai.R

@Composable
fun CameraScreen(
    onBackClick: () -> Unit,
    onPhotoCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler {
        onBackClick()
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isCameraReady by remember {
        mutableStateOf(false)
    }

    var cameraError by remember {
        mutableStateOf<String?>(null)
    }

    var cameraProvider by remember {
        mutableStateOf<ProcessCameraProvider?>(null)
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode =
                PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val preview = remember {
        Preview.Builder().build()
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(
                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            )
            .build()
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    // Bind Preview and ImageCapture to the same camera session
    LaunchedEffect(hasCameraPermission, lifecycleOwner) {

        if (!hasCameraPermission) return@LaunchedEffect

        isCameraReady = false
        cameraError = null

        val providerFuture =
            ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({

            try {
                val provider = providerFuture.get()

                provider.unbindAll()

                preview.setSurfaceProvider(
                    previewView.surfaceProvider
                )

                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

                cameraProvider = provider
                isCameraReady = true

            } catch (exception: Exception) {

                cameraError = exception.message
                    ?: "Unable to start camera"

                Toast.makeText(
                    context,
                    cameraError,
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    // Release camera when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            isCameraReady = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        if (hasCameraPermission) {

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )


            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 18.dp)
            ) {

                // Top header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(
                            Color.Black.copy(alpha = 0.45f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBackClick) {
                        Text(	stringResource(R.string.settings_back), color = Color.White)
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        stringResource(R.string.app_name),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Centre leaf guide
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(290.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.camera_position_leaf),
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.45f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    )

                    // Focus frame
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                2.dp,
                                Color.White.copy(alpha = 0.8f),
                                RoundedCornerShape(24.dp)
                            )
                    )
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = when {
                            cameraError != null -> "Camera error"
                            isCameraReady -> stringResource(R.string.camera_ready)
                            else -> "Starting camera..."
                        },
                        color = if (isCameraReady) {
                            Color(0xFF8BC34A)
                        } else {
                            Color.White
                        },
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val photoFile = File(
                                context.cacheDir,
                                "plant_scan_${System.currentTimeMillis()}.jpg"
                            )

                            val outputOptions =
                                ImageCapture.OutputFileOptions.Builder(
                                    photoFile
                                ).build()

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {

                                    override fun onImageSaved(
                                        outputFileResults: ImageCapture.OutputFileResults
                                    ) {
                                        onPhotoCaptured(photoFile.absolutePath)
                                    }

                                    override fun onError(
                                        exception: ImageCaptureException
                                    ) {
                                        Toast.makeText(
                                            context,
                                            "Capture failed: ${exception.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        },
                        enabled = isCameraReady && cameraProvider != null,
                        modifier = Modifier
                            .size(82.dp)
                            .border(4.dp, Color.White, CircleShape),
                        shape = CircleShape
                    ) {
                        Text("●", fontSize = 26.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        stringResource(R.string.camera_keep_steady),
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }


        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.camera_permission_required),
                    color = Color.White
                )

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    }
                ) {
                    Text(stringResource(R.string.camera_grant_permission))
                }
            }
        }
    }
}
