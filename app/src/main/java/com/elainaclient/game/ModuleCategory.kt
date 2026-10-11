package com.elainaclient.game

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.elainaclient.R

enum class ModuleCategory(
    @param:DrawableRes val iconResId: Int,
    @param:StringRes val labelResId: Int,
    val displayName: String
) {

    Combat(
        iconResId = R.drawable.swords_24px,
        labelResId = R.string.combat,
        displayName = "战斗"
    ),
    Motion(
        iconResId = R.drawable.sprint_24px,
        labelResId = R.string.motion,
        displayName = "移动"
    ),
    Visual(
        iconResId = R.drawable.view_in_ar_24px,
        labelResId = R.string.visual,
        displayName = "视觉"
    ),

    World(
        iconResId = R.drawable.public_24px,
        labelResId = R.string.world,
        displayName = "世界"
    ),
    Misc(
        iconResId = R.drawable.toc_24px,
        labelResId = R.string.misc,
        displayName = "杂项"
    ),
    Config(
        iconResId = R.drawable.ic_settings,
        labelResId = R.string.config,
        displayName = "配置"
    ),
}