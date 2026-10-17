# ElainaClient

An on-device utility client for **Minecraft Bedrock Edition (MCPE)**.

ElainaClient does **not** touch game memory. It works as a local **MITM proxy**: it runs a
RakNet/Bedrock server that the game connects to, opens a second connection to the real server, and
sits in the packet stream. Modules observe, rewrite, inject or drop packets in either direction to
implement their features.

> This is a personal fork. It is not affiliated with Mojang Studios or Microsoft.

## Features

Modules are grouped into categories:

- **Combat** — PvP helpers and automated combat logic
- **Motion** — movement and speed
- **Visual** — rendering and awareness (ESP, HUD, minimap, …)
- **World** — world/visual state tweaks
- **Misc** — everything else (chat commands, logging, utilities)

Per-module settings, the enabled set and shortcuts persist as JSON (`UserConfig.json`), exportable to
`Documents/ElainaClient/configs`. Chat commands are `.`-prefixed.

## Interface

- **Adaptive home** — a Material 3 shell (bottom navigation on phones, a rail on wide screens) with a
  dashboard for the account, target game, server and service status.
- **Light / dark** — follows the system by default, with a manual 系统 / 浅色 / 深色 override in
  Settings. In-game overlays stay dark (they sit over the game).
- **Click-GUI themes** — Classic / Kitsu / Grace / Protohax / Dream / HackAI / Pianaixel / PixelSight /
  LiquidPE, selectable in Settings. Kitsu, Grace and Protohax each have their own layout (ported from
  LuminaCN); Grace uses Elaina's violet palette. The five OpenClickGUI themes (Dream, HackAI,
  Pianaixel, PixelSight, LiquidPE) reproduce their original layouts: Dream / HackAI / Pianaixel /
  PixelSight are re-skinned with Elaina's brand palette and carry the "Elaina" name, while
  **LiquidPE was rewritten in Kotlin from the original Canvas renderer** (same drawing/animation
  and layout logic, modern sealed data model) and keeps the author's colors untouched; it uses
  the project's own Minecraft font.
- **界面 page** — a dedicated UI settings page: pick any click-GUI theme and set its own scale
  (80 %–160 %). All click-GUI code lives under `overlay/`; LiquidPE's original Canvas view is
  hosted directly and uses the project's own Minecraft font.
- **Module list HUD** — a Horion-style on-screen list of enabled modules (rainbow/category colours,
  outline/edge styles, adjustable size & spacing, optional Minecraft font).
- **In-game notifications** — non-intrusive top-centre toasts for module toggles and other events.
- Chinese UI throughout.

## Building

Requirements: **JDK 21**, Android SDK (compileSdk 36). The Gradle wrapper is included.

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK (R8 minify + shrink)
```

> Don't use the root `buildAll` / `buildDebugAll` tasks — they reference a `:desktop` module that
> does not exist in this project.

## Project layout

- `:app` — Android app (Compose UI, module system, world/game-state model).
- `:relay` — the proxy engine (RakNet server, upstream client, codec negotiation, packet
  listeners, auth handshake). Plain Kotlin/JVM, no Android SDK dependency.

See `CLAUDE.md` for an architecture overview and `PROTOCOL_NOTES.md` for protocol/codec details and
how to bump the Bedrock protocol version. `PLACEMENT_FIX_NOTES.md` is historical only — the
crystal/placement modules it described were removed.

## License

GPLv3 — see [LICENSE](LICENSE). Redistributed/modified versions must ship their source.

## Credits

- This fork: <https://github.com/WIheee/ElainaClient>
- Based on the **WClient** project (original authors: Prajwal, RA Legend, Flexy H6X).
- UI ported from **LuminaCN** (Project Lumina, GPLv3) — the adaptive home shell / dashboard, the
  Kitsu / Grace / Protohax click-GUI layouts, and the in-game notification overlay.
- Click-GUI themes **Dream / HackAI / Pianaixel / PixelSight / LiquidPE** are refactored from
  **[OpenMoxel/OpenClickGUI](https://github.com/OpenMoxel/OpenClickGUI)** (formerly OpenClickGUI).
  Thank you to the OpenClickGUI maintainers. **LiquidPE** deserves a special thank-you: it is ported
  with its original LiquidBounce-style colour scheme and Minecraft font, exactly as its author
  designed it (LiquidPE author: QQ 1513583976 · atlasca3@gmail.com). Dream / HackAI / Pianaixel /
  PixelSight are reimplemented on one Compose shell; LiquidPE was rewritten in Kotlin (same
  drawing/animation logic, sealed data model) — all driven by Elaina's module system.
- The **Scaffold** module's placement strategy (candidate-cell search, reference-block/face
  selection, silent rotation, hotbar selection) is modelled on **LiquidBounce**
  (<https://github.com/CCBlueX/LiquidBounce>, GPLv3). Thank you! Only its algorithms and decision
  rules were studied — the Bedrock implementation is original and contains no copied code.
- The on-screen module list ("ArrayList") uses the Horion-style **HorionArrayList** from
  **openhal4a** (dev1503).
- Uses **CloudburstMC/Protocol** (`bedrock-codec` / `bedrock-connection` / `common`) for the Bedrock
  protocol; `relay/Protocol` contains the vendored source (see its `LICENSE`).
- Xbox/Mojang login via `net.raphimc:MinecraftAuth`.

Thanks to the open-source projects this app is built on (each under its own license):

**Bedrock protocol / networking**
- [CloudburstMC/Protocol](https://github.com/CloudburstMC/Protocol) — `bedrock-codec`, `bedrock-connection`, `common`
- [CloudburstMC/Network](https://github.com/CloudburstMC/Network) — `codec-query`, `codec-rcon`, `transport-raknet`
- [CloudburstMC/NBT](https://github.com/CloudburstMC/NBT) · [CloudburstMC/math](https://github.com/CloudburstMC/math)
- [Netty](https://netty.io) · [fastutil](https://github.com/vigna/fastutil) · [aircompressor](https://github.com/airlift/aircompressor)
- [MinecraftAuth](https://github.com/RaphiMC/MinecraftAuth) (RaphiMC) · [jose4j](https://bitbucket.org/b_c/jose4j)

**Reference implementations / inspiration**
- [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) (CCBlueX) — Scaffold placement algorithm reference
- **LuminaCN** (Project Lumina) — home shell and click-GUI layouts
- [OpenMoxel/OpenClickGUI](https://github.com/OpenMoxel/OpenClickGUI) — Dream / HackAI / Pianaixel /
  PixelSight / LiquidPE click-GUI layouts (refactored onto one Compose shell)
- **LiquidPE** (mi0ch622 / atlasca3@gmail.com) — special thanks; ported with its original colours

**Android / UI**
- [AndroidX](https://developer.android.com/jetpack/androidx) & [Jetpack Compose](https://developer.android.com/jetpack/compose) (incl. Material 3, Navigation, Lifecycle, Activity)
- [Kotlin](https://kotlinlang.org) · [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) · [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
- [OkHttp](https://square.github.io/okhttp/) (Square) · [Coil](https://github.com/coil-kt/coil) · [androidx.browser](https://developer.android.com/jetpack/androidx/releases/browser) (Chrome Custom Tabs)

**Text / serialization / logging**
- [Gson](https://github.com/google/gson) · [Jackson](https://github.com/FasterXML/jackson) · [Kyori Adventure](https://github.com/KyoriPowered/adventure) · [Apache Log4j 2](https://logging.apache.org/log4j/2.x/)

## Disclaimer

Provided **"AS IS"**, without warranty. Use at your own risk and only where you are allowed to —
you are responsible for complying with any server rules and local laws.
