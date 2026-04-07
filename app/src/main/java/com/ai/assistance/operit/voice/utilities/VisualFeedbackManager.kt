package com.ai.assistance.operit.voice.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.ai.assistance.operit.voice.AudioWaveView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.voice.utilities.TTSManager
import com.ai.assistance.operit.voice.utilities.TtsVisualizer

class VisualFeedbackManager private constructor(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Components ---
    private var audioWaveView: AudioWaveView? = null
    private var ttsVisualizer: TtsVisualizer? = null
    private var transcriptionView: TextView? = null
    private var inputBoxView: View? = null
    private var thinkingIndicatorView: View? = null
    private var speakingOverlay: View? = null

    companion object {
        private const val TAG = "VisualFeedbackManager"

        @Volatile private var INSTANCE: VisualFeedbackManager? = null

        fun getInstance(context: Context): VisualFeedbackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VisualFeedbackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Check if the app has permission to draw overlays
     */
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    // --- TTS Wave Methods ---

    fun showTtsWave() {
        mainHandler.post {
            if (audioWaveView != null) {
                Log.d(TAG, "Audio wave is already showing.")
                return@post
            }
            setupAudioWaveEffect()
            showEdgeGlow() // Show edge glow during active TTS
        }
    }

    fun hideTtsWave() {
        mainHandler.post {
            audioWaveView?.let {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                    Log.d(TAG, "Audio wave view removed.")
                }
            }
            audioWaveView = null

            ttsVisualizer?.stop()
            ttsVisualizer = null
            TTSManager.getInstance(context).utteranceListener = null
            hideSpeakingOverlay()
            hideEdgeGlow() // Hide edge glow when TTS stops

            Log.d(TAG, "Audio wave effect has been torn down.")
        }
    }

    private fun setupAudioWaveEffect() {
        // Check if we have overlay permission before attempting to add views
        if (!hasOverlayPermission()) {
            Log.e(TAG, "Cannot setup audio wave effect: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        try {
            // Create and add the AudioWaveView
            audioWaveView = AudioWaveView(context)
            val heightInDp = 120
            val heightInPixels = (heightInDp * context.resources.displayMetrics.density).toInt()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, heightInPixels,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,

            ).apply {
                gravity = Gravity.BOTTOM
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            windowManager.addView(audioWaveView, params)
            Log.d(TAG, "Audio wave view added.")

            // Link to TTSManager
            val ttsManager = TTSManager.getInstance(context)
            val audioSessionId = ttsManager.getAudioSessionId()

            if (audioSessionId == 0) {
                Log.e(TAG, "Failed to get valid audio session ID. Visualizer not started.")
                return
            }

            ttsVisualizer = TtsVisualizer(audioSessionId) { normalizedAmplitude ->
                mainHandler.post {
                    audioWaveView?.setRealtimeAmplitude(normalizedAmplitude)
                }
            }

            ttsManager.utteranceListener = { isSpeaking ->
                mainHandler.post {
                    if (isSpeaking) {
                        audioWaveView?.setTargetAmplitude(0.2f)
                        ttsVisualizer?.start()
                    } else {
                        ttsVisualizer?.stop()
                        audioWaveView?.setTargetAmplitude(0.0f)
                    }
                }
            }
            Log.d(TAG, "Audio wave effect has been set up.")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio wave effect", e)
            // Clean up if something went wrong
            audioWaveView = null
            ttsVisualizer = null
        }
    }

    fun showSpeakingOverlay() {
        mainHandler.post {
            if (speakingOverlay != null) return@post

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show speaking overlay: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            // Operit: Clean, minimal white overlay (not purple-tinted)
            speakingOverlay = View(context).apply {
                setBackgroundColor(0x20FFFFFF.toInt()) // Subtle 12% white
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(speakingOverlay, params)
                Log.d(TAG, "Speaking overlay added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding speaking overlay", e)
                speakingOverlay = null
            }
        }
    }

    fun hideSpeakingOverlay() {
        mainHandler.post {
            speakingOverlay?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager.removeView(it)
                        Log.d(TAG, "Speaking overlay removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing speaking overlay", e)
                    }
                }
            }
            speakingOverlay = null
        }
    }

    // --- Transcription View ---

    fun showTranscription() {
        mainHandler.post {
            if (transcriptionView != null) return@post

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show transcription view: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            transcriptionView = TextView(context).apply {
                text = "Listening..."
                // Operit: Clean dark card with teal accent (not purple glassmorphism)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt())
                ).apply {
                    cornerRadius = 16f
                    setStroke(2, 0xFF00D4AA.toInt()) // Teal border
                }
                setTextColor(0xFFE8E8E8.toInt())
                textSize = 15f
                setPadding(32, 20, 32, 20)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (200 * context.resources.displayMetrics.density).toInt()
            }

            try {
                windowManager.addView(transcriptionView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add transcription view.", e)
                transcriptionView = null
            }
        }
    }

    fun updateTranscription(text: String) {
        transcriptionView?.text = text
    }

    fun hideTranscription() {
        mainHandler.post {
            transcriptionView?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager.removeView(it)
                        Log.d(TAG, "Transcription view removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing transcription view", e)
                    }
                }
            }
            transcriptionView = null
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    fun showInputBox(
        onActivated: () -> Unit,
        onSubmit: (String) -> Unit,
        onOutsideTap: () -> Unit
    ) {
        mainHandler.post {
            if (inputBoxView?.isAttachedToWindow == true) {
                inputBoxView?.findViewById<EditText>(R.id.overlayInputField)?.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputBoxView?.findViewById(R.id.overlayInputField), InputMethodManager.SHOW_IMPLICIT)
                return@post
            }

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show input box: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            if (inputBoxView != null) {
                try { windowManager.removeView(inputBoxView) } catch (e: Exception) {}
            }

            val inflater = LayoutInflater.from(context)
            inputBoxView = inflater.inflate(R.layout.overlay_input_box, null)

            val inputField = inputBoxView?.findViewById<EditText>(R.id.overlayInputField)
            val rootLayout = inputBoxView?.findViewById<View>(R.id.overlayRootLayout)

            // Operit: Redesign input box with teal accent (not purple)
            val cardBackground = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt())
            ).apply {
                cornerRadius = 20f
                setStroke(2, 0xFF00D4AA.toInt()) // Teal border
            }
            rootLayout?.background = cardBackground

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = (80 * context.resources.displayMetrics.density).toInt()
            }

            inputField?.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val inputText = v.text.toString().trim()
                    if (inputText.isNotEmpty()) {
                        onSubmit(inputText)
                        v.text = ""
                    } else {
                        hideInputBox()
                    }
                    true
                } else {
                    false
                }
            }

            inputField?.setOnTouchListener { _, _ ->
                onActivated()
                false
            }

            rootLayout?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    Log.d(TAG, "Outside touch detected.")
                    onOutsideTap()
                    return@setOnTouchListener true
                }
                false
            }

            try {
                windowManager.addView(inputBoxView, params)
                Log.d(TAG, "Input box overlay added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding input box overlay", e)
            }
        }
    }

    fun hideInputBox() {
        mainHandler.post {
            inputBoxView?.let { view ->
                if (view.isAttachedToWindow) {
                    try {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(view.windowToken, 0)
                        windowManager.removeView(view)
                        Log.d(TAG, "Input box removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing input box", e)
                    }
                }
            }
            inputBoxView = null
        }
    }

    // --- Thinking indicator ---
    fun showThinkingIndicator(initialText: String = "Thinking...") {
        if (thinkingIndicatorView != null) {
            updateThinking(initialText)
            return
        }

        showEdgeGlow() // Show edge glow during thinking/processing

        mainHandler.post {
            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show thinking indicator: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            thinkingIndicatorView?.let {
                try { if (it.isAttachedToWindow) windowManager.removeView(it) } catch (_: Exception) {}
            }

            // Operit: Clean dark card with pulsing teal border
            val textView = TextView(context).apply {
                text = initialText
                val background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt())
                ).apply {
                    cornerRadius = 16f
                    setStroke(2, 0xFF00D4AA.toInt()) // Teal border
                }
                background = background
                setTextColor(0xFFE8E8E8.toInt())
                textSize = 15f
                setPadding(32, 20, 32, 20)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                ViewCompat.setElevation(this, 8f)
            }

            thinkingIndicatorView = textView

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (280 * context.resources.displayMetrics.density).toInt()
            }

            try {
                windowManager.addView(textView, params)
                Log.d(TAG, "Thinking indicator added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding thinking indicator", e)
                thinkingIndicatorView = null
            }
        }
    }

    fun updateThinking(text: String) {
        mainHandler.post {
            (thinkingIndicatorView as? TextView)?.text = text
        }
    }

    fun hideThinkingIndicator() {
        mainHandler.post {
            thinkingIndicatorView?.let { view ->
                if (view.isAttachedToWindow) {
                    try {
                        windowManager.removeView(view)
                        Log.d(TAG, "Thinking indicator removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing thinking indicator", e)
                    }
                }
            }
            thinkingIndicatorView = null
            hideEdgeGlow() // Hide edge glow when thinking is done
        }
    }

    // ========== 4-Edge Screen Glow (Operit: teal/cyan accent) ==========

    private var edgeGlowView: View? = null
    private var edgeGlowAnimator: android.animation.ValueAnimator? = null

    fun showEdgeGlow() {
        mainHandler.post {
            if (edgeGlowView?.isAttachedToWindow == true) return@post

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show edge glow: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            val glowThickness = (6 * context.resources.displayMetrics.density).toInt() // 6dp - subtler

            edgeGlowView = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setStroke(glowThickness, 0x5500D4AA.toInt()) // Teal glow (#00D4AA at 33% opacity)
                    cornerRadius = 0f
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(edgeGlowView, params)
                startEdgeGlowAnimation()
                Log.d(TAG, "Edge glow view added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding edge glow view", e)
                edgeGlowView = null
            }
        }
    }

    private fun startEdgeGlowAnimation() {
        edgeGlowAnimator = android.animation.ValueAnimator.ofFloat(0.25f, 0.65f, 0.25f).apply {
            duration = 2500L // Slower, more subtle pulse
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                edgeGlowView?.background?.alpha = (alpha * 255).toInt()
            }
            start()
        }
    }

    fun hideEdgeGlow() {
        mainHandler.post {
            edgeGlowAnimator?.cancel()
            edgeGlowAnimator = null
            edgeGlowView?.let {
                if (it.isAttachedToWindow) {
                    try {
                        windowManager.removeView(it)
                        Log.d(TAG, "Edge glow view removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing edge glow view", e)
                    }
                }
            }
            edgeGlowView = null
        }
    }
}
