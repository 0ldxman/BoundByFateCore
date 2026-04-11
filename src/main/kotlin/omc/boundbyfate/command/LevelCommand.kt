package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import omc.boundbyfate.registry.BbfComponents

object LevelCommand {
    fun register(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        registryAccess: CommandRegistryAccess,
        environment: CommandManager.RegistrationEnvironment
    ) {
        dispatcher.register(
            CommandManager.literal("bbf")
                .then(
                    CommandManager.literal("level")
                        .then(
                            CommandManager.literal("info")
                                .executes(::showLevelInfo)
                        )
                        .then(
                            CommandManager.literal("set")
                                .requires { it.hasPermissionLevel(2) }
                                .then(
                                    CommandManager.argument("level", IntegerArgumentType.integer(1, 20))
                                        .executes(::setLevel)
                                )
                        )
                        .then(
                            CommandManager.literal("addxp")
                                .requires { it.hasPermissionLevel(2) }
                                .then(
                                    CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(::addExperience)
                                )
                        )
                )
        )
    }
    
    private fun showLevelInfo(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val levelComponent = BbfComponents.PLAYER_LEVEL.get(player)
        
        val level = levelComponent.level
        val xp = levelComponent.experience
        val requiredXp = levelComponent.getRequiredExperience(level)
        val proficiency = levelComponent.getProficiencyBonus()
        
        context.source.sendFeedback(
            { Text.literal("§6=== Character Info ===") },
            false
        )
        context.source.sendFeedback(
            { Text.literal("§eLevel: §f$level") },
            false
        )
        context.source.sendFeedback(
            { Text.literal("§eExperience: §f$xp / $requiredXp") },
            false
        )
        context.source.sendFeedback(
            { Text.literal("§eProficiency Bonus: §f+$proficiency") },
            false
        )
        
        return 1
    }
    
    private fun setLevel(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val newLevel = IntegerArgumentType.getInteger(context, "level")
        val levelComponent = BbfComponents.PLAYER_LEVEL.get(player)
        
        levelComponent.setLevel(newLevel)
        BbfComponents.PLAYER_LEVEL.sync(player)
        
        context.source.sendFeedback(
            { Text.literal("§aLevel set to $newLevel") },
            true
        )
        
        return 1
    }
    
    private fun addExperience(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val amount = IntegerArgumentType.getInteger(context, "amount")
        val levelComponent = BbfComponents.PLAYER_LEVEL.get(player)
        
        val leveledUp = levelComponent.addExperience(amount)
        BbfComponents.PLAYER_LEVEL.sync(player)
        
        if (leveledUp) {
            context.source.sendFeedback(
                { Text.literal("§6✦ Level Up! §eYou are now level ${levelComponent.level}") },
                true
            )
        } else {
            context.source.sendFeedback(
                { Text.literal("§a+$amount XP (${levelComponent.experience}/${levelComponent.getRequiredExperience(levelComponent.level)})") },
                false
            )
        }
        
        return 1
    }
}
