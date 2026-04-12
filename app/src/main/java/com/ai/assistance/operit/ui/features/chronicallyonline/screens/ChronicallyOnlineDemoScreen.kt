package com.ai.assistance.operit.ui.features.chronicallyonline.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.theme.*
import com.ai.assistance.operit.util.ChronicallyOnlineManager

/**
 * 🤪 Chronically Online Demo Screen
 * 
 * Shows users what their app will look like with Gen Z mode enabled!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronicallyOnlineDemoScreen(
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val chronicallyOnlineManager = remember { ChronicallyOnlineManager.getInstance(context) }
    var isChronicallyOnline by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        chronicallyOnlineManager.isChronicallyOnline.collect { isChronicallyOnline = it }
    }
    
    val examples = listOf(
        TransformationExample("Settings", "vibes check ⚡"),
        TransformationExample("Save", "lock in 🔒"),
        TransformationExample("Loading...", "the server is having a moment 💅"),
        TransformationExample("Delete", "send to the shadow realm 🗑️"),
        TransformationExample("Success", "WE LOVE TO SEE IT ✅"),
        TransformationExample("Error", "bestie, we need to talk ❌"),
        TransformationExample("Chat", "yapping session 💬"),
        TransformationExample("AI Assistant", "digital bestie 🤖"),
        TransformationExample("New Chat", "new arc ✨"),
        TransformationExample("Message", "spill the tea ☕"),
        TransformationExample("Search", "detective mode 🔍"),
        TransformationExample("OK", "bet ✅"),
        TransformationExample("Cancel", "nah I'm good ✌️"),
        TransformationExample("Help", "spill the tea ☕"),
        TransformationExample("About", "lore dump ℹ️"),
        TransformationExample("Notifications", "attention seeker mode 🔔"),
        TransformationExample("Dark Mode", "villain arc mode 🌑"),
        TransformationExample("Light Mode", "main character sunshine ☀️"),
        TransformationExample("Login", "enter the chat 🔑"),
        TransformationExample("Logout", "touch grass 🌿"),
        TransformationExample("Password", "secret sauce 🔑"),
        TransformationExample("Premium", "pay pig mode 💎"),
        TransformationExample("Free", "broke but happy 💚"),
        TransformationExample("Update", "glow up 📲"),
        TransformationExample("Backup", "emotional support backup 💾"),
        TransformationExample("Streak", "don't break the chain 🔥"),
        TransformationExample("Level Up", "main character moment ⚡"),
        TransformationExample("Badge", "flex badge 🏅"),
        TransformationExample("Community", "the squad 👥"),
        TransformationExample("Profile", "main character page 👤"),
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isChronicallyOnline) "chronically online demo 🤪" else "Chronically Online Demo",
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Hero Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Brush.horizontalGradient(
                        colors = listOf(HotPink, NeonPurple, ElectricBlue)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🤪",
                        fontSize = 64.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (isChronicallyOnline) "ur speaking gen z fr fr" else "Chronically Online Mode",
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isChronicallyOnline) 
                            "slay bestie, everything's a vibe now ✨" 
                        else 
                            "Transform ALL UI text into Gen Z slang!",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Status Indicator
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isChronicallyOnline) StreakFire.copy(alpha = 0.2f) else DarkSurface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isChronicallyOnline) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isChronicallyOnline) SuccessGreen else ErrorRed
                    )
                    Column {
                        Text(
                            text = if (isChronicallyOnline) "MODE: ACTIVATED 🔥" else "Mode: OFF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = if (isChronicallyOnline) 
                                "bestie, the whole app is speaking gen z now!" 
                            else 
                                "Enable it in Language Settings",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = OnDarkBg.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Transformations List
            Text(
                text = if (isChronicallyOnline) "the tea ☕" else "Before & After",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 600.dp)
            ) {
                items(examples) { example ->
                    TransformationCard(example, isChronicallyOnline)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // CTA Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurfaceHigh
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isChronicallyOnline) "wanna toggle it off?" else "Ready to enable?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isChronicallyOnline) 
                            "no cap, u can turn it off in Language Settings 💅" 
                        else 
                            "Go to Settings > Language > Enable Chronically Online",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = OnDarkBg.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TransformationCard(example: TransformationExample, isChronicallyOnline: Boolean) {
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
            // Before
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Before:",
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = OnDarkBg.copy(alpha = 0.5f)
                )
                Text(
                    text = example.normalText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = OnDarkBg.copy(alpha = 0.7f)
                )
            }
            
            // Arrow
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = NeonPurple,
                modifier = Modifier.size(24.dp)
            )
            
            // After
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "After:",
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = HotPink.copy(alpha = 0.7f)
                )
                Text(
                    text = if (isChronicallyOnline) example.genZText else example.normalText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isChronicallyOnline) HotPink else OnDarkBg.copy(alpha = 0.7f)
                )
            }
        }
    }
}

data class TransformationExample(
    val normalText: String,
    val genZText: String
)
