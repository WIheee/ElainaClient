# Block placement is one seam with face-consistent, randomized clicks

**Status**: accepted — the original callers (SurroundModule / PistonCrystalModule) were removed in
the crystal-feature purge; `ScaffoldModule` is now the sole production caller. The rules below still
describe how it must work.

Module code places blocks through a single module (`game/world/BlockPlacer.kt`) instead of building
`ITEM_USE` transactions itself, and hotbar switching is a separate module (`game/hotbar/HeldSlot.kt`)
that the caller invokes first. Three of the fields involved are not what a reader would assume, and
none of them are documented by the protocol library — this records why the code does what it does, so
the next reader doesn't "fix" it.

## The three non-obvious decisions

**1. `clickPosition` is randomized and pinned to the clicked face.** A Placement names a face to
click, so a real client's click physically lands *on* that surface: the axis matching the face is
pinned to `0f` or `1f`, and the other two are random within the face. The earlier live implementations
sent a fixed `(0.5, 0.5, 0.5)` — geometrically inconsistent with the face they declared, and, because
it was identical on every single placement, a constant that a server-side heuristic can fingerprint.
_Considered_: keeping the fixed centre. Rejected on both counts above.

**2. Placement does not predict the block locally by default.** Bedrock servers silently drop invalid
transactions — there is no NACK — so a locally predicted Placement that the server rejected stays
marked as done in our own world model forever, with nothing to correct it, and every later validity
check against that cell wrongly succeeds. Only multi-step sequences that validate step N+1 against
step N (piston → crystal → redstone) opt in via `predictLocally`.
_Consequence_: callers that pass `predictLocally = true` take on that failure mode deliberately.

**3. The face index convention `0=down, 1=up, 2=north, 3=south, 4=west, 5=east` is external
knowledge, not library-provided.** The vendored reference source serializes `blockFace` as a bare
byte with no enum, constant, or comment defining the directions
(`relay/Protocol/bedrock-codec/.../v2193/serializer/InventoryTransactionSerializer_v2193.java:89`,
see `PROTOCOL_NOTES.md`). The mapping comes from the vanilla client convention, corroborated by
ProtoHax, and is verified in-game only — so it is pinned by `BlockPlacerTest` rather than trusted.
For the same reason `actionType = 0` is kept even though the library defines no values for it (the
only hint is a stray `// Interact|Attack|ItemInteract` on an unrelated legacy class); `0` is what
in-game-captured placement packets used.

_Consequence_: a future edit that reorders the face table, flips a sign, or "corrects" `actionType`
will fail a test or change placement behaviour on the wire. That is intended.

**4. `triggerType` and `clientInteractPrediction` must be set explicitly.** Both are nullable and
null by default on `InventoryTransactionPacket`. `InventoryTransactionSerializer_v1001.writeItemUse`
(which every newer codec, including v2193/v2225, still inherits) calls `.ordinal()` on both while
encoding, so a packet built without them throws a `NullPointerException` inside the Netty encoder.
The relay catches send errors per-packet, so the symptom is not a crash: the placement transaction
simply never reaches the server and Scaffold looks completely dead. Use
`TriggerType.PLAYER_INPUT` and `PredictedResult.SUCCESS`, matching a real client-initiated click.
(`hand` defaults to `MAINHAND`; `clientCooldownState` defaults to `0`.)

## Why hotbar switching is not part of it

Sending the server-bound slot change bypasses the interception pipeline that is the only place
`heldItemSlot` is updated, so the change must then be predicted locally — an ordering invariant that
previously lived as a comment in `SurroundModule` and was silently relied on by two other modules. It
now lives in the module that owns it.

The client → server hotbar selection is a `MobEquipmentPacket` (carrying the slot's item);
`PlayerHotbarPacket` is the server → client "select this slot" message and is not processed as a
client request. `HeldSlot.switchOnServer` therefore sends `MobEquipmentPacket`, while
`HeldSlot.switchOnClient` keeps using `PlayerHotbarPacket` for the display-only case.
