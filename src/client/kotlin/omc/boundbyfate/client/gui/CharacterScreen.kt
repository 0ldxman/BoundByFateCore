package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats

/**
 * Character sheet screen - D&D style character information.
 *
 * Sections:
 * - Header: name, race, class, level
 * - Stats: 6 ability scores with modifiers
 * - Combat: HP, AC, speed, proficiency bonus
 * - Skills: skill list with bonuses
 *
 * Background: 352x222 px
 */
class CharacterScreen : Screen(Text.translatable("screen.boundbyfate.character")) {

    companion object {
        const val BG_WIDTH = 352
        const val BG_HEIGHT = 222
    }

    private var bgX = 0
    private var bgY = 0

    override fun init() {
        bgX = (width - BG_WIDTH) / 2
        bgY = (height - BG_HEIGHT) / 2
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)

        // Background
        context.fill(bgX, bgY, bgX + BG_WIDTH, bgY + BG_HEIGHT, 0xCC1A1A2E.toInt())
        drawBorder(context)

        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, bgX + BG_WIDTH / 2, bgY + 6, 0xFFD700)

        val player = MinecraftClient.getInstance().player ?: return
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val levelData = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)

        var y = bgY + 18

        // ── Header ────────────────────────────────────────────────────────────
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal(player.name.string).styled { it.withColor(0xFFFFFF) },
            bgX + BG_WIDTH / 2, y, 0xFFFFFF
        )
        y += 10

        val raceStr = raceData?.raceId?.path?.replaceFirstChar { it.uppercase() } ?: "Обыватель"
        val classStr = classData?.let { "${it.classId.path.replaceFirstChar { c -> c.uppercase() }} ${it.classLevel}" } ?: ""
        val levelStr = levelData?.let { "Уровень ${it.level}" } ?: ""

        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal("$raceStr  $classStr  $levelStr").styled { it.withColor(0xAAAAAA) },
            bgX + BG_WIDTH / 2, y, 0xAAAAAA
        )
        y += 14

        // Divider
        context.fill(bgX + 8, y, bgX + BG_WIDTH - 8, y + 1, 0xFF6B4C9A.toInt())
        y += 6

        // ── Ability Scores ────────────────────────────────────────────────────
        context.drawTextWithShadow(textRenderer, Text.literal("ХАРАКТЕРИСТИКИ"), bgX + 8, y, 0x9B59B6)
        y += 10

        val stats = listOf(
            BbfStats.STRENGTH,
            BbfStats.DEXTERITY,
            BbfStats.CONSTITUTION,
            BbfStats.INTELLIGENCE,
            BbfStats.WISDOM,
            BbfStats.CHARISMA
        )

        val statBoxW = 52
        val statBoxH = 36
        val statsStartX = bgX + (BG_WIDTH - stats.size * statBoxW) / 2

        stats.forEachIndexed { index, stat ->
            val sx = statsStartX + index * statBoxW
            val sy = y

            // Box
            context.fill(sx + 1, sy + 1, sx + statBoxW - 1, sy + statBoxH - 1, 0xFF2A2A3E.toInt())
            context.fill(sx, sy, sx + statBoxW, sy + 1, 0xFF6B4C9A.toInt())
            context.fill(sx, sy + statBoxH - 1, sx + statBoxW, sy + statBoxH, 0xFF6B4C9A.toInt())
            context.fill(sx, sy, sx + 1, sy + statBoxH, 0xFF6B4C9A.toInt())
            context.fill(sx + statBoxW - 1, sy, sx + statBoxW, sy + statBoxH, 0xFF6B4C9A.toInt())

            // Short name
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(stat.shortName).styled { it.withColor(0x9B59B6) },
                sx + statBoxW / 2, sy + 3, 0x9B59B6
            )

            // Value and modifier
            val statValue = statsData?.getStatValue(stat.id)
            val total = statValue?.total ?: stat.defaultValue
            val mod = statValue?.dndModifier ?: 0
            val modStr = if (mod >= 0) "+$mod" else "$mod"

            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("$total").styled { it.withColor(0xFFFFFF) },
                sx + statBoxW / 2, sy + 13, 0xFFFFFF
            )
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(modStr).styled { it.withColor(if (mod >= 0) 0x2ECC71 else 0xE74C3C) },
                sx + statBoxW / 2, sy + 23, 0xFFFFFF
            )
        }

        y += statBoxH + 8

        // Divider
        context.fill(bgX + 8, y, bgX + BG_WIDTH - 8, y + 1, 0xFF6B4C9A.toInt())
        y += 6

        // ── Combat Stats ──────────────────────────────────────────────────────
        context.drawTextWithShadow(textRenderer, Text.literal("БОЕВЫЕ ХАРАКТЕРИСТИКИ"), bgX + 8, y, 0x9B59B6)
        y += 10

        val hp = player.health.toInt()
        val maxHp = player.maxHealth.toInt()
        val profBonus = levelData?.getProficiencyBonus() ?: 2

        val combatStats = listOf(
            "HP" to "$hp / $maxHp",
            "Бонус мастерства" to "+$profBonus"
        )

        combatStats.forEachIndexed { index, (label, value) ->
            val cx = bgX + 8 + index * 120
            context.drawTextWithShadow(textRenderer, Text.literal("$label: ").styled { it.withColor(0xAAAAAA) }, cx, y, 0xAAAAAA)
            context.drawTextWithShadow(textRenderer, Text.literal(value).styled { it.withColor(0xFFFFFF) }, cx + textRenderer.getWidth("$label: "), y, 0xFFFFFF)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawBorder(context: DrawContext) {
        context.fill(bgX, bgY, bgX + BG_WIDTH, bgY + 1, 0xFF6B4C9A.toInt())
        context.fill(bgX, bgY + BG_HEIGHT - 1, bgX + BG_WIDTH, bgY + BG_HEIGHT, 0xFF6B4C9A.toInt())
        context.fill(bgX, bgY, bgX + 1, bgY + BG_HEIGHT, 0xFF6B4C9A.toInt())
        context.fill(bgX + BG_WIDTH - 1, bgY, bgX + BG_WIDTH, bgY + BG_HEIGHT, 0xFF6B4C9A.toInt())
    }

    override fun shouldPause() = false
}
