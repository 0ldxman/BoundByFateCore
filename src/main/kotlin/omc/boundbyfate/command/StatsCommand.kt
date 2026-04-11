package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.StatRegistry

object StatsCommand {
    fun register(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        registryAccess: CommandRegistryAccess,
        environment: CommandManager.RegistrationEnvironment
    ) {
        dispatcher.register(
            CommandManager.literal("bbf")
                .then(
                    CommandManager.literal("stats")
                        .then(
                            CommandManager.literal("info")
                                .executes(::showStatsInfo)
                        )
                )
        )
    }
    
    private fun showStatsInfo(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        
        if (statsData == null) {
            context.source.sendFeedback(
                { Text.literal("§cNo stats data found!") },
                false
            )
            return 0
        }
        
        context.source.sendFeedback(
            { Text.literal("§6=== Character Stats ===") },
            false
        )
        
        // Show all stats
        for (statDef in StatRegistry.getAll()) {
            val statValue = statsData.getStatValue(statDef.id)
            val modifierStr = if (statValue.dndModifier >= 0) "+${statValue.dndModifier}" else "${statValue.dndModifier}"
            
            context.source.sendFeedback(
                { Text.literal("§e${statDef.shortName} (${statDef.displayName}): §f${statValue.total} §7($modifierStr)") },
                false
            )
        }
        
        return 1
    }
}
