package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import omc.boundbyfate.system.identity.AlignmentSystem

/**
 * Commands for managing player alignment:
 * - /bbf alignment add <player> <axis> [value]
 * - /bbf alignment set <player> <goodEvil> <lawChaos>
 */
object AlignmentCommand {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("bbf")
                .then(
                    CommandManager.literal("alignment")
                        .requires { it.hasPermissionLevel(2) }
                        .then(
                            CommandManager.literal("add")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("axis", StringArgumentType.word())
                                                .suggests { _, builder ->
                                                    builder.suggest("good")
                                                    builder.suggest("evil")
                                                    builder.suggest("lawful")
                                                    builder.suggest("chaotic")
                                                    builder.buildFuture()
                                                }
                                                .executes { addAlignment(it, 1) }
                                                .then(
                                                    CommandManager.argument("value", IntegerArgumentType.integer())
                                                        .executes { addAlignment(it, IntegerArgumentType.getInteger(it, "value")) }
                                                )
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("set")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("goodEvil", IntegerArgumentType.integer(-6, 6))
                                                .then(
                                                    CommandManager.argument("lawChaos", IntegerArgumentType.integer(-6, 6))
                                                        .executes { setAlignment(it) }
                                                )
                                        )
                                )
                        )
                )
        )
    }

    private fun addAlignment(context: CommandContext<ServerCommandSource>, value: Int): Int {
        val player = EntityArgumentType.getPlayer(context, "player")
        val axis = StringArgumentType.getString(context, "axis").lowercase()
        
        val (lawChaosChange, goodEvilChange) = when (axis) {
            "good" -> 0 to value
            "evil" -> 0 to -value
            "lawful" -> -value to 0
            "chaotic" -> value to 0
            else -> {
                context.source.sendError(Text.literal("Invalid axis: $axis. Use good/evil/lawful/chaotic"))
                return 0
            }
        }
        
        val oldCoords = AlignmentSystem.getCoordinates(player)
        val oldAlignment = AlignmentSystem.getAlignment(player)
        
        AlignmentSystem.addAlignment(player, lawChaosChange, goodEvilChange, "Command: /bbf alignment add")
        
        val newCoords = AlignmentSystem.getCoordinates(player)
        val newAlignment = AlignmentSystem.getAlignment(player)
        
        if (oldAlignment != newAlignment) {
            context.source.sendFeedback(
                {
                    Text.literal("§e${player.name.string}'s alignment changed: §c${oldAlignment.name} §e→ §a${newAlignment.name}")
                },
                true
            )
        } else {
            context.source.sendFeedback(
                {
                    Text.literal("§e${player.name.string}'s alignment shifted: §7(${oldCoords.lawChaos}, ${oldCoords.goodEvil}) §e→ §7(${newCoords.lawChaos}, ${newCoords.goodEvil})")
                },
                true
            )
        }
        
        val (lcBorder, geBorder) = AlignmentSystem.isOnBorder(player)
        if (lcBorder || geBorder) {
            val borderMsg = buildString {
                append("§6⚠ Conviction wavering: ")
                if (lcBorder) append("Law-Chaos ")
                if (geBorder) append("Good-Evil")
            }
            context.source.sendFeedback({ Text.literal(borderMsg) }, true)
        }
        
        return 1
    }

    private fun setAlignment(context: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(context, "player")
        val goodEvil = IntegerArgumentType.getInteger(context, "goodEvil")
        val lawChaos = IntegerArgumentType.getInteger(context, "lawChaos")
        
        val oldAlignment = AlignmentSystem.getAlignment(player)
        
        AlignmentSystem.setAlignment(player, lawChaos, goodEvil, "Command: /bbf alignment set")
        
        val newAlignment = AlignmentSystem.getAlignment(player)
        val newCoords = AlignmentSystem.getCoordinates(player)
        
        context.source.sendFeedback(
            {
                Text.literal("§e${player.name.string}'s alignment set to: §a${newAlignment.name} §7(${newCoords.lawChaos}, ${newCoords.goodEvil})")
            },
            true
        )
        
        if (oldAlignment != newAlignment) {
            context.source.sendFeedback(
                {
                    Text.literal("§7Previous: §c${oldAlignment.name}")
                },
                false
            )
        }
        
        return 1
    }
}
