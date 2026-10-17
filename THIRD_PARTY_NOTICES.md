# Third-party notices

ElainaClient is licensed under the **GNU General Public License v3.0** (see [LICENSE](LICENSE)).
This file lists the third-party projects used by, or used as a reference for, ElainaClient, together
with their licenses and upstream repositories.

Project source: <https://github.com/WIheee/ElainaClient>

> This inventory is provided for attribution and license-compliance purposes. It does not change
> the terms of any third-party license. Where a project has no formal license file, that is stated
> explicitly below.

## Ported / derived code and layout references

| Project | License | Upstream | Used for |
|---|---|---|---|
| **WClient** (Prajwal, RA Legend, Flexy H6X) | GPL-3.0 | <https://github.com/RetrivedMods/WClient> | Upstream project this fork is based on. |
| **Lumina / LuminaClient** (Project Lumina) and **LuminaCN** (Chinese localization) | GPL-3.0 | <https://github.com/TheProjectLumina/LuminaClient> | Adaptive home shell/dashboard, Kitsu/Grace/Protohax click-GUI layouts, notification overlay. |
| **OpenClickGUI** (OpenMoxel) | No formal license file — see note below | <https://github.com/OpenMoxel/OpenClickGUI> | Dream / HackAI / Pianaixel / PixelSight / LiquidPE click-GUI layouts. Dream and HackAI were rewritten natively; Pianaixel/PixelSight are still being ported. |
| **LiquidPE** (part of OpenClickGUI; author credited in the upstream sources) | Covered by the OpenClickGUI note | <https://github.com/OpenMoxel/OpenClickGUI> | Canvas renderer rewritten in Kotlin; original LiquidBounce-style colours and Minecraft font are kept. |
| **LiquidBounce** (CCBlueX) | GPL-3.0 | <https://github.com/CCBlueX/LiquidBounce> | Scaffold placement strategy reference only (algorithms/decision rules; no copied code). |
| **openhal4a / HorionArrayList** (1503Dev) | LGPL-2.1 | <https://github.com/1503Dev/openhal4a> | Horion-style on-screen module list, ported into `overlay/hud/horion/`. |

### OpenClickGUI license note

The upstream OpenClickGUI repository does not contain a `LICENSE` file. Its README states that the
project is open source, that the code may be modified, and that it may be integrated into other
projects. ElainaClient credits the project and its authors, but **does not grant any rights beyond
what the OpenClickGUI authors have stated**. If you redistribute ElainaClient, verify the current
terms with the OpenClickGUI authors.

### LGPL notes

- **openhal4a** is LGPL-2.1. The project is distributed here under GPL-3.0, which LGPL-2.1 permits
  through its "GPL version 2 or later" conversion clause. The original LGPL-2.1 text is available
  from the upstream repository.
- **MinecraftAuth** is LGPL-3.0, which is compatible with the GPL-3.0 terms of this project.

## Runtime libraries

| Dependency | Version | License | Upstream |
|---|---|---|---|
| CloudburstMC/Protocol (`bedrock-codec`, `bedrock-connection`, `common`) | 3.0.0.Beta13-SNAPSHOT | Apache-2.0 | <https://github.com/CloudburstMC/Protocol> |
| CloudburstMC/Network (`codec-query`, `codec-rcon`, `transport-raknet`) — vendored reference copies | vendored source (not built) | Apache-2.0 | <https://github.com/CloudburstMC/Network> |
| CloudburstMC/NBT | 3.0.3.Final | Apache-2.0 | <https://github.com/CloudburstMC/NBT> |
| CloudburstMC/math (`immutable`) | 2.0 | MIT | <https://github.com/CloudburstMC/math> |
| CloudburstMC fastutil fork (`long-common`, maps) | 8.5.15 | Apache-2.0 | <https://github.com/vigna/fastutil> |
| Netty (`common`, `buffer`, `codec`, `transport`, `transport-native-unix-common`) | 4.2.0.Final | Apache-2.0 | <https://github.com/netty/netty> |
| aircompressor | 2.0.2 | Apache-2.0 | <https://github.com/airlift/aircompressor> |
| MinecraftAuth (RaphiMC) | 5.0.0 | LGPL-3.0 | <https://github.com/RaphiMC/MinecraftAuth> |
| jose4j | 0.9.6 | Apache-2.0 | <https://bitbucket.org/b_c/jose4j> |
| Jackson (`databind`, `annotations`) | 2.20.0 | Apache-2.0 | <https://github.com/FasterXML/jackson> |
| Kyori Adventure (`api`, legacy/json serializers) | 4.24.0 | MIT | <https://github.com/KyoriPowered/adventure> |
| Apache Log4j 2 (`api`, `core`) | 2.25.1 | Apache-2.0 | <https://github.com/apache/logging-log4j2> |
| Kotlin | 2.2.20 | Apache-2.0 | <https://github.com/JetBrains/kotlin> |
| kotlinx.coroutines | 1.10.2 | Apache-2.0 | <https://github.com/Kotlin/kotlinx.coroutines> |
| kotlinx.serialization | 1.9.0 | Apache-2.0 | <https://github.com/Kotlin/kotlinx.serialization> |
| AndroidX / Jetpack Compose / Material 3 / Navigation / Lifecycle / Activity | Compose BOM 2025.09.00 | Apache-2.0 | <https://github.com/androidx/androidx> · <https://developer.android.com/jetpack/compose> |
| Material Icons Extended | 1.7.8 | Apache-2.0 | <https://developer.android.com/jetpack/compose> |
| androidx.browser (Chrome Custom Tabs) | 1.6.0 | Apache-2.0 | <https://developer.android.com/jetpack/androidx/releases/browser> |
| OkHttp | 4.11.0 | Apache-2.0 | <https://github.com/square/okhttp> |
| Coil (`coil-compose`, `coil-gif`) | 2.5.0 | Apache-2.0 | <https://github.com/coil-kt/coil> |
| Gson | 2.13.2 | Apache-2.0 | <https://github.com/google/gson> |

## Test-only dependencies (not shipped in the APK)

| Dependency | License | Upstream |
|---|---|---|
| JUnit 4 | EPL-1.0 | <https://github.com/junit-team/junit4> |
| JUnit 5 / Jupiter | EPL-2.0 | <https://github.com/junit-team/junit5> |
| TestNG | Apache-2.0 | <https://github.com/testng-team/testng> |
| AndroidX Test / Espresso / Compose UI Test | Apache-2.0 | <https://github.com/androidx/androidx> |

## Game assets and data

- **Minecraft** is a trademark of Mojang Studios / Microsoft. ElainaClient is not affiliated with
  Mojang or Microsoft.
- The bundled `res/font/minecraft.ttf` and the block/item palette data
  (`nbt/block_palette.nbt`, `mcpedata/blocks`) originate from Minecraft game assets/data and remain
  the property of their respective owners. They are included for interoperability and visual
  fidelity and are **not** covered by this project's GPL-3.0 license.
- The GUI implementations include layouts/colour schemes inspired by the projects listed above;
  names and trademarks remain with their respective owners.

## Reporting a licensing problem

If you believe a work has been included without proper attribution or permission, please open an
issue at <https://github.com/WIheee/ElainaClient/issues> with the details so it can be corrected.
