package com.ai.assistance.operit.overlay

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
import android.widget.ImageButton
import android.widget.TextView
import com.ai.assistance.operit.R

class BlurrStyleOverlayManager private constructor(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var inputBoxView: View? = null
    private var thinkingView: TextView? = null
    private var micButton: ImageButton? = null
    private var statusView: TextView? = null

    private var onSubmitCallback: ((String) -> Unit)? = null
    private var onMicClickCallback: (() -> Unit)? = null

    companion object {
        private const val TAG = "BlurrStyleOverlay"
        
        @Volatile private var INSTANCE: BlurrStyleOverlayManager? = null

        fun getInstance(context: Context): BlurrStyleOverlayManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlurrStyleOverlayManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showInputBox(
        onSubmit: (String) -> Unit,
        onMicClick: (() -> Unit)? = null
    ) {
        mainHandler.post {
            if (inputBoxView?.isAttachedToWindow == true) return@post

            if (!hasOverlayPermission()) {
                Log.e(TAG, "Cannot show input box: SYSTEM_ALERT_WINDOW permission not granted")
                return@post
            }

            onSubmitCallback = onSubmit
            onMicClickCallback = onMicClick

            try { windowManager.removeView(inputBoxView) } catch (e: Exception) {}
            inputBoxView = null

            val inflater = LayoutInflater.from(context)
            inputBoxView = inflater.inflate(R.layout.blurr_input_box, null)

            val inputField = inputBoxView?.findViewById<EditText>(R.id.blurrInputField)
            val rootLayout = inputBoxView?.findViewById<View>(R.id.blurrRootLayout)
            micButton = inputBoxView?.findViewById(R.id.blurrMicButton)

            micButton?.setOnClickListener {
                onMicClickCallback?.invoke()
            }

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
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                    val inputText = v.text.toString().trim()
                    if (inputText.isNotEmpty()) {
                        onSubmitCallback?.invoke(inputText)
                        v.text = ""
                    }
                    true
                } else {
                    false
                }
            }

            rootLayout?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hideInputBox()
                    return@setOnTouchListener true
                }
                false
            }

            try {
                windowManager.addView(inputBoxView, params)
                inputField?.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding input box", e)
            }
        }
    }

    fun hideInputBox() {
        mainHandler.post {
            inputBoxView?.let {
                if (it.isAttachedToWindow) {
                    try {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(it.windowToken, 0)
                        windowManager.removeView(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing input box", e)
                    }
                }
            }
            inputBoxView = null
            micButton = null
        }
    }

    fun showThinking(text: String) {
        if (thinkingView == null) {
            mainHandler.post {
                if (!hasOverlayPermission()) return@post

                thinkingView = TextView(context).apply {
                    this.text = text
                    val glassBackground = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xDD0D0D2E.toInt(), 0xDD2A0D45.toInt())
                    ).apply {
                        cornerRadius = 24f
                        setStroke(1, 0x80FFFFFF.toInt())
                    }
                    background = glassBackground
                    setTextColor(0xFFE0E0E0.toInt())
                    textSize = 14f
                    setPadding(32, 16, 32, 16)
                    typeface = Typeface.MONOSPACE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = 200
                }

                try {
                    windowManager.addView(thinkingView, params)
                } catch (e: Exception) {
                    thinkingView = null
                }
            }
        } else {
            mainHandler.post {
                thinkingView?.text = text
            }
        }
    }

    fun updateThinking(text: String) {
        mainHandler.post {
            thinkingView?.text = text
        }
    }

    fun hideThinking() {
        mainHandler.post {
            thinkingView?.let {
                if (it.isAttachedToWindow) {
                    try { windowManager.removeView(it) } catch (_: Exception) {}
                }
            }
            thinkingView = null
        }
    }

    fun showStatus(text: String) {
        mainHandler.post {
            if (!hasOverlayPermission()) return@post

            if (statusView == null) {
                statusView = TextView(context).apply {
                    text = text
                    val bg = GradientDrawable().apply {
                        setColor(0xCC000000.toInt())
                        cornerRadius = 16f
                    }
                    background = bg
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 12f
                    setPadding(24, 12, 24, 12)
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 150
                }

                try {
                    windowManager.addView(statusView, params)
                } catch (e: Exception) {
                    statusView = null
                }
            } else {
                statusView?.text = text
            }
        }
    }

    fun hideStatus() {
        mainHandler.post {
            statusView?.let {
                if (it.isAttachedToWindow) {
                    try { windowManager.removeView(it) } catch (_: Exception) {}
                }
            }
            statusView = null
        }
    }

    fun hideAll() {
        hideInputBox()
        hideThinking()
        hideStatus()
    }

    fun isShowing(): Boolean {
        return inputBoxView?.isAttachedToWindow == true || 
               thinkingView?.isAttachedToWindow == true ||
               statusView?.isAttachedToWindow == true
    }
}