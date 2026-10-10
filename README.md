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

## Disclaimer

Provided **"AS IS"**, without warranty. Use at your own risk and only where you are allowed to —
you are responsible for complying with any server rules and local laws.
