package com.retrivedmods.wrelay.codec

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec

object CodecRegistry {

    private val codecMap = mutableMapOf<Int, BedrockCodec>()
    private val minecraftVersionMap = mutableMapOf<String, Int>()
    private val sortedProtocolVersions = mutableListOf<Int>()

    init {
        registerAllCodecs()
    }

    private fun registerAllCodecs() {
        // Only 1.26.x+ is supported (protocol 924 and newer). Every older registration was removed
        // deliberately: with nothing below 924 in the map, getClosestCodec() falls back to 924 for any
        // older client, DefaultCodec (the RakNet advertisement) points at the newest entry, and no
        // pre-1.26 codec is ever negotiated.
        //
        // Registration is reflective against the Maven org.cloudburstmc.protocol:bedrock-codec
        // artifact (org.cloudburstmc.protocol.bedrock.codec.v{N}.Bedrock_v{N}.CODEC). A class the
        // artifact doesn't ship yet is a logged no-op that silently falls back to the previous entry
        // (see registerCodec) - so an entry here is necessary but not sufficient; the Maven snapshot
        // must actually ship the class for it to take effect.
        registerCodec(924, "1.26.0", "org.cloudburstmc.protocol.bedrock.codec.v924.Bedrock_v924")
        registerCodec(944, "1.26.10", "org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944")
        registerCodec(975, "1.26.20", "org.cloudburstmc.protocol.bedrock.codec.v975.Bedrock_v975")
        registerCodec(1001, "1.26.30", "org.cloudburstmc.protocol.bedrock.codec.v1001.Bedrock_v1001")
        registerCodec(2168, "1.26.40", "org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168")
        registerCodec(2169, "1.26.45", "org.cloudburstmc.protocol.bedrock.codec.v2169.Bedrock_v2169")
        registerCodec(2193, "1.26.50", "org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193")
        registerCodec(2225, "1.26.60", "org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225")

        sortedProtocolVersions.sortDescending()
    }

    private fun registerCodec(protocolVersion: Int, minecraftVersion: String, className: String) {
        try {
            val codecClass = Class.forName(className)
            val codecField = codecClass.getDeclaredField("CODEC")
            codecField.isAccessible = true
            val codec = codecField.get(null) as BedrockCodec

            codecMap[protocolVersion] = codec
            minecraftVersionMap[minecraftVersion] = protocolVersion
            sortedProtocolVersions.add(protocolVersion)

            println("Registered codec: $minecraftVersion (protocol $protocolVersion)")
        } catch (e: Exception) {
            // A failure here for the newest entries means getLatestCodec()/getClosestCodec() silently
            // fall back to an older protocol than the one actually needed - e.g. a real client seeing
            // WRelay advertise a much older protocol and refusing to connect at the handshake stage
            // (Block / InitialConnection-90) before any game logic runs.
            // (android.util.Log can't be used here: :relay is a plain Kotlin/JVM module with no
            // Android SDK dependency - only :app has that. println still shows up in `adb logcat`.)
            println(
                "ERROR: Failed to register codec $minecraftVersion (protocol $protocolVersion, class $className): ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun getCodecByProtocol(protocolVersion: Int): BedrockCodec? {
        return codecMap[protocolVersion]
    }

    fun getClosestCodec(protocolVersion: Int): BedrockCodec {
        val exactMatch = codecMap[protocolVersion]
        if (exactMatch != null) {
            return exactMatch
        }

        val closestVersion = sortedProtocolVersions.findLast { it <= protocolVersion }
            ?: sortedProtocolVersions.last()

        return codecMap[closestVersion]!!
    }

    fun getLatestCodec(): BedrockCodec {
        return codecMap[sortedProtocolVersions.first()]!!
    }

    fun getMinecraftVersion(protocolVersion: Int): String? {
        return minecraftVersionMap.entries.find { it.value == protocolVersion }?.key
    }

}
