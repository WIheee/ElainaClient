# Placement fix (historical)

> **Historical.** The modules this note describes — PistonCrystal and Surround (and the wider
> crystal playstyle: AntiCrystal, CrystalSmash, HotbarSwitcher) — were **removed on 2026-10-17**.
> Nothing calls the placement path today.

The work this note recorded was: align PistonCrystal/Surround placement with the decompiled latest
WClient —

- cache a real outgoing ITEM_USE placement transaction as a template;
- clone that transaction for module placements so newer protocol fields are preserved;
- use the protocol BlockDefinition from the held ItemData directly;
- sanitize ItemData net-id before sending;
- rebuild server-authoritative inventory actions;
- keep local block state updated immediately.

The decompiled latest WClient Scaffold (`com.retrivedmods.wclient.game.module.misc.a1`) was used as
the behavioral reference.

## Where that knowledge lives now

The refactor that followed consolidated it into one seam, which is **retained but has no caller**:

- `app/src/main/java/com/elainaclient/game/world/BlockPlacer.kt` — `BlockPlacer.place(...)` plus the
  pure `PlacementRules` object (reference-block search, face table, `clickPosition`, consume action,
  packet construction, optional local prediction).
- `app/src/main/java/com/elainaclient/game/hotbar/HeldSlot.kt` — the hotbar-switch ordering invariant.
- `app/src/test/.../BlockPlacerTest.kt` — pins the face-index convention and the packet contract.
- `docs/adr/0001-block-placement-seam.md` — why `clickPosition` is face-consistent and randomized,
  why local prediction is off by default, and why the `blockFace` index mapping is external knowledge.
