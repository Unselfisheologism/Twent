package com.ai.assistance.operit.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 🤪 Chronically Online Mode Manager
 * 
 * Transforms ALL UI text into unhinged Gen Z slang, memes, and internet culture.
 * Just like Canva's "English (Chronically Online)" mode!
 * 
 * Usage:
 * - User enables it in Language Settings
 * - Every string in the app gets replaced with Gen Z slang
 * - "Settings" → "vibes check"
 * - "Save" → "lock in"
 * - "Loading..." → "the server is having a moment 💅"
 */
class ChronicallyOnlineManager private constructor(private val context: Context) {

    companion object {
        private const val DATASTORE_NAME = "chronically_online_prefs"
        private val Context.chronicallyOnlineDataStore: DataStore<Preferences> by preferencesDataStore(DATASTORE_NAME)

        @Volatile
        private var INSTANCE: ChronicallyOnlineManager? = null

        fun getInstance(context: Context): ChronicallyOnlineManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChronicallyOnlineManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        const val KEY_CHRONICALLY_ONLINE = "chronically_online_mode"
    }

    private val enabledKey = booleanPreferencesKey(KEY_CHRONICALLY_ONLINE)

    // Flow: Is chronically online mode enabled?
    val isChronicallyOnline: Flow<Boolean> = context.chronicallyOnlineDataStore.data.map { prefs ->
        prefs[enabledKey] ?: false
    }
    
    /**
     * Enable/disable chronically online mode
     */
    suspend fun setChronicallyOnlineMode(enabled: Boolean) {
        context.chronicallyOnlineDataStore.edit { prefs ->
            prefs[enabledKey] = enabled
        }
    }
    
    /**
     * 🤪 THE GEN Z DICTIONARY
     * 
     * Maps normal UI strings to unhinged Gen Z slang.
     * This is where the magic happens!
     */
    fun translateToGenZ(normalText: String): String {
        return genZDictionary[normalText.lowercase()] ?: normalText
    }
    
    /**
     * Get Gen Z version of a string resource
     */
    fun getGenZString(context: Context, stringResId: Int): String {
        val normalText = context.getString(stringResId)
        return translateToGenZ(normalText)
    }
    
    // 🎭 THE COMPLETE GEN Z DICTIONARY
    private val genZDictionary = mapOf(
        // Navigation & Main UI
        "settings" to "vibes check ⚡",
        "chat" to "yapping 💬",
        "tools" to "cheat codes 🛠️",
        "packages" to "plugin arc 📦",
        "memory" to "brain rot storage 🧠",
        "help" to "spill the tea ☕",
        "about" to "lore dump ℹ️",
        "home" to "main character energy 🏠",
        "back" to "plot twist back ⬅️",
        "menu" to "side quest menu ☰",
        
        // Actions
        "save" to "lock in 🔒",
        "cancel" to "nah I'm good ✌️",
        "delete" to "send to the shadow realm 🗑️",
        "edit" to "character development ✏️",
        "search" to "detective mode 🔍",
        "send" to "shoot your shot 📤",
        "share" to "pass the aux 📤",
        "download" to "secure the bag 📥",
        "upload" to "drop the file 📤",
        "export" to "pass it to the group chat 📤",
        "import" to "let it in 📥",
        "copy" to "ctrl+c but make it fashion 📋",
        "paste" to "slay it there 📋",
        "undo" to "erase that trauma 💅",
        "redo" to "do it but make it ✨aesthetic✨",
        
        // Status & Messages
        "loading" to "the server is having a moment 💅",
        "loading..." to "the server is having a moment 💅",
        "please wait" to "touch grass while you wait 🌿",
        "success" to "WE LOVE TO SEE IT ✅",
        "error" to "bestie, we need to talk ❌",
        "failed" to "emotional damage 💔",
        "not found" to "ghosted you 👻",
        "permission denied" to "not the vibe tbh 🔒",
        "access denied" to "you're not on the list bestie 🔒",
        "welcome" to "slay, you made it! 🎉",
        "hello" to "what's good? 👋",
        "goodbye" to "it's giving farewell 👋",
        
        // Chat & AI
        "ai assistant" to "digital bestie 🤖",
        "artificial intelligence" to "robot brain go brrr 🤖",
        "chat history" to "receipts 📜",
        "new chat" to "new arc ✨",
        "conversation" to "yapping session 💬",
        "message" to "tea ☕",
        "type a message" to "spill the tea bestie 💬",
        "send message" to "drop the tea ☕",
        "ask ai" to "ask the digital oracle 🔮",
        "assistant" to "bestie 💅",
        
        // AI Responses
        "thinking" to "processing that trauma 💭",
        "generating response" to "cooking up something fire 🔥",
        "ai is typing" to "bestie is yapping back 💬",
        
        // Features
        "agent" to "main character 🎭",
        "workflow" to "main quest 🔄",
        "terminal" to "hacker mode 💻",
        "file manager" to "digital hoarder mode 📁",
        "permissions" to "do you trust me? 🔒",
        "backup" to "emotional support backup 💾",
        "restore" to "bring it back from the dead 💾",
        "sync" to "group chat sync 🔄",
        
        // Settings Categories
        "general settings" to "main vibes ⚙️",
        "appearance" to "drip check 🎨",
        "notifications" to "attention seeker mode 🔔",
        "privacy" to "protect your peace 🔒",
        "account" to "main character profile 👤",
        "theme" to "aesthetic choice 🎨",
        "language" to "lingo pick 🗣️",
        "display" to "vision mode 👁️",
        "sound" to "audio vibes 🔊",
        "vibration" to "bzzzz mode 📳",
        
        // Social & Community
        "community" to "the squad 👥",
        "friends" to "ride or dies 👯",
        "followers" to "fans 🌟",
        "following" to "stalking (legally) 👀",
        "profile" to "main character page 👤",
        "avatar" to "pfp 🖼️",
        "bio" to "about me (but make it interesting) 📝",
        
        // Gamification
        "rewards" to "secure the bag 🏆",
        "points" to "clout points ⭐",
        "level" to "character level 📊",
        "experience" to "main character XP ⚡",
        "badge" to "flex badge 🏅",
        "achievement" to "W moment 🏆",
        "streak" to "don't break the chain 🔥",
        "daily challenge" to "daily side quest ⚡",
        "leaderboard" to "who's winning life 🏆",
        
        // Social Impact
        "social impact" to "making a difference fr 🌍",
        "carbon footprint" to "saving the planet 🌱",
        "trees planted" to "tree hugger status 🌳",
        "community service" to "helping the homies 🤝",
        "volunteer" to "free labor but make it wholesome 🤝",
        
        // Technical
        "version" to "which era are we in? 📱",
        "update" to "glow up 📲",
        "upgrade" to "level up ⬆️",
        "downgrade" to "return to your villain arc ⬇️",
        "install" to "let it live here 📦",
        "uninstall" to "evict it 🗑️",
        "configure" to "customize your experience 🎨",
        "advanced" to "nerd mode 🤓",
        "developer" to "code wizard 🧙",
        "debug" to "find the plot holes 🐛",
        
        // Time & Date
        "today" to "in this moment 📅",
        "yesterday" to "the past (cringe) 📅",
        "tomorrow" to "future you problem 📅",
        "now" to "rn fr rn 🕐",
        "recently" to "not too long ago bestie 🕐",
        "never" to "not in this lifetime 💅",
        "always" to "forever and ever and ever ♾️",
        
        // Quantity
        "all" to "the whole squad 👥",
        "none" to "absolutely zero, nada, zip 🚫",
        "some" to "a few besties 👥",
        "many" to "the whole internet 🌐",
        "few" to "just the homies 👯",
        
        // Conditions
        "enabled" to "activated (slay) ✅",
        "disabled" to "deactivated (touch grass) ❌",
        "on" to "we locked in ✅",
        "off" to "we chilling ❌",
        "active" to "main character energy ✨",
        "inactive" to "npc energy 💤",
        
        // Common UI Patterns
        "ok" to "bet ✅",
        "yes" to "say less ✅",
        "no" to "nah fr ❌",
        "maybe" to "it's giving maybe 🤔",
        "confirm" to "lock it in 🔒",
        "apply" to "make it happen ✨",
        "reset" to "erase the trauma 🔄",
        "clear" to "wipe that slate clean 🧹",
        "refresh" to "give it new life 🔄",
        "retry" to "do it but better this time 🔄",
        "next" to "continue the arc ➡️",
        "previous" to "plot twist back ⬅️",
        "finish" to "main quest complete ✅",
        "start" to "let's get this bread 🚀",
        "stop" to "pause the game ✋",
        "continue" to "keep the arc going ➡️",
        "skip" to "speedrun this part ⏭️",
        
        // File Operations
        "file" to "digital artifact 📄",
        "folder" to "digital closet 📁",
        "directory" to "where the files live 📁",
        "new folder" to "new digital closet ✨",
        "rename" to "character rename ✏️",
        "move" to "relocate the homie 📦",
        "create" to "manifest it into existence ✨",
        
        // Errors & Warnings
        "warning" to "bestie, heads up ⚠️",
        "critical error" to "CODE RED 🚨",
        "try again" to "do it but with more confidence 💪",
        "contact support" to "summon the help wizards 🧙",
        "report bug" to "snitch on the bug 🐛",
        
        // Authentication
        "login" to "enter the chat 🔑",
        "logout" to "touch grass (log out) 🌿",
        "register" to "join the server 📝",
        "sign up" to "get on the list 📝",
        "sign in" to "prove it's really you 🔑",
        "password" to "secret sauce 🔑",
        "username" to "main character name 👤",
        "email" to "digital mailbox 📧",
        
        // Progress & Stats
        "progress" to "character development 📊",
        "completed" to "main quest done ✅",
        "in progress" to "cooking 🍳",
        "pending" to "waiting for the plot to develop ⏳",
        "statistics" to "clout metrics 📊",
        "usage" to "how much you yapped 📊",
        
        // Categories & Types
        "category" to "vibe check category 📂",
        "type" to "what kind of bestie? 🤔",
        "category" to "main category 📂",
        "tags" to "hashtags but make it organized 🏷️",
        "label" to "what are we calling it? 🏷️",
        
        // Media
        "image" to "pic 🖼️",
        "photo" to "slay pic 📸",
        "video" to "tea in motion 🎬",
        "audio" to "sound wave vibes 🔊",
        "music" to "bops only 🎵",
        "camera" to "slay cam 📸",
        "microphone" to "yap mic 🎤",
        
        // Time-related
        "morning" to "rise and grind ☀️",
        "afternoon" to "midday slay ☀️",
        "evening" to "golden hour vibes 🌅",
        "night" to "villain arc hours 🌙",
        "midnight" to "when the real ones come out 🌙",
        
        // Miscellaneous
        "premium" to "pay pig mode 💎",
        "free" to "broke but happy 💚",
        "pro" to "main character energy 💪",
        "basic" to "npc tier 👤",
        "standard" to "mid tier 😐",
        "custom" to "made it yours fr 🎨",
        "default" to "the vibe they chose for you 🎨",
        "recommended" to "the squad approves ✅",
        "popular" to "everyone's doing it 🔥",
        "new" to "fresh drop ✨",
        "hot" to "trending fr 🔥",
        "trending" to "the algorithm chose this 🔥",
        
        // App-specific (Operit)
        "operit" to "your digital bestie 🤖",
        "operit ai" to "robot friend go brrr 🤖",
        "ai chat" to "yapping with the robot 💬",
        "agent clis" to "main character terminals 🤖",
        "toolbox" to "cheat code vault 🛠️",
        "mini apps" to "side quests 🎮",
        "mcp market" to "plugin marketplace 🛍️",
        "skill market" to "talent show 🎭",
        "model config" to "brain settings 🧠",
        "theme settings" to "drip configuration 🎨",
        "user preferences" to "how you like it 🎭",
        "token usage" to "clout spent 🎫",
        "workflow" to "main quest log 🔄",
        "shizuku commands" to "hacker tools 🔧",
        "update history" to "glow up timeline 📝",
        "help" to "the tea ☕",
        "about" to "the lore ℹ️",
        
        // More comprehensive translations
        "select" to "pick your fighter",
        "select language" to "pick your lingo 🗣️",
        "language settings" to "lingo vibes 🗣️",
        "app language" to "what language we speaking? 🗣️",
        "system language" to "phone's lingo 📱",
        "change language" to "switch the lingo 🔄",
        "supported languages" to "languages we speak 🌐",
        
        "notification settings" to "attention mode 🔔",
        "enable notifications" to "let it yell at you 🔔",
        "notification sound" to "attention noise 🔊",
        "vibration pattern" to "bzzz vibes 📳",
        
        "dark mode" to "villain arc mode 🌑",
        "light mode" to "main character sunshine ☀️",
        "auto" to "let the phone decide 🤖",
        "follow system" to "do what phone does 📱",
        
        "font size" to "text chonk level 🔤",
        "font family" to "text drip 📝",
        "font style" to "letter vibes ✨",
        
        "background" to "aesthetic backdrop 🎨",
        "wallpaper" to "main character background 🖼️",
        "theme color" to "color palette drip 🎨",
        "accent color" to "pop of color ✨",
        
        "connection" to "vibe check with server 📡",
        "network" to "internet homies 🌐",
        "wifi" to "free internet 📶",
        "mobile data" to "cellular tea 📶",
        "bluetooth" to "wireless homie connection 🔵",
        
        "storage" to "digital hoarding space 💾",
        "cache" to "temporary tea ☕",
        "data usage" to "how much tea you drank 📊",
        "clear cache" to "delete the temporary tea 🧹",
        
        "security" to "protect your peace 🔒",
        "encryption" to "secret code mode 🔐",
        "two factor authentication" to "double check it's you 🔐",
        "biometric" to "face id or fingerprint flex 🔐",
        "fingerprint" to "unique hand stamp 🔐",
        "face id" to "face recognition drip 🔐",
        
        "accessibility" to "everyone can slay ♿",
        "text to speech" to "robot reads for you 🔊",
        "speech to text" to "you yap, it types 🎤",
        "voice control" to "talk to it like a boss 🎤",
        "screen reader" to "robot describes the screen 👁️",
        
        "performance" to "speed run stats ⚡",
        "battery" to "energy levels 🔋",
        "battery saver" to "conserve the juice 🔋",
        "optimize" to "make it go vroom ⚡",
        
        "feedback" to "spill your thoughts 💭",
        "rate us" to "drop a review bestie ⭐",
        "share app" to "tell the homies 📤",
        "invite friends" to "bring the squad 👥",
        
        "terms of service" to "the rules (boring) 📄",
        "privacy policy" to "how we use your tea 🔒",
        "legal" to "lawyer speak ⚖️",
        "license" to "permission slip 📄",
        
        "version history" to "glow up timeline 📜",
        "what's new" to "fresh drops ✨",
        "release notes" to "dev tea ☕",
        "changelog" to "what they changed 📝",
        
        "beta" to "test subject mode 🧪",
        "stable" to "tried and true ✅",
        "experimental" to "mad scientist mode 🧪",
        "early access" to "vip treatment ✨",
    )
    
    /**
     * Transform an entire sentence into Gen Z slang
     * This is the unhinged version that translates whole sentences
     */
    fun transformSentenceToGenZ(sentence: String): String {
        // First try exact match
        val exactMatch = genZDictionary[sentence.lowercase()]
        if (exactMatch != null) return exactMatch
        
        // If no exact match, try to translate word by word
        val words = sentence.split(" ")
        val translatedWords = words.map { word ->
            val cleanWord = word.lowercase().replace(Regex("[^a-z]"), "")
            genZDictionary[cleanWord] ?: word
        }
        
        return translatedWords.joinToString(" ")
    }
}
