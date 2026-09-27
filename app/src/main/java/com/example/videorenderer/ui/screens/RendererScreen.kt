package com.example.videorenderer.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videorenderer.renderer.TimelineCanvasRenderer
import com.example.videorenderer.renderer.TimelineMath
import com.example.videorenderer.ui.theme.DarkBorder
import com.example.videorenderer.ui.theme.DarkCard
import com.example.videorenderer.ui.theme.EmeraldPrimary
import com.example.videorenderer.ui.theme.TextMuted
import com.example.videorenderer.ui.theme.TextPrimary
import com.example.videorenderer.ui.theme.TextSecondary
import com.example.videorenderer.viewmodel.TimelineViewModel

@Composable
fun RendererScreen(
    viewModel: TimelineViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val timeline = uiState.timeline
    val renderer = remember { TimelineCanvasRenderer() }
    val repository = remember { viewModel.getRepository() }

    // Cached bitmaps in Compose state
    val bitmapCache = remember { mutableStateMapOf<String, Bitmap?>() }

    // Preload scene assets whenever timeline changes
    LaunchedEffect(timeline, uiState.assetsTreeUri) {
        if (timeline != null) {
            for (scene in timeline.scenes) {
                if (scene.background.isNotBlank() && !bitmapCache.containsKey(scene.background)) {
                    bitmapCache[scene.background] = repository.loadBitmapAsset(scene.background, uiState.assetsTreeUri)
                }
                if (scene.character.isNotBlank() && !bitmapCache.containsKey(scene.character)) {
                    bitmapCache[scene.character] = repository.loadBitmapAsset(scene.character, uiState.assetsTreeUri)
                }
            }
        }
    }

    // Auto-start 30 FPS playback upon entering preview
    LaunchedEffect(Unit) {
        viewModel.startPlayback()
    }

    // Pause playback when leaving preview
    DisposableEffect(Unit) {
        onDispose {
            viewModel.pausePlayback()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "واپس (Back)",
                        tint = TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "پیش نظارہ کینوس (30 FPS)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${timeline?.settings?.resolution ?: "1080x1920"} @ 30 FPS",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Button(
                    onClick = {
                        viewModel.exportMp4Video()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFF022C22),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "برآمد (Export)",
                        fontSize = 12.sp,
                        color = Color(0xFF022C22),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // MAIN VIDEO CANVAS (9:16 vertical ratio)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    if (timeline != null && timeline.scenes.isNotEmpty()) {
                        val activeScene = timeline.findSceneAt(uiState.currentPlaybackTime) ?: timeline.scenes.first()
                        val frameState = TimelineMath.calculateFrameState(activeScene, uiState.currentPlaybackTime)

                        val bgBmp = bitmapCache[activeScene.background]
                        val charBmp = bitmapCache[activeScene.character]

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width.toInt()
                            val canvasHeight = size.height.toInt()

                            drawContext.canvas.nativeCanvas.let { nativeCanvas ->
                                renderer.renderFrame(
                                    canvas = nativeCanvas,
                                    width = canvasWidth,
                                    height = canvasHeight,
                                    currentTimeSec = uiState.currentPlaybackTime,
                                    totalDurationSec = uiState.totalDurationSec,
                                    state = frameState,
                                    bgBitmap = bgBmp,
                                    charBitmap = charBmp,
                                    showHud = true
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "کوئی منظر دستیاب نہیں",
                                color = TextMuted,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // BOTTOM PLAYBACK CONTROLS PANEL
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Time and Scene indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val curSec = uiState.currentPlaybackTime
                        val totSec = uiState.totalDurationSec
                        val activeScene = timeline?.findSceneAt(curSec)

                        Text(
                            text = activeScene?.label ?: "منظر نامہ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = EmeraldPrimary
                        )

                        Text(
                            text = String.format("%02d:%02d / %02d:%02d",
                                (curSec / 60).toInt(), (curSec % 60).toInt(),
                                (totSec / 60).toInt(), (totSec % 60).toInt()
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }

                    // Scrubber Slider
                    Slider(
                        value = uiState.currentPlaybackTime,
                        onValueChange = { newTime -> viewModel.seekTo(newTime) },
                        valueRange = 0f..uiState.totalDurationSec.coerceAtLeast(0.1f),
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldPrimary,
                            activeTrackColor = EmeraldPrimary,
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )

                    // Control Buttons (Replay, Play/Pause)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Replay to 0
                        IconButton(
                            onClick = { viewModel.seekTo(0f) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334155))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "دوبارہ چلائیں (Replay)",
                                tint = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // Play / Pause Toggle
                        IconButton(
                            onClick = { viewModel.togglePlayback() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "روکیں (Pause)" else "چلائیں (Play)",
                                tint = Color(0xFF022C22),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
