package com.pinakes.app.ui.screens.staff.s090

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pinakes.app.ui.components.PinakesTopBar

/** Temporary body of a 0.9.0 staff screen while its area is being built. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Staff090Placeholder(title: String, onNavigateUp: () -> Unit) {
    Scaffold(topBar = { PinakesTopBar(title = title, onNavigateUp = onNavigateUp) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(title) }
    }
}
