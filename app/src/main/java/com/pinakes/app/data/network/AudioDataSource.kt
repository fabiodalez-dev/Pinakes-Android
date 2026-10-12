@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pinakes.app.data.network

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient

/** No API bearer token: media may be hosted on a different origin. */
internal fun guardedMediaHttpClient(allowInsecureHttp: () -> Boolean): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(CleartextGuardInterceptor(allowInsecureHttp))
    // Application interceptors run once; this also checks each redirected destination.
    .addNetworkInterceptor(CleartextGuardInterceptor(allowInsecureHttp))
    .build()

internal fun audioDataSourceFactory(context: Context, allowInsecureHttp: () -> Boolean): DataSource.Factory =
    DefaultDataSource.Factory(context, OkHttpDataSource.Factory(guardedMediaHttpClient(allowInsecureHttp)))
