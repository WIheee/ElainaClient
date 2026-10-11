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
- **Click-GUI themes** — Classic / Kitsu / Grace / Protohax, selectable in Settings.
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

See `CLAUDE.md` for an architecture overview, `PROTOCOL_NOTES.md` for protocol/codec details and how
to bump the Bedrock protocol version, and `PLACEMENT_FIX_NOTES.md` for the item-placement modules.

## License

GPLv3 — see [LICENSE](LICENSE). Redistributed/modified versions must ship their source.

## Credits

- This fork: <https://github.com/WIheee/ElainaClient>
- Based on the **WClient** project (original authors: Prajwal, RA Legend, Flexy H6X).
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

**Android / UI**
- [AndroidX](https://developer.android.com/jetpack/androidx) & [Jetpack Compose](https://developer.android.com/jetpack/compose) (incl. Material 3, Navigation, Lifecycle, Activity)
- [Kotlin](https://kotlinlang.org) · [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) · [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
- [OkHttp](https://square.github.io/okhttp/) (Square) · [Coil](https://github.com/coil-kt/coil) · [androidx.browser](https://developer.android.com/jetpack/androidx/releases/browser) (Chrome Custom Tabs)

**Text / serialization / logging**
- [Gson](https://github.com/google/gson) · [Jackson](https://github.com/FasterXML/jackson) · [Kyori Adventure](https://github.com/KyoriPowered/adventure) · [Apache Log4j 2](https://logging.apache.org/log4j/2.x/)

## Disclaimer

Provided **"AS IS"**, without warranty. Use at your own risk and only where you are allowed to —
you are responsible for complying with any server rules and local laws.
