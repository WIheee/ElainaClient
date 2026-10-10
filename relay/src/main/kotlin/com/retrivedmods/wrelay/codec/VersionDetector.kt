package com.retrivedmods.wrelay.codec

object VersionDetector {

    // Only 1.26.x+ is tracked (protocol 924 and newer), matching CodecRegistry. Older protocols were
    // dropped deliberately - WRelay no longer advertises or negotiates anything below 1.26.
    private val versionRanges = mapOf(
        2225 to listOf("1.26.60"),
        // 1.26.50 and 1.26.51 are both bug-fix-only releases sharing network protocol 2193
        // (confirmed via Mojang/bedrock-protocol-docs release tags).
        2193 to listOf("1.26.50", "1.26.51"),
        // 1.26.45 bumped the wire protocol to 2169 from 1.26.44's 2168.
        2169 to listOf("1.26.45"),
        // 1.26.40 and 1.26.44 share protocol version 2168 (no wire-protocol break between
        // them - confirmed via minecraft.wiki/w/Protocol_version and bedrock-v/protocol-docs).
        2168 to listOf("1.26.40", "1.26.44"),
        1001 to listOf("1.26.30", "1.26.31", "1.26.32", "1.26.33"),
        975 to listOf("1.26.20", "1.26.21", "1.26.22", "1.26.23"),
        944 to listOf("1.26.10", "1.26.11", "1.26.12", "1.26.13"),
        924 to listOf("1.26.0", "1.26.1", "1.26.2", "1.26.3")
    )

    fun getSupportedVersionsForProtocol(protocolVersion: Int): List<String> {
        return versionRanges[protocolVersion] ?: emptyList()
    }

}
