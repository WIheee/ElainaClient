package com.elainaclient.game.module.world

import com.elainaclient.game.InterceptablePacket
import com.elainaclient.game.Module
import com.elainaclient.game.ModuleCategory
import com.elainaclient.game.hotbar.HeldSlot
import com.elainaclient.game.world.BlockPlacer
import com.elainaclient.game.world.PlacementResult
import com.elainaclient.game.world.PlacementRules
import com.elainaclient.game.world.chunk.Chunk
import com.elainaclient.util.NetLog
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Automatic bridging / scaffolding.
 *
 * The placement *algorithm* is modelled on LiquidBounce's Scaffold (Java Edition) — candidate-cell
 * search, reference-block/face selection, silent rotation, hotbar selection — but re-implemented
 * against Bedrock packets. It deliberately does **not** copy that project's code.
 *
 * Bedrock-specific design points:
 *  - The relay forwards the **raw** packet buffer, so mutating the decoded [PlayerAuthInputPacket]
 *    has no effect on its own. When a fake rotation is needed, the original packet is swallowed and
 *    a re-encoded copy is sent via [com.elainaclient.game.GameSession.serverBound].
 *  - Ordering: hotbar switch → auth input (with the fake rotation) → placement transaction. That is
 *    the order the server expects, and it is why the placement is issued from the same tick.
 *  - Placement itself goes through the retained [BlockPlacer], which owns the reference-block
 *    search, `clickPosition`, consume action and packet construction.
 *  - When `rotation` is Off, the placement runs in [afterPacketBound], i.e. after the original auth
 *    input has been forwarded (same ordering, without re-encoding).
 *  - Candidates are searched around the floor block the player stands on (LiquidBounce's
 *    `BlockPosOffsets.NORMAL`). It does **not** wait for the block under the player to become air:
 *    while walking toward an edge, the cell ahead is already air and is the one to fill.
 *  - Movement in this relay is client-driven. The Telly/GodBridge jumps are therefore sent as
 *    **client-bound** [org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket]s (the same
 *    pattern as AirJump/Bhop); rewriting inputData alone would not move the real player.
 *
 * This module must stay **last** in `ModuleManager`'s registration order: intercepting the auth
 * input stops every later module from seeing it.
 */
class ScaffoldModule : Module("scaffold", ModuleCategory.World) {

    /**
     * Bridging styles:
     * - [Normal] - aimed bridging; the fake rotation looks at the clicked block.
     * - [Breezily] - "no-look" bridging; level aim while placing underneath.
     * - [GodBridge] - diagonal strafe + continuous jumping, steep downward aim.
     * - [Telly] - jump-and-place rhythm; the bridge stays on the same layer while airborne.
     */
    enum class Technique { Normal, Breezily, GodBridge, Telly }
    enum class RotationMode { Off, OneTick }

    private var technique by enumValue("technique", Technique.Normal, Technique::class.java)
    private var expandRadius by intValue("expand_radius", 0, 0..2)
    private var placeDelay by intValue("place_delay", 1, 0..10)
    private var rotationMode by enumValue("rotation", RotationMode.OneTick, RotationMode::class.java)

    /** Lets Telly/GodBridge make the client jump themselves. No effect on Normal/Breezily. */
    private var autoJump by boolValue("auto_jump", true)
    private var jumpInterval by intValue("jump_interval", 10, 2..30)

    private var restoreDelay by intValue("restore_delay", 5, 0..20)
    private var blacklist by stringValue("blacklist", DEFAULT_BLACKLIST, null)
    private var onlyWhenMoving by boolValue("only_when_moving", false)

    // NOT `by lazy`: ModuleManager reuses module instances across relay sessions and reassigns
    // `session`, so a lazy instance would keep pointing at the previous (dead) GameSession.
    private val blockPlacer: BlockPlacer
        get() = BlockPlacer(session)

    private var heldSlotInstance: HeldSlot? = null
    private val heldSlot: HeldSlot
        get() = heldSlotInstance ?: HeldSlot(session).also { heldSlotInstance = it }

    private var tickCounter = 0
    private var cooldownTicks = 0
    private var restoreTicks = 0
    private var slotSwapped = false
    private var pendingPlan: PlacementPlan? = null

    /** Last cell we sent a transaction for, so the same cell isn't hammered before the echo. */
    private var lastAttemptTarget: Vector3i? = null
    private var lastAttemptTick = -1000
    private var lastDiagTick = -1000
    private var lastNoPlanTick = -1000
    private var lastPlaceLogTick = -1000
    private var placedSinceLog = 0

    /** Target of the most recent placement, checked once for the server's UpdateBlock echo. */
    private var pendingEchoTarget: Vector3i? = null
    private var pendingEchoTick = -1000

    /**
     * Leading cell of the bridge, remembered while airborne. Telly/GodBridge jump constantly, and
     * mid-jump the player's column has already moved past the last placed block - so stepping from
     * the player's own cell would target a cell that isn't face-adjacent to anything solid.
     */
    private var bridgeFront: Vector3i? = null
    private var lastJumpTick = -1000

    private data class PlacementPlan(
        val target: Vector3i,
        val reference: Pair<Vector3i, Int>,
        val slot: Int,
        val rotation: Vector3f,
    )

    // -----------------------------------------------------------------------------------------
    // Hooks
    // -----------------------------------------------------------------------------------------

    override fun onEnabled() {
        super.onEnabled()
        reset()
    }

    override fun onDisabled() {
        super.onDisabled()
        if (isSessionCreated) restoreSlot()
        reset()
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !isSessionCreated) return
        val packet = interceptablePacket.packet as? PlayerAuthInputPacket ?: return

        tick()

        if (cooldownTicks > 0) {
            cooldownTicks--
            return
        }

        val plan = buildPlan(packet) ?: return

        // Telly/GodBridge: make the real client jump (client-bound motion, the same mechanism
        // AirJump/Bhop use - injecting inputData only tells the server, it doesn't move anyone).
        maybeJump(packet)

        if (rotationMode != RotationMode.OneTick) {
            // No packet rewrite needed: place after the original auth input has been forwarded.
            pendingPlan = plan
            return
        }

        applySlot(plan.slot)
        packet.rotation = plan.rotation

        // Swallow the raw passthrough and send the rewritten packet ourselves, so the server
        // receives the fake rotation *before* the placement transaction.
        interceptablePacket.intercept()
        session.serverBound(packet)

        place(plan)
    }

    override fun afterPacketBound(packet: BedrockPacket) {
        if (packet !is PlayerAuthInputPacket) return
        val plan = pendingPlan ?: return
        pendingPlan = null
        applySlot(plan.slot)
        place(plan)
    }

    override fun onDisconnect(reason: String) {
        // A slot remembered in this session must never be "restored" into the next one.
        if (isSessionCreated) heldSlot.clearSavedSlot()
        reset()
    }

    // -----------------------------------------------------------------------------------------
    // Planning
    // -----------------------------------------------------------------------------------------

    private fun buildPlan(packet: PlayerAuthInputPacket): PlacementPlan? {
        val player = session.localPlayer
        val pos = player.vec3Position

        if (onlyWhenMoving && horizontalSpeed() < 0.02) return null

        // `base` is the block *the player stands on*, i.e. the floor level. Search the 3x3 ring
        // around it at that level - the Bedrock equivalent of LiquidBounce's BlockPosOffsets.NORMAL
        // (which searches around the feet block, one layer down).
        //
        // Deliberately NOT aborting when `base` itself is solid: while walking toward an edge the
        // cell under the player is still solid, but the cell *ahead* at the same level is already
        // air - that is the cell scaffold must fill. The earlier early-return made the module dead
        // until the player had already stepped into the void. The per-candidate air check below
        // is the only filter needed.
        val grounded = packet.inputData.contains(PlayerAuthInputData.VERTICAL_COLLISION)
        val under = floorCell(pos, grounded)
        val base = if (grounded) {
            bridgeFront = under
            under
        } else {
            // Mid-air (Telly/GodBridge): the player has already left the last placed block, so
            // continue from the bridge's leading cell instead of the player's own column.
            bridgeFront ?: under
        }

        // Also dump the model column while placements are succeeding - the no-plan branch alone
        // never fires when the module is placing, which is exactly the case we need to inspect.
        if (tickCounter - lastDiagTick >= 20) {
            lastDiagTick = tickCounter
            logColumn(packet, pos)
        }

        val slot = findBlockSlot()
        if (slot == null) {
            diag(
                "no placeable block (mapping=${session.isBlockMappingInitialized}, " +
                    "held=${player.inventory.heldItemSlot})"
            )
            return null
        }

        // Only the floor layer. `base` already tracks the player's Y, so a falling player's search
        // layer moves down with them; adding a second layer below would just place blocks under
        // the platform edge.
        val speed = sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ)
        val moving = speed >= 0.02f
        val dirX = if (moving) player.motionX / speed else 0f
        val dirZ = if (moving) player.motionZ / speed else 0f

        // While standing still, only the cell under the player is eligible (catching a fall).
        // Filling the whole ring around a stationary player was what turned the bridge into a
        // continent. While moving, the optional ring is anchored at the bridge's leading cell and
        // the immediate forward neighbour is always included.
        val radius = expandRadius.coerceIn(0, 2)
        val candidates = ArrayList<Vector3i>()
        val seen = HashSet<Vector3i>()
        fun add(cell: Vector3i) {
            if (seen.add(cell)) candidates += cell
        }
        fun addRing(center: Vector3i) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    add(center.add(dx, 0, dz))
                }
            }
        }

        if (moving) {
            addRing(base)

            // The cell the player is walking into. Always face-adjacent to the floor block, so it
            // can never skip the immediate neighbour (a purely position-predicted target can land
            // two cells ahead near a block boundary and leave a hole). Diagonally, both axis
            // neighbours are needed - a diagonal cell is only edge-adjacent and cannot be clicked
            // from `base`.
            val stepX = if (dirX > 0.35f) 1 else if (dirX < -0.35f) -1 else 0
            val stepZ = if (dirZ > 0.35f) 1 else if (dirZ < -0.35f) -1 else 0
            if (stepX != 0) add(base.add(stepX, 0, 0))
            if (stepZ != 0) add(base.add(0, 0, stepZ))
        } else {
            add(base)
        }

        sortCandidates(candidates, base, pos, dirX, dirZ, moving)

        // `pos` is already the eye position (Bedrock reports auth-input positions at eye level).
        val eye = pos
        var airCount = 0
        var unreachable = 0

        for (candidate in candidates) {
            if (!isAirOrUnknown(candidate)) continue
            airCount++

            // While moving, only extend along the travel direction *from the bridge's leading
            // cell* (not from the player - mid-jump the player has already passed it, and the
            // next cell can be right under them). Keeps the bridge one block wide.
            if (moving) {
                val forward = (candidate.x - base.x) * dirX + (candidate.z - base.z) * dirZ
                if (forward < 0.15f) continue
            }

            // The best candidate is one we just sent. Wait for the server's UpdateBlock echo
            // rather than falling through to a worse cell - that is what filled the ring around
            // the player with stray blocks instead of extending the bridge one cell per step.
            if (candidate == lastAttemptTarget && tickCounter - lastAttemptTick < RETRY_TICKS) return null

            val reference = PlacementRules.findReferenceBlock(
                lookup = { p -> session.level.getBlockAt(p) },
                pos = candidate,
            )
            if (reference == null) {
                unreachable++
                continue
            }

            // Server reach check (Bedrock allows roughly 5 blocks; be slightly conservative).
            if (distance(eye, reference.first) > 5.4) {
                unreachable++
                continue
            }

            return PlacementPlan(
                target = candidate,
                reference = reference,
                slot = slot,
                rotation = rotationFor(reference),
            )
        }

        // One line per second at most; the column dump has its own throttle above.
        if (tickCounter - lastNoPlanTick >= 20) {
            lastNoPlanTick = tickCounter
            NetLog.log(
                "SCAFFOLD",
                "no plan: eye=(${pos.x},${pos.y},${pos.z}) base=$base " +
                    "under=${session.level.getBlockAt(base).identifier} " +
                    "moving=${horizontalSpeed() > 0.02f} air=$airCount unreachable=$unreachable radius=$radius",
            )
        }
        return null
    }

    /**
     * Dumps what the local world model actually knows about the player's column, plus whether the
     * chunk/sections were tracked. This distinguishes "the position is eye-level" from "the level
     * model has no terrain here", which the previous log could not.
     */
    private fun logColumn(packet: PlayerAuthInputPacket, pos: Vector3f) {
        val x = floor(pos.x).toInt()
        val z = floor(pos.z).toInt()
        val top = floor(pos.y).toInt() + 1
        val column = (top - 7..top).joinToString(",") { y ->
            "$y=${session.level.getBlockAt(x, y, z).identifier.removePrefix("minecraft:")}"
        }
        val chunk = session.level.chunks[Chunk.hash(x shr 4, z shr 4)]
        val sections = chunk?.sectionStorage
            ?.mapIndexed { i, s -> if (s.populated) i else null }
            ?.filterNotNull()
            ?.joinToString("")
            ?: "none"
        val grounded = packet.inputData.contains(PlayerAuthInputData.VERTICAL_COLLISION)
        NetLog.log(
            "SCAFFOLD",
            "column x=$x z=$z grounded=$grounded is384=${session.level.is384WorldSupported} " +
                "chunk=${chunk != null} chunks=${session.level.chunks.size} " +
                "populatedSections=[$sections] | $column",
        )
    }

    /**
     * Block coordinate of the floor cell under the player.
     *
     * Primary source is our own tracked world, not an assumed eye height: Bedrock reports this
     * position at **eye level**, and its exact offset varies with pose (standing 1.62, sneaking
     * 1.27), so guessing it is fragile. Instead, scan the player's own column from the eye cell
     * downwards - the floor block is always at `floor(eyeY) - 1` or `floor(eyeY) - 2` for any eye
     * offset in (0, 2). The first solid cell found wins.
     *
     * Falls back to the eye-height maths only when the model has nothing tracked there (airborne
     * over the void, or a chunk we never received).
     */
    private fun floorCell(pos: Vector3f, grounded: Boolean): Vector3i {
        val x = floor(pos.x).toInt()
        val z = floor(pos.z).toInt()
        val eyeCell = floor(pos.y).toInt()
        for (y in eyeCell downTo eyeCell - 2) {
            val cell = Vector3i.from(x, y, z)
            if (!isAirOrUnknown(cell)) return cell
        }
        val blockY = if (grounded) {
            eyeCell - 2
        } else {
            floor((pos.y - EYE_HEIGHT).toDouble()).toInt() - 1
        }
        return Vector3i.from(x, blockY, z)
    }

    /**
     * Orders candidate cells so the cell the player is walking *into* wins over the cells beside or
     * behind them; falls back to plain distance when standing still. Without this, four cardinal
     * cells share the same distance and the stable sort always picked west first, so the module
     * built sideways instead of forward.
     */
    private fun sortCandidates(
        candidates: MutableList<Vector3i>,
        base: Vector3i,
        pos: Vector3f,
        dirX: Float,
        dirZ: Float,
        moving: Boolean,
    ) {
        if (!moving) {
            candidates.sortBy { distanceSqToPlayer(it, pos) }
            return
        }
        candidates.sortBy { candidate ->
            // Offsets from the bridge's leading cell, in the travel direction.
            val dx = (candidate.x - base.x).toFloat()
            val dz = (candidate.z - base.z).toFloat()
            // Positive = ahead along the travel direction.
            val forward = dx * dirX + dz * dirZ
            // Signed distance from the travel line. Penalising it keeps the bridge one block wide.
            val lateral = dx * -dirZ + dz * dirX
            val behindPenalty = if (forward < -0.05f) 100f else 0f
            behindPenalty + distanceSqToPlayer(candidate, pos) * 0.1f +
                lateral * lateral * 2f - forward * 0.5f
        }
    }

    private fun place(plan: PlacementPlan) {
        lastAttemptTarget = plan.target
        lastAttemptTick = tickCounter

        val result = blockPlacer.place(
            slot = plan.slot,
            pos = plan.target,
            reference = plan.reference,
            predictLocally = false,
        )
        pendingEchoTarget = plan.target
        pendingEchoTick = tickCounter

        placedSinceLog++
        if (tickCounter - lastPlaceLogTick >= 20) {
            lastPlaceLogTick = tickCounter
            val eye = session.localPlayer.vec3Position
            NetLog.log(
                "SCAFFOLD",
                "placed x$placedSinceLog in last second: last target=${plan.target} " +
                    "ref=${plan.reference.first}/face=${plan.reference.second} slot=${plan.slot} " +
                    "eye=(${eye.x},${eye.y},${eye.z}) result=$result rotation=${plan.rotation}",
            )
            placedSinceLog = 0
        }
        if (result == PlacementResult.PLACED) {
            // New leading edge for the airborne (Telly/GodBridge) continuation.
            bridgeFront = plan.target
            cooldownTicks = placeDelay
            restoreTicks = restoreDelay
        }
    }

    /** Throttled diagnostic line; one per second at most, so a failed plan can't spam the log. */
    private fun diag(message: String) {
        if (tickCounter - lastDiagTick < 20) return
        lastDiagTick = tickCounter
        NetLog.log("SCAFFOLD", message)
    }

    private fun applySlot(slot: Int) {
        val inventory = session.localPlayer.inventory
        if (inventory.heldItemSlot == slot) return

        if (!slotSwapped) {
            heldSlot.saveCurrentSlot()
            slotSwapped = true
        }
        heldSlot.switchOnServer(slot)
    }

    private fun restoreSlot() {
        if (!slotSwapped) return
        heldSlot.restoreSavedSlot()
        slotSwapped = false
    }

    private fun tick() {
        tickCounter++

        // Did the server actually confirm the last placement? One second is far longer than a LAN
        // round trip, so a cell that is still air means the transaction was rejected (or our
        // UpdateBlock tracking is broken).
        val echo = pendingEchoTarget
        if (echo != null && tickCounter - pendingEchoTick == 20) {
            if (isAirOrUnknown(echo)) {
                NetLog.log(
                    "SCAFFOLD",
                    "echo missing: $echo still air 20 ticks after placement " +
                        "(server rejected, or UpdateBlock not tracked)",
                )
            }
            pendingEchoTarget = null
        }

        if (restoreTicks > 0) {
            restoreTicks--
            if (restoreTicks == 0) restoreSlot()
        }
    }

    private fun reset() {
        pendingPlan = null
        cooldownTicks = 0
        restoreTicks = 0
        slotSwapped = false
        tickCounter = 0
        lastAttemptTarget = null
        lastAttemptTick = -1000
        lastDiagTick = -1000
        lastNoPlanTick = -1000
        lastPlaceLogTick = -1000
        placedSinceLog = 0
        pendingEchoTarget = null
        pendingEchoTick = -1000
        bridgeFront = null
        lastJumpTick = -1000
        heldSlotInstance = null
    }

    // -----------------------------------------------------------------------------------------
    // Rotation
    // -----------------------------------------------------------------------------------------

    private fun rotationFor(reference: Pair<Vector3i, Int>): Vector3f =
        when (technique) {
            // Normal and Telly actually aim at the block being clicked.
            Technique.Normal, Technique.Telly -> faceRotation(reference)

            // Breezily bridging means *not* looking down: report a level, forward-facing aim
            // while the block is placed underneath. (The real client's camera is untouched -
            // this is the one-tick rotation the server sees.)
            Technique.Breezily -> {
                val yaw = normalizeYaw(movementYaw())
                Vector3f.from(5f, yaw, yaw)
            }

            // God bridging looks steeply down while the injected strafe carries the player
            // diagonally along the bridge.
            Technique.GodBridge -> {
                val yaw = normalizeYaw(movementYaw())
                Vector3f.from(75.7f, yaw, yaw)
            }
        }

    private fun faceRotation(reference: Pair<Vector3i, Int>): Vector3f {
        val point = facePoint(reference.first, reference.second)
        // vec3Position is already the eye position here - do NOT add the eye height again.
        val eye = session.localPlayer.vec3Position

        val dx = point.x - eye.x
        val dy = point.y - eye.y
        val dz = point.z - eye.z
        val horizontal = sqrt(dx * dx + dz * dz)

        val yaw = Math.toDegrees(atan2(-dx.toDouble(), dz.toDouble())).toFloat()
        val pitch = Math.toDegrees(-atan2(dy.toDouble(), horizontal.toDouble())).toFloat()
        return Vector3f.from(pitch, yaw, yaw)
    }

    /** Face indices: 0=down, 1=up, 2=north, 3=south, 4=west, 5=east. */
    private fun facePoint(pos: Vector3i, face: Int): Vector3f {
        val x = pos.x.toFloat()
        val y = pos.y.toFloat()
        val z = pos.z.toFloat()
        return when (face) {
            0 -> Vector3f.from(x + 0.5f, y, z + 0.5f)
            1 -> Vector3f.from(x + 0.5f, y + 1f, z + 0.5f)
            2 -> Vector3f.from(x + 0.5f, y + 0.5f, z)
            3 -> Vector3f.from(x + 0.5f, y + 0.5f, z + 1f)
            4 -> Vector3f.from(x, y + 0.5f, z + 0.5f)
            5 -> Vector3f.from(x + 1f, y + 0.5f, z + 0.5f)
            else -> Vector3f.from(x + 0.5f, y + 0.5f, z + 0.5f)
        }
    }

    private fun movementYaw(): Float {
        val player = session.localPlayer
        if (abs(player.motionX) + abs(player.motionZ) > 0.01f) {
            return Math.toDegrees(
                atan2(-player.motionX.toDouble(), player.motionZ.toDouble())
            ).toFloat()
        }
        return player.rotationYaw
    }

    private fun normalizeYaw(yaw: Float): Float = ((yaw % 360f) + 360f) % 360f

    // -----------------------------------------------------------------------------------------
    // Technique movement
    // -----------------------------------------------------------------------------------------

    /**
     * Makes the real client jump for the acrobatic techniques.
     *
     * Movement in this relay is client-driven: mutating the auth input's inputData/rawMoveVector
     * only changes what the *server* is told, not where the client moves. The working pattern
     * (AirJump/Bhop/HighJump) is a client-bound [SetEntityMotionPacket] with upward motion, which
     * the game client applies to its own player.
     *
     * - Telly: one jump per [jumpInterval] ticks (the jump-and-place rhythm).
     * - GodBridge: continuous hopping; the player supplies the strafe.
     *
     * Only while grounded and moving, so it can't stack jumps mid-air.
     */
    private fun maybeJump(packet: PlayerAuthInputPacket) {
        if (!autoJump) return
        if (technique != Technique.Telly && technique != Technique.GodBridge) return
        if (!packet.inputData.contains(PlayerAuthInputData.VERTICAL_COLLISION)) return
        if (horizontalSpeed() < 0.02f) return
        if (tickCounter - lastJumpTick < jumpInterval) return

        lastJumpTick = tickCounter
        val player = session.localPlayer
        val motion = SetEntityMotionPacket().apply {
            runtimeEntityId = player.runtimeEntityId
            motion = Vector3f.from(player.motionX, JUMP_MOTION, player.motionZ)
        }
        // Client-bound: the real game client does the jumping; its next auth input carries the
        // updated position to the server.
        session.clientBound(motion)
    }

    // -----------------------------------------------------------------------------------------
    // Block selection
    // -----------------------------------------------------------------------------------------

    private fun findBlockSlot(): Int? {
        if (!session.isBlockMappingInitialized) return null

        val inventory = session.localPlayer.inventory
        val banned = parseBlacklist()

        val held = inventory.heldItemSlot
        if (isPlaceableBlock(inventory.content[held], banned)) return held

        var bestSlot = -1
        var bestCount = -1
        for (slot in 0 until 9) {
            val item = inventory.content[slot]
            if (!isPlaceableBlock(item, banned)) continue
            if (item.count > bestCount) {
                bestCount = item.count
                bestSlot = slot
            }
        }
        return bestSlot.takeIf { it >= 0 }
    }

    private fun isPlaceableBlock(
        item: org.cloudburstmc.protocol.bedrock.data.inventory.ItemData,
        banned: List<String>,
    ): Boolean {
        if (item.count <= 0) return false
        val identifier = item.definition?.identifier ?: return false
        if (banned.any { identifier == it }) return false
        // Bedrock has no block-shape API here, so reject obvious non-full shapes by name.
        if (NON_FULL_TOKENS.any { identifier.contains(it) }) return false
        return session.blockMapping.getRuntimeIdByIdentifier(identifier) != null
    }

    private fun parseBlacklist(): List<String> =
        blacklist.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    // -----------------------------------------------------------------------------------------
    // Small geometry helpers
    // -----------------------------------------------------------------------------------------

    private fun isAirOrUnknown(pos: Vector3i): Boolean {
        val identifier = session.level.getBlockAt(pos).identifier
        return identifier == "minecraft:air" || identifier == "minecraft:unknown"
    }

    private fun distance(from: Vector3f, to: Vector3i): Double {
        val dx = to.x + 0.5 - from.x
        val dy = to.y + 0.5 - from.y
        val dz = to.z + 0.5 - from.z
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    private fun distanceSqToPlayer(pos: Vector3i, player: Vector3f): Float {
        val dx = pos.x + 0.5f - player.x
        val dy = pos.y + 0.5f - player.y
        val dz = pos.z + 0.5f - player.z
        return dx * dx + dy * dy + dz * dz
    }

    private fun horizontalSpeed(): Float {
        val player = session.localPlayer
        return sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ)
    }

    private companion object {
        /** Ticks to wait before retrying the same target cell while waiting for the server echo. */
        const val RETRY_TICKS = 4

        /** Bedrock player eye height above the feet while standing. */
        const val EYE_HEIGHT = 1.62f

        /** Upward motion used to make the game client jump (vanilla jump velocity). */
        const val JUMP_MOTION = 0.42f

        const val DEFAULT_BLACKLIST =
            "minecraft:tnt,minecraft:cobweb,minecraft:sand,minecraft:red_sand," +
                "minecraft:gravel,minecraft:chest,minecraft:ender_chest," +
                "minecraft:crafting_table,minecraft:furnace,minecraft:hopper"

        /**
         * Name heuristics for shapes that are not full cubes. Bedrock's block registry here has no
         * shape/AABB data, so this stands in for Java's `isFullCube` check.
         */
        val NON_FULL_TOKENS = listOf(
            "slab", "stairs", "fence", "wall", "torch", "button", "pressure_plate",
            "door", "trapdoor", "carpet", "sign", "rail", "flower", "sapling", "plant",
            "bed", "banner", "pane", "ladder", "chain", "candle", "campfire", "lantern",
            "skull", "head", "pot", "hopper", "cauldron", "anvil", "bell", "conduit",
            "scaffolding", "concrete_powder",
        )
    }
}
