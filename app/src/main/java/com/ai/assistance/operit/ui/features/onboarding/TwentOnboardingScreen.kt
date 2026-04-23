package com.ai.assistance.operit.ui.features.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.CalivePixelFamily
import com.ai.assistance.operit.ui.theme.OrangePrimary
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextDecoration
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TwentOnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    // Total slides: 8 (0=Video, 1-6=content slides, 7=consent)
    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 8

    // Video player state
    var videoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Cleanup video player
    DisposableEffect(Unit) {
        onDispose {
            videoPlayer?.release()
        }
    }

    // Define slide data - matching user's Canva design
    val slideData = listOf(
        // Step 0: Video placeholder (handled separately)
        OnboardingStep(imageRes = 0, title = "", isVideo = true),

        // Step 1: "An Assistant So Great, You Feel You Were Missing Out."
        OnboardingStep(
            imageRes = R.drawable.remove_the_text_202604171810,
            title = "An Assistant So Great,\nYou Feel You Were\nMissing Out."
        ),

        // Step 2: "Get Back Time From Your Busy Life, Delegate The Boring"
        OnboardingStep(
            imageRes = R.drawable.remove_the_duplicate_202604171914,
            title = "Get Back Time\nFrom Your Busy\nLife, Delegate\nThe Boring"
        ),

        // Step 3: "A Swarm Of Agents At Your Service"
        OnboardingStep(
            imageRes = R.drawable.apply_the_first_202604171724,
            title = "A Swarm\nOf Agents\nAt Your\nService"
        ),

        // Step 4: "Total Privacy: Bring Your Own Key, No Tracking, No Telemetry, Local Models"
        OnboardingStep(
            imageRes = R.drawable.apply_the_first_202604171816,
            title = "Total Privacy:\nBring Your Own\nKey, No Tracking,\nNo Telemetry,\nLocal Models"
        ),

        // Step 5: "Your Agents Can Use 1000+ Integrations, You Control How"
        OnboardingStep(
            imageRes = R.drawable.apply_the_first_202604172002,
            title = "Your Agents Can Use\n1000+ Integrations,\nYou Control How"
        ),

        // Step 6: "Mini Apps For All Your Needs, Made By AI."
        OnboardingStep(
            imageRes = R.drawable.create_the_following_202604171506,
            title = "Mini Apps\nFor All Your\nNeeds,\nMade By AI."
        ),

        // Step 7: Consent dialog (no image, shown as overlay)
        OnboardingStep(imageRes = 0, title = "", isConsent = true)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Step 0: Video player (with intro.mp4 from res/raw)
        if (currentStep == 0) {
            // Stop video when leaving this screen
            DisposableEffect(Unit) {
                onDispose {
                    videoPlayer?.release()
                    videoPlayer = null
                }
            }
            
            LaunchedEffect(Unit) {
                // Initialize and start video
                val player = ExoPlayer.Builder(context).build().apply {
                    val uri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.intro}")
                    setMediaItem(MediaItem.fromUri(uri))
                    repeatMode = ExoPlayer.REPEAT_MODE_ONE
                    prepare()
                    playWhenReady = true
                }
                videoPlayer = player
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = videoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.player = videoPlayer
                }
            )

            // Tap to proceed to first slide
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { currentStep = 1 }
            ) {
                Text(
                    "Tap to continue →",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontFamily = CalivePixelFamily
                )
            }
        } else {
            // Show slide content for steps 1-6
            if (currentStep in 1..6) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(600)) with
                                fadeOut(animationSpec = tween(600))
                    },
                    label = "slideTransition"
                ) { step ->
                    val stepData = slideData[step]

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Image(
                            painter = painterResource(id = stepData.imageRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dark overlay for text readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                        )

                        // Text overlay - spread horizontally, bottom-aligned with padding
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 48.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = stepData.title,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFamily = CalivePixelFamily,
                                    fontSize = 32.sp,
                                    lineHeight = 38.sp,
                                    color = Color.White
                                ),
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            )
                        }
                    }
                }
            }

            // Step 7: Consent Dialog Overlay
            if (currentStep == 7) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ready to Begin?",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = CalivePixelFamily,
                                    fontSize = 28.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "By clicking 'Get Started', you consent to our Terms of Service and Privacy Policy. We respect your privacy and never share your data with third parties.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // TOS and Privacy links
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Terms of Service",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = OrangePrimary,
                                        textDecoration = TextDecoration.Underline
                                    ),
                                    modifier = Modifier
                                        .clickable {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://twent.xyz/terms")
                                            )
                                            context.startActivity(intent)
                                        }
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = "Privacy Policy",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = OrangePrimary,
                                        textDecoration = TextDecoration.Underline
                                    ),
                                    modifier = Modifier
                                        .clickable {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://twent.xyz/privacy")
                                            )
                                            context.startActivity(intent)
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = onComplete,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OrangePrimary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = "Get Started",
                                    fontFamily = CalivePixelFamily,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Navigation buttons (not shown on video step or consent step)
        if (currentStep in 1..6) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Progress indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(6) { index ->
                        val isActive = index + 1 == currentStep
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (isActive) 24.dp else 8.dp,
                                    height = 8.dp
                                )
                                .background(
                                    color = if (isActive) OrangePrimary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        if (index < 5) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Next / Back buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Back button (hide on first content slide)
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = "← Back",
                                fontFamily = CalivePixelFamily,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Next / Continue button
                    Button(
                        onClick = {
                            currentStep++
                        },
                        modifier = Modifier
                            .weight(if (currentStep > 1) 1f else 2f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrangePrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = "Next →",
                            fontFamily = CalivePixelFamily,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

private data class OnboardingStep(
    val imageRes: Int,
    val title: String,
    val isVideo: Boolean = false,
    val isConsent: Boolean = false
)
