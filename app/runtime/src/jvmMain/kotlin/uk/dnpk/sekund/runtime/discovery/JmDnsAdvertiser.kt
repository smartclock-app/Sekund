package uk.dnpk.sekund.runtime.discovery

import co.touchlab.kermit.Logger
import java.io.IOException
import javax.jmdns.JmmDNS
import javax.jmdns.ServiceInfo
import kotlin.concurrent.thread

/**
 * Desktop/Raspberry Pi mDNS using JmDNS. `JmmDNS` announces on every network interface, so the device
 * is found whether it's on Wi-Fi or Ethernet. Starting JmDNS can take a few seconds, so it runs on its
 * own thread.
 */
class JmDnsAdvertiser : ServiceAdvertiser {
    private val log = Logger.withTag("mDNS")
    private var mdns: JmmDNS? = null

    override fun advertise(service: AdvertisedService) {
        thread(name = "mdns-advertise", isDaemon = true) {
            try {
                stop()
                val created = JmmDNS.Factory.getInstance()
                val info = ServiceInfo.create("${AdvertisedService.TYPE}.local.", service.name, service.port, 0, 0, service.txt)
                created.registerService(info)
                mdns = created
                log.i { "Announcing '${service.name}' as ${AdvertisedService.TYPE} on port ${service.port}" }
            } catch (error: IOException) {
                log.w(error) { "Couldn't announce the device on the network" }
            }
        }
    }

    override fun stop() {
        val current = mdns ?: return
        mdns = null
        current.unregisterAllServices()
        current.close()
    }
}
