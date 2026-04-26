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
    
    // Top-left control buttons (same layer as shimmer glow)
    private var topLeftControlLayout: android.widget.LinearLayout? = null
    private var stopTaskButton: android.widget.ImageButton? = null
    private var pauseTaskButton: android.widget.ImageButton? = null

    // Callbacks for task control
    private var onStopTaskClicked: (() -> Unit)? = null
    private var onPauseTaskClicked: (() -> Unit)? = null
    private var onResumeTaskClicked: (() -> Unit)? = null

    // Callbacks for simplify and pause/play
    private var onSimplifyPageClicked: (() -> Unit)? = null
    private var onPausePlayClicked: ((isPaused: Boolean) -> Unit)? = null
    private var isAudioPaused = false
    
    // Callbacks for attach functionality
    private var onAttachClicked: (() -> Unit)? = null
    private var onAttachImageClicked: (() -> Unit)? = null
    private var onAttachFileClicked: (() -> Unit)? = null
    private var onAttachAudioClicked: (() -> Unit)? = null
    private var onAttachScreenClicked: (() -> Unit)? = null

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
     * Call this when a task starts - shows persistent edge glow AND top-left controls
     */
    fun showTaskActiveGlow(
        onStopClicked: () -> Unit = {},
        onPauseClicked: () -> Unit = {},
        onResumeClicked: () -> Unit = {}
    ) {
        showEdgeGlow()
        // Show buttons immediately after glow - they'll be at same z-order level
        // Since both are posted to mainHandler, they'll execute in order
        showTopLeftTaskControls(
            onStopClicked = onStopClicked,
            onPauseClicked = onPauseClicked,
            onResumeClicked = onResumeClicked
        )
        Log.d(TAG, "=== showTaskActiveGlow: Called both showEdgeGlow + showTopLeftTaskControls ===")
    }

    /**
     * Call this when a task is completed - hides the edge glow AND top-left controls
     */
    fun hideTaskActiveGlow() {
        hideEdgeGlow()
        hideTopLeftTaskControls()
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
        onOutsideTap: () -> Unit,
        placeholderText: String = "Ask Operit",
        onAttachClicked: (() -> Unit)? = null,
        onAttachImageClicked: (() -> Unit)? = null,
        onAttachFileClicked: (() -> Unit)? = null,
        onAttachAudioClicked: (() -> Unit)? = null,
        onAttachScreenClicked: (() -> Unit)? = null
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
            
            // Store the callbacks for later use
            this.onAttachClicked = onAttachClicked
            this.onAttachImageClicked = onAttachImageClicked
            this.onAttachFileClicked = onAttachFileClicked
            this.onAttachAudioClicked = onAttachAudioClicked
            this.onAttachScreenClicked = onAttachScreenClicked

            val inputField = inputBoxView?.findViewById<EditText>(R.id.overlayInputField)
            val rootLayout = inputBoxView?.findViewById<View>(R.id.overlayRootLayout)

            // Set custom placeholder text
            inputField?.hint = placeholderText

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
            
            // Set up attach button - shows selection menu
            val attachButton = inputBoxView?.findViewById<android.widget.ImageButton>(R.id.attachButton)
            attachButton?.setOnClickListener {
                showAttachSelectionMenu()
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

    // Full-screen tap overlay for stopping tasks
    private var fullScreenTapOverlay: View? = null
    private var onFullScreenTap: (() -> Unit)? = null

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

            Log.d(TAG, "=== showEdgeGlowInternal START ===")
            
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
                try { windowManager.addView(view, params); Log.d(TAG, "Added TOP glow strip") } catch (e: Exception) { Log.e(TAG, "Failed to add top glow strip", e) }
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
                try { windowManager.addView(view, params); Log.d(TAG, "Added BOTTOM glow strip") } catch (e: Exception) { Log.e(TAG, "Failed to add bottom glow strip", e) }
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
                try { windowManager.addView(view, params); Log.d(TAG, "Added LEFT glow strip") } catch (e: Exception) { Log.e(TAG, "Failed to add left glow strip", e) }
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
                try { windowManager.addView(view, params); Log.d(TAG, "Added RIGHT glow strip") } catch (e: Exception) { Log.e(TAG, "Failed to add right glow strip", e) }
            }

            isGlowLinkedToAudio = linkToAudio
            if (!linkToAudio) {
                startEdgeGlowShimmer()
            }
            Log.d(TAG, "=== showEdgeGlowInternal END (linkToAudio=$linkToAudio) ===")
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

    // ========== Full-Screen Tap to Stop Overlay ==========

    /**
     * Show a transparent full-screen overlay that captures taps anywhere to stop the task.
     * Tapping anywhere on the screen will trigger the onTap callback for immediate termination.
     */
    fun showFullScreenTapToStopOverlay(onTap: () -> Unit) {
        mainHandler.post {
            // If already shown, update callback
            fullScreenTapOverlay?.let { existing ->
                if (existing.isAttachedToWindow) {
                    onFullScreenTap = onTap
                    return@post
                }
            }

            onFullScreenTap = onTap

            try {
                val density = context.resources.displayMetrics.density
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                // Create transparent view that captures all touches
                fullScreenTapOverlay = android.view.View(context).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    // Set click listener - this captures ANY tap on the screen
                    setOnClickListener {
                        Log.d(TAG, "Full-screen tap detected - triggering stop")
                        onFullScreenTap?.invoke()
                    }

                    // Also handle touch for better responsiveness
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            Log.d(TAG, "Full-screen touch detected - triggering stop")
                            onFullScreenTap?.invoke()
                            true
                        } else {
                            false
                        }
                    }
                }

                windowManager.addView(fullScreenTapOverlay, params)
                Log.d(TAG, "Full-screen tap overlay added - tap anywhere to stop task")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding full-screen tap overlay", e)
            }
        }
    }

    /**
     * Hide the full-screen tap overlay
     */
    fun hideFullScreenTapOverlay() {
        mainHandler.post {
            fullScreenTapOverlay?.let { view ->
                if (view.isAttachedToWindow) {
                    try {
                        windowManager.removeView(view)
                        Log.d(TAG, "Full-screen tap overlay removed")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing full-screen tap overlay", e)
                    }
                }
            }
            fullScreenTapOverlay = null
            onFullScreenTap = null
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
     * Show attach selection menu (file or screen)
     */
    private fun showAttachSelectionMenu() {
        mainHandler.post {
            val options = arrayOf(
                "🖼️  Image",
                "📁  File (PDF, DOC, etc.)",
                "🎵  Audio",
                "📱  Current Screen"
            )
            
            // Create a custom view for the menu
            val layout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(0xFF1A1A2E.toInt())
                }
                setPadding(0, 0, 0, 0)
                
                // Title
                addView(android.widget.TextView(context).apply {
                    text = "📎 Attach Content"
                    textSize = 18f
                    setTextColor(0xFF00D4AA.toInt())
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(48, 32, 48, 24)
                })
                
                // Options
                options.forEachIndexed { index, option ->
                    addView(android.widget.TextView(context).apply {
                        text = option
                        textSize = 16f
                        setTextColor(0xFFE8E8E8.toInt())
                        setPadding(48, 24, 48, 24)
                        setOnClickListener {
                            // Remove this view first
                            (parent as? android.view.ViewGroup)?.removeView(this@apply)
                            when (index) {
                                0 -> onAttachImageClicked?.invoke()
                                1 -> onAttachFileClicked?.invoke()
                                2 -> onAttachAudioClicked?.invoke()
                                3 -> captureAndAttachCurrentScreen()
                            }
                        }
                    })
                }
                
                // Cancel button
                addView(android.widget.TextView(context).apply {
                    text = "Cancel"
                    textSize = 16f
                    setTextColor(0xFFFF6B6B.toInt())
                    gravity = android.view.Gravity.CENTER
                    setPadding(48, 32, 48, 32)
                    setOnClickListener {
                        (parent as? android.view.ViewGroup)?.removeView(this@apply)
                    }
                })
            }
            
            // Create AlertDialog with custom view
            val dialog = android.app.AlertDialog.Builder(context)
                .setView(layout)
                .create()
            
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dialog.show()
        }
    }
    
    /**
     * Capture current screen content using the same logic as Simplify Page button
     */
    private fun captureAndAttachCurrentScreen() {
        try {
            // Use FullPageCapture to get screen content (same as simplify page)
            val fullPageCapture = com.ai.assistance.operit.voice.utilities.FullPageCapture.getInstance(context)
            
            // Get screen text content
            val screenText = fullPageCapture.getCurrentScreenText()
            
            // Notify that screen content is captured
            mainHandler.post {
                android.widget.Toast.makeText(context, "Screen content captured (${screenText.length} chars)", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            // The actual attachment would be handled by the callback in ConversationalAgentService
            onAttachScreenClicked?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screen", e)
            mainHandler.post {
                android.widget.Toast.makeText(context, "Failed to capture screen", android.widget.Toast.LENGTH_SHORT).show()
            }
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
    
    /**
     * Show top-left control buttons (stop and pause/play) during ongoing tasks
     * These buttons are now part of the edge glow layer to ensure they're always visible
     * at the same z-order as the neon glow effect
     */
    fun showTopLeftTaskControls(
        onStopClicked: () -> Unit = {},
        onPauseClicked: () -> Unit = {},
        onResumeClicked: () -> Unit = {}
    ) {
        Log.d(TAG, "=== showTopLeftTaskControls CALLED ===")
        onStopTaskClicked = onStopClicked
        onPauseTaskClicked = onPauseClicked
        onResumeTaskClicked = onResumeClicked

        mainHandler.post {
            Log.d(TAG, "=== showTopLeftTaskControls EXECUTING on main thread ===")
            // If already showing, don't duplicate
            if (topLeftControlLayout?.isAttachedToWindow == true) {
                Log.d(TAG, "=== Top-left controls already attached, skipping ===")
                return@post
            }

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show top-left task controls: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            val density = context.resources.displayMetrics.density
            val buttonSize = (36 * density).toInt() // Much smaller: 36dp
            val cornerRadius = 18f // More rounded (half of buttonSize)

            // Create rounded background drawables (need 2+ colors for GradientDrawable)
            val stopButtonBg = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFF0000.toInt(), 0xFFE00000.toInt())
            )
            stopButtonBg.cornerRadius = cornerRadius

            val pauseButtonBg = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFFFF00.toInt(), 0xFFF5E600.toInt())
            )
            pauseButtonBg.cornerRadius = cornerRadius

            // Create horizontal layout for stop and pause buttons
            topLeftControlLayout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())

                // Stop button (red X) - with rounded background
                stopTaskButton = android.widget.ImageButton(context).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    background = stopButtonBg
                    setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                    contentDescription = "Stop task"
                    setOnClickListener {
                        onStopTaskClicked?.invoke()
                    }
                }

                // Pause/Play button (yellow) - with rounded background
                pauseTaskButton = android.widget.ImageButton(context).apply {
                    setImageResource(android.R.drawable.ic_media_pause)
                    background = pauseButtonBg
                    setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                    contentDescription = "Pause task"
                    setOnClickListener {
                        val isCurrentlyPaused = tag as? Boolean ?: false
                        if (isCurrentlyPaused) {
                            setImageResource(android.R.drawable.ic_media_pause)
                            contentDescription = "Pause task"
                            onResumeTaskClicked?.invoke()
                        } else {
                            setImageResource(android.R.drawable.ic_media_play)
                            contentDescription = "Resume task"
                            onPauseTaskClicked?.invoke()
                        }
                        tag = !isCurrentlyPaused
                    }
                }

                addView(stopTaskButton, android.widget.LinearLayout.LayoutParams(
                    buttonSize, buttonSize
                ).apply {
                    setMargins(0, 0, (12 * density).toInt(), 0)
                })
                addView(pauseTaskButton, android.widget.LinearLayout.LayoutParams(
                    buttonSize, buttonSize
                ))
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            params.x = (16 * density).toInt()
            params.y = (40 * density).toInt() // Higher: 40dp instead of 100dp

            try {
                windowManager.addView(topLeftControlLayout, params)
                Log.d(TAG, "Top-left task controls added at edge glow layer.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding top-left task controls", e)
                topLeftControlLayout = null
            }
        }
    }
    
    /**
     * Hide top-left control buttons (stop and pause/play)
     */
    fun hideTopLeftTaskControls() {
        mainHandler.post {
            topLeftControlLayout?.let { view ->
                if (view.isAttachedToWindow) {
                    try {
                        windowManager.removeView(view)
                        Log.d(TAG, "Top-left task controls removed.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing top-left task controls", e)
                    }
                }
            }
            topLeftControlLayout = null
            stopTaskButton = null
            pauseTaskButton = null
        }
    }

    /**
     * Bring top-left task controls to front (re-add to ensure highest z-order)
     * This should be called after other overlays are shown during task execution
     */
    fun bringTopLeftControlsToFront() {
        mainHandler.post {
            val layout = topLeftControlLayout ?: return@post
            if (!layout.isAttachedToWindow) return@post
            
            try {
                // Remove and re-add to bring to front
                windowManager.removeView(layout)
                
                // Re-add with same parameters
                val density = context.resources.displayMetrics.density
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }
                params.x = (16 * density).toInt()
                params.y = (40 * density).toInt()
                
                windowManager.addView(layout, params)
                Log.d(TAG, "Top-left task controls brought to front.")
            } catch (e: Exception) {
                Log.e(TAG, "Error bringing top-left controls to front", e)
            }
        }
    }
    
    /**
     * Update the pause/play button icon
     */
    fun updateTaskPauseButtonIcon(isPaused: Boolean) {
        mainHandler.post {
            pauseTaskButton?.setImageResource(
                if (isPaused) android.R.drawable.ic_media_play
                else android.R.drawable.ic_media_pause
            )
            pauseTaskButton?.tag = isPaused
        }
    }
}
