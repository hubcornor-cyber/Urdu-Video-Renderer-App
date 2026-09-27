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

class TimelineCanvasRenderer {

    companion object {
        private const val BASE_CANVAS_HEIGHT = 1920f
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val captionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 15, 23, 42) }
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
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 2, 6, 23) }

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
        if (width <= 0 || height <= 0) return

        val scene = state.scene

        // 1. Draw Background
        if (bgBitmap != null && !bgBitmap.isRecycled) {
            drawBackgroundWithCamera(canvas, width, height, bgBitmap, state)
        } else {
            drawMissingBackgroundFallback(canvas, width, height, scene)
        }

        // 2. Draw Character
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
        if (bw <= 0 || bh <= 0) return

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

        val minX = halfW
        val maxX = max(minX, bw - halfW)
        val minY = halfH
        val maxY = max(minY, bh - halfH)

        val centerX = if (minX >= maxX) bw / 2f else (state.cameraX * bw).coerceIn(minX, maxX)
        val centerY = if (minY >= maxY) bh / 2f else (state.cameraY * bh).coerceIn(minY, maxY)

        val left = (centerX - halfW).toInt().coerceIn(0, max(0, bw.toInt() - 1))
        val top = (centerY - halfH).toInt().coerceIn(0, max(0, bh.toInt() - 1))
        val right = (centerX + halfW).toInt().coerceIn(left + 1, bw.toInt())
        val bottom = (centerY + halfH).toInt().coerceIn(top + 1, bh.toInt())

        srcRect.set(left, top, right, bottom)
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
            textSize = max(24f, 42f * resFactor)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Background: ${scene.background}", width / 2f, height / 2f, notePaint)
    }

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
        val radius = max(20f, 120f * resFactor * state.characterScale * scaleMultiplier * state.scene.customScale)

        val charGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((state.characterOpacity * 180).toInt(), 16, 185, 129)
            style = Paint.Style.STROKE
            strokeWidth = max(2f, 6f * resFactor)
        }
        canvas.drawCircle(cx, cy - radius, radius * 0.45f, charGhostPaint)
        canvas.drawRoundRect(
            cx - radius * 0.7f,
            cy - radius * 0.5f,
            cx + radius * 0.7f,
            cy + radius,
            max(8f, 24f * resFactor), max(8f, 24f * resFactor),
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
        strongWordPaint.textSize = max(28f, 72f * resFactor)
        captionStrokePaint.strokeWidth = max(1.5f, 3f * resFactor)

        val textBounds = Rect()
        strongWordPaint.getTextBounds(captionText, 0, captionText.length, textBounds)
        val pillWidth = (textBounds.width() + 140f * resFactor).coerceAtLeast(240f).coerceAtMost(width * 0.9f)
        val pillHeight = max(60f, 120f * resFactor)
        val topY = height * 0.10f

        val pillRect = RectF(
            (width - pillWidth) / 2f,
            topY,
            (width + pillWidth) / 2f,
            topY + pillHeight
        )

        val corner = max(12f, 28f * resFactor)
        canvas.drawRoundRect(pillRect, corner, corner, captionBgPaint)
        canvas.drawRoundRect(pillRect, corner, corner, captionStrokePaint)

        val textY = topY + pillHeight / 2f + (textBounds.height() / 2f) - (4f * resFactor)
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
        hudPaint.textSize = max(18f, 36f * resFactor)

        val pad = max(12f, 32f * resFactor)
        val hudH = max(60f, 100f * resFactor)
        val hudW = width - (pad * 2)
        val hudTop = height - hudH - pad

        val rect = RectF(pad, hudTop, pad + hudW, hudTop + hudH)
        canvas.drawRoundRect(rect, max(8f, 18f * resFactor), max(8f, 18f * resFactor), hudBgPaint)

        val curM = (currentTimeSec / 60).toInt()
        val curS = (currentTimeSec % 60).toInt()
        val curMs = ((currentTimeSec % 1) * 100).toInt()

        val totM = (totalDurationSec / 60).toInt()
        val totS = (totalDurationSec % 60).toInt()

        val timeStr = String.format("%02d:%02d.%02d / %02d:%02d", curM, curS, curMs, totM, totS)
        val sceneStr = "${scene.label} (${String.format("%.1f", scene.start)}s - ${String.format("%.1f", scene.end)}s)"

        canvas.drawText(timeStr, pad + max(8f, 20f * resFactor), hudTop + max(24f, 42f * resFactor), hudPaint)
        canvas.drawText(sceneStr, pad + max(8f, 20f * resFactor), hudTop + max(48f, 82f * resFactor), hudPaint)
    }
}
