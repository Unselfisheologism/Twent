package com.ai.assistance.operit.ui.features.socialimpact.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 🌍 Social Impact Screen - Show your positive impact!
 * 
 * Gen Z cares about:
 * - Social accountability ✅
 * - Environmental impact ✅
 * - Community responsibility ✅
 * - Making a difference ✅
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialImpactScreen(
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    // Mock data - would come from a SocialImpactManager in production
    var treesPlanted by remember { mutableIntStateOf(12) }
    var carbonOffset by remember { mutableDoubleStateOf(45.5) }
    var communityActions by remember { mutableIntStateOf(28) }
    var peopleHelped by remember { mutableIntStateOf(156) }
    var impactLevel by remember { mutableIntStateOf(3) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Your Impact 🌍",
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // 🌱 Hero Impact Card
            ImpactHeroCard(
                treesPlanted = treesPlanted,
                carbonOffset = carbonOffset,
                impactLevel = impactLevel
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 📊 Impact Stats Grid
            ImpactStatsGrid(
                communityActions = communityActions,
                peopleHelped = peopleHelped,
                treesPlanted = treesPlanted,
                carbonOffset = carbonOffset
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 🎯 Active Challenges
            Text(
                "Community Challenges 🎯",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            ActiveChallengesList()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 💚 Accountability Feed
            Text(
                "Your Actions Matter 💚",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            AccountabilityFeed()
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ImpactHeroCard(
    treesPlanted: Int,
    carbonOffset: Double,
    impactLevel: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(SuccessGreen.copy(alpha = 0.8f), LimeGreen.copy(alpha = 0.6f))
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Impact Level Badge
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "🌍",
                        fontSize = 40.sp
                    )
                    Text(
                        "Lvl $impactLevel",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Eco Warrior",
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "You're making a real difference!",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress to next level
            LinearProgressIndicator(
                progress = 0.65f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "65% to Climate Champion",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ImpactStatsGrid(
    communityActions: Int,
    peopleHelped: Int,
    treesPlanted: Int,
    carbonOffset: Double
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon = Icons.Default.Favorite,
                value = peopleHelped.toString(),
                label = "People Helped",
                emoji = "❤️",
                color = HotPink,
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                icon = Icons.Default.ThumbUp,
                value = communityActions.toString(),
                label = "Actions Taken",
                emoji = "✊",
                color = ElectricBlue,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon = Icons.Default.Park,
                value = treesPlanted.toString(),
                label = "Trees Planted",
                emoji = "🌳",
                color = LimeGreen,
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                icon = Icons.Default.WbSunny,
                value = "${carbonOffset}kg",
                label = "CO₂ Offset",
                emoji = "☀️",
                color = CyberYellow,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    emoji: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                emoji,
                fontSize = 32.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                value,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                color = color
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                label,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = OnDarkBg.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ActiveChallengesList() {
    val challenges = listOf(
        ChallengeInfo(
            title = "Reduce Screen Time",
            description = "Save 1hr/day for mental health",
            progress = 0.7f,
            participants = 1234,
            emoji = "📱"
        ),
        ChallengeInfo(
            title = "Learn & Share",
            description = "Educate 5 friends on climate",
            progress = 0.4f,
            participants = 892,
            emoji = "📚"
        ),
        ChallengeInfo(
            title = "Community Helper",
            description = "Help 10 people this week",
            progress = 0.8f,
            participants = 2341,
            emoji = "🤝"
        )
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        challenges.forEach { challenge ->
            ChallengeCard(challenge)
        }
    }
}

@Composable
fun ChallengeCard(challenge: ChallengeInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        challenge.emoji,
                        fontSize = 28.sp
                    )
                    Column {
                        Text(
                            challenge.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            challenge.description,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = OnDarkBg.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Text(
                    "${challenge.participants}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = NeonPurple
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = challenge.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = ElectricBlue,
                trackColor = DarkSurfaceHighest
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "${(challenge.progress * 100).toInt()}% complete",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = OnDarkBg.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun AccountabilityFeed() {
    val actions = listOf(
        ActionItem("You helped someone learn about AI ethics", "2h ago", "🎓"),
        ActionItem("Reduced digital carbon footprint by 2kg", "5h ago", "🌱"),
        ActionItem("Completed community service challenge", "1d ago", "✊"),
        ActionItem("Shared mental health resources", "2d ago", "💚")
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            ActionCard(action)
        }
    }
}

@Composable
fun ActionCard(action: ActionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                action.emoji,
                fontSize = 24.sp
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    action.message,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    action.timestamp,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = OnDarkBg.copy(alpha = 0.5f)
                )
            }
        }
    }
}

data class ChallengeInfo(
    val title: String,
    val description: String,
    val progress: Float,
    val participants: Int,
    val emoji: String
)

data class ActionItem(
    val message: String,
    val timestamp: String,
    val emoji: String
)
