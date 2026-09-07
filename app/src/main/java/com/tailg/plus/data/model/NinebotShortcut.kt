package com.tailg.plus.data.model

/** Functions available in the three lower slots of the Ninebot control card. */
enum class NinebotShortcut(val storageValue: String) {
    INDUCTION("induction"),
    BATTERY("battery"),
    SEAT("seat"),
}

@ConsistentCopyVisibility
data class NinebotShortcutLayout private constructor(val slots: List<NinebotShortcut?>) {
    /** Selecting a function already in another slot swaps the two assignments. */
    fun assign(slot: Int, shortcut: NinebotShortcut?): NinebotShortcutLayout {
        require(slot in 0 until SLOT_COUNT)
        val next = slots.toMutableList()
        val previousSlot = if (shortcut == null) -1 else next.indexOf(shortcut)
        if (previousSlot >= 0 && previousSlot != slot) next[previousSlot] = next[slot]
        next[slot] = shortcut
        return NinebotShortcutLayout(next.toList())
    }

    fun encode(): String = slots.joinToString(",") { it?.storageValue ?: "_" }

    companion object {
        const val SLOT_COUNT = 3
        val Default = NinebotShortcutLayout(NinebotShortcut.entries.toList())

        /** Unknown or duplicate functions become empty slots; explicit empty layouts survive. */
        fun decode(value: String?): NinebotShortcutLayout {
            val values = value?.split(',') ?: return Default
            if (values.size != SLOT_COUNT) return Default
            val seen = mutableSetOf<NinebotShortcut>()
            return NinebotShortcutLayout(values.map { stored ->
                NinebotShortcut.entries.firstOrNull { it.storageValue == stored }
                    ?.takeIf { seen.add(it) }
            })
        }
    }
}
