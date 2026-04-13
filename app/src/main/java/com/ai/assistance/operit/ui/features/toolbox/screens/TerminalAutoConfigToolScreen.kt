package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.ai.assistance.operit.terminal.main.TerminalScreen as TerminalViewScreen

@Composable
fun TerminalAutoConfigToolScreen(navController: NavController) {
    TerminalViewScreen(
        navController = navController,
        forceShowSetup = true
    )
}
