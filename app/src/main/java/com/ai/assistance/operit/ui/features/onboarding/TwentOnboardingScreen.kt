package com.ai.assistance.operit.ui.features.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * New Twent Onboarding Screen - Dithered/Pixel/Techy/Godly aesthetic
 * Implements 3-phase Diagnostic-Prescription-Activation framework
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TwentOnboardingScreen(
    onContinue: () -> Unit,
    onBasicMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 5 })
    var selectedUserType by remember { mutableStateOf(-1) }
    
    // Animated background
    val infiniteTransition = rememberInfiniteTransition()
    val ditherOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .drawBehind {
                drawDitheredBackground(ditherOffset)
            }
    ) {
        // Main content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Skip button at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < 4) {
                    TextButton(onClick = onContinue) {
                        Text(
                            text = "Skip",
                            color = Color(0xFFB0B0B0),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // Horizontal pager for onboarding screens
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> DiagnosticScreen(selectedUserType) { selectedUserType = it }
                    1 -> AgenticOSRevelationScreen()
                    2 -> AgentCLIShowcaseScreen()
                    3 -> OverlayAgentScreen()
                    4 -> ActivationScreen(onContinue, onBasicMode)
                }
            }
            
            // Page indicators and navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(5) { index ->
                        val color = if (index == pagerState.currentPage) {
                            Color(0xFFF09020)
                        } else {
                            Color(0xFF282828)
                        }
                        val width = if (index == pagerState.currentPage) 24.dp else 8.dp
                        Box(
                            modifier = Modifier
                                .width(width)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )
                        if (index < 4) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Next/Continue button
                if (pagerState.currentPage < 4) {
                    Button(
                        onClick = {
                            // Animate to next page
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF09020),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Continue",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Terms of service
                val annotatedString = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFF505050),
                            fontSize = 12.sp
                        )
                    ) {
                        append("By continuing, you agree to our ")
                    }
                    
                    pushStringAnnotation(
                        tag = "URL",
                        annotation = "https://twent.xyz/terms"
                    )
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFFF09020).copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("Terms of Service")
                    }
                    pop()
                }
                
                androidx.compose.foundation.text.ClickableText(
                    text = annotatedString,
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(
                            tag = "URL",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DiagnosticScreen(
    selectedUserType: Int,
    onUserTypeSelected: (Int) -> Unit
) {
    // Animated pixel eye
    var eyeOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        eyeOpen = true
    }
    
    val eyeScale by animateFloatAsState(
        targetValue = if (eyeOpen) 1f else 0f,
        animationSpec = tween(1500, easing = EaseOutBack)
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Divine light rays
        Box(
            modifier = Modifier
                .size(200.dp)
                .drawBehind {
                    drawDivineRays(eyeScale)
                },
            contentAlignment = Alignment.Center
        ) {
            // Pixel eye
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = eyeScale
                        scaleY = eyeScale
                    }
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF09020),
                                Color(0xFFD07010)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Pupil
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0A0A))
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Headline
        Text(
            text = "Your Phone is Underutilized",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subheadline
        Text(
            text = "You're using 10% of what's possible",
            color = Color(0xFFB0B0B0),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // User type question
        Text(
            text = "What kind of user are you?",
            color = Color(0xFF80C0F0),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // User type options
        val userTypes = listOf(
            "Power User - I want automation" to Icons.Outlined.Bolt,
            "Developer - I want CLI access" to Icons.Outlined.Code,
            "Creator - I want AI assistance" to Icons.Outlined.AutoAwesome,
            "Explorer - Show me everything" to Icons.Outlined.Explore
        )
        
        userTypes.forEachIndexed { index, (text, icon) ->
            val isSelected = selectedUserType == index
            val borderColor = if (isSelected) Color(0xFFF09020) else Color(0xFF282828)
            val backgroundColor = if (isSelected) Color(0x1AF09020) else Color.Transparent
            
            OutlinedButton(
                onClick = { onUserTypeSelected(index) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = backgroundColor,
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected) Color(0xFFF09020) else Color(0xFF80C0F0)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
            
            if (index < userTypes.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AgenticOSRevelationScreen() {
    // Animated features appearing
    var featuresVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(800)
        featuresVisible = true
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated phone transformation
        Box(
            modifier = Modifier
                .size(200.dp)
                .drawBehind {
                    drawTechyGrid()
                },
            contentAlignment = Alignment.Center
        ) {
            // Phone outline with glow
            Box(
                modifier = Modifier
                    .size(160.dp, 280.dp)
                    .graphicsLayer {
                        rotationZ = -15f
                    }
                    .drawBehind {
                        drawPhoneOutline()
                    }
            )
            
            // Feature nodes
            val features = listOf(
                Color(0xFFF09020) to Offset(-60f, -40f),  // AI Agent
                Color(0xFF80C0F0) to Offset(60f, -40f),   // Terminal
                Color(0xFF405050) to Offset(-60f, 40f),    // Apps
                Color(0xFF90A0A0) to Offset(60f, 40f)     // Workflows
            )
            
            features.forEachIndexed { index, (color, offset) ->
                val scale by animateFloatAsState(
                    targetValue = if (featuresVisible) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 500,
                        delayMillis = 1000 + index * 300,
                        easing = EaseOutBack
                    )
                )
                
                Box(
                    modifier = Modifier
                        .offset(x = offset.x.dp, y = offset.y.dp)
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(color)
                )
            }
            
            // Connection lines
            if (featuresVisible) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val lineColor = Color(0xFFF09020).copy(alpha = 0.3f)
                    
                    drawLine(
                        color = lineColor,
                        start = center,
                        end = Offset(center.x - 60.dp.toPx(), center.y - 40.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF80C0F0).copy(alpha = 0.3f),
                        start = center,
                        end = Offset(center.x + 60.dp.toPx(), center.y - 40.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF405050).copy(alpha = 0.3f),
                        start = center,
                        end = Offset(center.x - 60.dp.toPx(), center.y + 40.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF90A0A0).copy(alpha = 0.3f),
                        start = center,
                        end = Offset(center.x + 60.dp.toPx(), center.y + 40.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Headline
        Text(
            text = "Your Personal Agentic OS",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subheadline
        Text(
            text = "All in your pocket",
            color = Color(0xFF80C0F0),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Features list
        val features = listOf(
            "🤖 AI Agent with 40+ tools",
            "🖥️ Full Ubuntu 24 Terminal",
            "🔌 1000+ App Connections",
            "⚡ Workflow Automations",
            "🧠 Skills & MCP Servers"
        )
        
        features.forEachIndexed { index, feature ->
            val alpha by animateFloatAsState(
                targetValue = if (featuresVisible) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 500,
                    delayMillis = 1500 + index * 200
                )
            )
            
            Text(
                text = feature,
                color = Color(0xFFB0B0B0).copy(alpha = alpha),
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AgentCLIShowcaseScreen() {
    // Terminal typing animation
    var terminalText by remember { mutableStateOf("") }
    val fullText = "$ twent --agent claude-code --task \"build an app\""
    
    LaunchedEffect(Unit) {
        delay(1000)
        fullText.forEachIndexed { index, _ ->
            terminalText = fullText.substring(0, index + 1)
            delay(50)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Terminal window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0A0A))
                .border(
                    width = 1.dp,
                    color = Color(0xFF282828),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Terminal header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color(0xFF1E1E1E))
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF4444))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFAA00))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00FF00))
                    )
                }
            }
            
            // Terminal content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                // Command prompt
                Row {
                    Text(
                        text = "$",
                        color = Color(0xFFF09020),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = terminalText,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    // Blinking cursor
                    var cursorVisible by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(500)
                            cursorVisible = !cursorVisible
                        }
                    }
                    if (cursorVisible) {
                        Text(
                            text = "█",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // CLI icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val cliTools = listOf(
                        "Claude Code" to Color(0xFFD97706),
                        "Codex" to Color(0xFF10A37F),
                        "Hermes-Agent" to Color(0xFF8B5CF6),
                        "Ubuntu" to Color(0xFFE95420)
                    )
                    
                    cliTools.forEachIndexed { index, (name, color) ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(2000L + index * 300)
                            visible = true
                        }
                        
                        val scale by animateFloatAsState(
                            targetValue = if (visible) 1f else 0f,
                            animationSpec = tween(500, easing = EaseOutBack)
                        )
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name,
                                color = Color(0xFFB0B0B0),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Headline
        Text(
            text = "World's Best Agent CLIs",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subheadline
        Text(
            text = "In your pocket",
            color = Color(0xFF80C0F0),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Features list
        val features = listOf(
            "Claude Code - Build anything",
            "Codex - Autonomous coding",
            "Hermes-Agent - Your AI companion",
            "Full Linux environment"
        )
        
        features.forEach { feature ->
            Text(
                text = "• $feature",
                color = Color(0xFFB0B0B0),
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun OverlayAgentScreen() {
    // Animated overlay activation
    var overlayActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(800)
        overlayActive = true
    }
    
    val overlayRadius by animateFloatAsState(
        targetValue = if (overlayActive) 60f else 0f,
        animationSpec = tween(1000, easing = EaseOutBack)
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Phone with overlay
        Box(
            modifier = Modifier
                .size(200.dp)
                .drawBehind {
                    drawDivineGlow(overlayActive)
                },
            contentAlignment = Alignment.Center
        ) {
            // Phone outline
            Box(
                modifier = Modifier
                    .size(120.dp, 200.dp)
                    .drawBehind {
                        drawPhoneOutline()
                    }
            )
            
            // Overlay circle
            if (overlayActive) {
                Box(
                    modifier = Modifier
                        .size(overlayRadius.dp * 2)
                        .drawBehind {
                            drawCircle(
                                color = Color(0xFFF09020).copy(alpha = 0.3f),
                                radius = overlayRadius.dp.toPx(),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                )
                
                // Agent eye in overlay
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF09020))
                )
            }
            
            // Automation taps
            if (overlayActive) {
                val taps = listOf(
                    Offset(-30f, -30f),
                    Offset(0f, 0f),
                    Offset(30f, -30f)
                )
                
                taps.forEachIndexed { index, offset ->
                    var tapVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(1500L + index * 300)
                        tapVisible = true
                    }
                    
                    if (tapVisible) {
                        Box(
                            modifier = Modifier
                                .offset(x = offset.x.dp, y = offset.y.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF80C0F0))
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Headline
        Text(
            text = "Ultimate UI Automation",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subheadline
        Text(
            text = "Your Agent sees and controls everything",
            color = Color(0xFF80C0F0),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Features list
        val features = listOf(
            "Long-press power button to activate",
            "Voice activation anywhere",
            "Screen context capture",
            "Tap, swipe, type automation",
            "Works with any app"
        )
        
        features.forEach { feature ->
            Text(
                text = "• $feature",
                color = Color(0xFFB0B0B0),
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ActivationScreen(
    onContinue: () -> Unit,
    onBasicMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Divine rays background
        Box(
            modifier = Modifier
                .size(200.dp)
                .drawBehind {
                    drawActivationRays()
                },
            contentAlignment = Alignment.Center
        ) {
            // Activation button
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .size(160.dp, 80.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color(0xFFF09020).copy(alpha = 0.3f),
                        spotColor = Color(0xFFF09020).copy(alpha = 0.3f)
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF09020),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "ACTIVATE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Headline
        Text(
            text = "Activate Your Agentic OS",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subheadline
        Text(
            text = "Grant permissions to unlock full power",
            color = Color(0xFF80C0F0),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Permissions needed
        val permissions = listOf(
            "Accessibility - For UI automation",
            "Overlay - For always-available agent",
            "Terminal - For CLI access"
        )
        
        permissions.forEach { permission ->
            Text(
                text = "• $permission",
                color = Color(0xFFB0B0B0),
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Basic mode option
        OutlinedButton(
            onClick = onBasicMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFB0B0B0)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = Color(0xFF282828)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Explore in Basic Mode",
                fontSize = 16.sp
            )
        }
    }
}

// Helper drawing functions
private fun DrawScope.drawDitheredBackground(offset: Float) {
    val ditherSize = 4.dp.toPx()
    val rows = (size.height / ditherSize).toInt() + 1
    val cols = (size.width / ditherSize).toInt() + 1
    
    for (row in 0..rows) {
        for (col in 0..cols) {
            val x = col * ditherSize
            val y = row * ditherSize
            val noise = sin((x + offset) * 0.01f) * cos((y + offset) * 0.01f)
            val alpha = (0.02f + 0.01f * noise).coerceIn(0f, 0.05f)
            
            drawRect(
                color = Color.White.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(ditherSize, ditherSize)
            )
        }
    }
}

private fun DrawScope.drawDivineRays(scale: Float) {
    val center = Offset(size.width / 2, size.height / 2)
    val rayLength = 100.dp.toPx() * scale
    
    for (i in 0 until 8) {
        val angle = i * 45f * (PI / 180f).toFloat()
        val endX = center.x + cos(angle) * rayLength
        val endY = center.y + sin(angle) * rayLength
        
        drawLine(
            color = Color(0xFFF09020).copy(alpha = 0.3f * scale),
            start = center,
            end = Offset(endX, endY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

private fun DrawScope.drawTechyGrid() {
    val gridSpacing = 20.dp.toPx()
    val gridColor = Color(0xFF282828).copy(alpha = 0.3f)
    
    // Vertical lines
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.dp.toPx()
        )
        x += gridSpacing
    }
    
    // Horizontal lines
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
        y += gridSpacing
    }
}

private fun DrawScope.drawPhoneOutline() {
    val strokeWidth = 3.dp.toPx()
    val cornerRadius = 12.dp.toPx()
    
    drawRoundRect(
        color = Color(0xFF80C0F0),
        topLeft = Offset(0f, 0f),
        size = size,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = strokeWidth)
    )
}

private fun DrawScope.drawDivineGlow(active: Boolean) {
    if (active) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = 80.dp.toPx()
        
        drawCircle(
            color = Color(0xFFF09020).copy(alpha = 0.1f),
            radius = radius,
            center = center
        )
        
        drawCircle(
            color = Color(0xFFF09020).copy(alpha = 0.05f),
            radius = radius * 1.5f,
            center = center
        )
    }
}

private fun DrawScope.drawActivationRays() {
    val center = Offset(size.width / 2, size.height / 2)
    val rayLength = 100.dp.toPx()
    
    for (i in 0 until 12) {
        val angle = i * 30f * (PI / 180f).toFloat()
        val endX = center.x + cos(angle) * rayLength
        val endY = center.y + sin(angle) * rayLength
        
        drawLine(
            color = Color(0xFFF09020).copy(alpha = 0.2f),
            start = center,
            end = Offset(endX, endY),
            strokeWidth = 2.dp.toPx()
        )
    }
}