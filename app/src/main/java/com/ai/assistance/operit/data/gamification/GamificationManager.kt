package com.ai.assistance.operit.data.gamification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 🎮 Gamification Manager - Streaks, XP, Levels & Badges 🔥
 * 
 * Gen Z loves progress bars and dopamine hits!
 */
class GamificationManager private constructor(context: Context) {
    
    companion object {
        private const val DATASTORE_NAME = "gamification_prefs"
        private val Context.gamificationDataStore: DataStore<Preferences> by preferencesDataStore(DATASTORE_NAME)
        
        @Volatile
        private var INSTANCE: GamificationManager? = null
        
        fun getInstance(context: Context): GamificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GamificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Badge IDs
        const val BADGE_FIRST_CHAT = "first_chat"
        const val BADGE_7_DAY_STREAK = "7_day_streak"
        const val BADGE_30_DAY_STREAK = "30_day_streak"
        const val BADGE_100_DAY_STREAK = "100_day_streak"
        const val BADGE_SOCIAL_WARRIOR = "social_warrior"
        const val BADGE_ECO_HERO = "eco_hero"
        const val BADGE_COMMUNITY_LEADER = "community_leader"
        const val BADGE_POWER_USER = "power_user"
        const val BADGE_NIGHT_OWL = "night_owl"
        const val BADGE_EARLY_BIRD = "early_bird"
    }
    
    // 🔥 Streak Tracking
    private val streakKey = stringPreferencesKey("current_streak")
    private val lastActiveDateKey = stringPreferencesKey("last_active_date")
    private val longestStreakKey = intPreferencesKey("longest_streak")
    
    // ⭐ XP & Level System
    private val totalXPKey = longPreferencesKey("total_xp")
    private val levelKey = intPreferencesKey("current_level")
    private val dailyXPKey = longPreferencesKey("daily_xp")
    private val lastXPResetDateKey = stringPreferencesKey("last_xp_reset_date")
    
    // 🏆 Badges (stored as comma-separated string)
    private val badgesKey = stringPreferencesKey("unlocked_badges")
    
    // 📊 Progress Tracking
    private val chatsCompletedKey = intPreferencesKey("chats_completed")
    private val toolsUsedKey = intPreferencesKey("tools_used")
    private val socialActionsKey = intPreferencesKey("social_actions_completed")
    
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    // Flow: Current Streak
    val currentStreak: Flow<Int> = context.gamificationDataStore.data.map { prefs ->
        prefs[streakKey]?.toIntOrNull() ?: 0
    }
    
    // Flow: Longest Streak
    val longestStreak: Flow<Int> = context.gamificationDataStore.data.map { prefs ->
        prefs[longestStreakKey] ?: 0
    }
    
    // Flow: Total XP
    val totalXP: Flow<Long> = context.gamificationDataStore.data.map { prefs ->
        prefs[totalXPKey] ?: 0L
    }
    
    // Flow: Current Level
    val currentLevel: Flow<Int> = context.gamificationDataStore.data.map { prefs ->
        prefs[levelKey] ?: 1
    }
    
    // Flow: Daily XP
    val dailyXP: Flow<Long> = context.gamificationDataStore.data.map { prefs ->
        prefs[dailyXPKey] ?: 0L
    }
    
    // Flow: Unlocked Badges
    val unlockedBadges: Flow<List<String>> = context.gamificationDataStore.data.map { prefs ->
        prefs[badgesKey]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    // Flow: Stats
    val chatsCompleted: Flow<Int> = context.gamificationDataStore.data.map { prefs ->
        prefs[chatsCompletedKey] ?: 0
    }
    
    val toolsUsed: Flow<Int> = context.gamificationDataStore.data.map { prefs ->
        prefs[toolsUsedKey] ?: 0
    }
    
    val socialActions: Flow<Int> = context.gamificationDataStore.data.map { prefs ->
        prefs[socialActionsKey] ?: 0
    }
    
    /**
     * 🔥 Record daily activity and update streak
     */
    suspend fun recordDailyActivity(context: Context) {
        val today = LocalDate.now().format(dateFormatter)
        
        context.gamificationDataStore.edit { prefs ->
            val lastActive = prefs[lastActiveDateKey]
            val currentStreak = prefs[streakKey]?.toIntOrNull() ?: 0
            
            when {
                lastActive == null -> {
                    // First time ever!
                    prefs[streakKey] = "1"
                    prefs[longestStreakKey] = 1
                }
                lastActive == today -> {
                    // Already recorded today, skip
                }
                else -> {
                    val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
                    if (lastActive == yesterday) {
                        // Streak continues! 🔥
                        val newStreak = currentStreak + 1
                        prefs[streakKey] = newStreak.toString()
                        prefs[longestStreakKey] = maxOf(prefs[longestStreakKey] ?: 0, newStreak)
                        
                        // Award XP for streak
                        awardXP(context, streakBonus = newStreak * 10)
                    } else {
                        // Streak broken 💔
                        prefs[streakKey] = "1"
                    }
                }
            }
            
            prefs[lastActiveDateKey] = today
        }
    }
    
    /**
     * ⭐ Award XP to user
     */
    suspend fun awardXP(context: Context, amount: Long = 0, streakBonus: Int = 0) {
        val today = LocalDate.now().format(dateFormatter)
        val xpAmount = if (amount > 0) amount else 25L // Default 25 XP per action
        val totalAwarded = xpAmount + streakBonus
        
        context.gamificationDataStore.edit { prefs ->
            // Reset daily XP if it's a new day
            val lastReset = prefs[lastXPResetDateKey]
            if (lastReset != today) {
                prefs[dailyXPKey] = 0L
                prefs[lastXPResetDateKey] = today
            }
            
            // Award XP
            prefs[totalXPKey] = (prefs[totalXPKey] ?: 0L) + totalAwarded
            prefs[dailyXPKey] = (prefs[dailyXPKey] ?: 0L) + totalAwarded
            
            // Check for level up
            val currentLevel = prefs[levelKey] ?: 1
            val totalXP = prefs[totalXPKey] ?: 0L
            val newLevel = calculateLevel(totalXP)
            
            if (newLevel > currentLevel) {
                prefs[levelKey] = newLevel
                // Could trigger level up notification here
            }
        }
    }
    
    /**
     * 🏆 Unlock a badge
     */
    suspend fun unlockBadge(context: Context, badgeId: String) {
        context.gamificationDataStore.edit { prefs ->
            val currentBadges = prefs[badgesKey]?.split(",")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            if (!currentBadges.contains(badgeId)) {
                currentBadges.add(badgeId)
                prefs[badgesKey] = currentBadges.joinToString(",")
            }
        }
    }
    
    /**
     * 📊 Update progress stats
     */
    suspend fun updateProgress(context: Context, chatsCompleted: Int? = null, toolsUsed: Int? = null, socialActions: Int? = null) {
        context.gamificationDataStore.edit { prefs ->
            chatsCompleted?.let { prefs[this.chatsCompletedKey] = it }
            toolsUsed?.let { prefs[this.toolsUsedKey] = it }
            socialActions?.let { prefs[this.socialActionsKey] = it }
        }
        
        // Check for badge unlocks based on progress
        checkAndUnlockBadges(context)
    }
    
    /**
     * 🎯 Check conditions and unlock badges
     */
    private suspend fun checkAndUnlockBadges(context: Context) {
        val streak = currentStreak
        val totalXPVal = totalXP
        val socialActionsCount = socialActions
        
        // This would need to be called with proper Flow collection
        // Simplified version for now
    }
    
    /**
     * 📈 Calculate level from total XP
     * 
     * Level formula: Each level requires 100 * level XP
     * Level 1: 0-100 XP
     * Level 2: 101-300 XP
     * Level 3: 301-600 XP
     * etc.
     */
    fun calculateLevel(totalXP: Long): Int {
        if (totalXP <= 100) return 1
        if (totalXP <= 300) return 2
        if (totalXP <= 600) return 3
        if (totalXP <= 1000) return 4
        if (totalXP <= 1500) return 5
        if (totalXP <= 2100) return 6
        if (totalXP <= 2800) return 7
        if (totalXP <= 3600) return 8
        if (totalXP <= 4500) return 9
        if (totalXP <= 5500) return 10
        
        // Beyond level 10: every 1000 XP = 1 level
        return 10 + ((totalXP - 5500) / 1000).toInt()
    }
    
    /**
     * 📊 Get XP needed for next level
     */
    fun getXpForNextLevel(currentLevel: Int): Long {
        return when (currentLevel) {
            0 -> 100L
            1 -> 100L
            2 -> 300L
            3 -> 600L
            4 -> 1000L
            5 -> 1500L
            6 -> 2100L
            7 -> 2800L
            8 -> 3600L
            9 -> 4500L
            10 -> 5500L
            else -> 5500L + (currentLevel - 10) * 1000L
        }
    }
    
    /**
     * 🏅 Get all available badges with info
     */
    fun getAllBadges(): List<BadgeInfo> {
        return listOf(
            BadgeInfo(BADGE_FIRST_CHAT, "First Chat 💬", "Sent your first message", 10),
            BadgeInfo(BADGE_7_DAY_STREAK, "Week Warrior 🔥", "7-day streak", 25),
            BadgeInfo(BADGE_30_DAY_STREAK, "Monthly Master 🔥", "30-day streak", 50),
            BadgeInfo(BADGE_100_DAY_STREAK, "Centurion 🔥", "100-day streak", 100),
            BadgeInfo(BADGE_SOCIAL_WARRIOR, "Social Warrior 🌍", "Completed 10 social actions", 30),
            BadgeInfo(BADGE_ECO_HERO, "Eco Hero 🌱", "Completed 25 eco-friendly actions", 50),
            BadgeInfo(BADGE_COMMUNITY_LEADER, "Community Leader 👑", "Helped 50 community members", 100),
            BadgeInfo(BADGE_POWER_USER, "Power User ⚡", "Used 20 different tools", 40),
            BadgeInfo(BADGE_NIGHT_OWL, "Night Owl 🦉", "Active after midnight", 15),
            BadgeInfo(BADGE_EARLY_BIRD, "Early Bird 🐦", "Active before 6 AM", 15)
        )
    }
    
    /**
     * 🎁 Daily challenge rewards
     */
    suspend fun completeDailyChallenge(context: Context, challengeId: String) {
        // Award bonus XP for completing daily challenges
        awardXP(context, amount = 50L)
    }
    
    data class BadgeInfo(
        val id: String,
        val name: String,
        val description: String,
        val xpReward: Int
    )
}
