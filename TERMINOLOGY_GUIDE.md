# 📝 Terminology & Branding Guide

## Current App Name
**Current Name:** "Operit"

**Usage Throughout Codebase:**
- App package name: `com.ai.assistance.operit`
- Wake word: "Operit"
- Directory names: "Operit"
- File exports: "Operit"
- User-Agent strings: "Operit"
- Default character name: "Operit"

## 🔧 What You Need to Change

### 1. **App Name & Branding** (HIGH PRIORITY)
Choose a new name for your app. Suggestions based on your logo (orange robot face):
- **RoboMind**
- **AICore** 
- **MindBot**
- **NeuralMate**
- **AssistAI**
- **SmartPilot**
- **VoiceBot**
- **AutoMate AI**

**Files to Update:**
```
app/src/main/java/com/ai/assistance/operit/
  ├── All references to "Operit" as app name
  ├── WakeWordDetector.kt (line 17): Change wake word
  ├── CharacterCardManager.kt (line 63): Default character name
  └── Various UI strings

app/src/main/res/values/strings.xml
  - All app name references
  
app/src/main/AndroidManifest.xml
  - package name (requires refactoring)
  - app label
```

**⚠️ WARNING:** Changing the package name (`com.ai.assistance.operit`) is a MAJOR undertaking:
- Requires refactoring all import statements
- May break existing user installations
- Changes the app's identity on Google Play
- Affects deep links and intents

**RECOMMENDATION:** Keep the package name as-is for technical reasons, but change:
- Display name shown to users
- Wake word
- User-facing strings
- Documentation

### 2. **Wake Word** (MEDIUM PRIORITY)
**Current:** "Operit"
**File:** `app/src/main/java/com/ai/assistance/operit/voice/api/WakeWordDetector.kt`
**Line:** 17

**Change to:** Whatever you want users to say to activate the assistant
```kotlin
private val wakeWord = "YourNewWakeWord"  // e.g., "Hey Assistant", "AI Bot", etc.
```

### 3. **Feature Terminology** (CUSTOMIZE AS NEEDED)

| Current Term | Location | Suggested Alternatives |
|-------------|----------|----------------------|
| "Assistant" | Throughout app | "AI Companion", "Bot", "Helper", "Agent" |
| "Triggers" | UI & settings | "Automations", "Workflows", "Rules", "Actions" |
| "Memories" | Memories feature | "Context", "Knowledge", "Recall", "History" |
| "Moments" | Moments feature | "Highlights", "Events", "Captures" |
| "Toolbox" | Navigation | "Features", "Tools", "Utilities" |
| "Skills" | Skill system | "Capabilities", "Functions", "Powers" |
| "Agent" | Agent features | "AI Worker", "Bot", "Helper" |

### 4. **UI Strings** (HIGH PRIORITY - User-Facing)

**Main String Files:**
```
app/src/main/res/values/strings.xml (default)
app/src/main/res/values-en/strings.xml (English)
app/src/main/res/values-*/strings.xml (other languages)
```

**Key Strings to Review:**
- App title/label
- Feature descriptions
- Permission explanations
- Settings labels
- Navigation menu items
- Toast/snackbar messages
- Notification text

### 5. **Privacy & Legal Pages** (HIGH PRIORITY)

**Files Already Updated:**
- ✅ `AgreementScreen.kt` - Modern, rewritten terms
- Need to update references to your app name

**Search for these in string resources:**
- Privacy policy content
- Terms of service
- Data collection descriptions
- Third-party service mentions

### 6. **Onboarding Content** (HIGH PRIORITY)

**Files Already Updated:**
- ✅ `PermissionGuideScreen.kt` - Modern, rewritten onboarding

**Still Need to Update:**
- String resources for onboarding texts
- Permission request descriptions
- Feature tour content

## 🎯 Recommended Action Plan

### Phase 1: Choose Your Brand Name
1. **Select a new app name** (see suggestions above or create your own)
2. **Decide on wake word** (what users say to activate)
3. **Choose preferred terminology** for features

### Phase 2: Update User-Facing Text (SAFE CHANGES)
```bash
# These changes are safe and won't break anything:

1. Update string resources
   app/src/main/res/values-en/strings.xml
   
2. Change wake word
   WakeWordDetector.kt line 17
   
3. Update default character name  
   CharacterCardManager.kt line 63
   
4. Review and update all user-facing strings
```

### Phase 3: Deep Branding Changes (OPTIONAL)
```bash
# These require more work but complete the rebrand:

1. Update app display name in AndroidManifest.xml
2. Change notification channel names
3. Update share intent text
4. Modify deep link schemes (if any)
5. Update app icon and splash screen
```

### Phase 4: Advanced Refactoring (ONLY IF NECESSARY)
```bash
# ⚠️ MAJOR CHANGES - Only if you want complete rebrand:

1. Rename package from com.ai.assistance.operit
   - Requires: Refactoring ALL imports
   - Risk: High, may break existing installations
   - Benefit: Complete brand separation

2. Update all file paths and directory names
   - Export directories
   - Storage paths
   - Cache locations
```

## 📋 Quick Checklist

### Immediate Changes (Do These First):
- [ ] Choose new app name
- [ ] Choose new wake word
- [ ] Update `WakeWordDetector.kt` line 17
- [ ] Update `CharacterCardManager.kt` line 63
- [ ] Review and update `strings.xml` files
- [ ] Update app label in `AndroidManifest.xml`

### Medium Priority:
- [ ] Update feature names (Triggers → Your term)
- [ ] Update menu labels
- [ ] Update settings titles
- [ ] Update notification text
- [ ] Update share intent text

### Lower Priority (Nice to Have):
- [ ] Update app icon references
- [ ] Update splash screen
- [ ] Update widget labels
- [ ] Update deep link schemes
- [ ] Update documentation

## 🔍 How to Find All Occurrences

### Search Commands:
```bash
# Find all "Operit" strings in Kotlin files
grep -r "\"Operit\"" app/src --include="*.kt" -n

# Find all string resources mentioning Operit
grep -r "Operit" app/src/main/res --include="strings*.xml" -n

# Find wake word references
grep -r "wakeWord\|wake_word\|WakeWord" app/src --include="*.kt" -n

# Find app name in manifests
grep -r "operit" app/src/main/AndroidManifest.xml
```

## 💡 Tips

1. **Keep package name** - It's technical and users won't see it
2. **Focus on user-facing text** - App name, labels, messages
3. **Be consistent** - Use your new name everywhere users see it
4. **Test thoroughly** - After changes, test all features
5. **Consider trademark** - Make sure your new name isn't trademarked

## 📞 Need Help?

Once you decide on your new name and terminology, I can help you:
- Update all the string resources
- Change the wake word
- Modify user-facing text throughout the app
- Create new branding materials

Just let me know what name you'd like to use!

---

**Status:** Color palette complete ✅
**Next Steps:** Choose your app name and wake word, then update terminology
