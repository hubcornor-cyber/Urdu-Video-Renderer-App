package com.example.videorenderer.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
            .safeDrawingPadding(), // Ensures screen top & bottom content never go off-screen
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP BAR (Compact, within safe area)
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
                        contentDescription = "واپس (Back)",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "پیش نظارہ کینوس (30 FPS)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${timeline?.settings?.resolution ?: "1080x1920"} • سائز: ${String.format("%.1f", uiState.characterScaleMultiplier)}x",
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFF022C22),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "برآمد",
                        fontSize = 11.sp,
                        color = Color(0xFF022C22),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // RESPONSIVE CANVAS CONTAINER (Fits inside remaining height without pushing menu off-screen)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight

                // Calculate optimal 9:16 aspect ratio box that fits within bounds
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
                                    characterScaleMultiplier = uiState.characterScaleMultiplier,
                                    showHud = true
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "کوئی منظر دستیاب نہیں",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // CHARACTER SCALE QUICK BAR (Live size adjustment while watching)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "کردار: ${String.format("%.1f", uiState.characterScaleMultiplier)}x",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.decreaseCharacterScale() },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "چھوٹا",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.increaseCharacterScale() },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "بڑا",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // BOTTOM PLAYBACK CONTROLS PANEL (Guaranteed to stay on screen)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Scrubber Slider
                    Slider(
                        value = uiState.currentPlaybackTime,
                        onValueChange = { newTime -> viewModel.seekTo(newTime) },
                        valueRange = 0f..uiState.totalDurationSec.coerceAtLeast(0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldPrimary,
                            activeTrackColor = EmeraldPrimary,
                            inactiveTrackColor = DarkBorder
                        )
                    )

                    // Control Buttons (Replay, Play/Pause)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { viewModel.seekTo(0f) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "دوبارہ چلائیں (Replay)",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = { viewModel.togglePlayback() },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "روکیں (Pause)" else "چلائیں (Play)",
                                tint = Color(0xFF022C22),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
