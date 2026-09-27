package com.example.videorenderer.data.model

import com.google.gson.annotations.SerializedName

/**
 * Root data model representing the video rendering configuration and scene timeline.
 */
data class TimelineData(
    @SerializedName("scenes")
    val scenes: List<SceneItem> = emptyList(),

    @SerializedName("settings")
    val settings: SettingsConfig = SettingsConfig()
) {
    val totalDurationSec: Float
        get() = scenes.maxOfOrNull { it.end } ?: 0f

    fun findSceneAt(timeSec: Float): SceneItem? {
        if (scenes.isEmpty()) return null
        return scenes.firstOrNull { timeSec >= it.start && timeSec < it.end }
            ?: if (timeSec >= totalDurationSec && scenes.isNotEmpty()) scenes.last() else scenes.firstOrNull()
    }
}

/**
 * Configuration for a single scene in the timeline.
 * Supports individual per-scene custom character scaling.
 */
data class SceneItem(
    @SerializedName("start")
    val start: Float = 0f,

    @SerializedName("end")
    val end: Float = 4f,

    @SerializedName("label")
    val label: String = "Scene",

    @SerializedName("background")
    val background: String = "",

    @SerializedName("character")
    val character: String = "",

    @SerializedName("captionStyle")
    val captionStyle: String = "hook",

    @SerializedName("strongWords")
    val strongWords: List<String> = emptyList(),

    @SerializedName("showText")
    val showText: Boolean = false,

    @SerializedName("camera")
    val camera: CameraConfig = CameraConfig(),

    @SerializedName("characterTransform")
    val characterTransform: CharacterTransformConfig = CharacterTransformConfig(),

    @SerializedName("customScale")
    val customScale: Float = 1.0f
) {
    val duration: Float
        get() = (end - start).coerceAtLeast(0.001f)
}

/**
 * Camera motion and pan/zoom coordinates (normalized 0.0 to 1.0).
 */
data class CameraConfig(
    @SerializedName("effect")
    val effect: String = "custom",

    @SerializedName("startX")
    val startX: Float = 0.5f,

    @SerializedName("endX")
    val endX: Float = 0.5f,

    @SerializedName("startY")
    val startY: Float = 0.5f,

    @SerializedName("endY")
    val endY: Float = 0.5f,

    @SerializedName("startZoom")
    val startZoom: Float = 1.0f,

    @SerializedName("endZoom")
    val endZoom: Float = 1.15f
)

/**
 * Character position, scale, rotation, and animation parameters.
 */
data class CharacterTransformConfig(
    @SerializedName("startX")
    val startX: Float = 0.5f,

    @SerializedName("endX")
    val endX: Float = 0.5f,

    @SerializedName("startY")
    val startY: Float = 0.75f,

    @SerializedName("endY")
    val endY: Float = 0.75f,

    @SerializedName("startScale")
    val startScale: Float = 1.0f,

    @SerializedName("endScale")
    val endScale: Float = 1.2f,

    @SerializedName("startRotation")
    val startRotation: Float = 0.0f,

    @SerializedName("endRotation")
    val endRotation: Float = 0.0f,

    @SerializedName("entrance")
    val entrance: String = "fade-in",

    @SerializedName("opacity")
    val opacity: Float = 1.0f
)

/**
 * Output video render settings.
 */
data class SettingsConfig(
    @SerializedName("resolution")
    val resolution: String = "1080x1920",

    @SerializedName("fps")
    val fps: String = "30",

    @SerializedName("quality")
    val quality: String = "8000000",

    @SerializedName("format")
    val format: String = "mp4",

    @SerializedName("voiceVolume")
    val voiceVolume: String = "1.0",

    @SerializedName("musicVolume")
    val musicVolume: String = "0.25"
) {
    val width: Int
        get() = try {
            resolution.split("x")[0].trim().toInt()
        } catch (e: Exception) {
            1080
        }

    val height: Int
        get() = try {
            resolution.split("x")[1].trim().toInt()
        } catch (e: Exception) {
            1920
        }

    val fpsInt: Int
        get() = try {
            fps.trim().toInt()
        } catch (e: Exception) {
            30
        }

    val bitrateInt: Int
        get() = try {
            quality.trim().toInt()
        } catch (e: Exception) {
            8_000_000
        }
}
