package com.elainaclient.game

import com.elainaclient.relay.listener.WRelayPacketListener
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket

interface ComposedPacketHandler : WRelayPacketListener {

    fun beforePacketBound(packet: BedrockPacket): Boolean

    fun afterPacketBound(packet: BedrockPacket) {}

    override fun beforeClientBound(packet: BedrockPacket): Boolean {
        return beforePacketBound(packet)
    }

    override fun beforeServerBound(packet: BedrockPacket): Boolean {
        return beforePacketBound(packet)
    }

    override fun afterClientBound(packet: BedrockPacket) {
        afterPacketBound(packet)
    }

    override fun afterServerBound(packet: BedrockPacket) {
        afterPacketBound(packet)
    }

}