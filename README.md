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
  LuminaCN); Grace uses Elaina's violet palette. **Dream and HackAI are native Kotlin ports** of
  their original layouts, re-skinned with Elaina's violet palette. **LiquidPE is a Kotlin Canvas
  rewrite** of the original renderer and keeps the author's colours and Minecraft font untouched.
  Pianaixel and PixelSight are still Compose approximations while their native ports are pending.
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

ElainaClient is **GPLv3** — see [LICENSE](LICENSE). Redistributed/modified versions must ship their
source. Copyright (C) 2026 **WIheee** and ElainaClient contributors; upstream portions retain their
authors' copyright.

Third-party components remain under their own licenses; the full inventory (versions, licenses and
upstream URLs) is in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The bundled Minecraft font and
game-data palettes are Mojang/Microsoft assets and are **not** covered by the GPLv3 license.

## Credits

- **WIheee** — 核心贡献者 (core contributor) — <https://github.com/WIheee>
- This fork: <https://github.com/WIheee/ElainaClient>
- Based on the **WClient** project (GPL-3.0) —
  <https://github.com/RetrivedMods/WClient> (original authors: Prajwal, RA Legend, Flexy H6X).
- Home shell / dashboard, the Kitsu / Grace / Protohax click-GUI layouts and the notification
  overlay are ported from **Lumina / LuminaCN** (Project Lumina, GPL-3.0) —
  <https://github.com/TheProjectLumina/LuminaClient>. LuminaCN provides the Chinese localization.
- Click-GUI themes **Dream / HackAI / Pianaixel / PixelSight / LiquidPE** originate from
  **[OpenMoxel/OpenClickGUI](https://github.com/OpenMoxel/OpenClickGUI)** (formerly OpenClickGUI).
  Thank you to the OpenClickGUI maintainers. **LiquidPE** deserves a special thank-you: it is ported
  with its original LiquidBounce-style colour scheme and Minecraft font, exactly as its author
  designed it (LiquidPE author: QQ 1513583976 · atlasca3@gmail.com). Dream and HackAI have since
  been rewritten as native layouts; Pianaixel / PixelSight are still on the shared Compose shell
  while their native ports are pending. All themes are driven by Elaina's module system.
  **Note:** OpenClickGUI's upstream repository has no formal `LICENSE` file; its README states the
  project is open source and free to modify/integrate. See
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) before redistributing.
- The **Scaffold** module's placement strategy (candidate-cell search, reference-block/face
  selection, silent rotation, hotbar selection) is modelled on **LiquidBounce**
  (<https://github.com/CCBlueX/LiquidBounce>, GPL-3.0). Thank you! Only its algorithms and decision
  rules were studied — the Bedrock implementation is original and contains no copied code.
- The on-screen module list ("ArrayList") uses the Horion-style **HorionArrayList** from
  **openhal4a** (1503Dev, LGPL-2.1) — <https://github.com/1503Dev/openhal4a>.
- Uses **CloudburstMC/Protocol** (`bedrock-codec` / `bedrock-connection` / `common`, Apache-2.0) for
  the Bedrock protocol; `relay/Protocol` contains the vendored source (see its `LICENSE`).
- Xbox/Mojang login via `net.raphimc:MinecraftAuth` (LGPL-3.0).

The complete dependency/version/license inventory, including compatibility notes for the LGPL
components, is in **[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)**.

Thanks to the open-source projects this app is built on (each under its own license):

**Bedrock protocol / networking**
- [CloudburstMC/Protocol](https://github.com/CloudburstMC/Protocol) — Apache-2.0 — `bedrock-codec`, `bedrock-connection`, `common`
- [CloudburstMC/Network](https://github.com/CloudburstMC/Network) — Apache-2.0 — `codec-query`, `codec-rcon`, `transport-raknet`
- [CloudburstMC/NBT](https://github.com/CloudburstMC/NBT) (Apache-2.0) · [CloudburstMC/math](https://github.com/CloudburstMC/math) (MIT)
- [Netty](https://github.com/netty/netty) (Apache-2.0) · [fastutil](https://github.com/vigna/fastutil) (Apache-2.0) · [aircompressor](https://github.com/airlift/aircompressor) (Apache-2.0)
- [MinecraftAuth](https://github.com/RaphiMC/MinecraftAuth) (RaphiMC, LGPL-3.0) · [jose4j](https://bitbucket.org/b_c/jose4j) (Apache-2.0)

**Reference implementations / inspiration**
- [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) (CCBlueX, GPL-3.0) — Scaffold placement algorithm reference
- [Lumina / LuminaClient](https://github.com/TheProjectLumina/LuminaClient) (Project Lumina, GPL-3.0) — home shell and click-GUI layouts
- [OpenMoxel/OpenClickGUI](https://github.com/OpenMoxel/OpenClickGUI) — Dream / HackAI / Pianaixel /
  PixelSight / LiquidPE click-GUI layouts (Dream/HackAI now native ports)
- [openhal4a](https://github.com/1503Dev/openhal4a) (1503Dev, LGPL-2.1) — HorionArrayList
- **LiquidPE** (mi0ch622 / atlasca3@gmail.com) — special thanks; ported with its original colours

**Android / UI**
- [AndroidX](https://developer.android.com/jetpack/androidx) & [Jetpack Compose](https://developer.android.com/jetpack/compose) (Apache-2.0; incl. Material 3, Navigation, Lifecycle, Activity)
- [Kotlin](https://github.com/JetBrains/kotlin) (Apache-2.0) · [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) (Apache-2.0) · [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (Apache-2.0)
- [OkHttp](https://github.com/square/okhttp) (Apache-2.0) · [Coil](https://github.com/coil-kt/coil) (Apache-2.0) · [androidx.browser](https://developer.android.com/jetpack/androidx/releases/browser) (Apache-2.0)

**Text / serialization / logging**
- [Gson](https://github.com/google/gson) (Apache-2.0) · [Jackson](https://github.com/FasterXML/jackson) (Apache-2.0) · [Kyori Adventure](https://github.com/KyoriPowered/adventure) (MIT) · [Apache Log4j 2](https://github.com/apache/logging-log4j2) (Apache-2.0)

**Game assets**
- **Minecraft** is a trademark of Mojang Studios / Microsoft. The bundled Minecraft font and
  block/item palette data belong to their respective owners and are **not** covered by this
  project's GPL-3.0 license; they are included for interoperability/visual fidelity.

## Disclaimer

Provided **"AS IS"**, without warranty. Use at your own risk and only where you are allowed to —
you are responsible for complying with any server rules and local laws.
