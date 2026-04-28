package omc.boundbyfate.client.util

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import java.io.InputStream

/**
 * Converts a string to a Minecraft Identifier.
 * Format: "namespace:path" or just "path" (uses "minecraft" namespace).
 */
val String.rl: Identifier
    get() = Identifier.tryParse(this) ?: error("Invalid Identifier: $this")

/**
 * Opens an InputStream for this Identifier from the resource manager.
 */
val Identifier.stream: InputStream
    get() = MinecraftClient.getInstance().resourceManager.open(this)

/**
 * Checks if this Identifier exists in the resource manager.
 */
fun Identifier.exists(): Boolean =
    MinecraftClient.getInstance().resourceManager.getResource(this).isPresent

/**
 * Gets the AbstractTexture for this Identifier from the texture manager.
 */
fun Identifier.toTexture(): net.minecraft.client.texture.AbstractTexture =
    MinecraftClient.getInstance().textureManager.getTexture(this)
