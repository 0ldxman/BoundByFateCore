package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import omc.boundbyfate.network.packet.s2c.OpenCharacterEditScreenPacket

/**
 * Регистрация команд BoundByFate.
 */
object BbfCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerBbfCommand(dispatcher)
        }
    }

    private fun registerBbfCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("bbf")
                .then(
                    CommandManager.literal("character")
                        .then(
                            CommandManager.literal("create")
                                .executes(::openCharacterEditScreen)
                        )
                )
        )
    }

    private fun openCharacterEditScreen(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        
        // Отправляем пакет клиенту для открытия экрана
        OpenCharacterEditScreenPacket.send(player)
        
        return 1
    }
}
