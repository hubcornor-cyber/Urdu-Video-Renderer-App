package com.example.videorenderer.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
 * UI State holding timeline configuration, playback progress, character scale, and video export status.
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
    val characterScaleMultiplier: Float = 1.0f, // Option to increase / decrease character size
    val isRendering: Boolean = false,
    val renderProgress: Float = 0f,
    val currentRenderFrame: Int = 0,
    val totalRenderFrames: Int = 0,
    val exportStatusMessage: String? = null,
    val exportedVideoUri: Uri? = null,
    val errorMessage: String? = null
)

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
                            jsonFileName = "sample_timeline.json (ڈیمو فائل)",
                            totalDurationSec = data.totalDurationSec,
                            sceneCount = data.scenes.size,
                            currentPlaybackTime = 0f
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "ڈیمو فائل لوڈ نہیں ہو سکی: ${error.localizedMessage}"
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
                            currentPlaybackTime = 0f
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "JSON فائل کو پارس کرنے میں خرابی: ${error.localizedMessage}"
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

    // Character Scale controls (Increase / Decrease character size)
    fun increaseCharacterScale() {
        _uiState.update {
            val newScale = (it.characterScaleMultiplier + 0.1f).coerceAtMost(3.0f)
            it.copy(characterScaleMultiplier = Math.round(newScale * 10f) / 10f)
        }
    }

    fun decreaseCharacterScale() {
        _uiState.update {
            val newScale = (it.characterScaleMultiplier - 0.1f).coerceAtLeast(0.3f)
            it.copy(characterScaleMultiplier = Math.round(newScale * 10f) / 10f)
        }
    }

    fun setCharacterScale(scale: Float) {
        _uiState.update {
            it.copy(characterScaleMultiplier = scale.coerceIn(0.3f, 3.0f))
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
                        state.copy(currentPlaybackTime = nextTime)
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
        _uiState.update { it.copy(currentPlaybackTime = clamped) }
    }

    /**
     * Renders and exports the full timeline video to MP4 using MediaCodec on a background thread.
     */
    fun exportMp4Video() {
        val timeline = _uiState.value.timeline
        if (timeline == null || timeline.scenes.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "براہ کرم پہلے ٹائم لائن JSON لوڈ کریں") }
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
                    exportStatusMessage = "ویڈیو رینڈر ہو رہی ہے...",
                    errorMessage = null,
                    exportedVideoUri = null
                )
            }

            val result = exportService.exportVideo(
                timeline = timeline,
                assetsTreeUri = _uiState.value.assetsTreeUri,
                characterScaleMultiplier = _uiState.value.characterScaleMultiplier,
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
                            exportStatusMessage = "ویڈیو کامیابی سے برآمد ہو گئی! (Downloads/rendered_video.mp4)",
                            exportedVideoUri = uri
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Export failed", error)
                    _uiState.update {
                        it.copy(
                            isRendering = false,
                            errorMessage = "برآمد کرنے میں خرابی: ${error.localizedMessage}"
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
