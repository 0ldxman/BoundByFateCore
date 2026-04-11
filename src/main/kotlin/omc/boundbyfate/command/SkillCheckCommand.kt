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
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.api.dice.AdvantageType
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfSkills
import omc.boundbyfate.registry.SkillRegistry
import omc.boundbyfate.system.check.CheckRequest
import omc.boundbyfate.system.check.CheckSystem
import omc.boundbyfate.system.check.PendingCheckRequest
import omc.boundbyfate.system.check.PendingCheckStore
import omc.boundbyfate.component.PlayerLevelData
import java.util.UUID

object SkillCheckCommand {

    fun register(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        registryAccess: CommandRegistryAccess,
        environment: CommandManager.RegistrationEnvironment
    ) {
        // /skillcheck-request <player> <skill> [dc] [advantage|disadvantage]
        dispatcher.register(
            CommandManager.literal("skillcheck-request")
                .requires { it.hasPermissionLevel(2) }
                .then(
                    CommandManager.argument("player", EntityArgumentType.player())
                        .then(
                            CommandManager.argument("skill", StringArgumentType.string())
                                .executes { ctx -> requestCheck(ctx, dc = null, advantage = AdvantageType.NONE) }
                                .then(
                                    CommandManager.argument("dc", IntegerArgumentType.integer(1, 40))
                                        .executes { ctx -> requestCheck(ctx, dc = IntegerArgumentType.getInteger(ctx, "dc"), advantage = AdvantageType.NONE) }
                                        .then(
                                            CommandManager.argument("advantage", StringArgumentType.word())
                                                .suggests { _, builder ->
                                                    builder.suggest("advantage")
                                                    builder.suggest("disadvantage")
                                                    builder.buildFuture()
                                                }
                                                .executes { ctx ->
                                                    val adv = parseAdvantage(StringArgumentType.getString(ctx, "advantage"))
                                                    requestCheck(ctx, dc = IntegerArgumentType.getInteger(ctx, "dc"), advantage = adv)
                                                }
                                        )
                                )
                        )
                )
        )

        // /skillcheck <skill> [requestId]
        // - Without requestId: free roll (player-initiated)
        // - With requestId: resolving a pending request from admin
        dispatcher.register(
            CommandManager.literal("skillcheck")
                .then(
                    CommandManager.argument("skill", StringArgumentType.string())
                        .executes { ctx -> freeCheck(ctx) }
                        .then(
                            CommandManager.argument("requestId", StringArgumentType.string())
                                .executes { ctx -> resolveCheck(ctx) }
                        )
                )
        )
    }

    // ── /skillcheck-request ───────────────────────────────────────────────────

    private fun requestCheck(
        context: CommandContext<ServerCommandSource>,
        dc: Int?,
        advantage: AdvantageType
    ): Int {
        val target = EntityArgumentType.getPlayer(context, "player")
        val skillStr = StringArgumentType.getString(context, "skill")

        val skillDef = resolveSkill(skillStr) ?: run {
            context.source.sendError(Text.literal("§cНеизвестный навык: $skillStr"))
            return 0
        }

        // Build pending request
        val request = PendingCheckRequest(
            id = UUID.randomUUID(),
            playerName = target.name.string,
            skillId = skillDef.id,
            dc = dc,
            advantage = advantage
        )
        PendingCheckStore.put(request)

        // Calculate total bonus for display
        val totalBonus = calculateTotalBonus(target, skillDef.id)
        val bonusStr = if (totalBonus >= 0) "+$totalBonus" else "$totalBonus"

        // Build the message for the player
        val advantageStr = when (advantage) {
            AdvantageType.ADVANTAGE -> " §a[Преимущество]"
            AdvantageType.DISADVANTAGE -> " §c[Помеха]"
            AdvantageType.NONE -> ""
        }

        val header = Text.literal("§6§l━━━ Проверка навыка ━━━\n")
        val skillLine = Text.literal("§eНавык: §f${skillDef.displayName}$advantageStr\n")
        val bonusLine = Text.literal("§eБонус: §f$bonusStr\n")
        val button = buildRollButton(request.id, skillDef.id)

        val message = header.append(skillLine).append(bonusLine).append(button)
        target.sendMessage(message, false)

        // Confirm to admin
        context.source.sendFeedback(
            { Text.literal("§aЗапрос на проверку §e${skillDef.displayName}§a отправлен игроку §e${target.name.string}") },
            false
        )

        return 1
    }

    private fun buildRollButton(requestId: UUID, skillId: Identifier): MutableText {
        val command = "/skillcheck ${skillId} $requestId"
        return Text.literal("§a§l[ Бросить ]")
            .styled { style ->
                style
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§7Нажмите чтобы совершить проверку")))
                    .withBold(true)
                    .withColor(net.minecraft.util.Formatting.GREEN)
            }
    }

    // ── /skillcheck (free roll) ───────────────────────────────────────────────

    private fun freeCheck(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val skillStr = StringArgumentType.getString(context, "skill")

        val skillDef = resolveSkill(skillStr) ?: run {
            context.source.sendError(Text.literal("§cНеизвестный навык: $skillStr"))
            return 0
        }

        val result = CheckSystem.check(
            player,
            CheckRequest(
                skillId = skillDef.id,
                dc = null,
                advantage = AdvantageType.NONE,
                visible = false // We handle output ourselves
            )
        ) ?: return 0

        // Send result to player and all admins
        val message = buildResultMessage(player.name.string, skillDef.displayName, result)
        broadcastToPlayerAndAdmins(player, message)

        return 1
    }

    // ── /skillcheck <skill> <requestId> (resolve pending) ────────────────────

    private fun resolveCheck(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val skillStr = StringArgumentType.getString(context, "skill")
        val requestIdStr = StringArgumentType.getString(context, "requestId")

        val requestId = try {
            UUID.fromString(requestIdStr)
        } catch (e: Exception) {
            context.source.sendError(Text.literal("§cНеверный ID запроса"))
            return 0
        }

        val pending = PendingCheckStore.take(requestId) ?: run {
            context.source.sendError(Text.literal("§cЗапрос не найден или истёк срок действия"))
            return 0
        }

        // Verify this request is for this player
        if (pending.playerName != player.name.string) {
            context.source.sendError(Text.literal("§cЭтот запрос предназначен для другого игрока"))
            return 0
        }

        val skillDef = SkillRegistry.get(pending.skillId) ?: run {
            context.source.sendError(Text.literal("§cНавык не найден"))
            return 0
        }

        val result = CheckSystem.check(
            player,
            CheckRequest(
                skillId = pending.skillId,
                dc = pending.dc,
                advantage = pending.advantage,
                visible = false // We handle output ourselves
            )
        ) ?: return 0

        // Build result message
        val message = buildResultMessage(player.name.string, skillDef.displayName, result)
        broadcastToPlayerAndAdmins(player, message)

        return 1
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildResultMessage(
        playerName: String,
        skillName: String,
        result: omc.boundbyfate.system.check.CheckResult
    ): Text {
        val dice = result.diceResult

        val header = "§6[Проверка] §e$playerName §7| §f$skillName\n"

        val rollPart = when {
            result.isCriticalSuccess -> "§a✦ КРИТИЧЕСКИЙ УСПЕХ! §7[20]"
            result.isCriticalFailure -> "§c✦ КРИТИЧЕСКИЙ ПРОВАЛ! §7[1]"
            else -> {
                val diceStr = if (dice.rolls.size == 2) {
                    val chosen = if (result.request.advantage == AdvantageType.ADVANTAGE)
                        maxOf(dice.rolls[0], dice.rolls[1]) else minOf(dice.rolls[0], dice.rolls[1])
                    "§7[${dice.rolls[0]}, ${dice.rolls[1]}]→§f$chosen"
                } else {
                    "§7[§f${dice.rolls[0]}§7]"
                }

                val statStr = if (result.statModifier != 0) {
                    val sign = if (result.statModifier > 0) "+" else ""
                    " §7$sign${result.statModifier}(стат)"
                } else ""

                val profStr = if (result.proficiencyBonus != 0) {
                    " §7+${result.proficiencyBonus}(мастерство)"
                } else ""

                val outcomeStr = result.request.dc?.let { dc ->
                    if (result.success == true) " §a→ Успех (DC$dc)" else " §c→ Провал (DC$dc)"
                } ?: ""

                "$diceStr$statStr$profStr §f= ${result.total}$outcomeStr"
            }
        }

        return Text.literal(header + rollPart)
    }

    private fun broadcastToPlayerAndAdmins(player: ServerPlayerEntity, message: Text) {
        // Send to the player
        player.sendMessage(message, false)

        // Send to all online operators (except the player themselves)
        player.server.playerManager.playerList
            .filter { it != player && it.hasPermissionLevel(2) }
            .forEach { it.sendMessage(message, false) }
    }

    private fun calculateTotalBonus(player: ServerPlayerEntity, skillId: Identifier): Int {
        val skillDef = SkillRegistry.get(skillId) ?: return 0

        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val statModifier = statsData?.getStatValue(skillDef.linkedStat)?.dndModifier ?: 0

        val levelData = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, PlayerLevelData())
        val profBonus = levelData.getProficiencyBonus()

        val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, null)
        val profLevel = skillData?.getProficiency(skillId)?.multiplier ?: 0

        return statModifier + (profBonus * profLevel)
    }

    private fun resolveSkill(input: String): omc.boundbyfate.api.skill.SkillDefinition? {
        // Try full ID first (boundbyfate-core:athletics)
        if (input.contains(":")) {
            return SkillRegistry.get(Identifier(input))
        }

        // Try short name match (case-insensitive)
        return SkillRegistry.getAll().find {
            it.id.path.equals(input, ignoreCase = true) ||
            it.displayName.equals(input, ignoreCase = true)
        }
    }

    private fun parseAdvantage(str: String): AdvantageType = when (str.lowercase()) {
        "advantage", "преимущество" -> AdvantageType.ADVANTAGE
        "disadvantage", "помеха" -> AdvantageType.DISADVANTAGE
        else -> AdvantageType.NONE
    }
}
