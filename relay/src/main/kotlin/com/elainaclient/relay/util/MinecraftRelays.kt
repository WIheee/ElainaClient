package com.elainaclient.relay.util

import com.elainaclient.relay.WRelay
import com.elainaclient.relay.WRelaySession
import com.elainaclient.relay.address.WAddress
import com.elainaclient.relay.codec.CodecRegistry
import org.cloudburstmc.protocol.bedrock.BedrockPong

fun captureGamePacket(
    advertisement: BedrockPong = WRelay.DefaultAdvertisement,
    localAddress: WAddress = WAddress("0.0.0.0", 19132),
    remoteAddress: WAddress,
    onSessionCreated: WRelaySession.() -> Unit
): WRelay {
    CodecRegistry.getLatestCodec()

    return WRelay(
        localAddress = localAddress,
        advertisement = advertisement
    ).capture(
        remoteAddress = remoteAddress,
        onSessionCreated = onSessionCreated
    )
}

// NOTE: this file previously also had a standalone authorize() function and a
// StepFullBedrockSession.FullBedrockSession.refresh() extension, both built on the MinecraftAuth
// 4.x step-chain API. Neither was referenced anywhere outside this file (grep-confirmed), and
// BedrockAuthManager (5.x) refreshes its own tokens internally via getUpToDate() - there's no
// longer a separate "session object" that needs a matching refresh() helper - so both were
// removed rather than ported.
