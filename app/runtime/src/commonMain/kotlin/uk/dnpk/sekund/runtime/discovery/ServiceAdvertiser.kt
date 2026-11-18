package uk.dnpk.sekund.runtime.discovery

/**
 * Announces the device on the local network with mDNS / DNS-SD, so companions find it without typing
 * an address (like a printer or a Chromecast shows up). Each platform has its own: JmDNS on desktop,
 * Android's NsdManager on Android. The entry point passes one to AppGraph.
 */
interface ServiceAdvertiser {
    /** Starts announcing [service]. Calling it again replaces the previous announcement. */
    fun advertise(service: AdvertisedService)

    /** Stops announcing. */
    fun stop()
}

/**
 * What's announced: `<name>._sekund._tcp.local` on [port], with [txt] records such as
 * `id=<deviceId> api=1 ver=1.2.0` that a companion can read before connecting.
 */
data class AdvertisedService(
    val name: String,
    val port: Int,
    val txt: Map<String, String>,
) {
    companion object {
        /** The DNS-SD service type for Sekund devices. */
        const val TYPE = "_sekund._tcp"
    }
}
