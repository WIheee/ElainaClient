package com.elainaclient.router.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elainaclient.ui.theme.WColors
import com.elainaclient.viewmodel.MainScreenViewModel

@Immutable
enum class MainScreenPages(
    val icon: ImageVector,
    val label: String,
    val content: @Composable () -> Unit
) {
    HomePage(Icons.Rounded.Home, "主页", { HomePageContent() }),
    AccountPage(Icons.Rounded.AccountCircle, "账号", { AccountPageContent() }),
    ServerPage(Icons.Rounded.Storage, "服务器", { ServerPageContent() }),
    SettingsPage(Icons.Rounded.Settings, "设置", { SettingsPageContent() }),
    AboutPage(Icons.Rounded.Info, "关于", { AboutPageContent() })
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen() {
    val vm: MainScreenViewModel = viewModel()
    val selectedPage by vm.selectedPage.collectAsStateWithLifecycle()
    val pages = remember { MainScreenPages.entries.toList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WColors.Background)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = selectedPage,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { it / 3 },
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(240)) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { -it / 4 },
                                animationSpec = tween(280, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(200))
                },
                label = "page_transition"
            ) { page ->
                page.content()
            }
        }

        NavigationBar(
            containerColor = WColors.Surface
        ) {
            pages.forEach { page ->
                NavigationBarItem(
                    selected = page == selectedPage,
                    onClick = { if (selectedPage != page) vm.selectPage(page) },
                    icon = { Icon(page.icon, contentDescription = page.label) },
                    label = { Text(page.label) }
                )
            }
        }
    }
}
