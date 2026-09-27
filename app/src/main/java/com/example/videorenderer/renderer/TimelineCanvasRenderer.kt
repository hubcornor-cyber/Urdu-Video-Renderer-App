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
import android.util.Log
import com.example.videorenderer.data.model.SceneItem
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, frame-accurate Android Canvas Renderer.
 * Supports dynamic character scale multiplier (increase/decrease size) and responsive viewport scaling.
 */
class TimelineCanvasRenderer {

    companion object {
        private const val TAG = "TimelineRenderer"
    }

    // Pre-allocated Paint objects to prevent allocations in 30 FPS rendering loop
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val captionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 15, 23, 42) // Dark translucent slate
    }
    private val captionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(160, 16, 185, 129) // Emerald border accent
    }
    private val captionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 64f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 4f, Color.argb(180, 0, 0, 0))
    }
    private val strongWordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(251, 191, 36) // Amber/Gold glow for strong words
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

    /**
     * Renders a single timeline frame to the provided Canvas.
     */
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

        // 1. CLEAR / DRAW BACKGROUND
        if (bgBitmap != null && !bgBitmap.isRecycled) {
            drawBackgroundWithCamera(canvas, width, height, bgBitmap, state)
        } else {
            drawMissingBackgroundFallback(canvas, width, height, scene)
        }

        // 2. DRAW CHARACTER WITH TRANSFORM & SCALE MULTIPLIER
        if (charBitmap != null && !charBitmap.isRecycled) {
            drawCharacterWithTransform(canvas, width, height, charBitmap, state, characterScaleMultiplier)
        } else if (scene.character.isNotBlank()) {
            drawMissingCharacterFallback(canvas, width, height, state, characterScaleMultiplier)
        }

        // 3. DRAW CAPTIONS (URDU STRONG WORDS)
        if (scene.showText || scene.strongWords.isNotEmpty()) {
            drawUrduCaptions(canvas, width, height, scene)
        }

        // 4. DRAW TIMELINE HUD OVERLAY
        if (showHud) {
            drawHudOverlay(canvas, width, height, currentTimeSec, totalDurationSec, scene)
        }
    }

    /**
     * Draws background with camera pan and zoom interpolation.
     */
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

        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 255, 255, 255)
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("پس منظر موجود نہیں: ${scene.background}", width / 2f, height / 2f - 40f, notePaint)
        canvas.drawText("Missing asset: ${scene.background}", width / 2f, height / 2f + 20f, notePaint)
    }

    /**
     * Draws character sprite with translated pivot, scaling, rotation, opacity, and scale multiplier.
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
        val scale = state.characterScale * scaleMultiplier
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
        val cx = state.characterX * width
        val cy = state.characterY * height
        val radius = 120f * state.characterScale * scaleMultiplier

        val charGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((state.characterOpacity * 180).toInt(), 16, 185, 129)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(cx, cy - radius, radius * 0.45f, charGhostPaint)
        canvas.drawRoundRect(
            cx - radius * 0.7f,
            cy - radius * 0.5f,
            cx + radius * 0.7f,
            cy + radius,
            24f, 24f,
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

        val textBounds = Rect()
        strongWordPaint.getTextBounds(captionText, 0, captionText.length, textBounds)
        val pillWidth = (textBounds.width() + 140f).coerceAtLeast(360f).coerceAtMost(width * 0.9f)
        val pillHeight = 120f
        val topY = height * 0.10f

        val pillRect = RectF(
            (width - pillWidth) / 2f,
            topY,
            (width + pillWidth) / 2f,
            topY + pillHeight
        )

        canvas.drawRoundRect(pillRect, 28f, 28f, captionBgPaint)
        canvas.drawRoundRect(pillRect, 28f, 28f, captionStrokePaint)

        val textY = topY + pillHeight / 2f + (textBounds.height() / 2f) - 6f
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
        val pad = 32f
        val hudH = 100f
        val hudW = width - (pad * 2)
        val hudTop = height - hudH - pad

        val rect = RectF(pad, hudTop, pad + hudW, hudTop + hudH)
        canvas.drawRoundRect(rect, 18f, 18f, hudBgPaint)

        val curM = (currentTimeSec / 60).toInt()
        val curS = (currentTimeSec % 60).toInt()
        val curMs = ((currentTimeSec % 1) * 100).toInt()

        val totM = (totalDurationSec / 60).toInt()
        val totS = (totalDurationSec % 60).toInt()

        val timeStr = String.format("%02d:%02d.%02d / %02d:%02d", curM, curS, curMs, totM, totS)
        val sceneStr = "${scene.label} (${String.format("%.1f", scene.start)}s - ${String.format("%.1f", scene.end)}s)"

        canvas.drawText(timeStr, pad + 20f, hudTop + 42f, hudPaint)
        canvas.drawText(sceneStr, pad + 20f, hudTop + 82f, hudPaint)
    }
}
