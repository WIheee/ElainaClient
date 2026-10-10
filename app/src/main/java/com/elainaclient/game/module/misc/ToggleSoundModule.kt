package com.elainaclient.game.module.misc

import com.elainaclient.game.InterceptablePacket
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory

class ToggleSoundModule : Module(
    name = "toggle_sounds",
    category = ModuleCategory.Misc,
    defaultEnabled = false
) {
    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
    }
}
