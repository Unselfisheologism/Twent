package com.ai.assistance.operit.ui.features.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.CalivePixelFamily
import com.ai.assistance.operit.ui.theme.OrangePrimary
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TwentOnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    // Total slides: 7 (0=Video, 1-6=content slides, 6=consent)
    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 7

    // Video player state
    var videoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Cleanup video player
    DisposableEffect(Unit) {
        onDispose {
            videoPlayer?.release()
            videoPlayer = null
        }
    }

    // Video completion callback - auto-advance to first slide
    LaunchedEffect(videoPlayer) {
        videoPlayer?.addListener(object : com.google.android.exoplayer2.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == com.google.android.exoplayer2.Player.STATE_ENDED) {
                    // Video finished playing once - advance to slide 1 with blur-fade
                    currentStep = 1
                }
            }
        })
    }

    // Define slide data with correct image resources and text
    val slideData = listOf(
        // Step 0: Video (handled separately)
        OnboardingStep(imageRes = 0, title = "", isVideo = true),

        // Step 1
        OnboardingStep(
            imageRes = R.drawable.onboarding_1,
            title = "An Assistant So Great,\nYou Feel You Were\nMissing Out."
        ),

        // Step 2
        OnboardingStep(
            imageRes = R.drawable.onboarding_2,
            title = "Get Back Time\nFrom Your Busy\nLife, Delegate\nThe Boring"
        ),

        // Step 3
        OnboardingStep(
            imageRes = R.drawable.onboarding_3,
            title = "A Swarm\nOf Agents\nAt Your\nService"
        ),

        // Step 4
        OnboardingStep(
            imageRes = R.drawable.onboarding_4,
            title = "Total Privacy:\nBring Your Own\nKey, No Tracking,\nNo Telemetry,\nLocal Models"
        ),

        // Step 5
        OnboardingStep(
            imageRes = R.drawable.onboarding_5,
            title = "Your Agents Can Use\n1000+ Integrations,\nYou Control How"
        ),

        // Step 6
        OnboardingStep(
            imageRes = R.drawable.onboarding_6,
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
        // Step 0: Video player
        if (currentStep == 0) {
            DisposableEffect(Unit) {
                onDispose {
                    videoPlayer?.release()
                    videoPlayer = null
                }
            }

            LaunchedEffect(Unit) {
                val player = ExoPlayer.Builder(context).build().apply {
                    val uri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.intro}")
                    setMediaItem(MediaItem.fromUri(uri))
                    repeatMode = ExoPlayer.REPEAT_MODE_OFF // play only once
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
        } else {
            // Slides 1-6 with blur-fade transitions
            if (currentStep in 1..6) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val duration = 800
                        // Custom blur-fade transition
                        val fadeIn = fadeIn(animationSpec = tween(duration))
                        val fadeOut = fadeOut(animationSpec = tween(duration))
                        val scaleIn = scaleIn(initialScale = 1.05f, animationSpec = tween(duration))
                        val scaleOut = scaleOut(targetScale = 0.95f, animationSpec = tween(duration))

                        // Blur transitions
                        val blurIn = keyframes {
                            durationMillis = duration
                            0.dp at 0 with LinearEasing
                            20.dp at duration / 2 with LinearEasing
                            0.dp at duration with LinearEasing
                        }
                        val blurOut = keyframes {
                            durationMillis = duration
                            0.dp at 0 with LinearEasing
                            20.dp at duration / 2 with LinearEasing
                            0.dp at duration with LinearEasing
                        }

                        (fadeIn + scaleIn) with (fadeOut + scaleOut)
                    },
                    label = "blurFadeTransition"
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
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f))
                        )

                        // Text overlay - positioned higher, left-aligned, not full width
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    top = 180.dp, // raised from bottom
                                    bottom = 100.dp // leave space for buttons
                                ),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text(
                                text = stepData.title,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFamily = CalivePixelFamily,
                                    fontSize = 30.sp,
                                    lineHeight = 38.sp,
                                    color = Color.White
                                ),
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .wrapContentSize()
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

        // Arrow navigation buttons - positioned at bottom corners
        if (currentStep in 1..6) {
            // Back arrow - bottom left (hidden on first slide)
            if (currentStep > 1) {
                IconButton(
                    onClick = { currentStep-- },
                    modifier = Modifier
                        .size(56.dp)
                        .padding(16.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_left),
                        contentDescription = "Back",
                        tint = OrangePrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Next arrow - bottom right
            IconButton(
                onClick = { currentStep++ },
                modifier = Modifier
                    .size(56.dp)
                    .padding(16.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_right),
                    contentDescription = "Next",
                    tint = OrangePrimary,
                    modifier = Modifier.size(32.dp)
                )
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
