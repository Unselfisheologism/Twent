package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.ai.assistance.operit.terminal.main.TerminalScreen as TerminalViewScreen

@Composable
fun TerminalToolScreen(navController: NavController, initialCommand: String? = null, forceShowSetup: Boolean = false) {
    TerminalViewScreen(
        navController = navController,
        initialCommand = initialCommand,
        forceShowSetup = forceShowSetup
    )
}
