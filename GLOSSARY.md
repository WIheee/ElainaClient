# ElainaClient

An Android MITM proxy for Minecraft Bedrock Edition. It runs a local Bedrock server that a real game
client connects to, opens a second connection to a real server, and sits between the two packet
streams so its modules can observe and rewrite them. The language below is the vocabulary of that
packet stream and of the world state built from it.

## Language

**Placement**:
A block appearing in the world because the client named an existing block and one of its faces to
click — the new block lands on the far side of that face, never at a position in empty air.
_Avoid_: build, block place

**Reference block**:
The existing, currently solid block a Placement names and clicks. It is not the block being placed;
conflating the two is a recurring bug in this codebase.
_Avoid_: target block, anchor block

**Face index**:
The numeric code for a block face as it travels on the wire. The protocol library defines no
constants or documentation for it, so the mapping is external knowledge.
_Avoid_: direction, side, normal

**Held-slot switch**:
Changing which hotbar slot the player holds. Two kinds exist and they are not interchangeable: one
the server is told about, and one that only changes what the local display shows.
_Avoid_: hotbar switch, slot change
