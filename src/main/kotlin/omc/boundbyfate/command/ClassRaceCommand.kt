package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.network.ServerPacketHandler
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.ClassRegistry
import omc.boundbyfate.registry.RaceRegistry
import omc.boundbyfate.system.charclass.ClassSystem
import omc.boundbyfate.system.race.RaceSystem
import java.util.concurrent.CompletableFuture

/**
 * Admin commands for changing a player's class and race.
 *
 * Usage:
 *   /bbf class set <player> <classId> [subclassId]
 *   /bbf class subclass set <player> <subclassId>
 *   /bbf race set <player> <raceId> [subraceId]
 *   /bbf race subrace set <player> <subraceId>
 */
object ClassRaceCommand {

    fun register(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        registryAccess: CommandRegistryAccess,
        environment: CommandManager.RegistrationEnvironment
    ) {
        dispatcher.register(
            CommandManager.literal("bbf")
                .then(
                    CommandManager.literal("class")
                        .requires { it.hasPermissionLevel(2) }
                        .then(
                            CommandManager.literal("set")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("classId", StringArgumentType.string())
                                                .suggests { ctx, builder -> suggestClasses(builder) }
                                                .executes { ctx -> setClass(ctx, null) }
                                                .then(
                                                    CommandManager.argument("subclassId", StringArgumentType.string())
                                                        .suggests { ctx, builder ->
                                                            suggestSubclasses(ctx, builder)
                                                        }
                                                        .executes { ctx ->
                                                            setClass(ctx, StringArgumentType.getString(ctx, "subclassId"))
                                                        }
                                                )
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("subclass")
                                .then(
                                    CommandManager.literal("set")
                                        .then(
                                            CommandManager.argument("player", EntityArgumentType.player())
                                                .then(
                                                    CommandManager.argument("subclassId", StringArgumentType.string())
                                                        .suggests { ctx, builder ->
                                                            suggestSubclasses(ctx, builder)
                                                        }
                                                        .executes(::setSubclass)
                                                )
                                        )
                                )
                        )
                )
                .then(
                    CommandManager.literal("race")
                        .requires { it.hasPermissionLevel(2) }
                        .then(
                            CommandManager.literal("set")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("raceId", StringArgumentType.string())
                                                .suggests { ctx, builder -> suggestRaces(builder) }
                                                .executes { ctx -> setRace(ctx, null) }
                                                .then(
                                                    CommandManager.argument("subraceId", StringArgumentType.string())
                                                        .suggests { ctx, builder ->
                                                            suggestSubraces(ctx, builder)
                                                        }
                                                        .executes { ctx ->
                                                            setRace(ctx, StringArgumentType.getString(ctx, "subraceId"))
                                                        }
                                                )
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("subrace")
                                .then(
                                    CommandManager.literal("set")
                                        .then(
                                            CommandManager.argument("player", EntityArgumentType.player())
                                                .then(
                                                    CommandManager.argument("subraceId", StringArgumentType.string())
                                                        .suggests { ctx, builder ->
                                                            suggestSubraces(ctx, builder)
                                                        }
                                                        .executes(::setSubrace)
                                                )
                                        )
                                )
                        )
                )
        )
    }

    // ── Executors ─────────────────────────────────────────────────────────────

    private fun setClass(ctx: CommandContext<ServerCommandSource>, subclassIdStr: String?): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val classIdStr = StringArgumentType.getString(ctx, "classId")
        val classId = Identifier.tryParse(classIdStr)
            ?: return error(ctx, "Неверный ID класса: $classIdStr")

        val classDef = ClassRegistry.getClass(classId)
            ?: return error(ctx, "Класс не найден: $classId")

        val subclassId = subclassIdStr?.let { Identifier.tryParse(it) }

        val level = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)?.level ?: 1
        ClassSystem.applyClass(player, classId, subclassId, level)

        syncAll(player, ctx.source)
        ctx.source.sendFeedback(
            { Text.literal("§aКласс §e${classDef.displayName}§a применён к §e${player.name.string}") },
            true
        )
        return 1
    }

    private fun setSubclass(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val subclassIdStr = StringArgumentType.getString(ctx, "subclassId")
        val subclassId = Identifier.tryParse(subclassIdStr)
            ?: return error(ctx, "Неверный ID подкласса: $subclassIdStr")

        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
            ?: return error(ctx, "У игрока нет класса")

        val subDef = ClassRegistry.getSubclassesFor(classData.classId).find { it.id == subclassId }
            ?: return error(ctx, "Подкласс не найден: $subclassId")

        player.setAttached(
            BbfAttachments.PLAYER_CLASS,
            classData.copy(subclassId = subclassId)
        )

        syncAll(player, ctx.source)
        ctx.source.sendFeedback(
            { Text.literal("§aПодкласс §e${subDef.displayName}§a применён к §e${player.name.string}") },
            true
        )
        return 1
    }

    private fun setRace(ctx: CommandContext<ServerCommandSource>, subraceIdStr: String?): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val raceIdStr = StringArgumentType.getString(ctx, "raceId")
        val raceId = Identifier.tryParse(raceIdStr)
            ?: return error(ctx, "Неверный ID расы: $raceIdStr")

        val raceDef = RaceRegistry.getRace(raceId)
            ?: return error(ctx, "Раса не найдена: $raceId")

        val subraceId = subraceIdStr?.let { Identifier.tryParse(it) }

        RaceSystem.applyRace(player, raceId, subraceId)

        syncAll(player, ctx.source)
        ctx.source.sendFeedback(
            { Text.literal("§aРаса §e${raceDef.displayName}§a применена к §e${player.name.string}") },
            true
        )
        return 1
    }

    private fun setSubrace(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val subraceIdStr = StringArgumentType.getString(ctx, "subraceId")
        val subraceId = Identifier.tryParse(subraceIdStr)
            ?: return error(ctx, "Неверный ID подрасы: $subraceIdStr")

        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)
            ?: return error(ctx, "У игрока нет расы")

        val subDef = RaceRegistry.getSubrace(subraceId)
            ?: return error(ctx, "Подраса не найдена: $subraceId")

        // Re-apply race with new subrace
        RaceSystem.applyRace(player, raceData.raceId, subraceId)

        syncAll(player, ctx.source)
        ctx.source.sendFeedback(
            { Text.literal("§aПодраса §e${subDef.displayName}§a применена к §e${player.name.string}") },
            true
        )
        return 1
    }

    // ── Suggestions ───────────────────────────────────────────────────────────

    private fun suggestClasses(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        ClassRegistry.getAllClasses().forEach { builder.suggest(it.id.toString()) }
        return builder.buildFuture()
    }

    private fun suggestSubclasses(ctx: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        ClassRegistry.getAllClasses()
            .flatMap { ClassRegistry.getSubclassesFor(it.id) }
            .forEach { builder.suggest(it.id.toString()) }
        return builder.buildFuture()
    }

    private fun suggestRaces(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        RaceRegistry.getAllRaces().forEach { builder.suggest(it.id.toString()) }
        return builder.buildFuture()
    }

    private fun suggestSubraces(ctx: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        RaceRegistry.getAllRaces()
            .flatMap { race -> RaceRegistry.getSubracesFor(race.id) }
            .forEach { builder.suggest(it.id.toString()) }
        return builder.buildFuture()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun syncAll(player: ServerPlayerEntity, source: ServerCommandSource) {
        ServerPacketHandler.syncPlayerData(player)
        source.server.playerManager.playerList
            .filter { it.hasPermissionLevel(2) }
            .forEach { ServerPacketHandler.syncGmData(it) }
    }

    private fun error(ctx: CommandContext<ServerCommandSource>, msg: String): Int {
        ctx.source.sendError(Text.literal("§c$msg"))
        return 0
    }
}
