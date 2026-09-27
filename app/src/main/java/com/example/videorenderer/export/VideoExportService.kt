package com.example.videorenderer.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import com.example.videorenderer.data.model.TimelineData
import com.example.videorenderer.data.repository.TimelineRepository
import com.example.videorenderer.renderer.TimelineCanvasRenderer
import com.example.videorenderer.renderer.TimelineMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Service that encodes the timeline frames into an MP4 video file using
 * Android MediaCodec (H.264) and MediaMuxer, saving the result into the Downloads directory.
 */
class VideoExportService(
    private val context: Context,
    private val repository: TimelineRepository
) {

    companion object {
        private const val TAG = "VideoExportService"
        private const val MIME_TYPE_VIDEO = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val I_FRAME_INTERVAL = 1 // Keyframe every 1 second
        private const val TIMEOUT_USEC = 10_000L
        private const val MAX_EOS_RETRIES = 15 // Prevents infinite freeze on final frame
    }

    private val renderer = TimelineCanvasRenderer()

    /**
     * Renders the complete timeline to an MP4 video on a background thread.
     */
    suspend fun exportVideo(
        timeline: TimelineData,
        assetsTreeUri: Uri?,
        characterScaleMultiplier: Float = 1.0f,
        onProgress: suspend (progress: Float, currentFrame: Int, totalFrames: Int) -> Unit
    ): Result<Uri> = withContext(Dispatchers.Default) {
        val width = timeline.settings.width
        val height = timeline.settings.height
        val fps = timeline.settings.fpsInt
        val bitrate = timeline.settings.bitrateInt
        val durationSec = timeline.totalDurationSec

        if (durationSec <= 0f) {
            return@withContext Result.failure(IllegalArgumentException("Timeline duration is 0 seconds."))
        }

        // Calculate exact total frames rounded to nearest integer
        val totalFrames = Math.round(durationSec * fps).toInt().coerceAtLeast(1)
        val tempOutputFile = File(context.cacheDir, "temp_render_${System.currentTimeMillis()}.mp4")

        var mediaCodec: MediaCodec? = null
        var mediaMuxer: MediaMuxer? = null
        var inputSurface: Surface? = null
        var videoTrackIndex = -1
        var muxerStarted = false

        // Preload bitmaps for all scenes to avoid I/O bottlenecks during 30 FPS encoding
        val sceneBitmaps = preloadSceneBitmaps(timeline, assetsTreeUri)

        try {
            Log.d(TAG, "Starting MP4 export: ${width}x${height} @ ${fps}fps, totalFrames=$totalFrames, bitrate=$bitrate")

            // 1. Configure Video Format & Encoder
            val format = MediaFormat.createVideoFormat(MIME_TYPE_VIDEO, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE_VIDEO)
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = mediaCodec.createInputSurface()
            mediaCodec.start()

            // 2. Configure Muxer
            mediaMuxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()

            // 3. Render frame by frame
            for (frameIndex in 0 until totalFrames) {
                val currentTimeSec = (frameIndex.toFloat() / fps).coerceAtMost(durationSec)
                val activeScene = timeline.findSceneAt(currentTimeSec) ?: timeline.scenes.first()
                val state = TimelineMath.calculateFrameState(activeScene, currentTimeSec)

                val bgBmp = sceneBitmaps[activeScene.background]
                val charBmp = sceneBitmaps[activeScene.character]

                // Lock Canvas from Surface
                val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        inputSurface.lockHardwareCanvas()
                    } catch (e: Exception) {
                        inputSurface.lockCanvas(null)
                    }
                } else {
                    inputSurface.lockCanvas(null)
                }

                try {
                    // Render frame without HUD overlay in final exported video
                    renderer.renderFrame(
                        canvas = canvas,
                        width = width,
                        height = height,
                        currentTimeSec = currentTimeSec,
                        totalDurationSec = durationSec,
                        state = state,
                        bgBitmap = bgBmp,
                        charBitmap = charBmp,
                        characterScaleMultiplier = characterScaleMultiplier,
                        showHud = false
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Drain encoded frames from codec into muxer
                videoTrackIndex = drainEncoder(
                    mediaCodec = mediaCodec,
                    mediaMuxer = mediaMuxer,
                    bufferInfo = bufferInfo,
                    videoTrackIndex = videoTrackIndex,
                    muxerStarted = muxerStarted,
                    endOfStream = false
                ).also { muxerStarted = true }

                // Update progress callback with strictly clamped frame numbers
                val currentDisplayFrame = (frameIndex + 1).coerceAtMost(totalFrames)
                val progress = currentDisplayFrame.toFloat() / totalFrames
                onProgress(progress, currentDisplayFrame, totalFrames)
            }

            // Signal End of Stream to MediaCodec
            try {
                mediaCodec.signalEndOfInputStream()
            } catch (e: Exception) {
                Log.w(TAG, "Error signaling end of input stream", e)
            }

            // Final drain until EOS buffer arrives (with timeout to prevent freeze!)
            drainEncoder(
                mediaCodec = mediaCodec,
                mediaMuxer = mediaMuxer,
                bufferInfo = bufferInfo,
                videoTrackIndex = videoTrackIndex,
                muxerStarted = muxerStarted,
                endOfStream = true
            )

            // Ensure UI shows 100% completion
            onProgress(1.0f, totalFrames, totalFrames)

            // Stop and release encoding components safely
            try { mediaCodec.stop() } catch (e: Exception) { Log.w(TAG, "Codec stop exception", e) }
            try {
                if (muxerStarted) {
                    mediaMuxer.stop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Muxer stop exception", e)
            }

            // 4. Save MP4 to Downloads folder
            val savedUri = saveToDownloads(tempOutputFile, "rendered_video.mp4")
            tempOutputFile.delete()

            Log.i(TAG, "Video export complete! File saved at: $savedUri")
            Result.success(savedUri)

        } catch (e: Exception) {
            Log.e(TAG, "Error during video export", e)
            tempOutputFile.delete()
            Result.failure(e)
        } finally {
            try { inputSurface?.release() } catch (_: Exception) {}
            try { mediaCodec?.release() } catch (_: Exception) {}
            try { mediaMuxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Drains available output buffers from MediaCodec and writes them to MediaMuxer.
     * Prevents infinite loop at EOS by using MAX_EOS_RETRIES counter.
     */
    private fun drainEncoder(
        mediaCodec: MediaCodec,
        mediaMuxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        videoTrackIndex: Int,
        muxerStarted: Boolean,
        endOfStream: Boolean
    ): Int {
        var currentTrackIndex = videoTrackIndex
        var isStarted = muxerStarted
        var eosRetries = 0

        while (true) {
            val encoderStatus = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break // No data available right now, continue encoding loop
                } else {
                    eosRetries++
                    if (eosRetries >= MAX_EOS_RETRIES) {
                        Log.w(TAG, "EOS drain timed out after $MAX_EOS_RETRIES attempts, exiting drain loop.")
                        break
                    }
                    try {
                        Thread.sleep(10)
                    } catch (_: Exception) {}
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isStarted) {
                    Log.w(TAG, "Format changed after muxer started")
                } else {
                    val newFormat = mediaCodec.outputFormat
                    currentTrackIndex = mediaMuxer.addTrack(newFormat)
                    mediaMuxer.start()
                    isStarted = true
                }
            } else if (encoderStatus >= 0) {
                val encodedData: ByteBuffer? = mediaCodec.getOutputBuffer(encoderStatus)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && isStarted && currentTrackIndex >= 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        try {
                            mediaMuxer.writeSampleData(currentTrackIndex, encodedData, bufferInfo)
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception writing sample data", e)
                        }
                    }

                    mediaCodec.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.i(TAG, "EOS reached successfully.")
                        break
                    }
                }
            }
        }
        return currentTrackIndex
    }

    /**
     * Preloads all required bitmaps in advance to maintain 30 FPS encoding throughput.
     */
    private suspend fun preloadSceneBitmaps(
        timeline: TimelineData,
        assetsTreeUri: Uri?
    ): Map<String, Bitmap?> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, Bitmap?>()
        for (scene in timeline.scenes) {
            if (scene.background.isNotBlank() && !map.containsKey(scene.background)) {
                map[scene.background] = repository.loadBitmapAsset(scene.background, assetsTreeUri)
            }
            if (scene.character.isNotBlank() && !map.containsKey(scene.character)) {
                map[scene.character] = repository.loadBitmapAsset(scene.character, assetsTreeUri)
            }
        }
        map
    }

    /**
     * Saves the encoded video to the Android Downloads folder via MediaStore (Android 10+)
     * or standard Downloads file path (Android 9 and below).
     */
    private fun saveToDownloads(sourceFile: File, targetFileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collectionUri, contentValues)
                ?: throw IllegalStateException("Could not create MediaStore entry in Downloads.")

            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)

            uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val targetFile = File(downloadsDir, targetFileName)

            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { out ->
                    input.copyTo(out)
                }
            }
            Uri.fromFile(targetFile)
        }
    }
}
