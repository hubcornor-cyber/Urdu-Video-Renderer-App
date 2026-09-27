package com.example.videorenderer.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videorenderer.data.model.SceneItem
import com.example.videorenderer.data.model.TimelineData
import com.example.videorenderer.data.repository.TimelineRepository
import com.example.videorenderer.export.VideoExportService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI State holding timeline configuration, playback progress, per-scene character scaling,
 * and exported video playback states.
 */
data class TimelineUiState(
    val isLoading: Boolean = false,
    val timeline: TimelineData? = null,
    val jsonFileName: String? = null,
    val assetsFolderName: String? = null,
    val assetsTreeUri: Uri? = null,
    val totalDurationSec: Float = 0f,
    val sceneCount: Int = 0,
    val currentPlaybackTime: Float = 0f,
    val isPlaying: Boolean = false,
    val selectedSceneIndex: Int = 0,
    val characterScaleMultiplier: Float = 1.0f,
    val isRendering: Boolean = false,
    val renderProgress: Float = 0f,
    val currentRenderFrame: Int = 0,
    val totalRenderFrames: Int = 0,
    val exportStatusMessage: String? = null,
    val exportedVideoUri: Uri? = null,
    val isVideoPlayerOpen: Boolean = false,
    val errorMessage: String? = null
) {
    val currentSelectedScene: SceneItem?
        get() = timeline?.scenes?.getOrNull(selectedSceneIndex)
}

class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TimelineViewModel"
    }

    private val repository = TimelineRepository(application)
    private val exportService = VideoExportService(application, repository)

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    init {
        loadSampleTimeline()
    }

    fun loadSampleTimeline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.loadSampleTimeline()
            result.fold(
                onSuccess = { data ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            timeline = data,
                            jsonFileName = "sample_timeline.json (Demo)",
                            totalDurationSec = data.totalDurationSec,
                            sceneCount = data.scenes.size,
                            currentPlaybackTime = 0f,
                            selectedSceneIndex = 0
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load demo timeline: ${error.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun loadTimelineFromUri(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.loadTimelineFromUri(uri)
            result.fold(
                onSuccess = { data ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            timeline = data,
                            jsonFileName = displayName,
                            totalDurationSec = data.totalDurationSec,
                            sceneCount = data.scenes.size,
                            currentPlaybackTime = 0f,
                            selectedSceneIndex = 0
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "JSON parsing error: ${error.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun setAssetsFolderUri(uri: Uri, folderName: String) {
        _uiState.update {
            it.copy(
                assetsTreeUri = uri,
                assetsFolderName = folderName
            )
        }
        repository.clearCache()
    }

    // Select Active Scene for editing
    fun selectScene(index: Int) {
        val total = _uiState.value.sceneCount
        if (index in 0 until total) {
            _uiState.update { it.copy(selectedSceneIndex = index) }
            // Seek playback to scene start time
            _uiState.value.timeline?.scenes?.getOrNull(index)?.let { scene ->
                seekTo(scene.start)
            }
        }
    }

    // PER-SCENE Scale Adjustment (har scene k liye alag image size change)
    fun setScaleForScene(sceneIndex: Int, newScale: Float) {
        val currentTimeline = _uiState.value.timeline ?: return
        val clampedScale = (Math.round(newScale * 10f) / 10f).coerceIn(0.2f, 3.5f)

        val updatedScenes = currentTimeline.scenes.mapIndexed { idx, scene ->
            if (idx == sceneIndex) {
                scene.copy(customScale = clampedScale)
            } else {
                scene
            }
        }

        _uiState.update {
            it.copy(timeline = currentTimeline.copy(scenes = updatedScenes))
        }
    }

    fun increaseCurrentSceneScale() {
        val activeIndex = _uiState.value.selectedSceneIndex
        val currentScale = _uiState.value.timeline?.scenes?.getOrNull(activeIndex)?.customScale ?: 1.0f
        setScaleForScene(activeIndex, currentScale + 0.1f)
    }

    fun decreaseCurrentSceneScale() {
        val activeIndex = _uiState.value.selectedSceneIndex
        val currentScale = _uiState.value.timeline?.scenes?.getOrNull(activeIndex)?.customScale ?: 1.0f
        setScaleForScene(activeIndex, currentScale - 0.1f)
    }

    fun applyScaleToAllScenes(scale: Float) {
        val currentTimeline = _uiState.value.timeline ?: return
        val clampedScale = (Math.round(scale * 10f) / 10f).coerceIn(0.2f, 3.5f)
        val updatedScenes = currentTimeline.scenes.map { it.copy(customScale = clampedScale) }
        _uiState.update {
            it.copy(timeline = currentTimeline.copy(scenes = updatedScenes))
        }
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        val total = _uiState.value.totalDurationSec
        if (total <= 0f) return

        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true) }

        playbackJob = viewModelScope.launch {
            val frameIntervalMs = 33L // ~30 FPS
            val stepSec = 0.033f

            while (isActive && _uiState.value.isPlaying) {
                delay(frameIntervalMs)
                _uiState.update { state ->
                    val nextTime = state.currentPlaybackTime + stepSec
                    if (nextTime >= state.totalDurationSec) {
                        state.copy(currentPlaybackTime = 0f, isPlaying = true)
                    } else {
                        // Also update selectedSceneIndex if scene boundary crossed
                        val activeScene = state.timeline?.findSceneAt(nextTime)
                        val activeIndex = state.timeline?.scenes?.indexOf(activeScene) ?: state.selectedSceneIndex
                        state.copy(
                            currentPlaybackTime = nextTime,
                            selectedSceneIndex = if (activeIndex >= 0) activeIndex else state.selectedSceneIndex
                        )
                    }
                }
            }
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(timeSec: Float) {
        val clamped = timeSec.coerceIn(0f, _uiState.value.totalDurationSec)
        _uiState.update { state ->
            val activeScene = state.timeline?.findSceneAt(clamped)
            val activeIndex = state.timeline?.scenes?.indexOf(activeScene) ?: state.selectedSceneIndex
            state.copy(
                currentPlaybackTime = clamped,
                selectedSceneIndex = if (activeIndex >= 0) activeIndex else state.selectedSceneIndex
            )
        }
    }

    fun openVideoPlayer() {
        if (_uiState.value.exportedVideoUri != null) {
            _uiState.update { it.copy(isVideoPlayerOpen = true) }
        }
    }

    fun closeVideoPlayer() {
        _uiState.update { it.copy(isVideoPlayerOpen = false) }
    }

    /**
     * Renders and exports the full timeline video to MP4 using MediaCodec on a background thread.
     */
    fun exportMp4Video() {
        val timeline = _uiState.value.timeline
        if (timeline == null || timeline.scenes.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please load a timeline JSON first.") }
            return
        }

        pausePlayback()

        viewModelScope.launch {
            val totalFrames = Math.round(timeline.totalDurationSec * timeline.settings.fpsInt).toInt().coerceAtLeast(1)

            _uiState.update {
                it.copy(
                    isRendering = true,
                    renderProgress = 0f,
                    currentRenderFrame = 0,
                    totalRenderFrames = totalFrames,
                    exportStatusMessage = "Encoding video @ 30 FPS...",
                    errorMessage = null,
                    exportedVideoUri = null,
                    isVideoPlayerOpen = false
                )
            }

            val result = exportService.exportVideo(
                timeline = timeline,
                assetsTreeUri = _uiState.value.assetsTreeUri,
                characterScaleMultiplier = 1.0f,
                onProgress = { progress, currentFrame, total ->
                    _uiState.update {
                        it.copy(
                            renderProgress = progress.coerceIn(0f, 1f),
                            currentRenderFrame = currentFrame.coerceAtMost(total),
                            totalRenderFrames = total
                        )
                    }
                }
            )

            result.fold(
                onSuccess = { uri ->
                    _uiState.update {
                        it.copy(
                            isRendering = false,
                            renderProgress = 1.0f,
                            currentRenderFrame = totalFrames,
                            exportStatusMessage = "Export complete! Saved to Downloads.",
                            exportedVideoUri = uri,
                            isVideoPlayerOpen = true // Automatically open player so user can watch immediately!
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Export failed", error)
                    _uiState.update {
                        it.copy(
                            isRendering = false,
                            errorMessage = "Export error: ${error.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun getRepository(): TimelineRepository = repository
}
