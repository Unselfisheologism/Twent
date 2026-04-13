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
 * Wrapper screen for Terminal Auto Config - forces setup screen to show
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TerminalAutoConfigToolScreen(navController: NavController) {
    val context = LocalContext.current
    val terminalManager = remember { TerminalManager.getInstance(context) }
    val env = rememberTerminalEnv(
        terminalManager = terminalManager,
        forceShowSetup = true
    )
    TerminalScreen(env = env)
}
