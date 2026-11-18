package uk.dnpk.sekund.plugins.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import uk.dnpk.sekund.sdk.video.VideoFrames

/** A camera on screen: its settings and its live pictures. */
class CameraFeed(val camera: CameraSettings, val frames: Flow<ImageBitmap>)

/** The full-screen overlay: the triggered cameras side by side, each as large as fits. */
@Composable
fun CameraOverlay(feeds: List<CameraFeed>) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (feed in feeds) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                VideoFrames(frames = feed.frames, aspectRatio = feed.camera.aspectRatio, label = feed.camera.entityId)
            }
        }
    }
}
