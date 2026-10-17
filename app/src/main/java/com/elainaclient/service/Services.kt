package com.elainaclient.service

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.graphics.PixelFormat
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elainaclient.game.AccountManager
import com.elainaclient.game.GameSession
import com.elainaclient.game.ModuleManager
import com.elainaclient.game.module.visual.ESPModule
import com.elainaclient.model.CaptureModeModel
import com.elainaclient.overlay.OverlayManager
import com.elainaclient.render.RenderOverlayView
import com.elainaclient.relay.WRelay
import com.elainaclient.relay.WRelaySession
import com.elainaclient.relay.address.WAddress
import com.elainaclient.relay.config.EnhancedServerConfig
import com.elainaclient.relay.definition.Definitions
import com.elainaclient.relay.listener.AutoCodecPacketListener
import com.elainaclient.relay.listener.GamingPacketHandler
import com.elainaclient.relay.listener.OnlineLoginPacketListener
import com.elainaclient.relay.util.captureGamePacket
import com.elainaclient.util.NetLog
import com.elainaclient.util.ServerCompatUtils
import java.io.File
import kotlin.concurrent.thread

@Suppress("MemberVisibilityCanBePrivate")
object Services {

    private val handler = Handler(Looper.getMainLooper())

    private var wRelay: WRelay? = null
    private var thread: Thread? = null

    private var renderView: RenderOverlayView? = null
    private var windowManager: WindowManager? = null

    var isActive by mutableStateOf(false)
    var detectedProtocolVersion by mutableStateOf<Int?>(null)
    var detectedMinecraftVersion by mutableStateOf<String?>(null)

    fun toggle(context: Context, captureModeModel: CaptureModeModel) {
        if (!isActive) {
            // The upstream connection is only ever initiated by OnlineLoginPacketListener, which
            // is installed only when an account is selected (see `on` below). Without one the
            // relay would accept the game client and then never dial the real server at all: the
            // client sits on "connecting" forever and the server records zero connections, with
            // no explanation anywhere. Refuse to start and say why.
            if (AccountManager.selectedAccount == null) {
                context.toast("请先在「账号」页选择账号：未选择账号时无法连接真实服务器")
                return
            }
            on(context, captureModeModel)
            return
        }

        off()
    }

    private fun clearNetworkCaches() {
        try {
            val inetAddressClass = Class.forName("java.net.InetAddress")
            val cacheField = inetAddressClass.getDeclaredField("addressCache")
            cacheField.isAccessible = true
            val cache = cacheField.get(null)
            val cacheMapField = cache.javaClass.getDeclaredField("cache")
            cacheMapField.isAccessible = true
            val cacheMap = cacheMapField.get(cache) as? java.util.Map<*, *>
            cacheMap?.clear()

            val negativeCacheField = inetAddressClass.getDeclaredField("negativeCache")
            negativeCacheField.isAccessible = true
            val negativeCache = negativeCacheField.get(null)
            val negativeCacheMapField = negativeCache.javaClass.getDeclaredField("cache")
            negativeCacheMapField.isAccessible = true
            val negativeCacheMap = negativeCacheMapField.get(negativeCache) as? java.util.Map<*, *>
            negativeCacheMap?.clear()

            System.gc()
            System.runFinalization()

            Log.d("Services", "Network caches cleared")
        } catch (e: Exception) {
            Log.e("Services", "Error clearing network caches: ${e.message}")
        }
    }

    private fun on(context: Context, captureModeModel: CaptureModeModel) {
        if (thread != null) {
            return
        }

        wRelay?.let { relay ->
            try {
                if (relay.javaClass.methods.any { it.name == "stop" }) {
                    relay.javaClass.getMethod("stop").invoke(relay)
                }
            } catch (e: Exception) {
                Log.e("Services", "Error stopping existing WRelay: ${e.message}")
            }
        }
        wRelay = null

        File(context.cacheDir, "token_cache.json")

        NetLog.start(context)
        NetLog.log("NET", "Service starting")

        isActive = true
        handler.post {
            OverlayManager.show(context)
        }

        setupOverlay(context)

        thread = thread(
            name = "WRelayThread",
            priority = Thread.MAX_PRIORITY
        ) {
            runCatching {
                ModuleManager.loadConfig()
            }.exceptionOrNull()?.let {
                it.printStackTrace()
                context.toast("Load configuration error: ${it.message}")
            }

            runCatching {
                Definitions.loadBlockPalette()
            }.exceptionOrNull()?.let {
                it.printStackTrace()
                context.toast("Load block palette error: ${it.message}")
            }

            val selectedAccount = AccountManager.selectedAccount

            runCatching {
                clearNetworkCaches()

                val remoteAddress = WAddress(
                    captureModeModel.serverHostName,
                    captureModeModel.serverPort
                )

                val serverConfig = getServerConfig(captureModeModel)
                val useEnhancedRelay =
                    captureModeModel.isProtectedServer() && captureModeModel.enableServerOptimizations

                NetLog.log(
                    "NET",
                    "Relay target ${remoteAddress.hostName}:${remoteAddress.port} " +
                        "(account=${selectedAccount?.displayName ?: "<NONE>"}, " +
                        "enhanced=$useEnhancedRelay, " +
                        "optimizations=${captureModeModel.enableServerOptimizations})"
                )
                if (selectedAccount == null) {
                    NetLog.log(
                        "NET",
                        "WARNING: no account selected - nothing will ever dial the upstream server"
                    )
                }

                wRelay = if (useEnhancedRelay) {
                    WRelay(
                        localAddress = WAddress("0.0.0.0", 19132),
                        serverConfig = serverConfig
                    ).capture(remoteAddress = remoteAddress) {
                        initModules(this)
                        listeners.add(AutoCodecPacketListener(this))
                        selectedAccount?.authManager?.let { OnlineLoginPacketListener(this, it) }
                            ?.let { listeners.add(it) }
                        listeners.add(GamingPacketHandler(this))
                    }
                } else {
                    captureGamePacket(
                        localAddress = WAddress("0.0.0.0", 19132),
                        remoteAddress = remoteAddress
                    ) {
                        initModules(this)
                        listeners.add(AutoCodecPacketListener(this))
                        selectedAccount?.authManager?.let { OnlineLoginPacketListener(this, it) }
                            ?.let { listeners.add(it) }
                        listeners.add(GamingPacketHandler(this))
                    }
                }
            }.exceptionOrNull()?.let {
                it.printStackTrace()
                NetLog.log("NET", "Start WRelay error: ${it.javaClass.simpleName}: ${it.message}")
                context.toast("Start WRelay error: ${it.message}")
            }
        }
    }

    private fun off() {
        thread(name = "WRelayThread") {
            ModuleManager.saveConfig()

            wRelay?.let { relay ->
                try {
                    // Guard each disconnect: the client/server session may already be closed (e.g.
                    // the game client dropped on its own), and disconnecting a closed session throws
                    // IllegalStateException("Session has been closed").
                    if (relay.wRelaySession?.client?.isConnected == true) {
                        relay.wRelaySession?.client?.disconnect()
                    }
                    if (relay.wRelaySession?.server?.isConnected == true) {
                        relay.wRelaySession?.server?.disconnect()
                    }

                    if (relay.javaClass.methods.any { it.name == "stop" }) {
                        relay.javaClass.getMethod("stop").invoke(relay)
                        Log.d("Services", "WRelay connection stopped successfully")
                    }
                } catch (e: Exception) {
                    Log.e("Services", "Error stopping WRelay: ${e.message}")
                    e.printStackTrace()
                }
            }
            wRelay = null

            clearNetworkCaches()

            try {
                Thread.sleep(500)
            } catch (e: Exception) {
                Log.e("Services", "Error during cleanup delay: ${e.message}")
            }

            handler.post {
                OverlayManager.dismiss()
            }
            removeOverlay()
            isActive = false
            thread?.interrupt()
            thread = null

            NetLog.log("NET", "Service stopped")
            NetLog.stop()
            Log.d("Services", "WRelay service stopped and cleaned up")
        }
    }

    private fun Context.toast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun initModules(wRelaySession: WRelaySession) {
        val session = GameSession(wRelaySession)
        wRelaySession.listeners.add(session)

        wRelaySession.listeners.add(com.elainaclient.relay.listener.VersionTrackingListener() { protocol, version ->
            detectedProtocolVersion = protocol
            detectedMinecraftVersion = version
            Log.i("Services", "Client version: Minecraft $version (Protocol $protocol)")
        })

        for (module in ModuleManager.modules) {
            module.session = session
        }
        NetLog.log("NET", "Session initialized: ${ModuleManager.modules.size} modules attached")
        Log.e("Services", "Init session")
    }

    private fun setupOverlay(context: Context) {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            // minSdk is 28 (> Android O), so the application-overlay type is always available.
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alpha = 0.8f
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                setFitInsetsTypes(0)
                setFitInsetsSides(0)
            }
        }

        renderView = RenderOverlayView(context)
        ESPModule.setRenderView(renderView!!)



        handler.post {
            try {
                windowManager?.addView(renderView, params)
            } catch (e: Exception) {
                e.printStackTrace()
                context.toast("Failed to add overlay view: ${e.message}")
            }
        }
    }

    private fun removeOverlay() {
        renderView?.let { view ->
            windowManager?.removeView(view)
            renderView = null
        }
    }

    private fun getServerConfig(captureModeModel: CaptureModeModel): EnhancedServerConfig {
        return when (captureModeModel.serverConfigType) {
            ServerCompatUtils.ServerConfigType.FAST -> EnhancedServerConfig.FAST
            ServerCompatUtils.ServerConfigType.DEFAULT -> EnhancedServerConfig.DEFAULT
            ServerCompatUtils.ServerConfigType.AGGRESSIVE -> EnhancedServerConfig.AGGRESSIVE
            ServerCompatUtils.ServerConfigType.STANDARD -> EnhancedServerConfig.DEFAULT
        }
    }
}