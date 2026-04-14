package com.ai.assistance.operit.ui.features.agreement.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.CyanPrimary
import com.ai.assistance.operit.ui.theme.OrangePrimary
import kotlinx.coroutines.delay

@Composable
fun AgreementScreen(onAgreementAccepted: () -> Unit) {
    val scrollState = rememberScrollState()
    val alpha = remember { Animatable(0f) }

    // Fade-in animation
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 500))
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
            Spacer(modifier = Modifier.height(16.dp))

            // Header with icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(OrangePrimary, CyanPrimary)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Policy,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "User Agreement & Privacy Policy",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Everything is free — now and always",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Agreement content in card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp)
                ) {
                    // Section 1: Overview
                    AgreementSection(
                        icon = Icons.Default.Info,
                        title = "Free & Open",
                        content = """
                            Operit is a free AI-powered voice assistant application. All features are available to you at no cost, both now and in the future. We believe in providing powerful automation tools accessible to everyone without paywalls or subscription fees.
                        """.trimIndent()
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 2: How We Sustain
                    AgreementSection(
                        icon = Icons.Default.MonetizationOn,
                        title = "How We Stay Free",
                        content = """
                            Operit is supported by AI-powered advertisements served through Kohala Labs (koa hlabs.com). These ads are designed to be relevant and non-intrusive. By using the app, you consent to the display of these advertisements.

                            We do not sell the app, charge for any features, or require payments from users. If you acquired this app through a paid channel, you were not charged by us.
                        """.trimIndent()
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 3: Privacy
                    AgreementSection(
                        icon = Icons.Default.Security,
                        title = "Your Privacy Matters",
                        content = """
                            We prioritize your privacy and data security. All voice processing and automation tasks are designed to work primarily on your device. We collect minimal data necessary to provide our services and serve advertisements.

                            Key Privacy Points:
                            • Voice data is processed locally when possible
                            • Cloud processing only occurs when necessary for AI features
                            • You can delete your data at any time
                            • We use industry-standard encryption for all transmissions
                            • Ad data is not linked to your personal identity
                        """.trimIndent()
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 4: Permissions
                    AgreementSection(
                        icon = Icons.Default.SettingsAccessibility,
                        title = "Required Permissions",
                        content = """
                            To function properly, the app requires several Android permissions:

                            • Accessibility Service: Enables automation of UI interactions
                            • Microphone: Allows voice command recognition
                            • Display Over Other Apps: Required for the floating assistant widget
                            • Notifications: Enables smart notification management
                            • Storage: Required for file management features

                            You can revoke these permissions at any time through your device settings, though some features may be limited.
                        """.trimIndent()
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 5: Usage
                    AgreementSection(
                        icon = Icons.Default.Lightbulb,
                        title = "Acceptable Use",
                        content = """
                            Operit is designed to enhance your productivity through automation and voice assistance. You agree to use the app responsibly and not for any unlawful purposes. The AI features are meant to assist you, and while we strive for accuracy, you should verify critical decisions.
                        """.trimIndent()
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 6: Updates
                    AgreementSection(
                        icon = Icons.Default.Update,
                        title = "Updates & Changes",
                        content = """
                            We may update these terms from time to time to reflect changes in our services or legal requirements. We'll notify you of significant changes. Continued use of the app after updates constitutes acceptance of the new terms.
                        """.trimIndent()
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 7: Contact
                    AgreementSection(
                        icon = Icons.Default.Mail,
                        title = "Contact Us",
                        content = """
                            If you have questions about these terms, privacy concerns, or need technical support, please reach out through our support channels. We're here to help and value your feedback in making our app better.
                        """.trimIndent()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Disclaimer
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = OrangePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Operit is distributed as a free APK. There is no paid version or tier. All features are unlocked by default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Accept button - always enabled
            Button(
                onClick = onAgreementAccepted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangePrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Text(
                    text = "Accept & Continue",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AgreementSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4
        )
    }
}
