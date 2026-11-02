package com.pinakes.app.ui.screens.staff.s090.patrons

import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.staff.Staff090PatronsErrors
import com.pinakes.app.ui.common.AppLocale
import com.pinakes.app.ui.screens.movements.MoneyFormat
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.toStaffMessage

/** The message for a failed patron-tools call: our wording for the 0.9.0 codes first. */
internal fun ApiResult.Failure.toPatronsMessage(@StringRes fallback: Int): StaffMessage =
    Staff090PatronsErrors.messageRes(this)?.let { StaffMessage(it) } ?: toStaffMessage(fallback)

/** Cents in the server's currency, as the reader movements screen writes them. */
@Composable
internal fun money(cents: Int, currency: String?): String = MoneyFormat.format(cents, currency, AppLocale.current())

/**
 * A patron photo from its bytes (fetched with the bearer token, kept in memory only), or the
 * person placeholder while there is none or it cannot be decoded.
 */
@Composable
internal fun PatronPhoto(bytes: ByteArray?, description: String?, size: Dp = 88.dp, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) { bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() } }
    val shape = RoundedCornerShape(12.dp)
    if (bitmap != null) {
        Image(bitmap, contentDescription = description, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(shape))
    } else {
        Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Person, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
        }
    }
}

/** The desk's short check-in tones: one beep for a welcome, two low ones for a problem. */
internal object DeskTone {
    fun play(success: Boolean) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(if (success) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, if (success) 150 else 300)
            // Released after the tone has played; a ToneGenerator holds a native audio track.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
        }
    }
}
