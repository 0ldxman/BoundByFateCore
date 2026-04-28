package omc.boundbyfate.util

import net.minecraft.util.Identifier

/**
 * Converts a string to a Minecraft Identifier.
 * Format: "namespace:path" or just "path" (uses "minecraft" namespace).
 */
val String.rl: Identifier
    get() = Identifier.tryParse(this) ?: error("Invalid Identifier: $this")
