package com.elainaclient.router.main

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elainaclient.ui.component.*
import com.elainaclient.R
import com.elainaclient.ui.theme.WColors
import com.elainaclient.util.LocalSnackbarHostState
import com.elainaclient.util.SnackbarHostStateScope
import androidx.core.net.toUri
import com.elainaclient.BuildConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AboutPageContent() {
    SnackbarHostStateScope {
        val snackbarHostState = LocalSnackbarHostState.current
        val context = LocalContext.current

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.about),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.animateContentSize()
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppInfoSection()
                LicenseSection()
                LegalSection()
                致谢Section(context)
                SourceCodeSection(context)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AppInfoSection() {
    WGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = WColors.Primary,
        glowIntensity = 0.4f
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ElainaClient",
                style = MaterialTheme.typography.displaySmall,
                color = WColors.Primary
            )
            Text(
                text = "RetrivedMods",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = WColors.Primary.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    color = WColors.Primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun LicenseSection() {
    WGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = WColors.Secondary,
        glowIntensity = 0.3f
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "许可证",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "允许的用途",
                    style = MaterialTheme.typography.titleMedium,
                    color = WColors.Accent
                )
                Surface(
                    color = WColors.Accent.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BulletPoint("个人使用与修改")
                        BulletPoint("使用 ElainaClient 创作内容")
                        BulletPoint("以 GPLv3 许可证再分发源代码")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "禁止的用途",
                    style = MaterialTheme.typography.titleMedium,
                    color = WColors.Error
                )
                Surface(
                    color = WColors.Error.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BulletPoint("在未提供源代码与许可证的情况下分发")
                        BulletPoint("声称拥有原始源代码的所有权")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "第三方组件",
                    style = MaterialTheme.typography.titleMedium,
                    color = WColors.Secondary
                )
                Surface(
                    color = WColors.Secondary.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "本项目使用 GPL-3.0、LGPL-3.0、LGPL-2.1、Apache-2.0 与 MIT " +
                            "许可的开源组件，均遵循各自许可证。完整清单与仓库地址见项目根目录的 " +
                            "THIRD_PARTY_NOTICES.md。Minecraft 字体与游戏数据不属于 GPLv3。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LegalSection() {
    WGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MaterialTheme.colorScheme.onSurfaceVariant,
        glowIntensity = 0.2f
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "法律",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            LegalItem(
                title = "免责声明",
                content = "This program is distributed under the GNU General Public License v3 (GPLv3). It is provided \"AS IS\", without any warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose, and noninfringement."
            )

            LegalItem(
                title = "责任限制",
                content = "In no event shall the author(s) or copyright holder(s) be liable for any claim, damages, or other liability, whether in an action of contract, tort, or otherwise, arising from, out of, or in connection with the software or the use or other dealings in the software."
            )

            LegalItem(
                title = "预期用途",
                content = "本软件仅用于教育与研究目的。任何违反适用法律、服务条款或对他人造成伤害的使用，均非本项目的本意，责任由使用者自负。"
            )
        }
    }
}

@Composable
private fun LegalItem(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight.times(1.5f)
        )
    }
}

@Composable
private fun 致谢Section(context: android.content.Context) {
    WGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = WColors.Primary,
        glowIntensity = 0.25f
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "致谢",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            Text(
                text = "特别感谢让这个项目成为可能的贡献者们。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContributorItem(
                    name = "WIheee",
                    description = "核心贡献者",
                    githubUrl = "https://github.com/WIheee",
                    context = context
                )

                ContributorItem(
                    name = "一剪沐橙",
                    description = "核心贡献者",
                    githubUrl = "https://github.com/mucute-qwq",
                    context = context
                )

                ContributorItem(
                    name = "radiantByte",
                    description = "项目贡献者",
                    githubUrl = null,
                    context = context
                )

                ContributorItem(
                    name = "WClient",
                    description = "上游项目（GPL-3.0；Prajwal / RA Legend / Flexy H6X）",
                    githubUrl = "https://github.com/RetrivedMods/WClient",
                    context = context
                )

                ContributorItem(
                    name = "Lumina / LuminaCN",
                    description = "主界面、Kitsu/Grace/Protohax 布局与通知悬浮层（GPL-3.0）",
                    githubUrl = "https://github.com/TheProjectLumina/LuminaClient",
                    context = context
                )

                ContributorItem(
                    name = "OpenClickGUI / OpenMoxel",
                    description = "Dream / HackAI / Pianaixel / PixelSight / LiquidPE 界面参考与重构" +
                        "（README 声明开源可修改；上游无标准 LICENSE 文件）",
                    githubUrl = "https://github.com/OpenMoxel/OpenClickGUI",
                    context = context
                )

                ContributorItem(
                    name = "LiquidPE",
                    description = "特别感谢：保留原版配色与 Minecraft 字体移植（作者 atlasca3@gmail.com）",
                    githubUrl = null,
                    context = context
                )

                ContributorItem(
                    name = "LiquidBounce / CCBlueX",
                    description = "Scaffold 模块算法参考（GPL-3.0；仅算法与逻辑，未复制代码）",
                    githubUrl = "https://github.com/CCBlueX/LiquidBounce",
                    context = context
                )

                ContributorItem(
                    name = "openhal4a / 1503Dev",
                    description = "HorionArrayList 模块列表（LGPL-2.1）",
                    githubUrl = "https://github.com/1503Dev/openhal4a",
                    context = context
                )

                ContributorItem(
                    name = "CloudburstMC / Protocol",
                    description = "Bedrock 协议编解码与连接（Apache-2.0）",
                    githubUrl = "https://github.com/CloudburstMC/Protocol",
                    context = context
                )

                ContributorItem(
                    name = "CloudburstMC / Network",
                    description = "RakNet / Query / RCON（Apache-2.0）",
                    githubUrl = "https://github.com/CloudburstMC/Network",
                    context = context
                )

                ContributorItem(
                    name = "MinecraftAuth / RaphiMC",
                    description = "Xbox / Mojang 登录（LGPL-3.0）",
                    githubUrl = "https://github.com/RaphiMC/MinecraftAuth",
                    context = context
                )

                ContributorItem(
                    name = "Kotlin / Jetpack Compose / AndroidX",
                    description = "开发语言与 UI 框架（Apache-2.0）",
                    githubUrl = "https://github.com/JetBrains/kotlin",
                    context = context
                )

                ContributorItem(
                    name = "完整第三方许可证清单",
                    description = "版本、许可证与仓库地址（THIRD_PARTY_NOTICES.md）",
                    githubUrl = "https://github.com/WIheee/ElainaClient/blob/main/THIRD_PARTY_NOTICES.md",
                    context = context
                )
            }
        }
    }
}

@Composable
private fun ContributorItem(
    name: String,
    description: String,
    githubUrl: String?,
    context: android.content.Context
) {
    Surface(
        onClick = if (githubUrl != null) {
            {
                val intent = Intent(Intent.ACTION_VIEW, githubUrl.toUri())
                context.startActivity(intent)
            }
        } else {
            {}
        },
        color = WColors.Primary.copy(alpha = 0.06f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (githubUrl != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "在 GitHub 查看",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceCodeSection(context: android.content.Context) {
    WGlassCard(
        onClick = {
            val intent = Intent(
                Intent.ACTION_VIEW,
                "https://github.com/WIheee/ElainaClient".toUri()
            )
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
        glowColor = WColors.Secondary,
        glowIntensity = 0.3f
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "源代码",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "在 GitHub 查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "打开 GitHub",
                tint = WColors.Secondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}