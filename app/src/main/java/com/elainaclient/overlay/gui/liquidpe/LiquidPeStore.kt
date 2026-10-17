package com.elainaclient.overlay.gui.liquidpe

/**
 * LiquidPE's module model, populated by [LiquidPeBridge] from Elaina's ModuleManager.
 *
 * The event is intentionally narrow: the renderer only mutates enabled state and key binds, so
 * there is no reason to expose the demo catalog / ARRAY-list metadata the original store carried.
 */
class LiquidPeStore {

    fun interface ModuleStateListener {
        fun onModuleEnabledChanged(module: ModuleEntry, enabled: Boolean)
    }

    val categories = mutableListOf<CategoryPanel>()
    private val modulesById = linkedMapOf<String, ModuleEntry>()
    private val listeners = mutableListOf<ModuleStateListener>()

    fun addCategory(id: String, name: String, x: Float, y: Float): CategoryPanel =
        CategoryPanel(id, name, x, y).also { categories += it }

    fun addModule(id: String, name: String, categoryId: String): ModuleEntry {
        require(!modulesById.containsKey(id)) { "Duplicate module id $id" }
        val category = findCategory(categoryId) ?: error("Unknown category $categoryId")
        return ModuleEntry(id, name, categoryId).also {
            modulesById[id] = it
            category.modules += it
        }
    }

    fun findModule(idOrName: String): ModuleEntry? =
        modulesById[idOrName] ?: modulesById.values.firstOrNull { it.name.equals(idOrName, true) }

    fun findCategory(idOrName: String): CategoryPanel? =
        categories.firstOrNull { it.id == idOrName || it.name.equals(idOrName, true) }

    fun addModuleStateListener(listener: ModuleStateListener) {
        if (listener !in listeners) listeners += listener
    }

    fun toggleModule(idOrName: String): Boolean {
        val module = findModule(idOrName) ?: return false
        return setModuleEnabled(module.id, !module.enabled)
    }

    fun setModuleEnabled(idOrName: String, enabled: Boolean): Boolean {
        val module = findModule(idOrName) ?: return false
        if (module.enabled == enabled) return false
        module.enabled = enabled
        module.enabledProgress.animateTo(if (enabled) 1f else 0f, LiquidPeDurations.MODULE_TOGGLE)
        listeners.toList().forEach { it.onModuleEnabledChanged(module, enabled) }
        return true
    }

    fun setModuleKeyBind(idOrName: String, bind: String?) {
        val module = findModule(idOrName) ?: return
        module.keyBind = bind?.takeIf { it.isNotEmpty() } ?: "None"
    }
}
