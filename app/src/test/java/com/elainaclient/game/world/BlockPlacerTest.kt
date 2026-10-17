package com.elainaclient.game.world

import com.elainaclient.game.registry.BlockDefinition
import com.elainaclient.game.registry.UnknownBlockDefinition
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pins the placement rules that used to be duplicated across SurroundModule / PistonCrystalModule /
 * LocalPlayer and had already drifted apart. Everything here is pure - no GameSession, no Android
 * context - which is the point of the [PlacementRules] seam (see ADR-0001).
 */
class BlockPlacerTest {

    private val pos = Vector3i.from(10, 64, 10)
    private val above = pos.add(0, 1, 0)
    private val below = pos.add(0, -1, 0)

    // ---------------------------------------------------------------- face table

    @Test
    fun faceTableCoversEveryFaceExactlyOnce() {
        val faces = PlacementRules.FACES.map { it.second }
        assertEquals((0..5).toList(), faces.sorted())
        assertEquals(6, faces.distinct().size)
    }

    @Test
    fun everyFaceEntryIsAUnitOffset() {
        PlacementRules.FACES.forEach { (toPos, face) ->
            val manhattan = kotlin.math.abs(toPos.x) + kotlin.math.abs(toPos.y) + kotlin.math.abs(toPos.z)
            assertEquals("face $face must be a unit offset, was $toPos", 1, manhattan)
        }
    }

    /**
     * The 0=down, 1=up, 2=north, 3=south, 4=west, 5=east convention is NOT provided by the protocol
     * library (the serializer writes `blockFace` as a bare byte). It is external knowledge, so it
     * gets pinned here rather than trusted - see ADR-0001. A future edit that flips a sign or swaps
     * two axes fails this test instead of silently placing blocks on the wrong side.
     */
    @Test
    fun faceTableMatchesTheDocumentedIndexConvention() {
        val toPosByFace = PlacementRules.FACES.associate { (toPos, face) -> face to toPos }
        assertEquals(Vector3i.from(0, -1, 0), toPosByFace[0]) // 0 = down
        assertEquals(Vector3i.from(0, 1, 0), toPosByFace[1])  // 1 = up
        assertEquals(Vector3i.from(0, 0, -1), toPosByFace[2]) // 2 = north
        assertEquals(Vector3i.from(0, 0, 1), toPosByFace[3])  // 3 = south
        assertEquals(Vector3i.from(-1, 0, 0), toPosByFace[4]) // 4 = west
        assertEquals(Vector3i.from(1, 0, 0), toPosByFace[5])  // 5 = east
    }

    // ------------------------------------------------------------- reference search

    @Test
    fun usesTheOnlySolidNeighbourAndItsFacingFace() {
        // Solid below means we click that block's UP face (1).
        val result = PlacementRules.findReferenceBlock(lookup(below to "minecraft:stone"), pos)
        assertEquals(below to 1, result)
    }

    @Test
    fun prefersEarlierFacesWhenSeveralNeighboursAreSolid() {
        // Order in FACES decides, not proximity - preserved from the original implementation.
        val result = PlacementRules.findReferenceBlock(
            lookup(above to "minecraft:stone", below to "minecraft:stone"),
            pos,
        )
        assertEquals(above to 0, result)
    }

    @Test
    fun returnsNullWhenEveryNeighbourIsAir() {
        assertNull(PlacementRules.findReferenceBlock(lookup(), pos))
    }

    /**
     * An `unknown` neighbour is not positively solid, so it is only a last resort - but it beats
     * giving up, since the chunk may simply not be tracked yet.
     */
    @Test
    fun fallsBackToAnUnknownNeighbourWhenNothingIsConfirmedSolid() {
        val result = PlacementRules.findReferenceBlock(
            { p -> if (p == above) UnknownBlockDefinition(0) else BlockDefinition(1, "minecraft:air") },
            pos,
        )
        assertEquals(above to 0, result)
    }

    @Test
    fun prefersAConfirmedSolidNeighbourOverAnUnknownOne() {
        val result = PlacementRules.findReferenceBlock(
            { p ->
                when (p) {
                    above -> UnknownBlockDefinition(0)
                    below -> BlockDefinition(1, "minecraft:stone")
                    else -> BlockDefinition(2, "minecraft:air")
                }
            },
            pos,
        )
        assertEquals(below to 1, result)
    }

    // -------------------------------------------------------------- click position

    /**
     * The axis matching the clicked face is pinned to that face's boundary; the other two stay
     * random. A fixed centre - what the two live implementations used to send - is both geometrically
     * inconsistent with the declared face and a fingerprintable constant. See ADR-0001.
     */
    @Test
    fun clickPositionPinsTheAxisMatchingEachFace() {
        val rx = 0.1f
        val ry = 0.2f
        val rz = 0.3f

        assertVector(Vector3f.from(rx, 0f, rz), PlacementRules.clickPositionForFace(0, fixedRandom(rx, ry, rz)))
        assertVector(Vector3f.from(rx, 1f, rz), PlacementRules.clickPositionForFace(1, fixedRandom(rx, ry, rz)))
        assertVector(Vector3f.from(rx, ry, 0f), PlacementRules.clickPositionForFace(2, fixedRandom(rx, ry, rz)))
        assertVector(Vector3f.from(rx, ry, 1f), PlacementRules.clickPositionForFace(3, fixedRandom(rx, ry, rz)))
        assertVector(Vector3f.from(0f, ry, rz), PlacementRules.clickPositionForFace(4, fixedRandom(rx, ry, rz)))
        assertVector(Vector3f.from(1f, ry, rz), PlacementRules.clickPositionForFace(5, fixedRandom(rx, ry, rz)))
    }

    @Test
    fun clickPositionFallsBackToTheCentreForAnUnrecognisedFace() {
        assertVector(
            Vector3f.from(0.5f, 0.5f, 0.5f),
            PlacementRules.clickPositionForFace(99, fixedRandom(0.1f, 0.2f, 0.3f)),
        )
    }

    // ----------------------------------------------------------- consume action

    @Test
    fun consumeItemActionDecrementsAStack() {
        val two = ItemData.AIR.toBuilder().count(2).build()

        val action = PlacementRules.consumeItemAction(3, two)

        assertEquals(3, action.slot)
        assertEquals(InventorySource.Type.CONTAINER, action.source.type)
        assertEquals(2, action.fromItem.count)
        assertEquals(1, action.toItem.count)
    }

    @Test
    fun consumeItemActionEmptiesTheLastItem() {
        val one = ItemData.AIR.toBuilder().count(1).build()

        val action = PlacementRules.consumeItemAction(0, one)

        assertEquals(ItemData.AIR, action.toItem)
    }

    // -------------------------------------------------------------- packet contract

    /**
     * The wire contract, asserted without a session. This is the part that historically got the
     * fields backwards (blockDefinition describing the placed block instead of the clicked one) and
     * the part no other test can reach, since the live modules only verify in-game.
     */
    @Test
    fun buildProducesTheItemUseWireContract() {
        val item = ItemData.AIR.toBuilder().count(2).build()
        val referenceDefinition = BlockDefinition(42, "minecraft:stone")
        val clickPosition = Vector3f.from(0.25f, 1f, 0.75f)
        val playerPosition = Vector3f.from(1f, 2f, 3f)

        val packet = PlacementRules.build(
            itemInHand = item,
            hotbarSlot = 4,
            blockPosition = below,
            blockFace = 1,
            clickPosition = clickPosition,
            playerPosition = playerPosition,
            blockDefinition = referenceDefinition,
        )

        assertEquals(InventoryTransactionType.ITEM_USE, packet.transactionType)
        assertEquals(0, packet.actionType)
        assertEquals(below, packet.blockPosition)
        assertEquals(1, packet.blockFace)
        assertEquals(4, packet.hotbarSlot)
        assertEquals(item, packet.itemInHand)
        assertVector(clickPosition, packet.clickPosition)
        assertVector(playerPosition, packet.playerPosition)

        // Describes the block being CLICKED, not the item being placed.
        assertSame(referenceDefinition, packet.blockDefinition)

        // Exactly the one action a real client sends alongside a placement.
        assertEquals(1, packet.actions.size)
        assertEquals(4, packet.actions[0].slot)
        assertEquals(2, packet.actions[0].fromItem.count)
        assertEquals(1, packet.actions[0].toItem.count)

        // Only read on the ITEM_RELEASE path, so it must stay null here.
        assertNull(packet.headPosition)
    }

    // ------------------------------------------------------------------- helpers

    private fun lookup(vararg entries: Pair<Vector3i, String>): (Vector3i) -> BlockDefinition {
        val byPosition = entries.toMap()
        var nextRuntimeId = 1
        return { p ->
            val identifier = byPosition[p]
            if (identifier == null) {
                BlockDefinition(nextRuntimeId++, "minecraft:air")
            } else {
                BlockDefinition(nextRuntimeId++, identifier)
            }
        }
    }

    private fun fixedRandom(vararg values: Float): () -> Float {
        var index = 0
        return { values[index++] }
    }

    /** Component-wise: avoids depending on Vector3f's float equality semantics. */
    private fun assertVector(expected: Vector3f, actual: Vector3f) {
        assertEquals(expected.x, actual.x, 1e-6f)
        assertEquals(expected.y, actual.y, 1e-6f)
        assertEquals(expected.z, actual.z, 1e-6f)
    }
}
