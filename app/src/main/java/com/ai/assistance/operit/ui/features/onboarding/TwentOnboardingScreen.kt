package com.ai.assistance.operit.ui.features.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TwentOnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    var currentStep by remember { mutableStateOf(0) }

    // Slide data: step 0=video, 1-6=content slides, 7=consent
    val slideData = listOf(
        OnboardingStep(imageRes = 0, title = "", isVideo = true),
        OnboardingStep(imageRes = R.drawable.onboarding_1, title = "An Assistant So Great,\nYou Feel You Were\nMissing Out."),
        OnboardingStep(imageRes = R.drawable.onboarding_2, title = "Get Back Time\nFrom Your Busy\nLife, Delegate\nThe Boring"),
        OnboardingStep(imageRes = R.drawable.onboarding_3, title = "A Swarm\nOf Agents\nAt Your\nService"),
        OnboardingStep(imageRes = R.drawable.onboarding_4, title = "Total Privacy:\nBring Your Own\nKey, No Tracking,\nNo Telemetry,\nLocal Models"),
        OnboardingStep(imageRes = R.drawable.onboarding_5, title = "Your Agents Can Use\n1000+ Integrations,\nYou Control How"),
        OnboardingStep(imageRes = R.drawable.onboarding_6, title = "Mini Apps\nFor All Your\nNeeds,\nMade By AI."),
        OnboardingStep(imageRes = 0, title = "", isConsent = true)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // AnimatedContent for video (0) and slides (1-6)
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val duration = 800
                // fade + scale transition (smooth, not blunt)
                fadeIn(animationSpec = tween(duration)) + scaleIn(initialScale = 1.05f, animationSpec = tween(duration)) with
                fadeOut(animationSpec = tween(duration)) + scaleOut(targetScale = 0.95f, animationSpec = tween(duration))
            },
            label = "slideTransition"
        ) { targetStep ->
            when {
                targetStep == 0 -> {
                    VideoStep(onVideoEnd = { currentStep = 1 })
                }
                targetStep in 1..6 -> {
                    SlideStep(step = targetStep, slideData = slideData)
                }
                else -> {}
            }
        }

        // Arrow navigation buttons for slides 1-6
        if (currentStep in 1..6) {
            // Back button (bottom left) - hidden on first slide
            if (currentStep > 1) {
                ArrowButton(
                    arrow = "←",
                    onClick = { currentStep-- },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomStart)
                )
            }

            // Next button (bottom right)
            ArrowButton(
                arrow = "→",
                onClick = { currentStep++ },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomEnd)
            )
        }

        // Consent overlay (step 7)
        if (currentStep == 7) {
            ConsentDialog(onComplete = onComplete)
        }
    }
}

@Composable
private fun VideoStep(onVideoEnd: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(Unit) {
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            val uri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.intro}")
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = ExoPlayer.REPEAT_MODE_OFF // play only once
            prepare()
            playWhenReady = true
            addListener(object : com.google.android.exoplayer2.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == com.google.android.exoplayer2.Player.STATE_ENDED) {
                        onVideoEnd()
                    }
                }
            })
        }
        player = exoPlayer

        onDispose {
            player?.release()
            player = null
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            view.player = player
        }
    )
}

@Composable
private fun SlideStep(step: Int, slideData: List<OnboardingStep>) {
    val data = slideData[step]
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = data.imageRes),
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
        // Text at bottom, full width, left-aligned, raised a bit
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 120.dp // raised from bottom
                ),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = CalivePixelFamily,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    color = Color.White
                ),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ArrowButton(
    arrow: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = OrangePrimary,
        contentColor = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = arrow,
                fontSize = 24.sp,
                fontFamily = CalivePixelFamily,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ConsentDialog(onComplete: () -> Unit) {
    val context = LocalContext.current
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
                modifier = Modifier.padding(24.dp),
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
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twent.xyz/terms"))
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
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twent.xyz/privacy"))
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

private data class OnboardingStep(
    val imageRes: Int,
    val title: String,
    val isVideo: Boolean = false,
    val isConsent: Boolean = false
)
