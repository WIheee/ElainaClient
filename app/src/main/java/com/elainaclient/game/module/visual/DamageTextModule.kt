package com.elainaclient.game.module.visual

import com.elainaclient.game.InterceptablePacket
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType
import com.elainaclient.game.entity.Player

class DamageTextModule : Module("DamageText", ModuleCategory.Visual) {


    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

        val packet = interceptablePacket.packet

        if (packet is EntityEventPacket && packet.type == EntityEventType.HURT) {
            val entityId = packet.runtimeEntityId


            if (entityId == session.localPlayer.runtimeEntityId) return


            val entity = session.level.entityMap[entityId]


            if (entity is Player) {
                val playerName = entity.username

                val stateText = "$playerName§r §cEnemy Damaged"
                val status = "§f$stateText"
                val message = " $status"



                session.displayClientMessage(message)
            }
        }
    }
}