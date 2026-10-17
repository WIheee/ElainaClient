package com.elainaclient.util

import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Some packet classes in org.cloudburstmc.protocol no longer expose public setters for certain
 * fields. The underlying fields still exist, they're just no longer part of the public Java Bean
 * API, which breaks Kotlin's synthetic property assignment (`packet.onGround = true`).
 *
 * This helper sets those fields directly via reflection so callers don't have to care whether a
 * given field is still publicly settable in the version of the protocol library that's currently
 * linked.
 *
 * NOTE: which fields are affected is per-field and varies between library versions - do not treat
 * any list of them as authoritative. An earlier revision of this comment claimed `selectHotbarSlot`
 * was one of them; that is wrong for the linked build, where `PlayerHotbarPacket.isSelectHotbarSlot
 * = true` compiles fine (Lombok `@Data` generates `setSelectHotbarSlot` - verified against the
 * reference source in relay/Protocol, see ADR-0001). Prefer the plain property assignment wherever
 * it compiles, and reach for this helper only where it doesn't.
 */
object PacketFieldUtil {

    private val fieldCache = ConcurrentHashMap<String, Field>()

    fun setField(target: Any, fieldName: String, value: Any?) {
        val field = resolveField(target.javaClass, fieldName)
            ?: throw NoSuchFieldException("Field '$fieldName' not found in ${target.javaClass.name} or its superclasses")
        field.set(target, value)
    }

    private fun resolveField(clazz: Class<*>, fieldName: String): Field? {
        val key = "${clazz.name}#$fieldName"
        fieldCache[key]?.let { return it }

        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                fieldCache[key] = field
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}

/** Convenience extension so call sites read almost like a normal assignment. */
fun Any.setPacketField(fieldName: String, value: Any?) {
    PacketFieldUtil.setField(this, fieldName, value)
}
