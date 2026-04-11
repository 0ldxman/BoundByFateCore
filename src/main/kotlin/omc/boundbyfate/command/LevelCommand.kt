package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import omc.boundbyfate.component.PlayerLevelData
import omc.boundbyfate.registry.BbfAttachments

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
        val levelData = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, PlayerLevelData())
        
        val level = levelData.level
        val xp = levelData.experience
        val requiredXp = levelData.getRequiredExperience(level)
        val proficiency = levelData.getProficiencyBonus()
        
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
        val levelData = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, PlayerLevelData())
        
        levelData.setLevel(newLevel)
        player.setAttached(BbfAttachments.PLAYER_LEVEL, levelData)
        
        context.source.sendFeedback(
            { Text.literal("§aLevel set to $newLevel") },
            true
        )
        
        return 1
    }
    
    private fun addExperience(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val amount = IntegerArgumentType.getInteger(context, "amount")
        val levelData = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, PlayerLevelData())
        
        val leveledUp = levelData.addExperience(amount)
        player.setAttached(BbfAttachments.PLAYER_LEVEL, levelData)
        
        if (leveledUp) {
            context.source.sendFeedback(
                { Text.literal("§6✦ Level Up! §eYou are now level ${levelData.level}") },
                true
            )
        } else {
            context.source.sendFeedback(
                { Text.literal("§a+$amount XP (${levelData.experience}/${levelData.getRequiredExperience(levelData.level)})") },
                false
            )
        }
        
        return 1
    }
}
