package com.example.videorenderer.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.collection.LruCache
import androidx.documentfile.provider.DocumentFile
import com.example.videorenderer.data.model.TimelineData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader

/**
 * Repository responsible for loading JSON timeline configurations, resolving asset files
 * from device storage / SAF tree URIs / local assets, and caching decoded Bitmaps.
 */
class TimelineRepository(private val context: Context) {

    companion object {
        private const val TAG = "TimelineRepository"
    }

    private val gson = Gson()

    // In-memory LRU bitmap cache (up to 64MB)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtMost(64 * 1024)
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    /**
     * Loads the default sample timeline from packaged assets.
     */
    suspend fun loadSampleTimeline(): Result<TimelineData> = withContext(Dispatchers.IO) {
        try {
            context.assets.open("sample_timeline.json").use { stream ->
                val reader = InputStreamReader(stream)
                val data = gson.fromJson(reader, TimelineData::class.java)
                Result.success(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sample_timeline.json", e)
            Result.failure(e)
        }
    }

    /**
     * Parses a JSON timeline from a user-selected SAF Document Uri.
     */
    suspend fun loadTimelineFromUri(uri: Uri): Result<TimelineData> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val reader = InputStreamReader(stream)
                val data = gson.fromJson(reader, TimelineData::class.java)
                Result.success(data)
            } ?: Result.failure(IllegalStateException("Could not open input stream from uri: $uri"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON from uri: $uri", e)
            Result.failure(e)
        }
    }

    /**
     * Parses a JSON timeline directly from raw string content.
     */
    suspend fun parseTimelineJson(jsonString: String): Result<TimelineData> = withContext(Dispatchers.Default) {
        try {
            val data = gson.fromJson(jsonString, TimelineData::class.java)
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse json string", e)
            Result.failure(e)
        }
    }

    /**
     * Resolves and decodes an image asset bitmap given its relative path (e.g. "assets/bg-1.jpg").
     * Checks:
     * 1. Cache
     * 2. User-selected Assets Tree Uri (if provided)
     * 3. App packaged assets
     * 4. Graceful fallback with warning
     */
    suspend fun loadBitmapAsset(relativePath: String, assetsTreeUri: Uri?): Bitmap? = withContext(Dispatchers.IO) {
        if (relativePath.isBlank()) return@withContext null

        val cacheKey = "${assetsTreeUri?.toString() ?: "app"}::$relativePath"
        bitmapCache.get(cacheKey)?.let { return@withContext it }

        // Normalize filename, e.g. "assets/bg-1.jpg" -> "bg-1.jpg"
        val fileName = File(relativePath).name

        var loadedBitmap: Bitmap? = null

        // 1. Try finding file in user-selected folder via DocumentFile
        if (assetsTreeUri != null) {
            try {
                val treeDoc = DocumentFile.fromTreeUri(context, assetsTreeUri)
                val targetDoc = treeDoc?.findFile(fileName)
                    ?: treeDoc?.findFile(relativePath)
                if (targetDoc != null && targetDoc.exists()) {
                    context.contentResolver.openInputStream(targetDoc.uri)?.use { stream ->
                        loadedBitmap = BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load asset '$relativePath' from treeUri $assetsTreeUri", e)
            }
        }

        // 2. Try loading from app packaged assets
        if (loadedBitmap == null) {
            try {
                // Try stripped name and original path
                val pathsToTry = listOf(relativePath, "assets/$fileName", fileName)
                for (p in pathsToTry) {
                    try {
                        context.assets.open(p).use { stream ->
                            loadedBitmap = BitmapFactory.decodeStream(stream)
                        }
                        if (loadedBitmap != null) break
                    } catch (_: Exception) {
                        // try next candidate
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load asset '$relativePath' from app assets", e)
            }
        }

        if (loadedBitmap != null) {
            bitmapCache.put(cacheKey, loadedBitmap!!)
        } else {
            Log.w(TAG, "Graceful fallback: Asset '$relativePath' was not found in storage or assets. Will render placeholder.")
        }

        loadedBitmap
    }

    /**
     * Clears cached bitmaps to free up device memory.
     */
    fun clearCache() {
        bitmapCache.evictAll()
    }
}
