package omc.boundbyfate.client.util

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.ResourceLocation
import java.io.InputStream

/**
 * Converts a string to a Minecraft ResourceLocation.
 * Format: "namespace:path" or just "path" (uses "minecraft" namespace).
 */
val String.rl: ResourceLocation
    get() = ResourceLocation.tryParse(this) ?: error("Invalid ResourceLocation: $this")

/**
 * Opens an InputStream for this ResourceLocation from the resource manager.
 */
val ResourceLocation.stream: InputStream
    get() = Minecraft.getInstance().resourceManager.open(this)

/**
 * Checks if this ResourceLocation exists in the resource manager.
 */
fun ResourceLocation.exists(): Boolean =
    Minecraft.getInstance().resourceManager.getResource(this).isPresent

/**
 * Gets the AbstractTexture for this ResourceLocation.
 */
fun ResourceLocation.toTexture(): AbstractTexture =
    Minecraft.getInstance().textureManager.getTexture(this)


