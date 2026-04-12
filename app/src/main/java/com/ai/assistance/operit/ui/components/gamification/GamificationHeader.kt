package com.ai.assistance.operit.ui.components.gamification

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.gamification.GamificationManager
import com.ai.assistance.operit.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 🔥 Gen Z Gamification Header
 * 
 * Shows streak & XP in a compact, eye-catching component
 * Perfect for adding to the top of screens!
 */
@Composable
fun GamificationHeader(
    onRewardsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gamificationManager = remember { GamificationManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var currentStreak by remember { mutableIntStateOf(0) }
    var totalXP by remember { mutableLongStateOf(0L) }
    var currentLevel by remember { mutableIntStateOf(1) }
    
    LaunchedEffect(Unit) {
        launch { gamificationManager.currentStreak.collectLatest { currentStreak = it } }
        launch { gamificationManager.totalXP.collectLatest { totalXP = it } }
        launch { gamificationManager.currentLevel.collectLatest { currentLevel = it } }
    }
    
    // Record daily activity on first load
    LaunchedEffect(Unit) {
        gamificationManager.recordDailyActivity(context)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onRewardsClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🔥 Streak Badge
        StreakBadge(streak = currentStreak)
        
        Spacer(modifier = Modifier.weight(1f))
        
        // ⭐ XP Display
        XPBadge(xp = totalXP, level = currentLevel)
    }
}

@Composable
fun StreakBadge(streak: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (streak > 0) StreakFire.copy(alpha = 0.2f) else DarkSurface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (streak > 0) StreakFire else OnDarkBg.copy(alpha = 0.3f)
            )
            Text(
                text = "$streak",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (streak > 0) StreakFire else OnDarkBg.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun XPBadge(xp: Long, level: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = ElectricBlue.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = CyberYellow
            )
            Column {
                Text(
                    text = "$xp XP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CyberYellow,
                    modifier = Modifier.animateContentSize()
                )
                Text(
                    text = "Lvl $level",
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = OnDarkBg.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 📊 Mini Progress Bar for XP
 * Compact version for tight spaces
 */
@Composable
fun MiniXPProgress(
    currentXP: Long,
    nextLevelXP: Long,
    modifier: Modifier = Modifier
) {
    val progress = if (nextLevelXP > 0) (currentXP.toDouble() / nextLevelXP.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$currentXP / $nextLevelXP XP",
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = OnDarkBg.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ElectricBlue,
            trackColor = DarkSurfaceHighest
        )
    }
}
