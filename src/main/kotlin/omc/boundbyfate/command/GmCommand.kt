package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.network.ServerPacketHandler

object GmCommand {
    fun register(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        registryAccess: CommandRegistryAccess,
        environment: CommandManager.RegistrationEnvironment
    ) {
        dispatcher.register(
            CommandManager.literal("bbf")
                .then(
                    CommandManager.literal("gm")
                        .requires { it.hasPermissionLevel(2) }
                        .executes { context ->
                            val player = context.source.playerOrThrow
                            // Sync all player data to GM
                            ServerPacketHandler.syncGmData(player)
                            // Sync registry (classes, races, skills, features)
                            ServerPacketHandler.syncGmRegistry(player)
                            // Tell client to open GM screen
                            ServerPlayNetworking.send(player, BbfPackets.OPEN_GM_SCREEN, PacketByteBufs.empty())
                            context.source.sendFeedback({ Text.literal("§aOpening GM panel...") }, false)
                            1
                        }
                )
        )
    }
}
