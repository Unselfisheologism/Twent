package com.ai.assistance.operit.ui.features.permission.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.permission.viewmodel.PermissionGuideViewModel
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES_COUNT })
    val uiState by viewModel.uiState.collectAsState()

    // 页面切换效果
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            in 0..WELCOME_PAGE_INDEX ->
                viewModel.setCurrentStep(PermissionGuideViewModel.Step.WELCOME)
        }
    }

    // 完成设置后的回调
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            delay(500) // 短暂延迟，让用户看到完成状态
            onComplete()
        }
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 进度指示器
        LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / pagerState.pageCount },
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // 主内容
        HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                userScrollEnabled = false
        ) { page ->
            when (page) {
                0 ->
                        IntroductionPage(
                                title = stringResource(R.string.permission_guide_intro_1_title),
                                description =
                                        stringResource(R.string.permission_guide_intro_1_desc),
                                pageIndex = 0
                        )
                1 ->
                        IntroductionPage(
                                title = stringResource(R.string.permission_guide_intro_2_title),
                                description =
                                        stringResource(R.string.permission_guide_intro_2_desc),
                                pageIndex = 1
                        )
                2 ->
                        IntroductionPage(
                                title = stringResource(R.string.permission_guide_intro_3_title),
                                description =
                                        stringResource(R.string.permission_guide_intro_3_desc),
                                pageIndex = 2
                        )
                WELCOME_PAGE_INDEX -> WelcomePage(
                    onComplete = {
                        viewModel.savePermissionLevel()
                    }
                )
            }
        }

        // 底部导航按钮
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            // 上一步按钮
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                IconButton(
                        onClick = {
                            scope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage > 0
                ) {
                    Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.permission_guide_previous),
                            tint =
                                    if (pagerState.currentPage > 0)
                                            MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            // 当前步骤文本
            Text(
                    text =
                            when (pagerState.currentPage) {
                                in 0 until INTRO_PAGES_COUNT ->
                                        stringResource(
                                                R.string.permission_guide_intro_page_indicator,
                                                pagerState.currentPage + 1,
                                                INTRO_PAGES_COUNT
                                        )
                                WELCOME_PAGE_INDEX -> stringResource(R.string.permission_guide_welcome)
                                BASIC_PERMISSIONS_PAGE_INDEX ->
                                        stringResource(R.string.permission_guide_basic_permissions)
                                else -> ""
                            },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
            )

            // 下一步按钮
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                IconButton(
                        onClick = {
                            scope.launch {
                                when {
                                    // 最后一页（欢迎页），完成设置
                                    pagerState.currentPage == WELCOME_PAGE_INDEX -> {
                                        viewModel.savePermissionLevel()
                                    }
                                    // 否则前进到下一页
                                    pagerState.currentPage < pagerState.pageCount - 1 -> {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            }
                        },
                        enabled =
                                when (pagerState.currentPage) {
                                    in 0..WELCOME_PAGE_INDEX -> true // 介绍页和欢迎页总是可以前进
                                    else -> false
                                }
                ) {
                    Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = stringResource(R.string.permission_guide_next),
                            tint =
                                    when {
                                        pagerState.currentPage < WELCOME_PAGE_INDEX ->
                                                MaterialTheme.colorScheme.primary
                                        pagerState.currentPage == WELCOME_PAGE_INDEX ->
                                                MaterialTheme.colorScheme.primary // 欢迎页总是显示可点击状态
                                        else ->
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.3f
                                                )
                                    }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroductionPage(title: String, description: String, pageIndex: Int) {
    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Box(
                modifier =
                        Modifier.size(80.dp)
                                .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        CircleShape
                                )
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
        ) {
            Text(
                    text = "#${pageIndex + 1}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun WelcomePage(onComplete: () -> Unit) {
    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Text(
                text = stringResource(R.string.permission_guide_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
                text = stringResource(R.string.permission_guide_welcome_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
                text = stringResource(R.string.permission_guide_welcome_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
                onClick = onComplete,
                modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                    text = stringResource(R.string.permission_guide_get_started),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
            )
        }
    }
}

