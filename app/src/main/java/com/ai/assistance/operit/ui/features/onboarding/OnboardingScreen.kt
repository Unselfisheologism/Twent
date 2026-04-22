package com.ai.assistance.operit.ui.features.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
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
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.OrangePrimary
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextDecoration

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    var currentStep by remember { mutableStateOf(0) }
    var stepAlpha by remember { mutableStateOf(1f) }

    val onboardingSteps = listOf(
        OnboardingStep(
            imageRes = R.drawable.onboarding_welcome,
            title = "",
            isVideo = true
        ),
        OnboardingStep(
            imageRes = R.drawable.onboarding_transition,
            title = "",
            isTransition = true
        ),
        OnboardingStep(
            imageRes = R.drawable.onboarding_slide1,
            title = "An Assistant So\nGreat,\nYou Feel You Were\nMissing Out."
        ),
        OnboardingStep(
            imageRes = R.drawable.onboarding_slide2,
            title = "Get Back Time\nFrom Your Busy\nLife, Delegate The\nBoring"
        ),
        OnboardingStep(
            imageRes = R.drawable.onboarding_slide3,
            title = "A Swarm\nOf Agents\nAt Your\nService"
        ),
        OnboardingStep(
            imageRes = R.drawable.onboarding_slide4,
            title = "Total Privacy: Bring\nYour Own Key, No\nTracking,\nNo Telemetry, Local\nModels"
        ),
        OnboardingStep(
            imageRes = R.drawable.onboarding_slide5,
            title = "Your Agents Can Use\n1000+ Integrations,\nYou Control How"
        ),
        OnboardingStep(
            imageRes = R.drawable.onboarding_slide6,
            title = "Mini Apps\nFor All Your\nNeeds,\nMade By AI."
        )
    )

    LaunchedEffect(currentStep) {
        if (currentStep == 0) {
            // Play opening video for 5 seconds
            delay(5000)
            stepAlpha = 0f
            delay(600)
            currentStep = 1
        } else if (currentStep == 1) {
            // Fade transition screen
            stepAlpha = 0f
            delay(400)
            currentStep = 2
            stepAlpha = 1f
        } else if (currentStep > 1 && currentStep < onboardingSteps.size - 1) {
            delay(3500)
            stepAlpha = 0f
            delay(300)
            currentStep += 1
            stepAlpha = 1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                fadeIn(animationSpec = tween(600)) with fadeOut(animationSpec = tween(600))
            }, label = "onboardingAnimation"
        ) { step ->
            val stepData = onboardingSteps[step]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(stepAlpha)
            ) {
                Image(
                    painter = painterResource(id = stepData.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (stepData.title.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            text = stepData.title,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 28.sp,
                                lineHeight = 34.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }

        // Footer at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Progress dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(6) { index ->
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (index == (currentStep - 2)) 24.dp else 8.dp,
                                    height = 8.dp
                                )
                                .background(
                                    color = if (index <= (currentStep - 2)) OrangePrimary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }

                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Get Started")
                }

                Spacer(modifier = Modifier.height(16.dp))

                val annotatedString = buildAnnotatedString {
                    append("By proceeding, you agree to our ")

                    pushStringAnnotation(
                        tag = "URL",
                        annotation = "https://twent.xyz/terms"
                    )
                    withStyle(
                        style = SpanStyle(
                            color = OrangePrimary.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("Terms of Service")
                    }
                    pop()
                }

                Text(
                    text = annotatedString,
                    modifier = Modifier.clickable {
                        annotatedString.getStringAnnotations(
                            tag = "URL",
                            start = 0,
                            end = annotatedString.length
                        ).firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

private data class OnboardingStep(
    val imageRes: Int,
    val title: String,
    val isVideo: Boolean = false,
    val isTransition: Boolean = false
)
