# ⚡ Quick Start: Adding Chronically Online Mode

## TL;DR - Add in 3 Steps

### Step 1: Import the localizer
```kotlin
import com.ai.assistance.operit.ui.components.genZString
```

### Step 2: Replace stringResource() calls
```kotlin
// BEFORE:
Text(stringResource(R.string.settings))

// AFTER:
Text(genZString(R.string.settings))
```

### Step 3: That's it! 🎉

---

## Real Examples

### Navigation Item
```kotlin
// BEFORE:
NavigationDrawerItem(
    label = { Text(stringResource(R.string.nav_settings)) },
    ...
)

// AFTER:
NavigationDrawerItem(
    label = { Text(genZString(R.string.nav_settings)) },
    ...
)
// Shows "vibes check ⚡" when enabled!
```

### Top Bar Title
```kotlin
// BEFORE:
TopAppBar(
    title = { Text(stringResource(R.string.settings)) }
)

// AFTER:
TopAppBar(
    title = { Text(genZString(R.string.settings)) }
)
// Shows "vibes check ⚡" when enabled!
```

### Button Text
```kotlin
// BEFORE:
Button(onClick = { ... }) {
    Text(stringResource(R.string.save))
}

// AFTER:
Button(onClick = { ... }) {
    Text(genZString(R.string.save))
}
// Shows "lock in 🔒" when enabled!
```

### Dialog Title
```kotlin
// BEFORE:
AlertDialog(
    title = { Text(stringResource(R.string.delete_confirmation)) },
    text = { Text(stringResource(R.string.delete_message)) },
    confirmButton = {
        TextButton(onClick = { ... }) {
            Text(stringResource(R.string.delete))
        }
    }
)

// AFTER:
AlertDialog(
    title = { Text(genZString(R.string.delete_confirmation)) },
    text = { Text(genZString(R.string.delete_message)) },
    confirmButton = {
        TextButton(onClick = { ... }) {
            Text(genZString(R.string.delete))
        }
    }
)
// Shows:
// Title: "send to the shadow realm? 🗑️"
// Message: "bestie, this is permanent ❌"
// Button: "send to the shadow realm 🗑️"
```

---

## Batch Replacement Tips

### Find & Replace in Android Studio

**Find:** `stringResource(R.string.`
**Replace:** `genZString(R.string.`

This will convert 90% of your strings automatically!

### Common Patterns to Replace

```kotlin
// Pattern 1: Direct stringResource
stringResource(R.string.xxx) 
→ genZString(R.string.xxx)

// Pattern 2: With context
context.getString(R.string.xxx)
→ chronicallyOnlineManager.getGenZString(context, R.string.xxx)

// Pattern 3: Hardcoded strings
"Settings"
→ "Settings".toGenZ()
```

---

## Priority Screens (Start Here)

### 1. Settings Screen ⭐⭐⭐
Most visible, highest impact

```kotlin
@Composable
fun SettingsScreen(...) {
    // Replace ALL stringResource calls
    Text(genZString(R.string.nav_settings))
    Text(genZString(R.string.theme_settings))
    Text(genZString(R.string.language_settings))
    // ... etc
}
```

### 2. Chat Screen ⭐⭐⭐
Core user interaction

```kotlin
@Composable
fun AIChatScreen(...) {
    Text(genZString(R.string.type_message))
    Text(genZString(R.string.send))
    Text(genZString(R.string.ai_thinking))
    // ... etc
}
```

### 3. Navigation Drawer ⭐⭐
Users see this constantly

```kotlin
@Composable
fun NavigationDrawer(...) {
    NavigationDrawerItem(
        label = { Text(genZString(item.titleRes)) }
    )
    // ... etc
}
```

### 4. Dialogs & Snackbars ⭐⭐
High visibility moments

```kotlin
scaffoldState.snackbarHostState.showSnackbar(
    message = genZString(R.string.success_message)
)
```

---

## Testing Your Changes

### Manual Test Checklist
- [ ] Enable Chronically Online mode in Settings > Language
- [ ] Navigate to modified screen
- [ ] Verify all text is translated
- [ ] Toggle mode off
- [ ] Verify all text returns to normal
- [ ] Check different screen sizes
- [ ] Check dark/light mode

### Quick Test
```kotlin
@Preview
@Composable
fun TestChronicallyOnline() {
    Column {
        Text(genZString(R.string.settings))
        Text(genZString(R.string.save))
        Text(genZString(R.string.delete))
        Text(genZString(R.string.loading))
        Text(genZString(R.string.success))
    }
}
```

---

## Common Issues & Solutions

### Issue: Text not translating
**Solution:** Make sure you're using `genZString()` not `stringResource()`

### Issue: App crashes after replacement
**Solution:** Check that the string resource exists in strings.xml

### Issue: Translation looks weird
**Solution:** Add the string to the Gen Z dictionary in ChronicallyOnlineManager.kt

### Issue: Want to translate dynamic text
**Solution:** Use `.toGenZ()` extension function
```kotlin
val dynamicText = "User ${user.name}"
Text(dynamicText.toGenZ())
```

---

## Performance Impact

**When disabled:** ZERO impact (just calls stringResource internally)

**When enabled:** Minimal impact (O(1) HashMap lookup)

**Memory:** Negligible (dictionary is ~50KB)

**CPU:** ~0.001ms per translation

---

## Pro Tips for Maximum Impact

### 1. Add Easter Eggs
```kotlin
// Special translation for rare events
if (Math.random() < 0.01) {
    return "you found a rare one! 🌟"
}
```

### 2. Context-Aware Translations
```kotlin
// Different translations based on time
val hour = LocalTime.now().hour
if (hour > 22 || hour < 5) {
    return "villain arc hours ${normalText} 🌙"
}
```

### 3. User Reactions
```kotlin
// Show snackbar when they first enable it
if (firstTimeEnabling) {
    showSnackbar("slay! ur speaking gen z now bestie 💅")
}
```

---

## Migration Script (Optional)

Want to auto-convert all files? Use this Python script:

```python
import re
import os

def convert_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Replace stringResource with genZString
    content = re.sub(
        r'stringResource\(R\.string\.(\w+)\)',
        r'genZString(R.string.\1)',
        content
    )
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# Convert all Kotlin files
for root, dirs, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            convert_file(os.path.join(root, file))
```

---

## Final Checklist

Before shipping:
- [ ] Added import to all modified files
- [ ] Replaced stringResource() with genZString()
- [ ] Tested with mode enabled
- [ ] Tested with mode disabled
- [ ] Added missing translations to dictionary
- [ ] Updated documentation
- [ ] Added to release notes

---

## Example PR Description

```
feat: Add Chronically Online mode support

- Replaced stringResource() with genZString() in:
  - SettingsScreen
  - AIChatScreen
  - NavigationDrawer
  - All dialogs and snackbars

- Users can now enable "English (Chronically Online)" in Language Settings
- All UI text transforms to Gen Z slang when enabled
- 300+ translations in the dictionary
- Zero performance impact when disabled

Inspired by Canva's "English (Chronically Online)" language option.
```

---

**That's it! Your app is now chronically online.** 🤪💅

*Need help? Check CHRONICALLY_ONLINE_MODE.md for full documentation.*
