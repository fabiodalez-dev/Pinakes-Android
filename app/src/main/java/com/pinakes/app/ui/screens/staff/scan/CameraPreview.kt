package com.pinakes.app.ui.screens.staff.scan

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * CameraX preview + ZXing analysis with a viewfinder overlay (a frame and a horizontal scan
 * line). Binds to the composition's lifecycle and unbinds when it leaves. [onHasTorch] reports
 * whether the camera has a flash unit; [torchOn] switches it.
 */
@Composable
fun CameraPreview(
    onCode: (String) -> Unit,
    torchOn: Boolean,
    onHasTorch: (Boolean) -> Unit,
    onUnavailable: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            runCatching {
                val p = providerFuture.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { a ->
                        a.setAnalyzer(executor, BarcodeAnalyzer { code -> mainExecutor.execute { currentOnCode(code) } })
                    }
                p.unbindAll()
                val selector = if (p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                else CameraSelector.DEFAULT_FRONT_CAMERA
                camera = p.bindToLifecycle(lifecycleOwner, selector, preview, analysis).also { c ->
                    onHasTorch(c.cameraInfo.hasFlashUnit())
                }
            }.onFailure { onUnavailable() }
        }, mainExecutor)
        onDispose {
            runCatching { provider?.unbindAll() }
            camera = null
            executor.shutdown()
        }
    }

    LaunchedEffect(torchOn, camera) {
        camera?.let { c -> if (c.cameraInfo.hasFlashUnit()) runCatching { c.cameraControl.enableTorch(torchOn) } }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val frameW = size.width * 0.82f
            val frameH = size.height * 0.5f
            val left = (size.width - frameW) / 2
            val top = (size.height - frameH) / 2
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(left, top),
                size = GeoSize(frameW, frameH),
                cornerRadius = CornerRadius(24f, 24f),
                style = Stroke(width = 4f),
            )
            drawLine(accent, Offset(left + 16f, size.height / 2), Offset(left + frameW - 16f, size.height / 2), strokeWidth = 4f)
        }
    }
}
