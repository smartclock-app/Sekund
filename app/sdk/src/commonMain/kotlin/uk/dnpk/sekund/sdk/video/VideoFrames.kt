package uk.dnpk.sekund.sdk.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.flow.Flow

/**
 * Shows live [frames] (e.g. from [mjpegFrames]) as a video, letterboxed to [aspectRatio].
 * Shows [label] (e.g. the camera's name) with "Connecting…" until the first frame arrives.
 *
 * The frames are collected only while this is on screen, so the camera stream is open only then.
 */
@Composable
fun VideoFrames(frames: Flow<ImageBitmap>, aspectRatio: Float, label: String, modifier: Modifier = Modifier) {
    var latest by remember(frames) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frames) {
        frames.collect { frame -> latest = frame }
    }

    Box(modifier = modifier.aspectRatio(aspectRatio), contentAlignment = Alignment.Center) {
        val frame = latest
        if (frame == null) {
            Text("$label: connecting…", color = Color.White)
        } else {
            Image(bitmap = frame, contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}
