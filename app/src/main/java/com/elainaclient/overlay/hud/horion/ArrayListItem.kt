package com.elainaclient.overlay.hud.horion

class ArrayListItem @JvmOverloads constructor(
    var text: String,
    var keybind: String? = null,
    var modeText: String? = null,
    var category: Int = 0
) {
    fun getFullText(): String {
        var keybindText = ""
        if (keybind != null) {
            keybindText = " [$keybind]"
        }
        var modeTextFinal = ""
        if (modeText != null) {
            modeTextFinal = " $modeText"
        }
        return "$text$keybindText$modeTextFinal"
    }
}
