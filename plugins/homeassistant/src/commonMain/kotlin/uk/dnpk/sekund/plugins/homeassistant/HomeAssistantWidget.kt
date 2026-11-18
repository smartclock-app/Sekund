package uk.dnpk.sekund.plugins.homeassistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.dnpk.sekund.sdk.context.Alert
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.Overlay
import uk.dnpk.sekund.sdk.settings.AccountRef
import uk.dnpk.sekund.sdk.video.mjpegFrames
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/**
 * Home Assistant: shows a camera full screen while a sensor is on (e.g. the doorbell), as an overlay.
 *
 * It draws nothing in its layout slot; problems appear as an alert at the top of the sidebar.
 */
class HomeAssistantWidget(private val context: InstanceContext<HomeAssistantSettings>) : Widget {
    private val settings = context.settings

    private val cameras = ActiveCameras(context.scope, settings.cameras, settings.showCameraFor) { shown ->
        updateOverlay(shown)
    }

    /** Each camera's picture stream, built once. Streams only open while the overlay is on screen. */
    private val feeds = mutableMapOf<CameraSettings, Flow<ImageBitmap>>()

    init {
        start()
    }

    private fun start() {
        val account = settings.account
        if (account == null) {
            context.alerts.show(Alert(title = "Home Assistant", subtitle = "Pick an account in the widget's settings"))
            return
        }
        if (settings.cameras.isEmpty()) {
            context.log.info("No cameras configured, so not connecting")
            return
        }
        for (camera in settings.cameras) {
            feeds[camera] = framesFor(account, camera)
        }
        val connection = HomeAssistantConnection(context, account) { trigger, state ->
            cameras.sensorChanged(trigger, state)
        }
        context.scope.launch { connection.run() }
    }

    /**
     * Home Assistant's own MJPEG stream needs the account's login, so it goes through the signed-in client.
     * A custom stream elsewhere (go2rtc, the camera itself) uses the plain client: credentials stay home.
     */
    private fun framesFor(account: AccountRef, camera: CameraSettings): Flow<ImageBitmap> {
        val server = context.auth.fields(account)["instance_url"]?.trim()?.trimEnd('/').orEmpty()
        val onError = { error: Throwable -> context.log.warn("Camera ${camera.entityId} stream failed", error) }

        if (camera.streamUrl.isNotEmpty()) {
            val isOnServer = server.isNotEmpty() && camera.streamUrl.startsWith("$server/")
            val client = if (isOnServer) context.auth.client(account) else context.http
            return mjpegFrames(client, camera.streamUrl, onError)
        }
        return mjpegFrames(context.auth.client(account), "$server/api/camera_proxy_stream/${camera.entityId}", onError)
    }

    private fun updateOverlay(shown: List<CameraSettings>) {
        if (shown.isEmpty()) {
            context.overlays.clear()
            return
        }
        val shownFeeds = shown.mapNotNull { camera ->
            val frames = feeds[camera]
            if (frames == null) null else CameraFeed(camera, frames)
        }
        context.overlays.show(
            Overlay(
                onDismissed = { context.scope.launch { cameras.dismissAll() } },
                content = { CameraOverlay(shownFeeds) },
            ),
        )
    }

    /** Nothing to show in the layout: the cameras appear as an overlay. */
    @Composable
    override fun Content(location: WidgetLocation) {
    }
}
