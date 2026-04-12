package omc.boundbyfate.system.skin

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * Loads skin PNG files from the world directory.
 * Skins are stored at: world/boundbyfate/skins/<skinName>.png
 */
object SkinLoader {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")

    fun getSkinDir(worldDir: Path): Path =
        worldDir.resolve("boundbyfate").resolve("skins")

    /**
     * Reads a skin PNG and returns it as Base64 encoded string.
     * Returns null if the file doesn't exist or can't be read.
     */
    fun loadAsBase64(worldDir: Path, skinName: String): String? {
        val skinDir = getSkinDir(worldDir)
        val skinFile = skinDir.resolve("$skinName.png")

        if (!Files.exists(skinFile)) {
            logger.warn("Skin file not found: $skinFile")
            return null
        }

        return try {
            val bytes = Files.readAllBytes(skinFile)
            Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            logger.error("Failed to read skin file: $skinFile", e)
            null
        }
    }

    /**
     * Returns all available skin names (without .png extension).
     */
    fun listAvailableSkins(worldDir: Path): List<String> {
        val skinDir = getSkinDir(worldDir)
        if (!Files.exists(skinDir)) return emptyList()

        return try {
            Files.list(skinDir).use { stream ->
                stream.filter { it.toString().endsWith(".png") }
                    .map { it.fileName.toString().removeSuffix(".png") }
                    .toList()
            }
        } catch (e: Exception) {
            logger.error("Failed to list skins", e)
            emptyList()
        }
    }

    fun ensureSkinDir(worldDir: Path) {
        val skinDir = getSkinDir(worldDir)
        if (!Files.exists(skinDir)) {
            Files.createDirectories(skinDir)
            logger.info("Created skins directory: $skinDir")
        }
    }
}
