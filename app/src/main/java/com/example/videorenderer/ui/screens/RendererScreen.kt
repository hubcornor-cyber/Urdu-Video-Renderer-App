package com.example.videorenderer.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
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
import com.example.videorenderer.ui.theme.AmberAccent
import com.example.videorenderer.ui.theme.DarkBorder
import com.example.videorenderer.ui.theme.EmeraldPrimary
import com.example.videorenderer.ui.theme.TextMuted
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

    LaunchedEffect(Unit) {
        viewModel.startPlayback()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.pausePlayback()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Canvas Preview (30 FPS)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val activeScene = uiState.currentSelectedScene
                    Text(
                        text = "Scene ${uiState.selectedSceneIndex + 1} • Size: ${String.format("%.1f", activeScene?.customScale ?: 1.0f)}x",
                        fontSize = 10.sp,
                        color = EmeraldPrimary
                    )
                }

                Button(
                    onClick = {
                        viewModel.exportMp4Video()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFF022C22),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Export",
                        fontSize = 12.sp,
                        color = Color(0xFF022C22),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // RESPONSIVE CANVAS CONTAINER (Fits on screen without clipping)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight

                val targetAspect = 9f / 16f
                val boxWidth = minOf(availableWidth, availableHeight * targetAspect)
                val boxHeight = boxWidth / targetAspect

                Box(
                    modifier = Modifier
                        .size(width = boxWidth, height = boxHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, DarkBorder, RoundedCornerShape(14.dp))
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
                                    characterScaleMultiplier = 1.0f,
                                    showHud = true
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No Scenes Available",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // PER-SCENE CHIPS & SCALE CONTROLS (Live adjustment during playback)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                val scenes = uiState.timeline?.scenes ?: emptyList()
                if (scenes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        scenes.forEachIndexed { index, scene ->
                            val isSelected = index == uiState.selectedSceneIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.surface)
                                    .border(1.dp, if (isSelected) EmeraldPrimary else DarkBorder, RoundedCornerShape(6.dp))
                                    .clickable { viewModel.selectScene(index) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "S${index + 1}: ${String.format("%.1f", scene.customScale)}x",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF022C22) else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Scale +/- for active scene
                val activeScene = uiState.currentSelectedScene
                val activeScale = activeScene?.customScale ?: 1.0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Scene ${uiState.selectedSceneIndex + 1} Size: ${String.format("%.1f", activeScale)}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.decreaseCurrentSceneScale() },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { viewModel.increaseCurrentSceneScale() },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // BOTTOM PLAYBACK CONTROLS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val curSec = uiState.currentPlaybackTime
                        val totSec = uiState.totalDurationSec
                        val activeScene = timeline?.findSceneAt(curSec)

                        Text(
                            text = activeScene?.label ?: "Scene",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = EmeraldPrimary,
                            maxLines = 1
                        )

                        Text(
                            text = String.format("%02d:%02d / %02d:%02d",
                                (curSec / 60).toInt(), (curSec % 60).toInt(),
                                (totSec / 60).toInt(), (totSec % 60).toInt()
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Slider(
                        value = uiState.currentPlaybackTime,
                        onValueChange = { newTime -> viewModel.seekTo(newTime) },
                        valueRange = 0f..uiState.totalDurationSec.coerceAtLeast(0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldPrimary,
                            activeTrackColor = EmeraldPrimary,
                            inactiveTrackColor = DarkBorder
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { viewModel.seekTo(0f) },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "Replay",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = { viewModel.togglePlayback() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                tint = Color(0xFF022C22),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
