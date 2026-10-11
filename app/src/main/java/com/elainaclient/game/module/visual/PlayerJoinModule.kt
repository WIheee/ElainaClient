package com.elainaclient.game.module.visual

import com.elainaclient.game.InterceptablePacket
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket

class PlayerJoinModule : Module("PlayerJoin", ModuleCategory.Visual) {

    private val trackedPlayers = mutableMapOf<String, String>()

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

        val packet = interceptablePacket.packet
        if (packet is PlayerListPacket) {
            // Since protocol v2168 the ADD/REMOVE action lives on each entry, not on the packet.
            for (entry in packet.entries) {
                val uuid = entry.uuid.toString()
                when (entry.action) {
                    PlayerListPacket.Action.ADD -> {
                        val name = entry.name?.toString() ?: "Unknown"
                        if (!trackedPlayers.containsKey(uuid)) {
                            trackedPlayers[uuid] = name
                            if (uuid != session.localPlayer.uuid.toString()) {
                                session.displayClientMessage("§a[+] §f$name §ajoined")
                            }
                        }
                    }
                    PlayerListPacket.Action.REMOVE -> {
                        val name = trackedPlayers.remove(uuid)
                        if (name != null) {
                            session.displayClientMessage("§c[-] §f$name §cleft")
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onDisabled() {
        trackedPlayers.clear()
    }
}