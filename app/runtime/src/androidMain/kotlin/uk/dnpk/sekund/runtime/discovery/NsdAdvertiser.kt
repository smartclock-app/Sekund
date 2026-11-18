package uk.dnpk.sekund.runtime.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import co.touchlab.kermit.Logger

/** Android mDNS using the system's NsdManager (no extra library, no permission beyond INTERNET). */
class NsdAdvertiser(context: Context) : ServiceAdvertiser {
    private val log = Logger.withTag("mDNS")
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    override fun advertise(service: AdvertisedService) {
        stop()
        val info = NsdServiceInfo()
        info.serviceName = service.name
        info.serviceType = AdvertisedService.TYPE
        info.port = service.port
        for ((key, value) in service.txt) {
            info.setAttribute(key, value)
        }

        val created = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registered: NsdServiceInfo) {
                log.i { "Announcing '${registered.serviceName}' as ${AdvertisedService.TYPE}" }
            }

            override fun onRegistrationFailed(failed: NsdServiceInfo, errorCode: Int) {
                log.w { "Couldn't announce the device on the network (error $errorCode)" }
            }

            override fun onServiceUnregistered(unregistered: NsdServiceInfo) {}

            override fun onUnregistrationFailed(failed: NsdServiceInfo, errorCode: Int) {}
        }
        listener = created
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, created)
    }

    override fun stop() {
        val current = listener ?: return
        listener = null
        manager.unregisterService(current)
    }
}
