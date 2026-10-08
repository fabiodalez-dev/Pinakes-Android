package com.pinakes.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.pinakes.app.ui.theme.LocalPinakesColors

/** Scrollable authentication sheet, including when the keyboard or large type is in use. */
@Composable
fun AuthForm(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalPinakesColors.current
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.heroWash, colors.background)))
        .imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = MaterialTheme.shapes.large, color = colors.surface, shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
    }
}
