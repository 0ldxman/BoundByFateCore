package omc.boundbyfate.client.skin

import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages custom skin textures on the client side.
 * Stores textures by player name and provides them for rendering.
 */
object ClientSkinManager {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    data class SkinEntry(
        val textureId: Identifier,
        val isSlim: Boolean
    )

    private val skins = ConcurrentHashMap<String, SkinEntry>()

    /**
     * Loads and registers a skin texture from Base64 PNG data.
     * Called when SYNC_PLAYER_SKIN packet is received.
     */
    fun setSkin(playerName: String, skinBase64: String, skinModel: String) {
        try {
            val bytes = Base64.getDecoder().decode(skinBase64)
            val image = NativeImage.read(ByteArrayInputStream(bytes))
            val texture = NativeImageBackedTexture(image)

            val textureId = Identifier("boundbyfate-core", "skin/${playerName.lowercase()}")

            MinecraftClient.getInstance().execute {
                val textureManager = MinecraftClient.getInstance().textureManager
                // Unregister old texture if exists
                skins[playerName]?.let { textureManager.destroyTexture(it.textureId) }
                textureManager.registerTexture(textureId, texture)
                skins[playerName] = SkinEntry(textureId, skinModel == "slim")
                logger.debug("Registered custom skin for $playerName (model=$skinModel)")
            }
        } catch (e: Exception) {
            logger.error("Failed to load skin for $playerName", e)
        }
    }

    /**
     * Removes a custom skin, reverting to Mojang skin.
     */
    fun clearSkin(playerName: String) {
        MinecraftClient.getInstance().execute {
            skins.remove(playerName)?.let { entry ->
                MinecraftClient.getInstance().textureManager.destroyTexture(entry.textureId)
                logger.debug("Cleared custom skin for $playerName")
            }
        }
    }

    /**
     * Returns the custom skin entry for a player, or null if using default Mojang skin.
     */
    fun getSkin(playerName: String): SkinEntry? = skins[playerName]

    fun hasSkin(playerName: String): Boolean = skins.containsKey(playerName)

    fun clearAll() {
        val client = MinecraftClient.getInstance()
        skins.forEach { (_, entry) -> client.textureManager.destroyTexture(entry.textureId) }
        skins.clear()
    }
}
