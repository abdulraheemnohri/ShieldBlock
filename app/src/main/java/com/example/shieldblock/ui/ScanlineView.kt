package com.example.shieldblock.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class ScanlineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1186FEA7.toInt()
        strokeWidth = 2f
    }

    private var offset = 0f
    private val animator = ValueAnimator.ofFloat(0f, 40f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            offset = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        var y = offset
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += 10f
        }
    }
}
