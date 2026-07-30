package com.dorybrain.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dorybrain.desktop.ui.DesktopApp

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1040.dp, 760.dp))

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Dory Brain"
    ) {
        val scope = rememberCoroutineScope()
        val store = remember { DesktopStore(scope) }

        DesktopApp(store)
    }
}
