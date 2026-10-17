package com.elainaclient.game

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.elainaclient.application.AppContext
import com.elainaclient.game.module.combat.ACAModule
import com.elainaclient.game.module.combat.AntiKnockbackModule
import com.elainaclient.game.module.combat.EnemyHunterModule
import com.elainaclient.game.module.combat.HitboxModule
import com.elainaclient.game.module.combat.KillauraModule
import com.elainaclient.game.module.combat.TriggerBotModule
import com.elainaclient.game.module.combat.WAuraModule
import com.elainaclient.game.module.combat.AutoFightModule
import com.elainaclient.game.module.combat.AutoHvHModule
import com.elainaclient.game.module.combat.AutoTotemModule
import com.elainaclient.game.module.combat.InfiniteAuraModule
import com.elainaclient.game.module.misc.ArrayListModule
import com.elainaclient.game.module.motion.NoClipModule
import com.elainaclient.game.module.misc.AutoDisconnectModule
import com.elainaclient.game.module.misc.CommandHandlerModule
import com.elainaclient.game.module.visual.CoordinatesModule
import com.elainaclient.game.module.misc.DesyncModule
import com.elainaclient.game.module.misc.MinerModule
import com.elainaclient.game.module.misc.NoChatModule
import com.elainaclient.game.module.misc.PieChartModule
import com.elainaclient.game.module.misc.PositionLoggerModule
import com.elainaclient.game.module.misc.ReplayModule
import com.elainaclient.game.module.misc.ChestStealerModule
import com.elainaclient.game.module.misc.SpammerModule
import com.elainaclient.game.module.misc.ToggleSoundModule
import com.elainaclient.game.module.misc.WaterMarkModule
import com.elainaclient.game.module.world.AntiDebuffModule
import com.elainaclient.game.module.world.EffectsModule
import com.elainaclient.game.module.world.ParticlesModule
import com.elainaclient.game.module.world.TimeShiftModule
import com.elainaclient.game.module.world.WeatherControllerModule
import com.elainaclient.game.module.motion.AirJumpModule
import com.elainaclient.game.module.motion.AntiAFKModule
import com.elainaclient.game.module.motion.AutoWalkModule
import com.elainaclient.game.module.motion.BhopModule
import com.elainaclient.game.module.motion.FlyModule
import com.elainaclient.game.module.motion.HighJumpModule
import com.elainaclient.game.module.motion.JetPackModule
import com.elainaclient.game.module.motion.MotionFlyModule
import com.elainaclient.game.module.motion.PlayerTPModule
import com.elainaclient.game.module.motion.SpeedModule
import com.elainaclient.game.module.motion.SpiderModule
import com.elainaclient.game.module.motion.SprintModule
import com.elainaclient.game.module.visual.CrosshairModule
import com.elainaclient.game.module.visual.DamageTextModule
import com.elainaclient.game.module.visual.ESPModule
import com.elainaclient.game.module.visual.FullbrightModule
import com.elainaclient.game.module.visual.MinimapModule
import com.elainaclient.game.module.visual.NetworkInfoModule
import com.elainaclient.game.module.visual.NoHurtCameraModule
import com.elainaclient.game.module.visual.PlayerJoinModule
import com.elainaclient.game.module.visual.SpeedDisplayModule
import com.elainaclient.game.module.visual.WorldStateModule
import com.elainaclient.game.module.visual.ZoomModule
import com.elainaclient.game.module.visual.TargetHudModule
import com.elainaclient.game.module.world.FreeCameraModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

object ModuleManager {

    private val _modules: MutableList<Module> = ArrayList()

    val modules: List<Module> = _modules

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        with(_modules) {
            // Combat
            add(WAuraModule())
            add(KillauraModule())
            add(AutoFightModule())
            add(InfiniteAuraModule())
            add(ACAModule())
            add(AutoTotemModule())
            add(AutoHvHModule())
            add(EnemyHunterModule())
            add(AntiKnockbackModule())

            add(HitboxModule())
            add(TriggerBotModule())

            // Motion
            add(MotionFlyModule())
            add(PlayerTPModule())
            add(FlyModule())
            add(SpeedModule())
            add(AirJumpModule())
            add(NoClipModule())
            add(JetPackModule())
            add(HighJumpModule())
            add(BhopModule())
            add(SprintModule())
            add(AutoWalkModule())
            add(AntiAFKModule())
            add(SpiderModule())

            // Visual
            add(DamageTextModule())
            add(ESPModule())
            add(PlayerJoinModule())
            add(ZoomModule())
            add(CoordinatesModule())
            add(NoHurtCameraModule())
            add(SpeedDisplayModule())
            add(NetworkInfoModule())
            add(WorldStateModule())
            add(MinimapModule())
            add(CrosshairModule())
            add(TargetHudModule())
            add(FullbrightModule())

            // World
            add(FreeCameraModule())
            add(TimeShiftModule())
            add(WeatherControllerModule())
            add(EffectsModule())
            add(ParticlesModule())
            add(AntiDebuffModule())

            // Misc

            add(AutoDisconnectModule())
            add(ArrayListModule())
            add(ToggleSoundModule())
            add(ChestStealerModule())
            add(DesyncModule())
            add(SpammerModule())
            add(WaterMarkModule())
            add(PositionLoggerModule())
            add(NoChatModule())
            add(CommandHandlerModule())
            add(ReplayModule())
            add(PieChartModule())
            add(MinerModule())
        }
    }

    fun saveConfig() {

        if (!AppContext.isInitialized) {
            return
        }

        val configsDir = AppContext.instance.filesDir.resolve("configs")
        configsDir.mkdirs()

        val config = configsDir.resolve("UserConfig.json")
        val jsonObject = buildJsonObject {
            put("modules", buildJsonObject {
                _modules.forEach {
                    if (it.private) {
                        return@forEach
                    }
                    put(it.name, it.toJson())
                }
            })
        }

        config.writeText(json.encodeToString(JsonObject.serializer(), jsonObject))
    }

    fun loadConfig() {

        if (!AppContext.isInitialized) {
            return
        }

        val configsDir = AppContext.instance.filesDir.resolve("configs")
        configsDir.mkdirs()

        val config = configsDir.resolve("UserConfig.json")
        if (!config.exists()) {
            return
        }

        val jsonString = config.readText()
        if (jsonString.isEmpty()) {
            return
        }

        try {
            val jsonObject = json.parseToJsonElement(jsonString).jsonObject
            val modules = jsonObject["modules"]?.jsonObject ?: return

            _modules.forEach { module ->
                (modules[module.name] as? JsonObject)?.let {
                    module.fromJson(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun exportConfig(): String {
        val jsonObject = buildJsonObject {
            put("modules", buildJsonObject {
                _modules.forEach {
                    if (it.private) {
                        return@forEach
                    }
                    put(it.name, it.toJson())
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), jsonObject)
    }

    fun importConfig(configStr: String) {
        try {
            val jsonObject = json.parseToJsonElement(configStr).jsonObject
            val modules = jsonObject["modules"]?.jsonObject ?: return

            _modules.forEach { module ->
                modules[module.name]?.let {
                    if (it is JsonObject) {
                        module.fromJson(it)
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid config format: ${e.message}")
        }
    }

    fun exportConfigToFile(context: Context, filePath: String): Boolean {
        return try {
            val file = if (filePath.contains("/")) {
                File(filePath)
            } else {
                val configsDir = context.getExternalFilesDir("configs")
                configsDir?.mkdirs()
                File(configsDir, if (filePath.endsWith(".json")) filePath else "$filePath.json")
            }

            file.parentFile?.mkdirs()
            file.writeText(exportConfig())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getElainaClientConfigsDirectory(): File? {
        return try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            val baseDir = if (documentsDir.exists() || documentsDir.mkdirs()) {
                documentsDir
            } else {
                downloadsDir
            }

            val wclientDir = File(baseDir, "ElainaClient")
            val configsDir = File(wclientDir, "configs")

            if (configsDir.exists() || configsDir.mkdirs()) {
                configsDir
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun importConfigFromFile(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val configStr = input.bufferedReader().readText()
                importConfig(configStr)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}