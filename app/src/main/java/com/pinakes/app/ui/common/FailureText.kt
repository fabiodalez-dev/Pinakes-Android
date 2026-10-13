package com.pinakes.app.ui.common

import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes

/**
 * The server's own message for a failure, or null when there is none worth showing. A NETWORK
 * failure carries the raw IOException text ("Failed to connect to /10.0.2.2:8081"), which must
 * never reach the UI: callers fall back to their localized message (offline wording for NETWORK).
 */
fun ApiResult.Failure.serverMessage(): String? = message.takeIf { it.isNotBlank() && code != ErrorCodes.NETWORK }

/** True for "no connection" failures. */
val ApiResult.Failure.isOffline: Boolean get() = code == ErrorCodes.NETWORK

/** The text to show for [this] failure: the server's message, else offline wording or [fallback]. */
@androidx.compose.runtime.Composable
fun ApiResult.Failure.uiText(@androidx.annotation.StringRes fallback: Int): String =
    serverMessage() ?: androidx.compose.ui.res.stringResource(if (isOffline) com.pinakes.app.R.string.offline_generic else fallback)
