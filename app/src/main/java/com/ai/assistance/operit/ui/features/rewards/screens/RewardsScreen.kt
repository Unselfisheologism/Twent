package com.ai.assistance.operit.ui.features.rewards.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
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
import com.ai.assistance.operit.data.gamification.GamificationManager
import com.ai.assistance.operit.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 🏆 Rewards Screen - Show off your achievements!
 * 
 * Gen Z loves:
 * - Progress bars everywhere ✅
 * - Visual dopamine hits ✅
 * - Streaks & levels ✅
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gamificationManager = remember { GamificationManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Collect gamification data
    var currentLevel by remember { mutableIntStateOf(1) }
    var totalXP by remember { mutableLongStateOf(0L) }
    var dailyXP by remember { mutableLongStateOf(0L) }
    var currentStreak by remember { mutableIntStateOf(0) }
    var longestStreak by remember { mutableIntStateOf(0) }
    var unlockedBadges by remember { mutableStateOf<List<String>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        launch { gamificationManager.currentLevel.collectLatest { currentLevel = it } }
        launch { gamificationManager.totalXP.collectLatest { totalXP = it } }
        launch { gamificationManager.dailyXP.collectLatest { dailyXP = it } }
        launch { gamificationManager.currentStreak.collectLatest { currentStreak = it } }
        launch { gamificationManager.longestStreak.collectLatest { longestStreak = it } }
        launch { gamificationManager.unlockedBadges.collectLatest { unlockedBadges = it } }
    }
    
    val xpForNextLevel = gamificationManager.getXpForNextLevel(currentLevel)
    val xpProgress = if (xpForNextLevel > 0) (totalXP.toDouble() / xpForNextLevel.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Your Rewards 🏆",
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // 🎯 Level Card - Big & Bold
            LevelCard(
                level = currentLevel,
                currentXP = totalXP,
                nextLevelXP = xpForNextLevel,
                progress = xpProgress
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 🔥 Streak Card
            StreakCard(
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                dailyXP = dailyXP
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 🏅 Badges Section
            Text(
                "Your Badges 🏅",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            BadgesGrid(
                unlockedBadges = unlockedBadges,
                allBadges = gamificationManager.getAllBadges()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 💡 Daily Challenges Preview
            DailyChallengesCard()
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun LevelCard(
    level: Int,
    currentXP: Long,
    nextLevelXP: Long,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Level Number - MASSIVE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Level $level",
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        color = ElectricBlue
                    )
                    Text(
                        "Keep going! 🚀",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = OnDarkBg.copy(alpha = 0.7f)
                    )
                }
                
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = GoldBadge
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // XP Progress Bar - Dopamine hit! 🎯
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = ElectricBlue,
                trackColor = DarkSurfaceHighest
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // XP Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$currentXP XP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NeonPurple
                )
                Text(
                    "$nextLevelXP to Level ${level + 1}",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = OnDarkBg.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun StreakCard(
    currentStreak: Int,
    longestStreak: Int,
    dailyXP: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${currentStreak}🔥",
                        fontWeight = FontWeight.Black,
                        fontSize = 40.sp,
                        color = StreakFire
                    )
                    Text(
                        "Day Streak",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = OnDarkBg.copy(alpha = 0.7f)
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = StreakFire
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Best: $longestStreak",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = OnDarkBg.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Daily XP earned
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = CyberYellow
                )
                Text(
                    "$dailyXP XP earned today",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = CyberYellow
                )
            }
        }
    }
}

@Composable
fun BadgesGrid(
    unlockedBadges: List<String>,
    allBadges: List<GamificationManager.BadgeInfo>
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.heightIn(max = 400.dp)
    ) {
        items(allBadges) { badge ->
            val isUnlocked = unlockedBadges.contains(badge.id)
            BadgeItem(badge = badge, isUnlocked = isUnlocked)
        }
    }
}

@Composable
fun BadgeItem(
    badge: GamificationManager.BadgeInfo,
    isUnlocked: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) DarkSurfaceHighest else DarkSurface.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (isUnlocked) "🏆" else "🔒",
                fontSize = 32.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                badge.name.split(" ").firstOrNull() ?: "",
                fontWeight = if (isUnlocked) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = if (isUnlocked) OnDarkBg else OnDarkBg.copy(alpha = 0.4f),
                maxLines = 2
            )
        }
    }
}

@Composable
fun DailyChallengesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Brush.horizontalGradient(
                colors = listOf(GradientStart, GradientMid, GradientEnd)
            )
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Daily Challenge ⚡",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Color.White
                )
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "Send 5 messages today",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = 0.6f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "3/5 • +50 XP",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
