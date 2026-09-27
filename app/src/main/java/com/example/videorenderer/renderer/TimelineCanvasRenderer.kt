package com.example.videorenderer.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.videorenderer.data.model.SceneItem
import kotlin.math.max

/**
 * Resolution-Normalized Android Canvas Renderer.
 * Ensures 100% pixel-perfect scaling parity between screen preview (e.g. 540x960)
 * and final exported video (1080x1920), fixing the issue where characters appear
 * smaller in export than in the preview.
 */
class TimelineCanvasRenderer {

    companion object {
        private const val BASE_CANVAS_HEIGHT = 1920f
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val captionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 15, 23, 42)
    }
    private val captionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(160, 16, 185, 129)
    }
    private val captionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 64f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 4f, Color.argb(180, 0, 0, 0))
    }
    private val strongWordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(251, 191, 36)
        textSize = 72f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(12f, 0f, 6f, Color.argb(220, 245, 158, 11))
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 241, 245, 249)
        textSize = 36f
        typeface = Typeface.MONOSPACE
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 2, 6, 23)
    }

    private val matrix = Matrix()
    private val srcRect = Rect()
    private val dstRect = RectF()

    fun renderFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        currentTimeSec: Float,
        totalDurationSec: Float,
        state: InterpolatedFrameState,
        bgBitmap: Bitmap?,
        charBitmap: Bitmap?,
        characterScaleMultiplier: Float = 1.0f,
        showHud: Boolean = true
    ) {
        val scene = state.scene

        // 1. Draw Background
        if (bgBitmap != null && !bgBitmap.isRecycled) {
            drawBackgroundWithCamera(canvas, width, height, bgBitmap, state)
        } else {
            drawMissingBackgroundFallback(canvas, width, height, scene)
        }

        // 2. Draw Character (Resolution normalized so preview & export match identically)
        if (charBitmap != null && !charBitmap.isRecycled) {
            drawCharacterWithTransform(canvas, width, height, charBitmap, state, characterScaleMultiplier)
        } else if (scene.character.isNotBlank()) {
            drawMissingCharacterFallback(canvas, width, height, state, characterScaleMultiplier)
        }

        // 3. Draw Captions
        if (scene.showText || scene.strongWords.isNotEmpty()) {
            drawUrduCaptions(canvas, width, height, scene)
        }

        // 4. Draw HUD (preview only)
        if (showHud) {
            drawHudOverlay(canvas, width, height, currentTimeSec, totalDurationSec, scene)
        }
    }

    private fun drawBackgroundWithCamera(
        canvas: Canvas,
        width: Int,
        height: Int,
        bitmap: Bitmap,
        state: InterpolatedFrameState
    ) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val zoom = max(1.0f, state.cameraZoom)

        val targetAspect = width.toFloat() / height.toFloat()
        val bmpAspect = bw / bh

        val visibleWidth: Float
        val visibleHeight: Float

        if (bmpAspect > targetAspect) {
            visibleHeight = bh / zoom
            visibleWidth = visibleHeight * targetAspect
        } else {
            visibleWidth = bw / zoom
            visibleHeight = visibleWidth / targetAspect
        }

        val halfW = visibleWidth / 2f
        val halfH = visibleHeight / 2f

        val centerX = (state.cameraX * bw).coerceIn(halfW, bw - halfW)
        val centerY = (state.cameraY * bh).coerceIn(halfH, bh - halfH)

        srcRect.set(
            (centerX - halfW).toInt(),
            (centerY - halfH).toInt(),
            (centerX + halfW).toInt(),
            (centerY + halfH).toInt()
        )
        dstRect.set(0f, 0f, width.toFloat(), height.toFloat())

        canvas.drawBitmap(bitmap, srcRect, dstRect, bgPaint)
    }

    private fun drawMissingBackgroundFallback(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: SceneItem
    ) {
        placeholderPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.rgb(15, 23, 42), Color.rgb(30, 41, 59), Color.rgb(15, 23, 42)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), placeholderPaint)
        placeholderPaint.shader = null

        val resFactor = height.toFloat() / BASE_CANVAS_HEIGHT
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 255, 255, 255)
            textSize = 42f * resFactor
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Missing asset: ${scene.background}", width / 2f, height / 2f, notePaint)
    }

    /**
     * Draws character sprite with normalized resolution scaling.
     * `resFactor = height / 1920f` ensures that in preview (e.g. height=960) and
     * export (height=1920) the character has the EXACT same proportion of the screen!
     */
    private fun drawCharacterWithTransform(
        canvas: Canvas,
        width: Int,
        height: Int,
        bitmap: Bitmap,
        state: InterpolatedFrameState,
        scaleMultiplier: Float
    ) {
        val cx = state.characterX * width
        val cy = state.characterY * height

        val resFactor = height.toFloat() / BASE_CANVAS_HEIGHT
        val scale = state.characterScale * scaleMultiplier * state.scene.customScale * resFactor

        val rotation = state.characterRotation
        val alpha = (state.characterOpacity * 255).toInt().coerceIn(0, 255)

        charPaint.alpha = alpha

        matrix.reset()
        val pw = bitmap.width / 2f
        val ph = bitmap.height / 2f

        matrix.postTranslate(-pw, -ph)
        matrix.postScale(scale, scale)
        matrix.postRotate(rotation)
        matrix.postTranslate(cx, cy)

        canvas.drawBitmap(bitmap, matrix, charPaint)
    }

    private fun drawMissingCharacterFallback(
        canvas: Canvas,
        width: Int,
        height: Int,
        state: InterpolatedFrameState,
        scaleMultiplier: Float
    ) {
        val resFactor = height.toFloat() / BASE_CANVAS_HEIGHT
        val cx = state.characterX * width
        val cy = state.characterY * height
        val radius = 120f * resFactor * state.characterScale * scaleMultiplier * state.scene.customScale

        val charGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((state.characterOpacity * 180).toInt(), 16, 185, 129)
            style = Paint.Style.STROKE
            strokeWidth = 6f * resFactor
        }
        canvas.drawCircle(cx, cy - radius, radius * 0.45f, charGhostPaint)
        canvas.drawRoundRect(
            cx - radius * 0.7f,
            cy - radius * 0.5f,
            cx + radius * 0.7f,
            cy + radius,
            24f * resFactor, 24f * resFactor,
            charGhostPaint
        )
    }

    private fun drawUrduCaptions(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: SceneItem
    ) {
        val strongWords = scene.strongWords
        if (strongWords.isEmpty()) return

        val captionText = strongWords.joinToString(" • ")

        val resFactor = height.toFloat() / BASE_CANVAS_HEIGHT
        strongWordPaint.textSize = 72f * resFactor
        captionStrokePaint.strokeWidth = 3f * resFactor

        val textBounds = Rect()
        strongWordPaint.getTextBounds(captionText, 0, captionText.length, textBounds)
        val pillWidth = (textBounds.width() + 140f * resFactor).coerceAtLeast(360f * resFactor).coerceAtMost(width * 0.9f)
        val pillHeight = 120f * resFactor
        val topY = height * 0.10f

        val pillRect = RectF(
            (width - pillWidth) / 2f,
            topY,
            (width + pillWidth) / 2f,
            topY + pillHeight
        )

        val corner = 28f * resFactor
        canvas.drawRoundRect(pillRect, corner, corner, captionBgPaint)
        canvas.drawRoundRect(pillRect, corner, corner, captionStrokePaint)

        val textY = topY + pillHeight / 2f + (textBounds.height() / 2f) - (6f * resFactor)
        canvas.drawText(captionText, width / 2f, textY, strongWordPaint)
    }

    private fun drawHudOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        currentTimeSec: Float,
        totalDurationSec: Float,
        scene: SceneItem
    ) {
        val resFactor = height.toFloat() / BASE_CANVAS_HEIGHT
        hudPaint.textSize = 36f * resFactor

        val pad = 32f * resFactor
        val hudH = 100f * resFactor
        val hudW = width - (pad * 2)
        val hudTop = height - hudH - pad

        val rect = RectF(pad, hudTop, pad + hudW, hudTop + hudH)
        canvas.drawRoundRect(rect, 18f * resFactor, 18f * resFactor, hudBgPaint)

        val curM = (currentTimeSec / 60).toInt()
        val curS = (currentTimeSec % 60).toInt()
        val curMs = ((currentTimeSec % 1) * 100).toInt()

        val totM = (totalDurationSec / 60).toInt()
        val totS = (totalDurationSec % 60).toInt()

        val timeStr = String.format("%02d:%02d.%02d / %02d:%02d", curM, curS, curMs, totM, totS)
        val sceneStr = "${scene.label} (${String.format("%.1f", scene.start)}s - ${String.format("%.1f", scene.end)}s)"

        canvas.drawText(timeStr, pad + (20f * resFactor), hudTop + (42f * resFactor), hudPaint)
        canvas.drawText(sceneStr, pad + (20f * resFactor), hudTop + (82f * resFactor), hudPaint)
    }
}
