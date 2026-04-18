package omc.boundbyfate.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import omc.boundbyfate.component.GoalStatus
import omc.boundbyfate.component.TaskStatus
import omc.boundbyfate.system.identity.MotivationSystem

/**
 * Commands for managing player goals and motivations:
 *
 * /bbf goal add <player> <title> <description>
 * /bbf goal complete <player> <goalId> [newDescription]
 * /bbf goal fail <player> <goalId> [newDescription]
 * /bbf goal delete <player> <goalId>
 * /bbf goal task <player> <goalId> <status>
 *
 * /bbf motivation add <player> <text>
 * /bbf motivation remove <player> <motivationId>
 * /bbf motivation accept <player> <proposalId>
 * /bbf motivation reject <player> <proposalId>
 */
object GoalCommand {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("bbf")
                .then(
                    CommandManager.literal("goal")
                        .requires { it.hasPermissionLevel(2) }
                        .then(
                            CommandManager.literal("add")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("title", StringArgumentType.word())
                                                .then(
                                                    CommandManager.argument("description", StringArgumentType.greedyString())
                                                        .executes { addGoal(it) }
                                                )
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("complete")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("goalId", StringArgumentType.word())
                                                .executes { completeGoal(it, null) }
                                                .then(
                                                    CommandManager.argument("newDescription", StringArgumentType.greedyString())
                                                        .executes { completeGoal(it, StringArgumentType.getString(it, "newDescription")) }
                                                )
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("fail")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("goalId", StringArgumentType.word())
                                                .executes { failGoal(it, null) }
                                                .then(
                                                    CommandManager.argument("newDescription", StringArgumentType.greedyString())
                                                        .executes { failGoal(it, StringArgumentType.getString(it, "newDescription")) }
                                                )
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("delete")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("goalId", StringArgumentType.word())
                                                .executes { deleteGoal(it) }
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("task")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("goalId", StringArgumentType.word())
                                                .then(
                                                    CommandManager.argument("status", StringArgumentType.word())
                                                        .suggests { _, builder ->
                                                            builder.suggest("completed")
                                                            builder.suggest("failed")
                                                            builder.suggest("cancelled")
                                                            builder.buildFuture()
                                                        }
                                                        .executes { advanceTask(it) }
                                                )
                                        )
                                )
                        )
                )
                .then(
                    CommandManager.literal("motivation")
                        .requires { it.hasPermissionLevel(2) }
                        .then(
                            CommandManager.literal("add")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("text", StringArgumentType.greedyString())
                                                .executes { addMotivation(it) }
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("remove")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("motivationId", StringArgumentType.word())
                                                .executes { removeMotivation(it) }
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("accept")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("proposalId", StringArgumentType.word())
                                                .executes { acceptProposal(it) }
                                        )
                                )
                        )
                        .then(
                            CommandManager.literal("reject")
                                .then(
                                    CommandManager.argument("player", EntityArgumentType.player())
                                        .then(
                                            CommandManager.argument("proposalId", StringArgumentType.word())
                                                .executes { rejectProposal(it) }
                                        )
                                )
                        )
                )
        )
    }

    private fun addGoal(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val title = StringArgumentType.getString(ctx, "title")
        val description = StringArgumentType.getString(ctx, "description")
        val id = MotivationSystem.addGoal(player, title, description, null, emptyList())
        ctx.source.sendFeedback({ Text.literal("§aGoal '§f$title§a' added for §f${player.name.string}§a (id: §7$id§a)") }, true)
        return 1
    }

    private fun completeGoal(ctx: CommandContext<ServerCommandSource>, newDesc: String?): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val goalId = StringArgumentType.getString(ctx, "goalId")
        return if (MotivationSystem.completeGoal(player, goalId, newDesc)) {
            ctx.source.sendFeedback({ Text.literal("§a✓ Goal §f$goalId§a completed for §f${player.name.string}") }, true)
            1
        } else {
            ctx.source.sendError(Text.literal("Goal not found: $goalId")); 0
        }
    }

    private fun failGoal(ctx: CommandContext<ServerCommandSource>, newDesc: String?): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val goalId = StringArgumentType.getString(ctx, "goalId")
        return if (MotivationSystem.failGoal(player, goalId, newDesc)) {
            ctx.source.sendFeedback({ Text.literal("§c✗ Goal §f$goalId§c failed for §f${player.name.string}") }, true)
            1
        } else {
            ctx.source.sendError(Text.literal("Goal not found: $goalId")); 0
        }
    }

    private fun deleteGoal(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val goalId = StringArgumentType.getString(ctx, "goalId")
        return if (MotivationSystem.removeGoal(player, goalId)) {
            ctx.source.sendFeedback({ Text.literal("§7Goal §f$goalId§7 deleted for §f${player.name.string}") }, true)
            1
        } else {
            ctx.source.sendError(Text.literal("Goal not found: $goalId")); 0
        }
    }

    private fun advanceTask(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val goalId = StringArgumentType.getString(ctx, "goalId")
        val statusStr = StringArgumentType.getString(ctx, "status").uppercase()
        val taskStatus = try { TaskStatus.valueOf(statusStr) } catch (e: Exception) {
            ctx.source.sendError(Text.literal("Invalid status: $statusStr. Use completed/failed/cancelled")); return 0
        }
        return if (MotivationSystem.advanceTask(player, goalId, taskStatus)) {
            ctx.source.sendFeedback({ Text.literal("§eTask advanced (§f$statusStr§e) for goal §f$goalId") }, true)
            1
        } else {
            ctx.source.sendError(Text.literal("Goal not found or already finished: $goalId")); 0
        }
    }

    private fun addMotivation(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val text = StringArgumentType.getString(ctx, "text")
        val id = MotivationSystem.addMotivation(player, text, byGm = true)
        ctx.source.sendFeedback({ Text.literal("§aMotivation added for §f${player.name.string}§a (id: §7$id§a)") }, true)
        return 1
    }

    private fun removeMotivation(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val motivationId = StringArgumentType.getString(ctx, "motivationId")
        return if (MotivationSystem.removeMotivation(player, motivationId)) {
            ctx.source.sendFeedback({ Text.literal("§7Motivation §f$motivationId§7 removed (linked goals frozen)") }, true)
            1
        } else {
            ctx.source.sendError(Text.literal("Motivation not found: $motivationId")); 0
        }
    }

    private fun acceptProposal(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val proposalId = StringArgumentType.getString(ctx, "proposalId")
        return if (MotivationSystem.acceptProposal(player, proposalId)) {
            ctx.source.sendFeedback({ Text.literal("§aProposal §f$proposalId§a accepted for §f${player.name.string}") }, true)
            1
        } else {
            ctx.source.sendError(Text.literal("Proposal not found: $proposalId")); 0
        }
    }

    private fun rejectProposal(ctx: CommandContext<ServerCommandSource>): Int {
        val player = EntityArgumentType.getPlayer(ctx, "player")
        val proposalId = StringArgumentType.getString(ctx, "proposalId")
        return if (MotivationSystem.rejectProposal(player, proposalId)) {
            ctx.source.sendFeedback({ Text.literal("§7Proposal §f$proposalId§7 rejected") }, true)
            1
        } else {
            ctx.source.sendError(Text.literal("Proposal not found: $proposalId")); 0
        }
    }
}
