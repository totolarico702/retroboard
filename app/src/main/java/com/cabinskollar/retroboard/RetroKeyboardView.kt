package com.cabinskollar.retroboard

import android.content.Context
import android.graphics.*
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

class RetroKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : KeyboardView(context, attrs, defStyle) {

    private val bgPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val rect  = RectF()
    private val rectE = RectF()

    var skinTokens: SkinTokens? = null

    override fun onDraw(canvas: Canvas) {
        val t = skinTokens ?: run { super.onDraw(canvas); return }
        val kb = keyboard   ?: run { super.onDraw(canvas); return }

        val radius = when (SkinManager.current) {
            Skin.IBM   -> 8f
            Skin.ATARI -> 6f
            Skin.APPLE -> 16f
        }

        // Fond général
        canvas.drawColor(t.keyboardBg)

        for (key in kb.keys) {
            val x  = key.x.toFloat()
            val y  = key.y.toFloat()
            val w  = key.width.toFloat()
            val h  = key.height.toFloat()
            val gap = 5f

            val code = key.codes.firstOrNull() ?: 0

            // Couleurs selon type de touche
            val (bgTop, bgBot, edge) = when (code) {
                -100 -> Triple(lighten(t.copyBg, 0.25f),   t.copyBg,   darken(t.copyBg,   0.4f))
                -102 -> Triple(lighten(t.pasteBg, 0.25f),  t.pasteBg,  darken(t.pasteBg,  0.4f))
                -1, 10, -5, -101, -103 -> Triple(lighten(t.actionBg, 0.2f), t.actionBg, darken(t.actionBg, 0.4f))
                else -> Triple(lighten(t.keyBg, 0.25f), t.keyBg, t.keyEdge)
            }

// Ombre basse (effet relief)
            edgePaint.color = edge
            rectE.set(x + gap, y + gap + 4f, x + w - gap, y + h - gap + 6f)
            canvas.drawRoundRect(rectE, radius, radius, edgePaint)

// Corps touche avec gradient
            val shader = LinearGradient(
                x, y + gap,
                x, y + h - gap,
                bgTop, bgBot,
                Shader.TileMode.CLAMP
            )
            topPaint.shader = shader
            rect.set(x + gap, y + gap, x + w - gap, y + h - gap - 3f)
            canvas.drawRoundRect(rect, radius, radius, topPaint)

            // Label
            val label = key.label?.toString() ?: continue
            if (label.isEmpty()) continue

            textPaint.color = when (code) {
                -100          -> t.copyText
                -102          -> t.pasteText
                in 48..57     -> t.numText
                -1, 10, -5,
                -101, -103    -> t.actionText
                else          -> t.keyText
            }

            textPaint.textSize = when {
                label.length > 4 -> 22f
                label.length > 2 -> 26f
                label.length > 1 -> 30f
                else             -> 36f
            }

            val tx = x + w / 2f
            val ty = y + h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, tx, ty, textPaint)
        }
    }

    private fun lighten(color: Int, factor: Float): Int {
        val r = ((Color.red(color)   + (255 - Color.red(color))   * factor).toInt()).coerceIn(0, 255)
        val g = ((Color.green(color) + (255 - Color.green(color)) * factor).toInt()).coerceIn(0, 255)
        val b = ((Color.blue(color)  + (255 - Color.blue(color))  * factor).toInt()).coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color)   * (1f - factor)).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1f - factor)).toInt().coerceIn(0, 255)
        val b = (Color.blue(color)  * (1f - factor)).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}