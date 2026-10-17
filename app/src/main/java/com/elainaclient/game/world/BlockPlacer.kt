package com.elainaclient.game.world

import com.elainaclient.game.GameSession
import com.elainaclient.game.registry.BlockDefinition
import com.elainaclient.game.registry.BlockMapping
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket

/**
 * The one place a block gets placed.
 *
 * Replaces the three separate implementations that used to exist (two live copies in
 * SurroundModule/PistonCrystalModule that had silently diverged on `clickPosition`, plus a dead
 * `LocalPlayer.placeBlock` that was the only one computing it correctly). See ADR-0001.
 *
 * One verb, and everything behind it:
 *  - finds a real, currently solid neighbour to "click" (Bedrock places a new block adjacent to
 *    whatever *existing* block you click, not at the position you name)
 *  - builds the `ITEM_USE` transaction, including the `InventoryActionData` describing the consumed
 *    item that `inventoriesServerAuthoritative` servers require
 *  - computes a `clickPosition` geometrically consistent with the declared `blockFace`
 *  - sends it
 *
 * Deliberately does **not** switch the held hotbar slot — that is [com.elainaclient.game.hotbar.HeldSlot]'s
 * job, and the caller must have switched *before* calling [place] (see [place]'s contract).
 *
 * ## Dependency shape
 *
 * [place] takes the whole session because it has to send. Everything underneath it is pure and takes
 * narrowed dependencies instead ([PlacementRules.findReferenceBlock] takes a block-lookup function,
 * [PlacementRules.blockDefinitionFor] takes a nullable [BlockMapping]), so the placement rules are
 * testable without a session or an Android context — see `BlockPlacerTest`.
 */
class BlockPlacer(private val session: GameSession) {

    /**
     * Places the item held in [slot] at [pos], or reports why it couldn't.
     *
     * @param slot hotbar slot holding the item. The caller must already have switched the held slot
     *   to [slot] (via [com.elainaclient.game.hotbar.HeldSlot.switchOnServer]) — the server validates the
     *   packet's `itemInHand` against the slot it believes is selected. Note that the packet itself
     *   is built from `content[slot]`, not `inventory.hand` (= `content[heldItemSlot]`), so a caller
     *   that forgot to switch sends a *self-consistent* transaction that the server rejects, rather
     *   than one whose `hotbarSlot` and `itemInHand` contradict each other.
     * @param reference pre-resolved (block-to-click, face) to use instead of searching. Callers that
     *   already know the base block pass it to preserve their existing, in-game-verified behaviour
     *   (PistonCrystalModule's end-crystal path knows the block below is obsidian/bedrock).
     * @param predictLocally optimistically write the placed block into our own world model.
     *   **Off by default and dangerous to enable loosely**: Bedrock servers silently drop invalid
     *   transactions (there is no NACK), so a rejected placement that was predicted locally stays
     *   marked as done forever, with nothing to correct it and every later validity check against
     *   that cell wrongly succeeding. Only multi-step sequences that validate step N+1 against step
     *   N (piston -> crystal -> redstone) need it.
     */
    fun place(
        slot: Int,
        pos: Vector3i,
        reference: Pair<Vector3i, Int>? = null,
        predictLocally: Boolean = false,
    ): PlacementResult {
        // Read the item from the slot we're about to name in the packet, not from `hand`. See the
        // `slot` param doc above.
        val itemInHand = session.localPlayer.inventory.content[slot]

        val (referencePos, referenceFace) = reference
            ?: PlacementRules.findReferenceBlock(::blockDefinitionAt, pos)
            ?: return PlacementResult.NO_REFERENCE_BLOCK

        val packet = PlacementRules.build(
            itemInHand = itemInHand,
            hotbarSlot = slot,
            blockPosition = referencePos,
            blockFace = referenceFace,
            clickPosition = PlacementRules.clickPositionForFace(referenceFace),
            playerPosition = session.localPlayer.vec3Position,
            // Must describe the EXISTING block being clicked, not the item being placed - see
            // PlacementRules.build's doc.
            blockDefinition = blockDefinitionAt(referencePos),
        )

        send(packet)

        if (predictLocally) {
            PlacementRules.predictLocalBlockChange(
                setBlock = { target, runtimeId ->
                    session.level.setBlockIdAt(target.x, target.y, target.z, runtimeId)
                },
                pos = pos,
                definition = PlacementRules.blockDefinitionFor(blockMappingOrNull, itemInHand),
            )
        }

        return PlacementResult.PLACED
    }

    private fun blockDefinitionAt(pos: Vector3i): BlockDefinition = session.level.getBlockAt(pos)

    /** `blockMapping` is `lateinit`, so it must not be read before the mapping is installed. */
    private val blockMappingOrNull: BlockMapping?
        get() = if (session.isBlockMappingInitialized) session.blockMapping else null

    /**
     * Sends [packet].
     *
     * The old `[AutoPlaceLog]` chat dump is gone: it lived in PacketDebugLog, which was removed
     * together with PacketLoggerModule when the placing modules were dropped. Connection-level
     * diagnostics now go through NetLog instead.
     */
    private fun send(packet: InventoryTransactionPacket) {
        session.serverBound(packet)
    }
}

/** Outcome of [BlockPlacer.place]. */
enum class PlacementResult {
    /** The transaction was built and sent. Says nothing about whether the server accepted it. */
    PLACED,

    /**
     * [pos] has no solid neighbour to click (it's floating in open air, or its chunk isn't tracked
     * yet, so every neighbour reads back as air/unknown). Retrying on a later tick is meaningful -
     * unlike a missing item, which will not fix itself.
     */
    NO_REFERENCE_BLOCK,
}

/**
 * The pure half of placement: no [GameSession], no Android context, so it can be exercised directly
 * from unit tests. [BlockPlacer] is the only production caller.
 */
internal object PlacementRules {

    /**
     * `(direction from the neighbour toward `pos`, block-face index of that neighbour to click)`,
     * tried in this order.
     *
     * Face index convention: **0=down, 1=up, 2=north, 3=south, 4=west, 5=east**.
     *
     * This mapping is NOT provided by the protocol library and cannot be read off it: the serializer
     * writes `blockFace` as a raw byte with no enum, constant or comment defining the directions
     * (relay/Protocol .../v2193/serializer/InventoryTransactionSerializer_v2193.java:89 — see
     * PROTOCOL_NOTES.md and ADR-0001). It is the vanilla client convention, corroborated by ProtoHax
     * and verified in-game. Pinned by `BlockPlacerTest` so a future edit that flips a sign fails.
     *
     * Order matters when several neighbours are solid - it decides which one gets clicked - so it is
     * preserved from the original implementation rather than reordered for readability.
     */
    internal val FACES = listOf(
        Vector3i.from(0, -1, 0) to 0, // pos lies below the neighbour -> click its DOWN face
        Vector3i.from(0, 1, 0) to 1,  // pos lies above the neighbour -> click its UP face
        Vector3i.from(0, 0, -1) to 2, // pos lies north of the neighbour -> click its NORTH face
        Vector3i.from(0, 0, 1) to 3,  // south
        Vector3i.from(-1, 0, 0) to 4, // west
        Vector3i.from(1, 0, 0) to 5,  // east
    )

    /**
     * Finds an existing non-air neighbour of [pos] to use as the transaction's
     * `blockPosition`/`blockFace` (the new block ends up at [pos], on the far side of the returned
     * face). Returns null if [pos] is fully isolated - a real Minecraft client couldn't place there
     * either.
     *
     * Prefers a neighbour positively confirmed solid: sending `blockDefinition` as
     * `minecraft:unknown` gets the whole transaction rejected by the server (confirmed via
     * `[AutoPlaceLog]`), since the server validates that field against its own world state. Only
     * falls back to an unconfirmed guess - still worth trying - if nothing around [pos] is known.
     */
    internal fun findReferenceBlock(
        lookup: (Vector3i) -> BlockDefinition,
        pos: Vector3i,
    ): Pair<Vector3i, Int>? {
        var fallback: Pair<Vector3i, Int>? = null
        for ((toPos, face) in FACES) {
            val neighbour = pos.add(-toPos.x, -toPos.y, -toPos.z)
            val identifier = lookup(neighbour).identifier
            if (identifier != "minecraft:air" && identifier != "minecraft:unknown") {
                return neighbour to face
            }
            if (identifier == "minecraft:unknown" && fallback == null) {
                fallback = neighbour to face
            }
        }
        return fallback
    }

    /**
     * Runtime block definition for the item that's about to be placed, used only for
     * [predictLocalBlockChange]. Returns null for non-block items (like `minecraft:end_crystal`,
     * which spawns an entity) and while the block mapping isn't loaded yet.
     */
    internal fun blockDefinitionFor(
        blockMapping: BlockMapping?,
        itemInHand: ItemData,
    ): BlockDefinition? {
        val mapping = blockMapping ?: return null
        val identifier = itemInHand.definition?.identifier ?: return null
        val runtimeId = mapping.getRuntimeIdByIdentifier(identifier) ?: return null
        return mapping.getDefinition(runtimeId)
    }

    /**
     * A `clickPosition` consistent with [face]: the axis matching the clicked face pinned to its
     * boundary (0f or 1f), the other two randomized within the face like a real click would be
     * (e.g. (0.875, 1.0, 0.125) for an up-face click).
     *
     * Both axes of the convention matter. Pinned to the face boundary because a real client's click
     * lands *on* the clicked surface, and randomized because a fixed point on every single placement
     * is a constant a server-side heuristic can fingerprint. Do not "simplify" this back to a fixed
     * centre - see ADR-0001.
     *
     * [random] is injected so tests can assert the per-face pinning deterministically.
     */
    internal fun clickPositionForFace(
        face: Int,
        random: () -> Float = { Math.random().toFloat() },
    ): Vector3f {
        val rx = random()
        val ry = random()
        val rz = random()
        return when (face) {
            0 -> Vector3f.from(rx, 0f, rz)
            1 -> Vector3f.from(rx, 1f, rz)
            2 -> Vector3f.from(rx, ry, 0f)
            3 -> Vector3f.from(rx, ry, 1f)
            4 -> Vector3f.from(0f, ry, rz)
            5 -> Vector3f.from(1f, ry, rz)
            else -> Vector3f.from(0.5f, 0.5f, 0.5f)
        }
    }

    /**
     * The [InventoryActionData] a real client always includes alongside an `ITEM_USE` placement,
     * describing the held item being consumed from the hotbar slot. Missing this is what made every
     * placement from these modules get silently dropped on `inventoriesServerAuthoritative` servers
     * (the modern default) - confirmed against a real captured placement packet, which always had
     * exactly one of these.
     */
    internal fun consumeItemAction(hotbarSlot: Int, current: ItemData): InventoryActionData {
        val afterUse = if (current.count > 1) {
            current.toBuilder().count(current.count - 1).build()
        } else {
            ItemData.AIR
        }
        return InventoryActionData(
            InventorySource.fromContainerWindowId(0),
            hotbarSlot,
            current,
            afterUse,
        )
    }

    /**
     * Builds the `ITEM_USE` transaction. Pure: same inputs, same packet, no session.
     *
     * Two fields are worth spelling out, because both were got wrong historically and neither is
     * documented by the protocol library:
     *
     *  - `blockDefinition` must describe the **existing block being clicked** ([blockPosition]), not
     *    the block or item being placed. A real captured packet held the wall block that was
     *    clicked, not the obsidian being placed; using the new block here was backwards. The server
     *    validates this against its own world state and silently rejects the whole transaction on a
     *    mismatch.
     *  - `actionType` is a plain `int` with no constants and no documented values anywhere in the
     *    library (the only hint is a stray `// Interact|Attack|ItemInteract` on an unrelated legacy
     *    class, `ItemUseTransaction.java:19`). `0` is what in-game-captured placement packets used
     *    (verified via PacketLoggerModule) - do not "correct" it from a guess. See ADR-0001.
     *
     * `headPosition` is deliberately left null: the library only serializes it in the `ITEM_RELEASE`
     * branch (`InventoryTransactionSerializer_v291.java:86`, via a `checkNotNull`), so it is never
     * read on the `ITEM_USE` path.
     *
     * `triggerType` and `clientInteractPrediction` MUST be set. Both are null by default and
     * `InventoryTransactionSerializer_v1001.writeItemUse` (still the implementation used by every
     * newer codec, incl. v2225) calls `.ordinal()` on them - leaving them null throws an NPE while
     * encoding. The relay catches that per-send and only prints it to stdout, so the placement
     * packet silently never leaves the device. This was why Scaffold appeared to do nothing.
     */
    internal fun build(
        itemInHand: ItemData,
        hotbarSlot: Int,
        blockPosition: Vector3i,
        blockFace: Int,
        clickPosition: Vector3f,
        playerPosition: Vector3f,
        blockDefinition: BlockDefinition,
    ): InventoryTransactionPacket = InventoryTransactionPacket().apply {
        transactionType = InventoryTransactionType.ITEM_USE
        actionType = 0
        triggerType = ItemUseTransaction.TriggerType.PLAYER_INPUT
        clientInteractPrediction = ItemUseTransaction.PredictedResult.SUCCESS
        this.blockPosition = blockPosition
        this.blockFace = blockFace
        this.hotbarSlot = hotbarSlot
        this.itemInHand = itemInHand
        this.playerPosition = playerPosition
        this.clickPosition = clickPosition
        this.blockDefinition = blockDefinition
        actions.add(consumeItemAction(hotbarSlot, itemInHand))
    }

    /**
     * Writes the placed block into our own world model instead of waiting for the server to echo an
     * `UpdateBlockPacket` back. Matters for placement sequences (piston -> crystal -> redstone, or
     * Surround's many-blocks-per-tick ring) where a later step's validity checks query
     * `level.getBlockAt()` and would otherwise still see stale (air) data for up to a full
     * round-trip. See [BlockPlacer.place]'s `predictLocally` for why this is opt-in.
     *
     * [setBlock] is injected so this stays testable without a Level.
     */
    internal fun predictLocalBlockChange(
        setBlock: (Vector3i, Int) -> Unit,
        pos: Vector3i,
        definition: BlockDefinition?,
    ) {
        definition ?: return
        setBlock(pos, definition.runtimeId)
    }
}
