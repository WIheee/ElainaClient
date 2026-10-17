# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ElainaClient (a fork of the WClient project) is an Android utility/modification client for
**Minecraft Bedrock Edition (MCPE)**. It does
not touch game memory. Instead it is an on-device **MITM proxy**: it runs a local RakNet/Bedrock
server that a real MCPE client connects to, opens a second connection out to the real server, and
sits in the middle of the packet stream. Modules observe/rewrite/inject/suppress packets in either
direction to implement features (combat, motion, visual, world, misc).

Note: The game modules here are cheat features (kill aura, flight, ESP, etc.). When a user requests the creation of a new feature, please ask detailed questions; when enhancing modules, you must be detailed.

## Modules (Gradle)

- `:app` — Android application (`com.elainaclient`). Compose UI + the module system + the
  game-state model. Depends on `:relay`. Most deps come from the version catalog
  (`gradle/libs.versions.toml`), but a few are declared inline in `app/build.gradle.kts`:
  OkHttp, `androidx.browser` (Chrome Custom Tabs, used by `AuthWebView`), and Coil
  (`coil-compose` + `coil-gif`). `versionName = "B2"`, `versionCode = 2`.
- `:relay` — plain Kotlin/JVM library (`com.elainaclient.relay`). The proxy engine: RakNet server,
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
./gradlew :relay:test --tests "com.elainaclient.relay.SomeTest"   # single relay test
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

`OverlayManager` (`TYPE_APPLICATION_OVERLAY` windows) hosts the launcher button, the per-module
shortcut buttons, the click GUI, the notification toasts, and the HUD/ESP overlays;
`RelayService` is a foreground service (`:relay` process) that keeps things alive.

### Module system (`app/game`)

- `Module` (abstract) — has `name`, `category`, `isEnabled` (Compose state), a `session: GameSession`,
  and implements `InterruptiblePacketHandler` + `AutoConfiguration`. `isEnabled` setter fires
  `onEnabled`/`onDisabled`. Modules with `private = true` are excluded from config save/export.
- `ModuleManager` (object) — the single registry. **New modules must be added to its `init` block**;
  there is no auto-discovery. `saveConfig`/`loadConfig` persist to
  `filesDir/configs/UserConfig.json` (external export goes to `Documents/ElainaClient/configs`).
- `ComposedPacketHandler` / `InterruptiblePacketHandler` — the module packet hooks. `beforePacketBound`
  returns whether to swallow; `afterPacketBound`/`onDisconnect` are optional.
- **Packet rewrites need interception.** `WRelaySession` forwards the *raw* packet buffer, so mutating
  the decoded packet in a listener does **not** change what the server receives. To send a modified
  packet, call `interceptablePacket.intercept()` and re-send the object via `session.serverBound(...)`.
  `ScaffoldModule` is the reference pattern (silent rotation + movement injection); it must stay last
  in `ModuleManager` because interception stops dispatch to later modules. Its placement strategy is
  modelled on LiquidBounce (GPLv3, CCBlueX) — algorithms/rules only, no copied code; see the credit
  in `README.md`.
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
lives alongside: `game/entity` (the entity model — `LocalPlayer`, `Player`, `Entity`, `Item`,
`MobList`), `game/player` (`PlayerCache`), `game/world` (Level + chunk palettes under `world/chunk`),
`game/inventory`, `game/friend` (friends list, consulted by combat modules to skip targets).

Chat commands: `.`-prefixed chat lines are intercepted by `CommandManager.handleChat`
(`game/command/manager/CommandManager.kt`), which dispatches to `Command` impls in
`game/command/impl` (`FriendCommand`, `HelpCommand`). `CommandManager.init()` must be called to
register them; `CommandHandlerModule` (misc) routes incoming chat into it.

### Auth / accounts

`game/AccountManager.kt` persists `BedrockAuthManager` sessions as JSON under `cacheDir/accounts/`,
refreshing tokens every 30 min. `AccountManager.GAME_VERSION` and `util/MinecraftUtils.RECOMMENDED_VERSION`
must stay in sync. Login to Mojang/Xbox uses `net.raphimc:MinecraftAuth`.

Note: **all Realms support was removed** — `service/RealmsManager.kt`, `model/RealmWorld.kt`, and
`game/RealmsAuthFlow.kt` are deleted, and `AccountManager` no longer calls `RealmsManager`. The Bedrock
login MSA config (`BEDROCK_ANDROID_APPLICATION_CONFIG`) that `AuthWebView` uses now lives inline in
`ui/component/AuthWebView.kt`. The ad-gate is gone entirely (`auth/VerificationManager.kt` and
`ui/component/VerificationDialog.kt` were deleted).

### UI (`app`) — Material 3, adaptive light/dark

`MainActivity` is locked to **portrait** and runs full-screen immersive, then
`ElainaClientTheme(darkTheme = AppTheme.isDark()) { Navigation() }` — there is **no loading screen and
no ad-gate/verification** (both were removed). `Navigation.kt` is a thin single-destination `NavHost`
→ `MainScreen`.

**Home shell** (`router/main/MainScreen.kt`) is ported from LuminaCN-2.1.0: an adaptive shell —
left `NavigationRail` in landscape / bottom `NavigationBar` in portrait — over the `MainScreenPages`
enum (**主页 / 账号 / 关于 / 设置**), selected via `MainScreenViewModel.selectedPage` and swapped with
a direction-aware `AnimatedContent`. Each page still owns its own `Scaffold` + `TopAppBar`.

**Dashboard** (`HomePage.kt` → `HomePageContent`): account card, game picker, server config (IP/port),
a collapsible server-list card, and a service-status card, plus an `ExtendedFloatingActionButton` that
runs the overlay + notification permission flow, then `Services.toggle(...)`. There is **no separate
server page** any more — presets + user-added custom servers (persisted as JSON under the
`custom_servers` key of `SharedPreferences("game_settings")`) live inside the dashboard card via
`ServerPage.kt`'s content-only `ServerListContent()`.

**Theme**: `ui/theme/Theme.kt` keeps the violet `WColors` palette, but `WColors` is now used **only by
the always-dark overlay HUDs**. App pages use `MaterialTheme.colorScheme` so they follow the light/dark
scheme. Appearance is `ui/theme/ThemeMode.kt` (`object AppTheme`, values `SYSTEM/LIGHT/DARK`, persisted
in `SharedPreferences("settings")` under `theme_mode`) and is chosen in `SettingsPage`. Overlays stay
dark (they sit over the game): `OverlayManager` wraps every overlay's `Content()` in
`ElainaClientTheme { ... }`.

**Overlays / click GUI**: `OverlayManager` owns the launcher button, per-module shortcut buttons, and
the click GUI. The click GUI is skinned by a selectable `GUITheme` (`overlay/GUITheme.kt`: CLASSIC /
KITSU / GRACE / PROTOHAX / DREAM / HACKAI / PIANAIXEL / PIXELSIGHT / LIQUIDPE). **KITSU, GRACE and
PROTOHAX each have their own layout**, all ported from
LuminaCN-2.1.0: `overlay/gui/kitsu/` (`kitsugui/*`: 660×500 card, logo + category sidebar, header
link/close buttons), `overlay/gui/grace/` (`GraceMenuUi`: horizontal animated-gradient rail + 70 dp
module grid + left-sliding settings panel; layout from Lumina, re-skinned with Elaina's violet
palette in `GracePalette.kt`), and
`overlay/gui/protohax/` (`ProtohaxUi`: vertical Material3 rail + expand-in-place module cards). Only
CLASSIC uses the classic layout (`overlay/gui/classic/OverlayClickGUI.kt`) re-skinned by palette.

The **OpenClickGUI ports** have two shapes:

- `overlay/gui/open/` — Dream, HackAI, Pianaixel, PixelSight run on one parametrised Compose shell,
  `OpenGuiWindow`, configured by `OpenGuiSpecs` (`OpenGuiSpec.kt`): Dream (sidebar + card list +
  detail settings pane), HackAI (sidebar + cards + inline settings), Pianaixel (category column +
  rows + side settings), PixelSight (logo sidebar + cards + side settings + footer). They are
  re-skinned with Elaina's violet palette and show the "Elaina" brand.
- `overlay/gui/liquidpe/` — **LiquidPE was fully rewritten in Kotlin** (`LiquidPeRenderer`,
  `LiquidPeOverlayView`, `LiquidPeModels`, `LiquidPeStore`, `LiquidPeStyle`) from OpenClickGUI's
  Java renderer. Drawing/hit-testing/panel-drag/animations are preserved; the model uses sealed
  setting classes and null-safe properties. The unused HUD/binds renderer and the unused grid
  container were dropped. `LiquidPeGuiWindow` embeds `LiquidPeOverlayView` in an `AndroidView`;
  `LiquidPeBridge` builds LiquidPE's model from `ModuleManager` and pumps settings both ways
  (store→Elaina after touch, Elaina→store on draw). Colours are untouched; it uses the project's
  own `res/font/minecraft.ttf` (the original author's Roboto copy was removed). The search box is
  typed inline through a transparent Compose field laid over the Canvas box.

All click-GUI windows set `OverlayWindow.immersive = true`, which adds `FLAG_FULLSCREEN` and hides
the system bars at the view level, so the clock/status bar never shows over a GUI.

A dedicated **界面** page (`router/main/UISettingsPage.kt`, nav item) selects the theme and stores a
per-theme scale in `overlay/UISettings.kt` (`ui_scale_<THEME>`, 0.8–1.6). Each window reads it when
created; the Compose themes apply it with `graphicsLayer`, LiquidPE multiplies its reference
viewport (top-left anchored when the enlarged canvas overflows). Credit in `README.md` /
`AboutPage`.

`GUITheme.createClickGUI()`
(`overlay/gui/GuiThemeProvider.kt`) maps theme → window; `OverlayManager.openClickGUI()` builds it lazily
and `applyTheme()` re-attaches overlays after a change. Palettes live in `game/ClickGuiStyle.kt` +
`LocalClickGuiStyle` (CLASSIC's `ModuleContent`/`ConfigurationScreen` read it through `@Composable` colour
getters; KitsuGUI provides `ClickGuiStyle.Kitsu`). In-game toasts are
`overlay/notify/OverlayNotificationManager.show(message, type, durationMs)` — a non-touchable top-centre
`OverlayWindow`; `Module.sendToggleMessage` fires one on every enable/disable.

**本地化 (汉化)**: overlay/click-GUI labels are English identifiers. `game/ModuleNames.kt`
(`String.localizedModule()` / `localizedOption()`) maps module + option names to Chinese, falling back
to `util/TranslationUtil.kt`'s `translatedSelf`. **Add new modules to its `MODULE_NAMES` map.** HUD
fixed text (coordinates, target status, pie-chart legend …) is localised inline in the `overlay/hud/*`
files.

## Conventions & gotchas

- This is a **fork branded ElainaClient** (app package/applicationId `com.elainaclient`, relay
  `com.elainaclient.relay`, app label + Compose theme `ElainaClientTheme`, violet accent). UI is
  portrait Material 3 with an adaptive (rail/bar) shell. The original GPLv3 `LICENSE` is kept as-is;
  `README.md` credits the upstream WClient project and CloudburstMC/Protocol.
- **Colour rule**: app pages use `MaterialTheme.colorScheme` (they must follow light/dark). `WColors`
  is the dark **overlay-only** palette — don't reach for it in app UI. `ui/component/WComponents.kt`
  is theme-aware and uses a **static** glow (the per-frame `rememberInfiniteTransition` glow was
  removed for performance — don't reintroduce animated shadows).
- **Overlay HUD perf**: `Crosshair`/`Coordinates` skip their 16 ms loop in STATIC colour mode and
  `WaterMark` runs at ~20 fps. `ArrayListModule` pushes to the overlay only when the enabled set
  changes, and `ModuleContent`'s list is keyed — keep it that way.
- The on-screen module list ("ArrayList") HUD is the Horion-style `HorionArrayList` custom View
  (ported from the `openhal4a` project, under `overlay/hud/horion/`), embedded via `AndroidView` in
  `ArrayListOverlay`; `ArrayListModule`'s options are mapped onto it. **Read every option in the
  composable body, never inside `AndroidView`'s `update` lambda** — state read only inside `update`
  didn't reliably recompose, which is what made items show up only after toggling twice. Same for the
  click-GUI: KITSU is LuminaCN's layout (`overlay/gui/kitsu/`). Both are third-party ports — credit in
  `README.md`.
- Minecraft font: `res/font/minecraft.ttf` (the full ~16 MB font) backs `FontStyle.MINECRAFT` in the
  ArrayList (`ResourcesCompat.getFont`) and `WaterMarkOverlay`; it replaced the old small `minecraft.otf`.
- The enable/disable chat line (`Module.sendToggleMessage`) uses the theme-violet `§d` brand tag, not red.
- Removed from this fork: Realms (UI *and* backend), the startup loading screen, and the ad-gate
  (`VerificationManager`/`VerificationDialog`). Don't reintroduce them.
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
- There are **two** `ServerCompatUtils` objects and they are not the same thing:
  `relay/.../util/ServerCompatUtils.kt` (operates on a `WAddress`, returns `EnhancedServerConfig`
  presets — this is the one `ConnectionManager` uses) and `app/.../util/ServerCompatUtils.kt`
  (operates on a `String` hostname, returns UI-facing connection tips / a `ServerConfigType`).
  Grep the one you actually mean.
- Companion docs: `PROTOCOL_NOTES.md` (protocol pitfalls + how to upgrade the protocol library).
- **Crystal/placement features were removed** (2026-10-17): Surround, PistonCrystal, CrystalSmash,
  AntiCrystal, HitAndRun, HotbarSwitcher, FakeDeath, FakeXP and PacketLogger are gone, together with
  `BlockPlacementUtils`, `PacketDebugLog`, `LocalPlayer.placeBlock` and
  `Level.findPlacementReference`. This project does not target a crystal playstyle; do not reintroduce
  them without being asked. The placement seam is **retained but currently unused**:
  `game/world/BlockPlacer.kt` (+ internal `PlacementRules`, covered by `BlockPlacerTest`) and
  `game/hotbar/HeldSlot.kt` have **no production caller** — read
  `docs/adr/0001-block-placement-seam.md` before wiring anything to them.
  `PLACEMENT_FIX_NOTES.md` is now historical only.

## Agent skills

### Issue tracker

Issues live in GitHub Issues at WIheee/ElainaClient, driven by the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles map 1:1 to their default label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
