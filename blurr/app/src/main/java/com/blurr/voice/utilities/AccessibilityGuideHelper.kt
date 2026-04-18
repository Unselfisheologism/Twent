package com.blurr.voice.utilities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.Html
import android.text.Spanned
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.blurr.voice.R

/**
 * The Supademo interactive tutorial URL for accessibility setup.
 */
private const val DEMO_TUTORIAL_URL = "https://app.supademo.com/demo/cmo4mfzy702e2yd0jl0j1z1c8?preview=true"

/**
 * Helper that builds an OEM-aware, step-by-step guide for granting the
 * Accessibility permission on Android 13+ (where "restricted settings"
 * adds an extra gate before the accessibility toggle can be switched on).
 *
 * On Android 12 and below the user is taken straight to Accessibility
 * Settings (old, simpler flow).
 */
object AccessibilityGuideHelper {

    /**
     * Returns the manufacturer label used in the guide text.
     * Maps the raw Build.MANUFACTURER to a friendly name.
     */
    private fun getOemLabel(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("samsung") -> "Samsung"
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "Xiaomi / Redmi / POCO"
            m.contains("oppo") -> "OPPO"
            m.contains("vivo") -> "vivo"
            m.contains("oneplus") -> "OnePlus"
            m.contains("huawei") || m.contains("honor") -> "Huawei / Honor"
            m.contains("realme") -> "Realme"
            m.contains("motorola") || m.contains("moto") -> "Motorola"
            m.contains("nokia") -> "Nokia"
            m.contains("google") || m.contains("pixel") -> "Google Pixel"
            m.contains("nothing") -> "Nothing"
            m.contains("sony") -> "Sony"
            m.contains("lg") -> "LG"
            m.contains("zte") -> "ZTE"
            m.contains("asus") -> "ASUS"
            m.contains("lenovo") -> "Lenovo"
            else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Returns the OEM-specific label for "App info" / app details screen.
     */
    private fun getAppInfoLabel(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("samsung") -> "App info"
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "App info"
            m.contains("huawei") || m.contains("honor") -> "App info"
            else -> "App info"
        }
    }

    /**
     * Returns the OEM-specific menu location for "Allow restricted settings".
     * On Samsung it's under three-dots (⋮) on the App info screen.
     * On Xiaomi/Redmi it's often under "Other permissions" or similar.
     * On most stock Android / Pixel it's under the three-dot menu.
     */
    private fun getRestrictedSettingsMenuPath(): List<String> {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("samsung") -> listOf(
                "Tap the three-dot menu (⋮) in the top-right corner.",
                "Tap \"Allow restricted settings\".",
                "Enter your phone's PIN or password to confirm."
            )
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> listOf(
                "Scroll down and look for \"Special app access\" or tap \"Other permissions\".",
                "Find \"Allow restricted settings\" and toggle it ON.",
                "Enter your phone's PIN or password to confirm."
            )
            m.contains("oppo") || m.contains("realme") -> listOf(
                "Look for \"Special app access\" or tap the three-dot menu (⋮).",
                "Tap \"Allow restricted settings\".",
                "Enter your phone's PIN or password to confirm."
            )
            m.contains("vivo") -> listOf(
                "Tap the three-dot menu (⋮) or look for \"Special access\".",
                "Tap \"Allow restricted settings\".",
                "Enter your phone's PIN or password to confirm."
            )
            m.contains("huawei") || m.contains("honor") -> listOf(
                "Tap the three-dot menu (⋮) in the top-right corner.",
                "Tap \"Allow restricted settings\".",
                "Enter your phone's PIN or password to confirm."
            )
            else -> listOf(
                "Tap the three-dot menu (⋮) in the top-right corner.",
                "Tap \"Allow restricted settings\".",
                "Enter your phone's PIN or password to confirm."
            )
        }
    }

    /**
     * Returns the OEM-specific location for finding the app in Settings.
     * Some OEMs bury "Apps" under different names.
     */
    private fun getSettingsAppsPath(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("samsung") -> "Settings → Apps"
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "Settings → Apps → Manage apps (or \"Installed apps\")"
            m.contains("oppo") -> "Settings → App management"
            m.contains("vivo") -> "Settings → Apps → See all apps"
            m.contains("huawei") || m.contains("honor") -> "Settings → Apps → Apps"
            m.contains("realme") -> "Settings → App management"
            m.contains("oneplus") -> "Settings → Apps → App management"
            m.contains("google") || m.contains("pixel") -> "Settings → Apps → See all apps"
            else -> "Settings → Apps"
        }
    }

    /**
     * Returns the OEM-specific location for finding "Installed apps" or "All apps"
     * inside the accessibility settings page.
     */
    private fun getAccessibilityInstalledAppsLabel(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("samsung") -> "\"Installed apps\""
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "\"Downloaded apps\" or scroll to find \"Twent\""
            m.contains("huawei") || m.contains("honor") -> "\"Installed services\" or scroll down to find the app"
            else -> "\"Installed apps\" or look for \"Twent\" in the list"
        }
    }

    /**
     * Builds the full HTML guide text for Android 13+ restricted settings flow.
     */
    fun buildGuideHtml(context: Context): Spanned {
        val oem = getOemLabel()
        val settingsAppsPath = getSettingsAppsPath()
        val menuSteps = getRestrictedSettingsMenuPath()
        val a11yAppsLabel = getAccessibilityInstalledAppsLabel()
        val appName = context.getString(R.string.app_name)

        val menuStepsHtml = menuSteps.mapIndexed { i, step ->
            val num = i + 3 // starts at step 3
            "<b>Step $num:</b> $step"
        }.joinToString("<br>")

        val html = """
            <b>On Android 13+, accessibility for side-loaded apps is locked behind a "Restricted Settings" gate.</b><br><br>
            <b>You need to complete TWO phases:</b>
            <font color='#FF9800'><b>Phase A</b></font> — Allow Restricted Settings (one-time unlock)<br>
            <font color='#4CAF50'><b>Phase B</b></font> — Enable Accessibility Service<br><br>

            <font color='#FF9800'><b>━━━ PHASE A: Allow Restricted Settings ━━━</b></font><br><br>

            <b>Step 1:</b> Open your phone's <b>Settings</b> app.<br><br>

            <b>Step 2:</b> Navigate to <b>$settingsAppsPath</b> and find <b>"$appName"</b> in the list. Tap on it.<br>
            <i>(Tip: You can also search for "$appName" in Settings search bar.)</i><br><br>

            $menuStepsHtml<br>
            <i>(If you don't see "Allow restricted settings", it may already be allowed — skip to Phase B.)</i><br><br>

            <font color='#4CAF50'><b>━━━ PHASE B: Enable Accessibility ━━━</b></font><br><br>

            <b>Step 5:</b> Come back to the <b>$appName</b> app and tap <b>"Grant Accessibility Permission"</b> again.<br><br>

            <b>Step 6:</b> You'll be taken to your phone's <b>Accessibility</b> settings page.<br><br>

            <b>Step 7:</b> Tap on <b>$a11yAppsLabel</b> to see all apps with accessibility services.<br><br>

            <b>Step 8:</b> Find and tap on <b>"$appName"</b>.<br><br>

            <b>Step 9:</b> You'll see a toggle switch that is <font color='#F44336'>turned OFF</font>. <b>Tap the toggle to turn it ON.</b><br><br>

            <b>Step 10:</b> A confirmation popup will appear. Tap <b>"Allow"</b>.<br><br>

            <font color='#4CAF50'><b>✓ Done!</b></font> The accessibility permission should now be granted.<br>
            Come back to the $appName app to verify.<br><br>
            <font color='#2196F3'><b>💡 Prefer a visual walkthrough?</b></font> Tap <b>"Watch Demo"</b> below for an interactive step-by-step tutorial.
        """.trimIndent()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }

    /**
     * Builds the simple guide text for Android 12 and below (no restricted settings).
     */
    fun buildSimpleGuideHtml(context: Context): Spanned {
        val appName = context.getString(R.string.app_name)
        val html = """
            <b>Follow these steps to enable Accessibility for $appName:</b><br><br>

            <b>Step 1:</b> Tap <b>"Open Accessibility Settings"</b> below.<br><br>

            <b>Step 2:</b> In the Accessibility settings, find <b>"Installed apps"</b> (or scroll to find "$appName").<br><br>

            <b>Step 3:</b> Tap on <b>"$appName"</b>.<br><br>

            <b>Step 4:</b> Tap the <b>toggle switch</b> to turn it ON.<br><br>

            <b>Step 5:</b> A confirmation popup will appear. Tap <b>"Allow"</b>.<br><br>

            <font color='#4CAF50'><b>✓ Done!</b></font> Come back to the $appName app to verify.<br><br>
            <font color='#2196F3'><b>💡 Prefer a visual walkthrough?</b></font> Tap <b>"Watch Demo"</b> below for an interactive step-by-step tutorial.
        """.trimIndent()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }

    /**
     * Shows a scrollable guide dialog, then navigates to Accessibility Settings
     * when the user taps the action button.
     *
     * On Android 13+ shows the two-phase guide.
     * On Android 12 and below shows the simple guide.
     */
    fun showGuideDialog(context: Context, onDismiss: (() -> Unit)? = null) {
        val isRestrictedSettingsNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val oemLabel = getOemLabel()

        val title = if (isRestrictedSettingsNeeded) {
            "How to Enable Accessibility ($oemLabel device)"
        } else {
            "How to Enable Accessibility"
        }

        val guideText = if (isRestrictedSettingsNeeded) {
            buildGuideHtml(context)
        } else {
            buildSimpleGuideHtml(context)
        }

        // Build custom dialog layout
        val scrollView = ScrollView(context).apply {
            setPadding(48, 32, 48, 16)
        }

        val textView = TextView(context).apply {
            text = guideText
            textSize = 14f
            setLineSpacing(4f, 1.1f)
        }

        scrollView.addView(textView)

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("Open Accessibility Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            }
            .setNeutralButton("Watch Demo") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(DEMO_TUTORIAL_URL))
                context.startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }
            .setCancelable(true)

        val dialog = builder.create()
        dialog.show()

        // Style the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { btn ->
            btn.setTextColor(context.getColor(android.R.color.holo_green_dark))
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { btn ->
            btn.setTextColor(context.getColor(android.R.color.holo_blue_dark))
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { btn ->
            btn.setTextColor(context.getColor(android.R.color.holo_red_dark))
        }
    }
}
