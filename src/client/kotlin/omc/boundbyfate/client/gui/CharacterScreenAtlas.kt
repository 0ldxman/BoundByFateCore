package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats

/**
 * Character sheet screen с использованием атласа.
 * 
 * Использует GuiAtlas и NineSliceRenderer для отрисовки.
 */
class CharacterScreenAtlas : Screen(Text.translatable("screen.boundbyfate.character")) {

    companion object {
        const val WINDOW_WIDTH = 400
        const val WINDOW_HEIGHT = 500
    }

    private var windowX = 0
    private var windowY = 0

    override fun init() {
        windowX = (width - WINDOW_WIDTH) / 2
        windowY = (height - WINDOW_HEIGHT) / 2
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)

        // Рисуем главное окно из атласа
        NineSliceRenderer.drawWindow(
            context,
            windowX, windowY,
            WINDOW_WIDTH, WINDOW_HEIGHT
        )

        val player = MinecraftClient.getInstance().player ?: return
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val levelData = player.getAttachedOrElse(BbfAttachments.PLAYER_LEVEL, null)
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)

        var y = windowY + 20

        // ═══ HEADER BANNER ═══
        NineSliceRenderer.drawHeader(
            context,
            windowX + 20,
            y,
            WINDOW_WIDTH - 40,
            withHighlight = true
        )

        // Имя персонажа на баннере
        context.drawCenteredTextWithShadow(
            textRenderer,
            player.name,
            windowX + WINDOW_WIDTH / 2,
            y + 40,
            0xFFD700
        )

        y += 110

        // Раса, класс, уровень
        val raceStr = raceData?.raceId?.path?.replaceFirstChar { it.uppercase() } ?: "Commoner"
        val classStr = classData?.let { "${it.classId.path.replaceFirstChar { c -> c.uppercase() }} ${it.classLevel}" } ?: ""
        val levelStr = levelData?.let { "Level ${it.level}" } ?: ""

        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal("$raceStr  $classStr  $levelStr"),
            windowX + WINDOW_WIDTH / 2,
            y,
            0xAAAAAA
        )

        y += 20

        // ═══ ABILITY SCORES ═══
        val stats = listOf(
            BbfStats.STRENGTH,
            BbfStats.DEXTERITY,
            BbfStats.CONSTITUTION,
            BbfStats.INTELLIGENCE,
            BbfStats.WISDOM,
            BbfStats.CHARISMA
        )

        stats.forEachIndexed { index, stat ->
            val statX = windowX + 40 + (index % 3) * 120
            val statY = y + (index / 3) * 100

            // Фон характеристики из атласа (масштабируем до нужного размера)
            GuiAtlas.ICON_STAT_BG.draw(
                context,
                statX - 10, statY - 10,
                80, 90
            )

            // Значения
            val value = statsData?.getStatValue(stat.id)?.total ?: 10
            val mod = statsData?.getStatValue(stat.id)?.dndModifier ?: 0

            // Короткое имя
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(stat.shortName),
                statX + 25, statY + 5,
                0xD4AF37
            )

            // Значение
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("$value"),
                statX + 25, statY + 30,
                0xFFFFFF
            )

            // Модификатор
            val modStr = if (mod >= 0) "+$mod" else "$mod"
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(modStr),
                statX + 25, statY + 50,
                if (mod >= 0) 0x2ECC71 else 0xE74C3C
            )
        }

        y += 220

        // ═══ HP BAR ═══
        val hpX = windowX + 40
        val hpY = y

        // Фон HP из атласа
        GuiAtlas.ICON_HP_BG.draw(
            context,
            hpX, hpY,
            WINDOW_WIDTH - 80, 40
        )

        // HP текст
        val hp = player.health.toInt()
        val maxHp = player.maxHealth.toInt()
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("HP: $hp / $maxHp"),
            hpX + 10, hpY + 15,
            0xFFFFFF
        )

        y += 60

        // ═══ SAVING THROWS ═══
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("SAVING THROWS"),
            windowX + 40, y,
            0xD4AF37
        )

        y += 15

        val saves = listOf(
            "STR" to "+3",
            "DEX" to "+5",
            "CON" to "+5",
            "INT" to "+0",
            "WIS" to "+1",
            "CHA" to "-1"
        )

        saves.forEachIndexed { index, (name, bonus) ->
            val saveX = windowX + 40 + (index % 3) * 120
            val saveY = y + (index / 3) * 35

            // Фон спасброска
            GuiAtlas.ICON_SAVE_BG.draw(context, saveX, saveY)

            // Иконка владения (если есть)
            if (index < 3) { // Пример: первые 3 с владением
                GuiAtlas.ICON_PROFICIENCY.draw(context, saveX + 2, saveY + 2)
            }

            // Название и бонус
            context.drawTextWithShadow(
                textRenderer,
                Text.literal("$name $bonus"),
                saveX + 30, saveY + 7,
                0xFFFFFF
            )
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun shouldPause() = false
}
