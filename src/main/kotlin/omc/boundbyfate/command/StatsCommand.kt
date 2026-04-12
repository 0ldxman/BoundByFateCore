package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats
import omc.boundbyfate.registry.ClassRegistry
import omc.boundbyfate.registry.StatRegistry
import omc.boundbyfate.system.HitPointsSystem
import omc.boundbyfate.system.stat.StatEffectProcessor

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
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .requires { it.hasPermissionLevel(2) }
                                        .executes(::showStatsInfoOther)
                                )
                        )
                        .then(
                            CommandManager.literal("set")
                                .requires { it.hasPermissionLevel(2) }
                                .then(
                                    CommandManager.argument("stat", StringArgumentType.string())
                                        .then(
                                            CommandManager.argument("value", IntegerArgumentType.integer(1, 30))
                                                .executes(::setStatSelf)
                                                .then(
                                                    CommandManager.argument("player", EntityArgumentType.player())
                                                        .executes(::setStatOther)
                                                )
                                        )
                                )
                        )
                )
        )
    }
    
    private fun showStatsInfo(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        return showStatsForPlayer(context, player)
    }
    
    private fun showStatsInfoOther(context: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(context, "player")
        return showStatsForPlayer(context, player)
    }
    
    private fun showStatsForPlayer(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        
        if (statsData == null) {
            context.source.sendFeedback(
                { Text.literal("§cNo stats data found for ${player.name.string}!") },
                false
            )
            return 0
        }
        
        context.source.sendFeedback(
            { Text.literal("§6=== Character Stats: ${player.name.string} ===") },
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
    
    private fun setStatSelf(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        return setStatForPlayer(context, player)
    }
    
    private fun setStatOther(context: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(context, "player")
        return setStatForPlayer(context, player)
    }
    
    private fun setStatForPlayer(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val statIdStr = StringArgumentType.getString(context, "stat")
        val value = IntegerArgumentType.getInteger(context, "value")
        
        // Parse stat ID
        val statId = try {
            if (statIdStr.contains(":")) {
                Identifier(statIdStr)
            } else {
                // Allow short names like "STR" -> "boundbyfate-core:strength"
                StatRegistry.getAll().find { it.shortName.equals(statIdStr, ignoreCase = true) }?.id
                    ?: throw IllegalArgumentException("Unknown stat: $statIdStr")
            }
        } catch (e: Exception) {
            context.source.sendError(Text.literal("§cInvalid stat ID: $statIdStr"))
            return 0
        }
        
        // Check if stat exists
        val statDef = StatRegistry.get(statId)
        if (statDef == null) {
            context.source.sendError(Text.literal("§cUnknown stat: $statId"))
            return 0
        }
        
        // Validate value
        val clampedValue = value.coerceIn(statDef.minValue, statDef.maxValue)
        if (clampedValue != value) {
            context.source.sendFeedback(
                { Text.literal("§eValue clamped to range [${statDef.minValue}, ${statDef.maxValue}]") },
                false
            )
        }
        
        // Get current stats
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        if (statsData == null) {
            context.source.sendError(Text.literal("§cNo stats data found for ${player.name.string}!"))
            return 0
        }
        
        // Update base stat
        val updatedStats = statsData.withBase(statId, clampedValue)
        player.setAttached(BbfAttachments.ENTITY_STATS, updatedStats)
        
        // Reapply effects
        StatEffectProcessor.applyAll(player, updatedStats)

        // Recalculate HP (CON modifier affects max HP)
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        val classDef = classData?.let { ClassRegistry.getClass(it.classId) }
        val level = classData?.classLevel ?: 1
        HitPointsSystem.applyHitPoints(player, classDef, level)
        
        // Send confirmation
        val newValue = updatedStats.getStatValue(statId)
        val modifierStr = if (newValue.dndModifier >= 0) "+${newValue.dndModifier}" else "${newValue.dndModifier}"
        
        context.source.sendFeedback(
            { Text.literal("§aSet ${statDef.shortName} for ${player.name.string} to §f$clampedValue §7($modifierStr)") },
            true
        )
        
        return 1
    }
}
