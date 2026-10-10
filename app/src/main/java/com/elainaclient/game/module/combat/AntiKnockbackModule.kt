package com.elainaclient.game.module.combat

import com.elainaclient.game.InterceptablePacket
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket

class AntiKnockbackModule : Module("anti_knockback", ModuleCategory.Combat) {

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

        val packet = interceptablePacket.packet
        if (packet is SetEntityMotionPacket) {


            if (packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
                interceptablePacket.intercept()
            }
        }
    }
}
