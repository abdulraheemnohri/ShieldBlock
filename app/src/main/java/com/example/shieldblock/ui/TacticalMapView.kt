package com.example.shieldblock.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.*

class TacticalMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2286FEA7.toInt()
        style = Paint.Style.FILL
    }

    private val pingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pings = mutableListOf<Ping>()
    private val random = Random()

    data class Ping(val x: Float, val y: Float, var radius: Float, var alpha: Int)

    init {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (random.nextInt(10) == 0) addRandomPing()
                updatePings()
                invalidate()
            }
        }
        animator.start()
    }

    private fun addRandomPing() {
        pings.add(Ping(random.nextFloat(), random.nextFloat(), 2f, 255))
        if (pings.size > 15) pings.removeAt(0)
    }

    private fun updatePings() {
        val it = pings.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.radius += 1.5f
            p.alpha -= 8
            if (p.alpha <= 0) it.remove()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Draw a simplified "digital" grid map background
        mapPaint.style = Paint.Style.STROKE
        mapPaint.strokeWidth = 1f
        mapPaint.alpha = 20

        val step = 40f
        var x = 0f
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, mapPaint)
            x += step
        }
        var y = 0f
        while (y < h) {
            canvas.drawLine(0f, y, w, y, mapPaint)
            y += step
        }

        // Draw some "landmass" rectangles to simulate a map
        mapPaint.style = Paint.Style.FILL
        mapPaint.alpha = 30
        canvas.drawRect(w * 0.1f, h * 0.2f, w * 0.3f, h * 0.5f, mapPaint) // North Americaish
        canvas.drawRect(w * 0.15f, h * 0.55f, w * 0.25f, h * 0.8f, mapPaint) // South Americaish
        canvas.drawRect(w * 0.45f, h * 0.2f, w * 0.6f, h * 0.45f, mapPaint) // Eurasiaish
        canvas.drawRect(w * 0.48f, h * 0.5f, w * 0.58f, h * 0.75f, mapPaint) // Africaish
        canvas.drawRect(w * 0.75f, h * 0.6f, w * 0.85f, h * 0.8f, mapPaint) // Australiaish

        // Draw active pings
        pings.forEach { p ->
            pingPaint.color = 0xFF86FEA7.toInt()
            pingPaint.alpha = p.alpha
            canvas.drawCircle(p.x * w, p.y * h, p.radius, pingPaint)

            pingPaint.style = Paint.Style.STROKE
            canvas.drawCircle(p.x * w, p.y * h, p.radius * 1.5f, pingPaint)
            pingPaint.style = Paint.Style.FILL
        }
    }
}
