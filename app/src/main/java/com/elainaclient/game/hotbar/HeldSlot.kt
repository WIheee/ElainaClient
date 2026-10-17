package com.elainaclient.game.hotbar

import com.elainaclient.game.GameSession
import org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket

/**
 * The one place the held hotbar slot changes.
 *
 * Two genuinely different behaviours, deliberately given different names instead of one shared
 * private slot-switch copy-pasted into three modules:
 *
 *  - [switchOnServer] tells the **real server** which slot is held. Required before a placement:
 *    the server checks the packet's `itemInHand` against the slot it believes is selected, so a
 *    placement sent while the server still thinks a different slot is held gets rejected.
 *  - [switchOnClient] only changes the **local display** (`clientBound`). Used by cosmetic hotbar
 *    cycling, which is not trying to convince the server of anything.
 *
 * ## The ordering invariant this module exists to own
 *
 * `WRelaySession.serverBound()`/`clientBound()` bypass the normal interception pipeline entirely,
 * and that pipeline is the ONLY place `PlayerInventory.heldItemSlot` gets updated (it listens for
 * the real client's own traffic). So after sending a `PlayerHotbarPacket` ourselves, the change must
 * be predicted locally or `heldItemSlot` silently never moves - leaving `hand`
 * (= `content[heldItemSlot]`) pointing at whichever slot was selected before, and mismatching the
 * `hotbarSlot` in every subsequent placement packet.
 *
 * This used to be a three-line note in SurroundModule that PistonCrystal referenced by name. It is a
 * precondition of calling this class correctly, so it lives here now.
 *
 * ## Callers must guard
 *
 * Every method reads `session.localPlayer`, so only call these while the session is live (the
 * modules' `isSessionCreated` check). [saveCurrentSlot]/[restoreSavedSlot] are the exception to
 * nothing - guard those too, since [restoreSavedSlot] switches slots.
 */
class HeldSlot(private val session: GameSession) {

    private var rememberedSlot = -1

    /** Tells the real server that [slot] is now held, and predicts the change locally. */
    fun switchOnServer(slot: Int) {
        val packet = PlayerHotbarPacket().apply {
            selectedHotbarSlot = slot
            containerId = 0
            // Plain property assignment, not PacketFieldUtil.setPacketField("selectHotbarSlot", ...):
            // Lombok @Data generates setSelectHotbarSlot, so the setter is public in the linked build
            // (verified against relay/Protocol - see ADR-0001). The reflective form was redundant.
            isSelectHotbarSlot = true
        }
        session.serverBound(packet)

        // Order matters and is load-bearing - see the class doc above.
        session.localPlayer.inventory.predictHeldItemSlot(slot)
    }

    /**
     * Changes only the local display: the server is not told, and `heldItemSlot` is deliberately
     * left alone so our model stays aligned with what the server believes.
     */
    fun switchOnClient(slot: Int) {
        val packet = PlayerHotbarPacket().apply {
            selectedHotbarSlot = slot
            containerId = 0
            isSelectHotbarSlot = true
        }
        session.clientBound(packet)
    }

    /** Captures the current slot once, for a later [restoreSavedSlot]. Later calls are no-ops. */
    fun saveCurrentSlot() {
        if (rememberedSlot == -1) {
            rememberedSlot = session.localPlayer.inventory.heldItemSlot
        }
    }

    /** Switches back to the slot captured by [saveCurrentSlot], if any, and forgets it. */
    fun restoreSavedSlot() {
        if (rememberedSlot != -1) {
            switchOnServer(rememberedSlot)
        }
        rememberedSlot = -1
    }

    /**
     * Forgets the remembered slot **without** switching back. For module re-enable: the previous
     * lifecycle used to reset its own `oldSlot = -1` there, and a slot remembered in an earlier
     * session must not be restored into a later one.
     */
    fun clearSavedSlot() {
        rememberedSlot = -1
    }
}
