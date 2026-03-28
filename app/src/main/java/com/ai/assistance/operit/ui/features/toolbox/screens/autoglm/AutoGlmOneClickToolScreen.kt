package com.ai.assistance.operit.ui.features.toolbox.screens.autoglm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun AutoGlmOneClickToolScreen(
    navController: NavController,
    onNavigateToModelConfig: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("AutoGLM One Click Tool")
    }
}