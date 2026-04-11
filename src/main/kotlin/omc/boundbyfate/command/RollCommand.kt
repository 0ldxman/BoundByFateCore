package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import omc.boundbyfate.api.dice.DiceRoller

object RollCommand {

    fun register(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        registryAccess: CommandRegistryAccess,
        environment: CommandManager.RegistrationEnvironment
    ) {
        dispatcher.register(
            CommandManager.literal("roll")
                .then(
                    CommandManager.argument("formula", StringArgumentType.string())
                        .executes { ctx ->
                            val formula = StringArgumentType.getString(ctx, "formula")
                            val player = ctx.source.playerOrThrow

                            val result = DiceRoller.parse(formula) ?: run {
                                ctx.source.sendError(Text.literal("§cНеверная формула: §f$formula §7(пример: d20, 2d6, 1d8+3)"))
                                return@executes 0
                            }

                            val message = when {
                                result.isCriticalSuccess ->
                                    "§6[Бросок] §e${player.name.string} §7бросает §f${result.expression}\n§a✦ КРИТИЧЕСКИЙ УСПЕХ! §7[20]"
                                result.isCriticalFailure ->
                                    "§6[Бросок] §e${player.name.string} §7бросает §f${result.expression}\n§c✦ КРИТИЧЕСКИЙ ПРОВАЛ! §7[1]"
                                result.rolls.size == 1 ->
                                    "§6[Бросок] §e${player.name.string} §7бросает §f${result.expression} §7→ §f${result}"
                                else ->
                                    "§6[Бросок] §e${player.name.string} §7бросает §f${result.expression} §7→ §f${result}"
                            }

                            // Broadcast to all players
                            player.server.playerManager.playerList.forEach {
                                it.sendMessage(Text.literal(message), false)
                            }

                            1
                        }
                )
        )
    }
}
