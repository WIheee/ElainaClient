# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

WClient is an Android utility/modification client for **Minecraft Bedrock Edition (MCPE)**. It does
not touch game memory. Instead it is an on-device **MITM proxy**: it runs a local RakNet/Bedrock
server that a real MCPE client connects to, opens a second connection out to the real server, and
sits in the middle of the packet stream. Modules observe/rewrite/inject/suppress packets in either
direction to implement features (combat, motion, visual, world, misc).

Note: game modules here are cheats (killaura, fly, ESP, etc.). This file documents structure only;
treat module *behavior* as given, don't extend it speculatively.

## Modules (Gradle)

- `:app` — Android application (`com.retrivedmods.wclient`). Compose UI + the module system + the
  game-state model. Depends on `:relay`.
- `:relay` — plain Kotlin/JVM library (`com.retrivedmods.wrelay`). The proxy engine: RakNet server,
  upstream client, codec negotiation, packet listeners, auth handshake. **No Android SDK dependency** —
  it can't use `android.util.Log`; it logs via `println` (visible in logcat for the app process).
- `relay/Network/*` (codec-query, codec-rcon, transport-raknet) — vendored, listed in
  `settings.gradle.kts`, but **nothing depends on them**. `relay/Protocol/*` — the vendored (newest)
  CloudburstMC Protocol **source**, kept in the tree as reference, **not built**.
  `:relay` compiles against **Maven** instead (`org.cloudburstmc.protocol:bedrock-codec/connection/common`
  `3.0.0.Beta13-SNAPSHOT` from opencollab's repos). Don't "fix" the vendored copies. See `PROTOCOL_NOTES.md`.

## Build / run / test

```bash
./gradlew :app:assembleDebug            # debug APK (signed with buildKey.jks, alias UntrustedKey, pw 123456)
./gradlew :app:assembleRelease          # release APK (R8 minify + shrink, fullMode on)
./gradlew :relay:test                   # relay unit tests (JUnit 5 / useJUnitPlatform)
./gradlew :relay:test --tests "com.retrivedmods.wrelay.SomeTest"   # single relay test
./gradlew :app:testDebugUnitTest        # app unit tests (JUnit 4)
./gradlew :app:connectedDebugAndroidTest  # instrumented tests (needs a device/emulator)
```

Test deps are wired up (JUnit 5 in `:relay`, JUnit 4 + Espresso + Compose UI-test in `:app`), but
there are currently **no test source dirs or `*Test.kt` files** — the tasks above pass with zero tests.
Don't expect an existing test to crib from.

Toolchain: Gradle 8.13, AGP 8.13.0, Kotlin 2.2.20, **JDK 21** (`:app` `compileOptions`/`jvmTarget` 21,
`:relay` `jvmToolchain(21)`). `compileSdk 36`, `minSdk 28`, `targetSdk 35`, ABI filters `arm64-v8a` +
`armeabi-v7a`. Version catalog is `gradle/libs.versions.toml`.

Gotcha: the root `build.gradle.kts` registers `buildAll`/`buildDebugAll` tasks that depend on
`:desktop:shadowJar`, but **there is no `:desktop` module** in `settings.gradle.kts` — those tasks
will fail. Use `:app:assemble*` directly.

CI: `.github/workflows/build.yml` (workflow_dispatch only) runs `./gradlew assembleRelease` on JDK 21.

## Architecture

### Relay engine (`:relay`)

Packet flow, wired in `WRelay.capture()` (`relay/.../WRelay.kt`):

1. `WRelay` binds a RakNet **server** on `0.0.0.0:19132` advertising a Bedrock pong. When an MCPE
   client connects, `createSession0` builds a `WRelaySession` and calls `onSessionCreated`.
2. `WRelaySession` (`relay/.../WRelaySession.kt`) holds a `ServerSession` (toward the game client) and a
   `ClientSession` (toward the real server). Listener callbacks fire around each packet:
   `beforeClientBound`/`afterClientBound` (S→C) and `beforeServerBound`/`afterServerBound` (C→S).
   Returning `true` from a `before*` callback **swallows** the packet (does not forward it).
3. Packets that arrive before the upstream `client` exists are queued in `packetQueue` and flushed
   when `client` is assigned.

Listeners (`WRelayPacketListener`, in `relay/.../listener/`) are added in order by the app. Key ones:

- `AutoCodecPacketListener` — on the client's `RequestNetworkSettingsPacket`, selects the best codec by
  protocol version and patches it (InventoryContent/InventorySlot serializers to v729 for proto >729),
  sets `codecHelper` definitions/encoding limits, replies with `NetworkSettingsPacket`, enables ZLIB.
- `OnlineLoginPacketListener` — only added when an account is selected. Holds the client's `LoginPacket`,
  connects upstream, sends a fresh chained `LoginPacket` from the stored credentials, and completes
  `ServerToClientHandshake` encryption.
- `VersionTrackingListener` — reports detected protocol/Minecraft version to the UI.
- `GamingPacketHandler` — sets item/block/camera definitions on `StartGamePacket` / `CameraPresetsPacket`.
- `TransferPacketListener` — rewrites an outgoing `TransferPacket` target to the proxy's local
  address so the client reconnects through the relay, and updates `wRelay.remoteAddress`.

`CodecRegistry` (`relay/.../codec/CodecRegistry.kt`) registers codecs by **reflecting** on
`org.cloudburstmc.protocol.bedrock.codec.v{N}.Bedrock_v{N}.CODEC`. A wrong/missing class name is a
non-fatal no-op (logged) — `getClosestCodec()` then falls back to the nearest lower version.
**Only protocol 924…2225 (Minecraft 1.26.x) is registered** (`VersionDetector` mirrors this). The
codec classes form a long inheritance chain (`v924 extends v898`, `v975 extends v944`, …), so the
*source* can't be pruned by version — only the *registration*. To advertise a newer protocol (or to
build the protocol from the vendored source), follow `PROTOCOL_NOTES.md`.

`Definitions` (`relay/.../definition/`) loads a bundled `nbt/block_palette.nbt` into hashed/non-hashed
block definition registries plus an item registry. `ConnectionManager` handles upstream connect with
retry/backoff/rate limit, using `EnhancedServerConfig` presets (FAST/DEFAULT/AGGRESSIVE) chosen by
`ServerCompatUtils.isProtectedServer`.

### App: wiring the proxy to the UI

`service/Services.kt` is the orchestrator (singleton `object`). `Services.toggle()` (called from
`HomePage.kt`) starts, on a `WRelayThread`:

- loads block palette + module config,
- builds a `WRelay` (or `captureGamePacket(...)`) pointed at the user's `CaptureModeModel` server,
- in `onSessionCreated` → `initModules()`: creates a `GameSession`, adds it as a listener, then adds
  `AutoCodecPacketListener`, optional `OnlineLoginPacketListener`, `GamingPacketHandler`.

`GameSession` (`game/GameSession.kt`) is the bridge: it is a `ComposedPacketHandler` (a relay listener),
owns `LocalPlayer` + `Level` (world state built from incoming packets), sets up codecHelper
definitions/block mappings from `StartGamePacket`, and dispatches every packet to all modules. It also
overrides `beforeClientBound`/`beforeServerBound` separately so modules can tell packet direction via
`InterceptablePacket.isClientBound`.

An always-on overlay (`OverlayManager` + `render/RenderOverlayView` as a `TYPE_APPLICATION_OVERLAY`
window) draws HUDs/ESP; `RelayService` is a foreground service (`:relay` process) that keeps things alive.

### Module system (`app/game`)

- `Module` (abstract) — has `name`, `category`, `isEnabled` (Compose state), a `session: GameSession`,
  and implements `InterruptiblePacketHandler` + `AutoConfiguration`. `isEnabled` setter fires
  `onEnabled`/`onDisabled`. Modules with `private = true` are excluded from config save/export.
- `ModuleManager` (object) — the single registry. **New modules must be added to its `init` block**;
  there is no auto-discovery. `saveConfig`/`loadConfig` persist to
  `filesDir/configs/UserConfig.json` (external export goes to `Documents/WClient/configs`).
- `ComposedPacketHandler` / `InterruptiblePacketHandler` — the module packet hooks. `beforePacketBound`
  returns whether to swallow; `afterPacketBound`/`onDisconnect` are optional.
- `ModuleValues.kt` — `AutoConfiguration` builds typed settings (`boolValue`, `floatValue`, `intValue`,
  `enumValue`, `listValue`) that double as Kotlin delegates and serialize to JSON. This is how per-module
  options are declared and persisted.
- Each module implements `onEnabled`/`onDisabled` and inspects packets in `beforePacketBound`. When
  rewriting packets, use `Any.setPacketField(name, value)` (`util/PacketFieldUtil.kt`) — newer cloudburst
  builds dropped public setters on many fields, so reflection is required for things like `onGround`,
  `text`, `needsTranslation`. Preference/config values live in `game/data`, entities in `game/entity`,
  block/item name↔id mapping in `game/registry`.

Module categories are the `ModuleCategory` enum (`game/ModuleCategory.kt`) with one subpackage each:
`module/combat`, `module/motion`, `module/visual`, `module/world`, `module/misc`. Supporting state
lives alongside: `game/player` (LocalPlayer), `game/world` (Level + chunk palettes), `game/entity`,
`game/inventory`, `game/friend` (friends list, consulted by combat modules to skip targets).

Chat commands: `.`-prefixed chat lines are intercepted by `CommandManager.handleChat`
(`game/command/manager/CommandManager.kt`), which dispatches to `Command` impls in
`game/command/impl` (`FriendCommand`, `HelpCommand`). `CommandManager.init()` must be called to
register them; `CommandHandlerModule` (misc) routes incoming chat into it.

### Auth / accounts

`game/AccountManager.kt` persists `BedrockAuthManager` sessions as JSON under `cacheDir/accounts/`,
refreshing tokens every 30 min. `AccountManager.GAME_VERSION` and `util/MinecraftUtils.RECOMMENDED_VERSION`
must stay in sync. `service/RealmsManager.kt` and `game/RealmsAuthFlow.kt` handle Realms listing/join.
Login to Mojang/Xbox uses `net.raphimc:MinecraftAuth`.

### UI (`app`)

Compose + Navigation (`Navigation.kt` → `MainScreen`). `MainScreen` renders an animated `IconSidebar` over
six pages (`router/main/*Page.kt`) selected by `MainScreenViewModel`: Home (start/stop capture, pick target
game), Account, Server, Realms, Settings, About. Theming in `ui/theme/Theme.kt`. Game settings persist
in `SharedPreferences("game_settings")`.

## Conventions & gotchas

- The codebase carries unusually long explanatory comments documenting past Bedrock-protocol workarounds
  (blob-cache disabling in `GameSession`, block-mapping source ordering, v2168/v2193 codec registration,
  the Beta1→Beta12/13 dependency jump). Read them before changing adjacent behavior — they encode
  hard-won protocol details.
- `ClientCacheStatusPacket` is deliberately force-set to `isSupported = false` so servers send full chunk
  data (there's no blob-fetch implementation).
- `GameSession` prefers the bundled block-mapping asset over the server's `StartGamePacket.blockProperties`
  (which is usually a partial custom-block list, not the full palette); the live palette is last-resort.
- Diagnostic chat messages from inside `StartGamePacket` handling are queued and flushed on the first
  `PlayerAuthInputPacket`, because injected `TextPacket`s don't display before the session is spawned.
- `relay/build.gradle.kts` pins cloudburst `3.0.0.Beta13-SNAPSHOT`; the vendored `relay/Protocol/*`
  sources are not on the app's classpath (reference only — see `PROTOCOL_NOTES.md`).
- `WRelaySession`'s `client` setter runs during the upstream peer's **`channelActive`** (BedrockPeer
  eagerly creates session 0 there), i.e. possibly after the downstream game client has gone. Never
  read `server.codec` / `server.peer.codecHelper` on that path (it NPEs once downstream closes) — use
  the codec captured at negotiation (`onCodecNegotiated` / `negotiatedProtocolVersion()`).
- `Module.isEnabled`'s setter only fires `onEnabled`/`onDisabled` when `isSessionCreated`: config
  restore (`Module.fromJson`) runs before any `GameSession` exists, and those callbacks read `session`.
- This repo lives on a **FUSE mount** (`/storage/emulated/0`): `cp -a` **and** `cp -r` can produce files
  whose `stat` size reads 0 while the content is intact. Copy source trees file-by-file and verify with
  `find <dir> -type f -size 0`.
- Companion docs: `PROTOCOL_NOTES.md` (protocol pitfalls + how to upgrade the protocol library),
  `PLACEMENT_FIX_NOTES.md` (item-placement modules).
- Placement modules (PistonCrystal/Surround/Scaffold) don't hand-build `ItemUse` transactions: they
  cache a real outgoing `ITEM_USE` placement from the player and clone it, sanitize the held `ItemData`
  net-id, rebuild server-authoritative inventory actions, and update local block state immediately.
  See `PLACEMENT_FIX_NOTES.md` before touching this path.
