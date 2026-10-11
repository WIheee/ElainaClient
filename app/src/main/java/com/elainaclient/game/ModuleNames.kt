package com.elainaclient.game

import com.elainaclient.util.translatedSelf

/**
 * Chinese display names for the overlay / click-GUI.
 *
 * Module + option labels are stored as English identifiers on the [Module]/`AutoConfiguration`
 * side; this is the single place that maps them to Chinese for display. Anything not in the maps
 * falls back to [translatedSelf] (the camelCase/snake_case prettifier), so new modules still render
 * sensibly without an entry here.
 */

/** Module `name` → 中文显示名. */
private val MODULE_NAMES: Map<String, String> = mapOf(
    // Combat
    "ACA" to "ACA",
    "Auto HVH" to "自动 HVH",
    "Auto Totem" to "自动图腾",
    "AutoFight" to "自动战斗",
    "ChestStealer" to "箱子窃取",
    "EnemyHunter" to "敌人猎手",
    "InfiniteAura" to "无限光环",
    "WAura" to "W 光环",
    "anti_crystal" to "防水晶",
    "anti_knockback" to "防击退",
    "crystal_smash" to "水晶破坏",
    "hit_and_run" to "打了就跑",
    "hitbox" to "碰撞箱",
    "killaura" to "杀戮光环",
    "piston_crystal" to "活塞水晶",
    "surround" to "环绕",
    "switcher" to "快捷栏切换",
    "trigger_bot" to "扳机机器人",

    // Motion
    "MotionFly" to "运动飞行",
    "PlayerTP" to "玩家传送",
    "air_jump" to "空中跳跃",
    "anti_afk" to "防挂机",
    "auto_walk" to "自动行走",
    "bhop" to "连跳",
    "fly" to "飞行",
    "high_jump" to "高跳",
    "jetpack" to "喷气背包",
    "no_clip" to "穿墙",
    "speed" to "加速",
    "spider" to "爬墙",
    "sprint" to "疾跑",

    // Visual
    "Coordinates" to "坐标",
    "Crosshair" to "准星",
    "DamageText" to "伤害显示",
    "Minimap" to "小地图",
    "PlayerJoin" to "玩家加入",
    "SpeedDisplay" to "速度显示",
    "TargetHud" to "目标信息",
    "esp" to "透视",
    "fullbright" to "夜视",
    "keystrokes" to "按键显示",
    "minimap" to "小地图",
    "network_info" to "网络信息",
    "no_hurt_camera" to "无受伤晃动",
    "speed_display" to "速度显示",
    "targethud" to "目标信息",
    "world_state" to "世界状态",
    "worldstate" to "世界状态",
    "zoom" to "缩放",

    // World
    "FreeCamera" to "自由视角",
    "TimeShift" to "时间调节",
    "WeatherController" to "天气控制",
    "anti_debuff" to "防负面效果",
    "effects" to "药水效果",
    "free_camera" to "自由视角",
    "particles" to "粒子",
    "time_shift" to "时间调节",
    "weather_controller" to "天气控制",

    // Misc
    "AutoDisconnect" to "自动断线",
    "ArrayList" to "模块列表",
    "FakeDeath" to "假死",
    "FakeXP" to "假经验",
    "Miner" to "自动挖掘",
    "PacketLogger" to "数据包日志",
    "PieChart" to "饼图",
    "PositionLogger" to "坐标记录",
    "Spammer" to "刷屏",
    "arraylist" to "模块列表",
    "auto_disconnect" to "自动断线",
    "command_handler" to "指令处理",
    "desync" to "失同步",
    "fake_death" to "假死",
    "fake_xp" to "假经验",
    "fast_miner" to "快速挖掘",
    "network" to "网络",
    "no_chat" to "屏蔽聊天",
    "packet_logger" to "数据包日志",
    "piechart" to "饼图",
    "position_logger" to "坐标记录",
    "replay_mod" to "回放",
    "watermark" to "水印",
)

/** Setting/option/value `name` → 中文显示名 (best effort; falls back to translatedSelf). */
private val OPTION_NAMES: Map<String, String> = mapOf(
    "range" to "范围",
    "speed" to "速度",
    "mode" to "模式",
    "delay" to "延迟",
    "interval" to "间隔",
    "height" to "高度",
    "distance" to "距离",
    "angle" to "角度",
    "rotation" to "旋转",
    "target" to "目标",
    "players" to "玩家",
    "mobs" to "生物",
    "items" to "物品",
    "through_walls" to "隔墙",
    "wall_check" to "隔墙检测",
    "rainbow" to "彩虹",
    "static" to "静态",
    "category" to "按分类",
    "length" to "按长度",
    "alphabetical" to "按字母",
    "custom" to "自定义",
    "font" to "字体",
    "font_style" to "字体样式",
    "font_size" to "字体大小",
    "size" to "大小",
    "opacity" to "不透明度",
    "color" to "颜色",
    "color_mode" to "颜色模式",
    "glow" to "发光",
    "glow_effect" to "发光效果",
    "border" to "边框",
    "spacing" to "间距",
    "sort" to "排序",
    "sort_mode" to "排序方式",
    "position" to "位置",
    "vertical" to "垂直",
    "horizontal" to "水平",
    "background" to "背景",
    "offset_x" to "X 偏移",
    "offset_y" to "Y 偏移",
    "scale" to "缩放",
    "smooth" to "平滑",
    "gradient" to "渐变",
    "custom_text" to "自定义文本",
    "text" to "文本",
)

fun String.localizedModule(): String = MODULE_NAMES[this] ?: translatedSelf

fun String.localizedOption(): String = OPTION_NAMES[this] ?: translatedSelf
