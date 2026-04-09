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
    private var actionStatusView: View? = null
    private var statusTextView: TextView? = null
    private var pausePlayButton: android.widget.ImageButton? = null
    private var simplifyButton: android.widget.ImageButton? = null

    // Callbacks for simplify and pause/play
    private var onSimplifyPageClicked: (() -> Unit)? = null
    private var onPausePlayClicked: ((isPaused: Boolean) -> Unit)? = null
    private var isAudioPaused = false

    // Track spoken text for display
    private val spokenTextHistory = StringBuilder()

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
            // Don't hide edge glow here - it's managed by task lifecycle

            Log.d(TAG, "Audio wave effect has been torn down.")
        }
    }

    /**
     * Call this when a task starts - shows persistent edge glow
     */
    fun showTaskActiveGlow() {
        showEdgeGlow()
    }

    /**
     * Call this when a task is completed - hides the edge glow
     */
    fun hideTaskActiveGlow() {
        hideEdgeGlow()
        hideTtsWave()
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
                // Input box is already showing, just ensure focus and keyboard
                val existingField = inputBoxView?.findViewById<EditText>(R.id.overlayInputField)
                existingField?.isFocusableInTouchMode = true
                existingField?.postDelayed({
                    existingField.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(existingField, InputMethodManager.SHOW_IMPLICIT)
                }, 50)
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

            // Ensure the EditText is focusable in touch mode (critical for overlay windows)
            inputField?.isFocusable = true
            inputField?.isFocusableInTouchMode = true
            inputField?.isClickable = true
            inputField?.isLongClickable = true

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = (80 * context.resources.displayMetrics.density).toInt()
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            inputField?.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val inputText = v.text.toString().trim()
                    // Hide keyboard first
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)

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
                val bgDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt())
                ).apply {
                    cornerRadius = 16f
                    setStroke(2, 0xFF00D4AA.toInt()) // Teal border
                }
                background = bgDrawable
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

    // ========== 4-Edge Screen Glow with Subtle Traveling Shimmer ==========

    private var edgeGlowViewTop: View? = null
    private var edgeGlowViewBottom: View? = null
    private var edgeGlowViewLeft: View? = null
    private var edgeGlowViewRight: View? = null
    private var edgeGlowAnimator: android.animation.ValueAnimator? = null
    private var shimmerAnimator: android.animation.ValueAnimator? = null
    private var isGlowLinkedToAudio = false

    fun showEdgeGlow() {
        showEdgeGlowInternal(linkToAudio = false)
    }

    /**
     * Show edge glow with optional audio amplitude linkage
     * @param linkToAudio if true, the glow will pulse based on audio amplitude
     */
    private fun showEdgeGlowInternal(linkToAudio: Boolean) {
        mainHandler.post {
            if (edgeGlowViewTop?.isAttachedToWindow == true) return@post

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show edge glow: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            val glowThickness = (4 * context.resources.displayMetrics.density).toInt()
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // Teal glow with gradient for shimmer effect
            val glowColor = 0x8800D4AA.toInt()

            // Top strip - with horizontal gradient for shimmer
            edgeGlowViewTop = View(context).apply {
                val gradient = android.graphics.LinearGradient(
                    0f, 0f, screenWidth.toFloat(), 0f,
                    intArrayOf(0x4400D4AA.toInt(), 0xCC00FFCC.toInt(), 0x4400D4AA.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.MIRROR
                )
                background = android.graphics.drawable.PaintDrawable().apply {
                    paint.shader = gradient
                }
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            }
            edgeGlowViewTop?.let { view ->
                val params = WindowManager.LayoutParams(
                    screenWidth, glowThickness,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP }
                try { windowManager.addView(view, params) } catch (e: Exception) { Log.e(TAG, "Failed to add top glow strip", e) }
            }

            // Bottom strip
            edgeGlowViewBottom = View(context).apply {
                val gradient = android.graphics.LinearGradient(
                    0f, 0f, screenWidth.toFloat(), 0f,
                    intArrayOf(0x4400D4AA.toInt(), 0xCC00FFCC.toInt(), 0x4400D4AA.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.MIRROR
                )
                background = android.graphics.drawable.PaintDrawable().apply {
                    paint.shader = gradient
                }
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            }
            edgeGlowViewBottom?.let { view ->
                val params = WindowManager.LayoutParams(
                    screenWidth, glowThickness,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.BOTTOM }
                try { windowManager.addView(view, params) } catch (e: Exception) { Log.e(TAG, "Failed to add bottom glow strip", e) }
            }

            // Left strip - with vertical gradient
            edgeGlowViewLeft = View(context).apply {
                val gradient = android.graphics.LinearGradient(
                    0f, 0f, 0f, screenHeight.toFloat(),
                    intArrayOf(0x4400D4AA.toInt(), 0xCC00FFCC.toInt(), 0x4400D4AA.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.MIRROR
                )
                background = android.graphics.drawable.PaintDrawable().apply {
                    paint.shader = gradient
                }
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            }
            edgeGlowViewLeft?.let { view ->
                val params = WindowManager.LayoutParams(
                    glowThickness, screenHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.START }
                try { windowManager.addView(view, params) } catch (e: Exception) { Log.e(TAG, "Failed to add left glow strip", e) }
            }

            // Right strip
            edgeGlowViewRight = View(context).apply {
                val gradient = android.graphics.LinearGradient(
                    0f, 0f, 0f, screenHeight.toFloat(),
                    intArrayOf(0x4400D4AA.toInt(), 0xCC00FFCC.toInt(), 0x4400D4AA.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.MIRROR
                )
                background = android.graphics.drawable.PaintDrawable().apply {
                    paint.shader = gradient
                }
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            }
            edgeGlowViewRight?.let { view ->
                val params = WindowManager.LayoutParams(
                    glowThickness, screenHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.END }
                try { windowManager.addView(view, params) } catch (e: Exception) { Log.e(TAG, "Failed to add right glow strip", e) }
            }

            isGlowLinkedToAudio = linkToAudio
            if (!linkToAudio) {
                startEdgeGlowShimmer()
            }
            Log.d(TAG, "Edge glow strips added (linkToAudio=$linkToAudio).")
        }
    }

    private fun startEdgeGlowShimmer() {
        // Shimmer animation: moves the gradient offset along the 4 edges
        shimmerAnimator = android.animation.ValueAnimator.ofFloat(0f, screenWidthF()).apply {
            duration = 3000L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.RESTART
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animator ->
                val offset = animator.animatedValue as Float
                updateGradientOffset(offset)
            }
            start()
        }

        // Alpha pulse for subtle breathing effect
        edgeGlowAnimator = android.animation.ValueAnimator.ofFloat(0.6f, 1f, 0.6f).apply {
            duration = 2000L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                listOf(edgeGlowViewTop, edgeGlowViewBottom, edgeGlowViewLeft, edgeGlowViewRight).forEach { view ->
                    view?.alpha = alpha
                }
            }
            start()
        }
    }

    private fun screenWidthF(): Float {
        return context.resources.displayMetrics.widthPixels.toFloat()
    }

    private fun screenHeightF(): Float {
        return context.resources.displayMetrics.heightPixels.toFloat()
    }

    private fun updateGradientOffset(offset: Float) {
        val sw = screenWidthF()
        val sh = screenHeightF()
        val color1 = 0x4400D4AA.toInt()
        val color2 = 0xCC00FFCC.toInt()
        val color3 = 0x4400D4AA.toInt()

        // Top - horizontal shimmer moving right
        (edgeGlowViewTop?.background as? android.graphics.drawable.PaintDrawable)?.paint?.shader =
            android.graphics.LinearGradient(offset - sw, 0f, offset, 0f, intArrayOf(color1, color2, color3), floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.MIRROR)

        // Bottom - horizontal shimmer moving left
        (edgeGlowViewBottom?.background as? android.graphics.drawable.PaintDrawable)?.paint?.shader =
            android.graphics.LinearGradient(sw - offset, 0f, sw - offset + sw, 0f, intArrayOf(color1, color2, color3), floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.MIRROR)

        // Left - vertical shimmer moving down
        (edgeGlowViewLeft?.background as? android.graphics.drawable.PaintDrawable)?.paint?.shader =
            android.graphics.LinearGradient(0f, offset - sh, 0f, offset, intArrayOf(color1, color2, color3), floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.MIRROR)

        // Right - vertical shimmer moving up
        (edgeGlowViewRight?.background as? android.graphics.drawable.PaintDrawable)?.paint?.shader =
            android.graphics.LinearGradient(0f, sh - offset, 0f, sh - offset + sh, intArrayOf(color1, color2, color3), floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.MIRROR)

        // Force redraw
        edgeGlowViewTop?.invalidate()
        edgeGlowViewBottom?.invalidate()
        edgeGlowViewLeft?.invalidate()
        edgeGlowViewRight?.invalidate()
    }

    fun updateEdgeGlowAmplitude(amplitude: Float) {
        mainHandler.post {
            if (edgeGlowViewTop?.isAttachedToWindow == true && isGlowLinkedToAudio) {
                val alpha = amplitude.coerceIn(0f, 1f)
                listOf(edgeGlowViewTop, edgeGlowViewBottom, edgeGlowViewLeft, edgeGlowViewRight).forEach { view ->
                    view?.alpha = alpha
                }
            }
        }
    }

    fun hideEdgeGlow() {
        mainHandler.post {
            edgeGlowAnimator?.cancel()
            edgeGlowAnimator = null
            shimmerAnimator?.cancel()
            shimmerAnimator = null
            isGlowLinkedToAudio = false
            listOf(edgeGlowViewTop, edgeGlowViewBottom, edgeGlowViewLeft, edgeGlowViewRight).forEach { view ->
                view?.let {
                    if (it.isAttachedToWindow) {
                        try {
                            windowManager.removeView(it)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error removing edge glow strip", e)
                        }
                    }
                }
            }
            edgeGlowViewTop = null
            edgeGlowViewBottom = null
            edgeGlowViewLeft = null
            edgeGlowViewRight = null
            Log.d(TAG, "Edge glow strips removed.")
        }
    }

    // ========== Action Status Display (for non-UI-automation tasks) ==========

    /**
     * Show the action status display - a card showing what the AI is doing,
     * with pause/play and simplify page buttons.
     * This is shown when the AI agent is performing a non-UI-automation task.
     */
    fun showActionStatusView(
        onSimplifyPage: () -> Unit = {},
        onPausePlay: (isPaused: Boolean) -> Unit = {}
    ) {
        onSimplifyPageClicked = onSimplifyPage
        onPausePlayClicked = onPausePlay

        mainHandler.post {
            if (actionStatusView?.isAttachedToWindow == true) return@post

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show action status view: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            val inflater = LayoutInflater.from(context)
            actionStatusView = inflater.inflate(R.layout.overlay_action_status, null)

            statusTextView = actionStatusView?.findViewById(R.id.statusText)
            pausePlayButton = actionStatusView?.findViewById(R.id.pausePlayButton)
            simplifyButton = actionStatusView?.findViewById(R.id.simplifyButton)

            // Set up pause/play button
            pausePlayButton?.setOnClickListener {
                isAudioPaused = !isAudioPaused
                pausePlayButton?.setImageResource(
                    if (isAudioPaused) android.R.drawable.ic_media_play
                    else android.R.drawable.ic_media_pause
                )
                pausePlayButton?.contentDescription =
                    if (isAudioPaused) "Resume audio output" else "Pause audio output"
                onPausePlayClicked?.invoke(isAudioPaused)
            }

            // Set up simplify button
            simplifyButton?.setOnClickListener {
                onSimplifyPageClicked?.invoke()
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                y = (120 * context.resources.displayMetrics.density).toInt()
            }

            try {
                windowManager.addView(actionStatusView, params)
                Log.d(TAG, "Action status view added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding action status view", e)
                actionStatusView = null
            }
        }
    }

    /**
     * Append text to the status display (this shows what the AI is saying/doing)
     */
    fun appendStatusText(text: String) {
        mainHandler.post {
            if (text.isBlank()) return@post
            spokenTextHistory.append(text).append("\n\n")

            // Keep only last 5000 chars to avoid overflow
            if (spokenTextHistory.length > 5000) {
                spokenTextHistory.delete(0, spokenTextHistory.length - 5000)
            }

            statusTextView?.text = spokenTextHistory.toString()
            // Scroll to bottom
            statusTextView?.let { tv ->
                tv.post {
                    val scrollAmount = tv.layout?.getLineTop(tv.lineCount) ?: 0
                    tv.scrollTo(0, scrollAmount)
                }
            }
        }
    }

    /**
     * Update the status text (replace entirely)
     */
    fun updateStatusText(text: String) {
        mainHandler.post {
            spokenTextHistory.clear()
            spokenTextHistory.append(text)
            statusTextView?.text = text
        }
    }

    /**
     * Clear the status text
     */
    fun clearStatusText() {
        mainHandler.post {
            spokenTextHistory.clear()
            statusTextView?.text = ""
        }
    }

    /**
     * Hide the action status view
     */
    fun hideActionStatusView() {
        mainHandler.post {
            actionStatusView?.let { view ->
                if (view.isAttachedToWindow) {
                    try {
                        windowManager.removeView(view)
                        Log.d(TAG, "Action status view removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing action status view", e)
                    }
                }
            }
            actionStatusView = null
            statusTextView = null
            pausePlayButton = null
            simplifyButton = null
        }
    }

    /**
     * Check if audio is currently paused
     */
    fun isAudioPaused(): Boolean = isAudioPaused

    /**
     * Reset the pause state
     */
    fun resetPauseState() {
        isAudioPaused = false
        pausePlayButton?.setImageResource(android.R.drawable.ic_media_pause)
    }
}
