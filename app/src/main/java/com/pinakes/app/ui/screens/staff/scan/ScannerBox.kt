package com.pinakes.app.ui.screens.staff.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.pinakes.app.R
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.theme.LocalPinakesColors
import com.pinakes.app.ui.theme.Spacing

/**
 * The camera viewfinder of the staff scanners (copy labels, periodical issues): asks for the
 * camera once, re-checks on resume, explains a refusal with [permissionText], and dims with a
 * spinner while [looking]. The manual entry next to it stays the path without a camera.
 */
@Composable
internal fun ScannerBox(
    formats: List<BarcodeFormat>,
    onCode: (String) -> Unit,
    looking: Boolean,
    permissionText: String,
    modifier: Modifier = Modifier,
    appendEanAddOn: Boolean = false,
) {
    val context = LocalContext.current
    fun hasCamera() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasCamera()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var cameraBroken by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted && !asked) permission.launch(Manifest.permission.CAMERA) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = hasCamera() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(LocalPinakesColors.current.coverBlank),
        contentAlignment = Alignment.Center,
    ) {
        if (granted && !cameraBroken) {
            CameraPreview(
                onCode = onCode, torchOn = false, onHasTorch = {}, onUnavailable = { cameraBroken = true },
                accent = LocalPinakesColors.current.accent, modifier = Modifier.fillMaxSize(), formats = formats,
                appendEanAddOn = appendEanAddOn,
            )
        } else {
            Column(Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (cameraBroken) stringResource(R.string.staff_camera_unavailable) else permissionText,
                    color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
                if (!granted && !cameraBroken) {
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(stringResource(R.string.staff_scan_permission_allow), onClick = { permission.launch(Manifest.permission.CAMERA) })
                }
            }
        }
        if (looking) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            }
        }
    }
}
