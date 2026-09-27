package com.example.videorenderer.renderer

import com.example.videorenderer.data.model.CameraConfig
import com.example.videorenderer.data.model.CharacterTransformConfig
import com.example.videorenderer.data.model.SceneItem

/**
 * Calculated interpolated state of a scene at a specific fractional progress (0.0 .. 1.0).
 */
data class InterpolatedFrameState(
    val scene: SceneItem,
    val progress: Float,
    val cameraX: Float,
    val cameraY: Float,
    val cameraZoom: Float,
    val characterX: Float,
    val characterY: Float,
    val characterScale: Float,
    val characterRotation: Float,
    val characterOpacity: Float
)

/**
 * Mathematical utilities and interpolation functions for timeline rendering.
 */
object TimelineMath {

    /**
     * Standard linear interpolation.
     */
    fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction.coerceIn(0f, 1f)
    }

    /**
     * Smooth Hermite ease-in-out curve for cinematic camera motion.
     */
    fun smoothStep(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Evaluates the interpolated transform and camera state for a scene at a given timestamp.
     *
     * @param scene The active scene configuration.
     * @param currentTimeSec Current playback position in seconds.
     * @return InterpolatedFrameState containing pan, zoom, character positions, and alpha.
     */
    fun calculateFrameState(scene: SceneItem, currentTimeSec: Float): InterpolatedFrameState {
        val sceneDuration = scene.duration
        val rawFraction = (currentTimeSec - scene.start) / sceneDuration
        val fraction = rawFraction.coerceIn(0f, 1f)
        val smoothFraction = smoothStep(fraction)

        val cam: CameraConfig = scene.camera
        // Interpolate camera pan & zoom over time
        val cameraX = lerp(cam.startX, cam.endX, smoothFraction)
        val cameraY = lerp(cam.startY, cam.endY, smoothFraction)
        val cameraZoom = lerp(cam.startZoom, cam.endZoom, smoothFraction)

        val charT: CharacterTransformConfig = scene.characterTransform
        // Interpolate character transform
        val charX = lerp(charT.startX, charT.endX, smoothFraction)
        val charY = lerp(charT.startY, charT.endY, smoothFraction)
        val charScale = lerp(charT.startScale, charT.endScale, smoothFraction)
        val charRot = lerp(charT.startRotation, charT.endRotation, smoothFraction)

        // Handle entrance effects (e.g. fade-in during the first 25% of scene duration)
        val entranceOpacity = when (charT.entrance.lowercase()) {
            "fade-in" -> {
                val fadeInDurationRatio = 0.25f
                if (fraction < fadeInDurationRatio) {
                    (fraction / fadeInDurationRatio).coerceIn(0f, 1f)
                } else {
                    1f
                }
            }
            else -> 1f
        }
        val finalOpacity = (charT.opacity * entranceOpacity).coerceIn(0f, 1f)

        return InterpolatedFrameState(
            scene = scene,
            progress = fraction,
            cameraX = cameraX,
            cameraY = cameraY,
            cameraZoom = cameraZoom,
            characterX = charX,
            characterY = charY,
            characterScale = charScale,
            characterRotation = charRot,
            characterOpacity = finalOpacity
        )
    }
}
