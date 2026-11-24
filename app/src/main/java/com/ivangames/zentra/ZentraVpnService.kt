package com.ivangames.zentra

import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

class ZentraVpnService : VpnService(), CoreCallbackHandler {

    companion object {
        const val ACTION_START = "com.ivangames.zentra.START"
        const val ACTION_STOP = "com.ivangames.zentra.STOP"
        const val EXTRA_CONFIG = "config"
        @Volatile var running = false
        var listener: ((Boolean, String) -> Unit)? = null
    }

    private var tun: ParcelFileDescriptor? = null
    private var core: CoreController? = null
    private val ui = Handler(Looper.getMainLooper())

    private fun report(on: Boolean, msg: String) {
        running = on
        ui.post { listener?.invoke(on, msg) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val config = intent.getStringExtra(EXTRA_CONFIG)
            if (config != null) Thread { startCore(config) }.start()
        } else {
            stopCore()
            report(false, "")
        }
        return START_NOT_STICKY
    }

    private fun startCore(config: String) {
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, "zentra")
            val pfd = Builder()
                .setSession("Zentra")
                .setMtu(1500)
                .addAddress("10.10.10.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addDisallowedApplication(packageName)
                .establish() ?: throw Exception("VPN не создан")
            tun = pfd
            val c = Libv2ray.newCoreController(this)
            core = c
            c.startLoop(config, pfd.fd)
            report(true, "")
        } catch (e: Throwable) {
            val m = e.message ?: e.toString()
            stopCore()
            report(false, m)
        }
    }

    private fun stopCore() {
        try { core?.stopLoop() } catch (e: Throwable) { }
        core = null
        try { tun?.close() } catch (e: Throwable) { }
        tun = null
        stopSelf()
    }

    override fun onRevoke() {
        stopCore()
        report(false, "")
    }

    override fun onDestroy() {
        stopCore()
        running = false
        super.onDestroy()
    }

    override fun startup(): Long = 0
    override fun shutdown(): Long = 0
    override fun onEmitStatus(code: Long, text: String?): Long = 0
}
