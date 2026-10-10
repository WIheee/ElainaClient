# Bedrock protocol notes — pitfalls & how to upgrade the protocol library

Companion to `CLAUDE.md`. Everything here is about the Bedrock **protocol layer** (`:relay` +
`relay/Protocol`), why it's wired the way it is, the traps that were hit, and the step-by-step for
bumping to a newer protocol in the future. Read this before touching codec registration, the login
handshake, or `WRelaySession`.

## Where the protocol actually comes from

- **Compiled against Maven**, not the vendored source. `relay/build.gradle.kts` pulls
  `org.cloudburstmc.protocol:bedrock-codec` / `bedrock-connection` / `common`
  **`3.0.0.Beta13-SNAPSHOT`** (opencollab snapshot repos, declared in `settings.gradle.kts`).
- `relay/Protocol/{adventure,bedrock-codec,bedrock-connection,common}` — vendored CloudburstMC/
  Protocol **source**, kept in the tree as reference, **NOT built** (not in `settings.gradle.kts`,
  no `project(...)` dep). It currently tracks codecs through **v2225**.
- `relay/Network/{codec-query,codec-rcon,transport-raknet}` — vendored, listed in
  `settings.gradle.kts`, but **nothing depends on them**.
- Codec selection is **reflective**: `CodecRegistry` loads
  `org.cloudburstmc.protocol.bedrock.codec.v{N}.Bedrock_v{N}.CODEC` by class name. Version labels:
  `VersionDetector`. Protocol→display-string: `VersionInfoProvider`/`VersionInfo`.

## Traps that were hit (2026-10)

1. **FUSE mount source-copy corruption.** This repo lives on `/storage/emulated/0` (FUSE/sdcardfs).
   **Both `cp -a` and `cp -r` can silently create files whose `stat` size reads 0** while the content
   is actually intact (`cmp` says identical, `md5sum` matches, but `diff -rq` falsely reports
   "differ" because it trusts the size). A compiler trusting `stat` would see empty files.
   **Rule: copy source trees file-by-file with plain `cp`, then verify with
   `find <dir> -type f -size 0`.** (Hit twice copying `relay/Protocol`: 4 files, then 17.)

2. **Codec classes are a long inheritance chain — you cannot delete old versions.** Examples from the
   vendored source: `Bedrock_v924 extends Bedrock_v898`, `Bedrock_v975 extends Bedrock_v944`,
   `Bedrock_v944 extends Bedrock_v924`, `Bedrock_v428 extends Bedrock_v422`… and the newer codecs also
   **import serializers from much older packages** (v786, v898, v361, …). Deleting any low-version
   package breaks compilation of the high-version ones. So "only keep 924+" is possible at the
   **registration** layer (`CodecRegistry`/`VersionDetector`), **never** at the source layer.

3. **`WRelaySession` upstream-codec NPE (crashed every join).** `BedrockPeer.channelActive` **eagerly**
   creates session 0 → `BedrockChannelInitializer.createSession` → `initSession` →
   `wRelaySession.client = clientSession` runs on the **upstream** event loop *while the background
   connect may still be retrying*. By then the game client may already have disconnected, so reading
   `server.codec` / `server.peer.codecHelper` (which go through
   `BedrockPeer.getCodec()` → `pipeline().get(BedrockPacketCodec.class)` — `null` once the downstream
   session closes) throws NPE. **Fix in place:** capture the codec + helper at negotiation time
   (`AutoCodecPacketListener` calls `wRelaySession.onCodecNegotiated(codec, helper)`), and have the
   `client` setter read the captured values. **Do not reintroduce `server.codec` reads on the
   upstream path** — use `wRelaySession.negotiatedProtocolVersion()`.

4. **Queue-drain NPE.** The old flush loop was
   `while (packetQueue.poll().also { pair = it } != null)` — `.also` still runs when `poll()` returns
   `null`, assigning `null` into the non-null `pair`. Fixed to
   `while (true) { val next = packetQueue.poll() ?: break; … }`.

5. **Module `lateinit session` crash on startup/shutdown.** `Module.isEnabled`'s setter used to fire
   `onEnabled()`/`onDisabled()` unconditionally, including during config restore
   (`Module.fromJson`) where no `GameSession` exists yet — subclasses like `InfiniteAuraModule` read
   `session` in those callbacks and threw `UninitializedPropertyAccessException`. Fixed: skip no-op
   sets and only fire when `isSessionCreated`.

6. **`Services.off()` disconnect on a closed session.** Disconnecting an already-closed Bedrock
   session throws `IllegalStateException("Session has been closed")`. Guarded with `isConnected`.

7. **Git merge left conflict markers committed as "resolved".** A bad pull/merge can leave
   `<<<<<<<` / `=======` / `>>>>>>>` inside files (it broke the build at `gradle/libs.versions.toml`
   line 29). Resolution: strip the markers keeping the wanted side; a stale `.git/index.lock` (0 bytes,
   no running git process) blocks `git checkout` restores — remove it once you've confirmed no git is
   running.

8. **R8 `RakChannel is defined multiple times`.** Having **both** Maven `netty-transport-raknet` and
   `project(":relay:Network:transport-raknet")` on the classpath breaks release R8. Keep only the
   Maven one (that's why the `project(...)` dep is not present in `relay/build.gradle.kts`).

## Codec registration model

```kotlin
registerCodec(protocolVersion, "minecraftVersion", "org.cloudburstmc.protocol.bedrock.codec.v{N}.Bedrock_v{N}")
```

`registerCodec` does `Class.forName(...)` + `getDeclaredField("CODEC")`. A class the artifact doesn't
ship is a **logged no-op** (an `ERROR: Failed to register codec …ClassNotFoundException` line in
logcat) — `getClosestCodec()` then falls back to the nearest registered version at or below the
requested protocol (or the lowest registered if the client is older than all of them).
**Currently only protocol 924…2225 (Minecraft 1.26.x) is registered.**

## How to upgrade to a newer protocol

### Scenario A — the Maven artifact already ships the new codec class (the common case)

1. Bump the snapshot in `relay/build.gradle.kts` if a newer one is needed
   (`3.0.0.BetaNN-SNAPSHOT`; the deps are inline strings, not in the version catalog).
2. `CodecRegistry.kt` — add `registerCodec(newProto, "mcVersion", "…v{newProto}.Bedrock_v{newProto}")`.
3. `VersionDetector.kt` — add `newProto to listOf("1.26.x", …)`.
4. Keep `AccountManager.GAME_VERSION` and `MinecraftUtils.RECOMMENDED_VERSION` in sync
   (`GAME_VERSION` feeds Xbox auth; `RECOMMENDED_VERSION` is the "Supports:" label on Home).
5. Rebuild and watch logcat: `Registered codec: …` means it loaded; an
   `ERROR: Failed to register codec …ClassNotFoundException` line means the artifact doesn't ship that
   class yet and the entry is a no-op fallback (harmless, but you're not actually on the new protocol).

### Scenario B — build the protocol from the vendored source instead of Maven

1. Update the source under `relay/Protocol/*` to the desired upstream revision
   (copy file-by-file — see trap **#1**).
2. `settings.gradle.kts` — add
   `include(":relay:Protocol:bedrock-codec", ":relay:Protocol:bedrock-connection", ":relay:Protocol:common")`
   (and `:relay:Protocol:adventure` if used).
3. `relay/build.gradle.kts` — replace the three `api("org.cloudburstmc.protocol:…")` lines with
   `api(project(":relay:Protocol:…"))`.
4. Adapt each vendored module's `build.gradle.kts`: they use *their own* type-safe accessors
   (`projects.bedrockCodec`, `libs.netty.transport.raknet`, `libs.snappy`) and bring
   `maven-publish`/`signing`/javadoc jars — rewire to WClient's `gradle/libs.versions.toml` and drop
   the publishing/signing bits.
5. Watch the R8 `RakChannel` duplicate-class trap (**#8**): do **not** leave a project
   `transport-raknet` on the classpath together with Maven's `netty-transport-raknet`.
6. Then do the registration/label bumps from Scenario A steps 2–5.

### Where the version strings come from

The vendored source is the source of truth for the version strings: each
`Bedrock_v{N}.java` declares `.protocolVersion(N)` and `.minecraftVersion("…")`, and
`relay/Protocol/VERSIONS.md` lists the class↔Minecraft-version table.

## Debugging "the client can't join a server"

- `WRelaySession.ClientSession.onPacket` logs `Upstream DisconnectPacket: reason=… kickMessage=…`.
  Keep it: a server rejection is otherwise forwarded to the game client silently and looks like a
  timeout.
- If the server sends **nothing at all** after our `LoginPacket` (no packets, no Disconnect), it's a
  server-side *silent* reject — check the account login chain (valid / not expired) and that the
  server actually accepts the client's protocol version.
- The full join flow logs (from `:relay`, via `println` → logcat): codec selected → compression
  enabled → "Processing login packet" → "Connected to server, sending network settings request" →
  "Network settings request sent" → "Compression enabled" → "Login packet sent successfully". A gap in
  that sequence pinpoints the step that failed.
