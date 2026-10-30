package com.example.wifishare.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** رادار حي: موجات تتسع ونقاط تدور. يتوقف تلقائيًا عند إخفائه أو إذا عُطّلت الحركات في النظام. */
class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFFFFFFFF.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private var phase = 0.3f
    private var animator: ValueAnimator? = null

    private fun animationsEnabled(): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    } catch (_: Exception) { true }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null && animationsEnabled()) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 3600L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f
        if (maxR <= 0f) return

        fill.alpha = 40
        canvas.drawCircle(cx, cy, maxR * 0.32f, fill)

        for (i in 0 until 3) {
            val t = (phase + i / 3f) % 1f
            ring.alpha = ((1f - t) * 150f).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, maxR * (0.30f + 0.70f * t), ring)
        }

        for (i in 0 until 3) {
            val dir = if (i % 2 == 0) 1.0 else -1.0
            val ang = phase * 2.0 * PI * dir + i * 2.0 * PI / 3.0
            val orbit = maxR * (0.50f + 0.14f * i)
            fill.alpha = 210
            canvas.drawCircle(
                cx + orbit * cos(ang).toFloat(),
                cy + orbit * sin(ang).toFloat(),
                (3.5f + i) * density,
                fill
            )
        }
    }
}
