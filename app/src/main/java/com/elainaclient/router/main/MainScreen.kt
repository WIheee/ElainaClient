package com.elainaclient.router.main

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elainaclient.R
import com.elainaclient.viewmodel.MainScreenViewModel

/**
 * Shell pages. The dashboard (HomePage) also hosts the server picker, so there is no
 * separate "servers" page anymore.
 */
@Immutable
enum class MainScreenPages(
    val icon: ImageVector,
    val labelResId: Int,
    val content: @Composable () -> Unit
) {
    HomePage(Icons.Rounded.Dashboard, R.string.home, { HomePageContent() }),
    AccountPage(Icons.Rounded.AccountCircle, R.string.account, { AccountPageContent() }),
    UISettingsPage(Icons.Rounded.Build, R.string.ui_settings, { UISettingsPageContent() }),
    AboutPage(Icons.Rounded.Info, R.string.about, { AboutPageContent() }),
    SettingsPage(Icons.Rounded.Settings, R.string.settings, { SettingsPageContent() })
}

/**
 * Adaptive shell: a [NavigationRail] in landscape, a bottom [NavigationBar] in portrait.
 * Each page owns its own Scaffold/TopAppBar, so the shell only provides navigation.
 */
@Composable
fun MainScreen() {
    val vm: MainScreenViewModel = viewModel()
    val selectedPage by vm.selectedPage.collectAsStateWithLifecycle()
    val pages = remember { MainScreenPages.entries.toList() }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(16.dp))
                pages.forEach { page ->
                    NavigationRailItem(
                        selected = page == selectedPage,
                        onClick = { if (selectedPage != page) vm.selectPage(page) },
                        icon = { Icon(page.icon, contentDescription = null) },
                        label = { Text(stringResource(page.labelResId)) }
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                PageHost(selectedPage)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PageHost(selectedPage)
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                pages.forEach { page ->
                    NavigationBarItem(
                        selected = page == selectedPage,
                        onClick = { if (selectedPage != page) vm.selectPage(page) },
                        icon = { Icon(page.icon, contentDescription = null) },
                        label = { Text(stringResource(page.labelResId)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun PageHost(page: MainScreenPages) {
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val enter = slideInHorizontally(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                initialOffsetX = { if (forward) it else -it }
            ) + fadeIn(tween(240))
            val exit = slideOutHorizontally(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                targetOffsetX = { if (forward) -it else it }
            ) + fadeOut(tween(200))
            enter togetherWith exit
        },
        label = "page_transition"
    ) { selected ->
        selected.content()
    }
}
