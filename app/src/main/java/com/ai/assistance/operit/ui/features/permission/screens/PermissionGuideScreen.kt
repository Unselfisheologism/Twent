package com.ai.assistance.operit.ui.features.permission.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.ui.features.permission.viewmodel.PermissionGuideViewModel
import com.ai.assistance.operit.ui.theme.CyanPrimary
import com.ai.assistance.operit.ui.theme.OrangePrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INTRO_PAGES_COUNT = 3
private const val WELCOME_PAGE_INDEX = INTRO_PAGES_COUNT
private const val TOTAL_PAGES_COUNT = INTRO_PAGES_COUNT + 1

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PermissionGuideScreen(
    viewModel: PermissionGuideViewModel = viewModel(),
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES_COUNT })
    val uiState by viewModel.uiState.collectAsState()

    // Animation for welcome page
    val scale = remember { Animatable(0.8f) }
    LaunchedEffect(pagerState.currentPage == WELCOME_PAGE_INDEX) {
        if (pagerState.currentPage == WELCOME_PAGE_INDEX) {
            scale.animateTo(1f, animationSpec = tween(durationMillis = 500))
        }
    }

    // 完成设置后的回调
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            delay(500)
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 进度指示器 - Modern design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(TOTAL_PAGES_COUNT) { index ->
                    val isActive = index <= pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 32.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) OrangePrimary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                    )
                    if (index < TOTAL_PAGES_COUNT - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            // 主内容
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> IntroductionPage(
                        icon = Icons.Default.SmartToy,
                        title = "Your AI Assistant",
                        description = "Meet your intelligent companion that understands your needs and automates your digital life with voice commands and smart triggers.",
                        pageIndex = 0
                    )
                    1 -> IntroductionPage(
                        icon = Icons.Default.RocketLaunch,
                        title = "Powerful Automation",
                        description = "Create custom workflows and automations. Schedule tasks, trigger actions, and let AI handle the repetitive work while you focus on what matters.",
                        pageIndex = 1
                    )
                    2 -> IntroductionPage(
                        icon = Icons.Default.Lightbulb,
                        title = "Always Learning",
                        description = "Your assistant grows smarter with every interaction. It remembers your preferences and adapts to your unique workflow patterns.",
                        pageIndex = 2
                    )
                    WELCOME_PAGE_INDEX -> WelcomePage(
                        modifier = Modifier.scale(scale.value),
                        onComplete = {
                            viewModel.savePermissionLevel()
                        }
                    )
                }
            }

            // 底部导航按钮 - Modern design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 上一步按钮
                IconButton(
                    onClick = {
                        scope.launch {
                            if (pagerState.currentPage > 0) {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Previous",
                        tint = if (pagerState.currentPage > 0)
                            OrangePrimary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // 页面指示器文本
                Text(
                    text = when (pagerState.currentPage) {
                        in 0 until INTRO_PAGES_COUNT ->
                            "${pagerState.currentPage + 1} / $INTRO_PAGES_COUNT"
                        WELCOME_PAGE_INDEX -> "Ready to Start"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                // 下一步/完成按钮
                IconButton(
                    onClick = {
                        scope.launch {
                            when {
                                pagerState.currentPage == WELCOME_PAGE_INDEX -> {
                                    viewModel.savePermissionLevel()
                                }
                                pagerState.currentPage < pagerState.pageCount - 1 -> {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        }
                    },
                    enabled = when (pagerState.currentPage) {
                        in 0..WELCOME_PAGE_INDEX -> true
                        else -> false
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (pagerState.currentPage == WELCOME_PAGE_INDEX)
                            Icons.Default.Check
                        else
                            Icons.Default.ArrowForward,
                        contentDescription = if (pagerState.currentPage == WELCOME_PAGE_INDEX)
                            "Complete"
                        else
                            "Next",
                        tint = when {
                            pagerState.currentPage < WELCOME_PAGE_INDEX -> OrangePrimary
                            pagerState.currentPage == WELCOME_PAGE_INDEX -> CyanPrimary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroductionPage(
    icon: ImageVector,
    title: String,
    description: String,
    pageIndex: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon container with gradient border
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(OrangePrimary, CyanPrimary)
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(25.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = OrangePrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3
        )
    }
}

@Composable
private fun WelcomePage(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Welcome icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            OrangePrimary.copy(alpha = 0.3f),
                            CyanPrimary.copy(alpha = 0.1f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = CyanPrimary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your AI assistant is ready to help. Grant the necessary permissions to unlock all features and start automating your digital life.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Get Started Button
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OrangePrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
