package omc.boundbyfate.util

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelStorage
import java.nio.file.Path

/**
 * Utility for reliably getting the world/save directory.
 * Works on both dedicated and integrated (singleplayer) servers.
 */
object WorldDirUtil {

    /**
     * Returns the root save directory for the current world.
     * On dedicated server: <server_root>/<world_name>/
     * On integrated server: <game_dir>/saves/<world_name>/
     *
     * Uses server.getSavePath() which is the official Fabric/Minecraft API.
     */
    fun getWorldDir(server: MinecraftServer): Path {
        // server.getSavePath(WorldSavePath.ROOT) returns the world root directory
        // This works correctly on both dedicated and integrated servers
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize()
    }

    /**
     * Returns the boundbyfate data directory inside the world.
     * Creates it if it doesn't exist.
     */
    fun getBbfDir(server: MinecraftServer): Path {
        val dir = getWorldDir(server).resolve("boundbyfate")
        if (!java.nio.file.Files.exists(dir)) {
            java.nio.file.Files.createDirectories(dir)
        }
        return dir
    }
}
