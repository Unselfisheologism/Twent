package com.ai.assistance.operit.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Operit's voice wave visualization - completely redesigned from the original.
 * Uses a warm amber-to-emerald gradient with smooth pill-shaped bars instead of sine waves.
 */
class AudioWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    companion object {
        private const val MIN_DB_VALUE = -60f
        private const val MAX_DB_VALUE = -5f
    }

    // Pill bar design instead of sine waves
    private val barCount = 24
    private val barSpacing = 3f
    private val minIdleHeight = 6f
    private val maxBarHeightScale = 0.85f
    private val targetAmplitudeTransitionDuration = 400L
    private val realtimeAmplitudeTransitionDuration = 80L

    // Completely different color scheme: warm amber → coral → deep orange → teal
    private val barColors = intArrayOf(
        0xFFB84C.toColorInt(), // Amber
        0xFF8A2B.toColorInt(), // Orange
        0xFF6B1A.toColorInt(), // Deep orange
        0xFF4D0F.toColorInt(), // Burnt orange
        0xFF3A0D.toColorInt(), // Dark orange
        0xFF2D6B.toColorInt(), // Teal accent
        0xFF1A8A.toColorInt(), // Cyan-teal
    )

    private var amplitudeAnimator: ValueAnimator? = null
    private val barPaints = mutableListOf<Paint>()
    private val barHeights = FloatArray(barCount)
    private val barTargetHeights = FloatArray(barCount)
    private val barSpeeds = FloatArray(barCount)
    private val barPhases = FloatArray(barCount)

    private var audioAmplitude = 0.15f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // Initialize bar properties with varied but smooth values
        for (i in 0 until barCount) {
            val normalizedIndex = i.toFloat() / barCount
            // Create a wave-like height distribution
            barPhases[i] = normalizedIndex * 3.14159f * 2f
            barSpeeds[i] = 0.015f + Random.nextFloat() * 0.01f
            barHeights[i] = minIdleHeight
            barTargetHeights[i] = minIdleHeight

            barPaints.add(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = barColors[i % barColors.size]
                alpha = 200
            })
        }

        // Continuous gentle wave animation
        ValueAnimator.ofFloat(0f, 1f).apply {
            interpolator = LinearInterpolator()
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val speedFactor = 1.0f + (audioAmplitude * 3.0f)
                for (i in 0 until barCount) {
                    barPhases[i] += barSpeeds[i] * speedFactor
                    // Smooth height updates
                    val waveOffset = sin(barPhases[i]).toFloat()
                    val baseHeight = minIdleHeight + (audioAmplitude * maxBarHeightScale * height * 0.5f)
                    barTargetHeights[i] = baseHeight * (0.6f + 0.4f * (waveOffset * 0.5f + 0.5f))
                }
                invalidate()
            }
            start()
        }
    }

    fun updateAmplitude(rmsdB: Float) {
        val normalizedAmplitude = ((rmsdB - MIN_DB_VALUE) / (MAX_DB_VALUE - MIN_DB_VALUE)).coerceIn(0f, 2.0f)
        setRealtimeAmplitude(normalizedAmplitude)
    }

    fun setRealtimeAmplitude(amplitude: Float) {
        val scaledAmplitude = amplitude.pow(1.5f).coerceIn(0.0f, 1.0f)
        val targetAmplitude = 0.15f + (scaledAmplitude * maxBarHeightScale)
        amplitudeAnimator?.cancel()
        amplitudeAnimator = ValueAnimator.ofFloat(audioAmplitude, targetAmplitude).apply {
            duration = realtimeAmplitudeTransitionDuration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                audioAmplitude = animation.animatedValue as Float
            }
            start()
        }
    }

    fun setTargetAmplitude(target: Float) {
        val targetAmplitude = 0.15f + (target * maxBarHeightScale)
        amplitudeAnimator?.cancel()
        amplitudeAnimator = ValueAnimator.ofFloat(audioAmplitude, targetAmplitude).apply {
            duration = targetAmplitudeTransitionDuration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                audioAmplitude = animation.animatedValue as Float
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalSpacing = barSpacing * (barCount - 1)
        val barWidth = (width - totalSpacing) / barCount
        val cornerRadius = barWidth / 2f

        for (i in 0 until barCount) {
            // Smooth interpolation to target height
            barHeights[i] += (barTargetHeights[i] - barHeights[i]) * 0.15f
            val barHeight = barHeights[i].coerceAtLeast(minIdleHeight)

            val x = i * (barWidth + barSpacing)
            val y = height - barHeight

            val paint = barPaints[i % barPaints.size]
            // Draw rounded rectangle (pill shape)
            val rect = RectF(x, y, x + barWidth, height.toFloat())
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }
}
