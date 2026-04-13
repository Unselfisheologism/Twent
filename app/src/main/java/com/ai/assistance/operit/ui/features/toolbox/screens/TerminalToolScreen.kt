package com.ai.assistance.operit.ui.features.toolbox.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.main.TerminalScreen
import com.ai.assistance.operit.terminal.rememberTerminalEnv

/**
 * Wrapper screen for Terminal - creates TerminalEnv and passes to TerminalScreen
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TerminalToolScreen(
    navController: NavController,
    initialCommand: String? = null,
    forceShowSetup: Boolean = false
) {
    val context = LocalContext.current
    val terminalManager = remember { TerminalManager.getInstance(context) }
    val env = rememberTerminalEnv(
        terminalManager = terminalManager,
        forceShowSetup = forceShowSetup,
        initialCommand = initialCommand
    )
    TerminalScreen(env = env)
}
