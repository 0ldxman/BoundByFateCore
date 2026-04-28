package omc.boundbyfate.util

import net.minecraft.resources.ResourceLocation

/**
 * Converts a string to a Minecraft ResourceLocation.
 * Format: "namespace:path" or just "path" (uses "minecraft" namespace).
 */
val String.rl: ResourceLocation
    get() = ResourceLocation.tryParse(this) ?: error("Invalid ResourceLocation: $this")
