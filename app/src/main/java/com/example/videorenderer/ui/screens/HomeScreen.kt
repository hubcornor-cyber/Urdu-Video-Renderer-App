package com.example.videorenderer.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videorenderer.ui.theme.AmberAccent
import com.example.videorenderer.ui.theme.DarkBorder
import com.example.videorenderer.ui.theme.DarkCard
import com.example.videorenderer.ui.theme.EmeraldPrimary
import com.example.videorenderer.ui.theme.RoseError
import com.example.videorenderer.ui.theme.TextMuted
import com.example.videorenderer.ui.theme.TextPrimary
import com.example.videorenderer.ui.theme.TextSecondary
import com.example.videorenderer.viewmodel.TimelineViewModel

@Composable
fun HomeScreen(
    viewModel: TimelineViewModel,
    onNavigateToPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // SAF Picker for JSON file
    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "timeline.json"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
            viewModel.loadTimelineFromUri(uri, fileName)
        }
    }

    // SAF Picker for Assets directory
    val assetsFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val folderName = uri.lastPathSegment ?: "Assets Folder"
            viewModel.setAssetsFolderUri(uri, folderName)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Urdu Title & App Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldPrimary.copy(alpha = 0.2f))
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Renderer Logo",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ویڈیو رینڈرنگ سسٹم",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Native Android Jetpack Compose Video Engine",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = "JSON ٹائم لائن اور اثاثہ جات سے MP4 ویڈیو تیار کریں",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // STATUS & FILE BADGES CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "فعال فائلز کی تفصیلات (Active Configuration)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // JSON file status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = if (uiState.timeline != null) EmeraldPrimary else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "JSON فائل:",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Text(
                                text = uiState.jsonFileName ?: "کوئی فائل منتخب نہیں",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Assets folder status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (uiState.assetsTreeUri != null) AmberAccent else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "اثاثہ جات فولڈر (Assets):",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Text(
                                text = uiState.assetsFolderName ?: "ڈیفالٹ ایپ اثاثہ جات (assets/)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Duration and Scenes Counter Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "کل دورانیہ (Duration)",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "${String.format("%.1f", uiState.totalDurationSec)} سیکنڈ",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "مناظر کی تعداد (Scenes)",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "${uiState.sceneCount} مناظر",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberAccent
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // PICKER ACTION BUTTONS
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Load JSON Button
                Button(
                    onClick = { jsonPickerLauncher.launch("application/json") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = EmeraldPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "جے ایس او این لوڈ کریں (Load JSON)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                // 2. Select Assets Folder Button
                Button(
                    onClick = { assetsFolderPickerLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = AmberAccent
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "اثاثہ جات فولڈر منتخب کریں (Select Assets Folder)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                // Reset to Demo JSON Button
                OutlinedButton(
                    onClick = { viewModel.loadSampleTimeline() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ڈیمو ٹائم لائن بحال کریں (Reload Demo)",
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // RENDERING PROGRESS BAR & STATUS
            AnimatedVisibility(visible = uiState.isRendering) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ویڈیو رینڈر ہو رہی ہے...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary
                            )
                            Text(
                                text = "${(uiState.renderProgress * 100).toInt()}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { uiState.renderProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmeraldPrimary,
                            trackColor = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "فریم: ${uiState.currentRenderFrame} / ${uiState.totalRenderFrames} (MediaCodec H.264 @ 30 FPS)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // SUCCESS EXPORT MESSAGE
            AnimatedVisibility(visible = uiState.exportStatusMessage != null && !uiState.isRendering) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(1.dp, EmeraldPrimary, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.exportStatusMessage ?: "",
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                }
            }

            // ERROR MESSAGE
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(1.dp, RoseError, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RoseError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                }
            }

            // PRIMARY ACTION BUTTONS: PREVIEW & EXPORT
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Preview Button
                Button(
                    onClick = onNavigateToPreview,
                    enabled = (uiState.timeline != null && !uiState.isRendering),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)) // Sky Blue
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "پیش نظارہ (Preview Timeline)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Render & Export MP4 Button
                Button(
                    onClick = { viewModel.exportMp4Video() },
                    enabled = (uiState.timeline != null && !uiState.isRendering),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    if (uiState.isRendering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ویڈیو رینڈر ہو رہی ہے...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color(0xFF022C22)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ویڈیو رینڈر اور برآمد کریں (Render & Export MP4)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF022C22)
                        )
                    }
                }
            }
        }
    }
}
