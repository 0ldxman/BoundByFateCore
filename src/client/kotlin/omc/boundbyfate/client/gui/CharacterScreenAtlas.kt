package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.component.EntityStatsData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats

/**
 * Character sheet screen с использованием атласа.
 * 
 * Новый дизайн:
 * - Модель игрока в центре
 * - Щиты характеристик слева и справа (3 + 3)
 * - Навыки рядом с характеристиками
 * - 3 баннера сверху (имя, класс, раса)
 */
class CharacterScreenAtlas : Screen(Text.translatable("screen.boundbyfate.character")) {

    companion object {
        // Размеры щита характеристики (соотношение 109:172 из атласа)
        const val STAT_SHIELD_WIDTH = 55
        const val STAT_SHIELD_HEIGHT = 86
        const val STAT_SPACING = 10
        
        // Размеры иконки навыка
        const val SKILL_ICON_SIZE = 24
        const val SKILL_SPACING = 5
        
        // Размеры баннеров
        const val BANNER_WIDTH = 150
        const val BANNER_HEIGHT = 60
    }

    private var centerX = 0
    private var centerY = 0

    override fun init() {
        centerX = width / 2
        centerY = height / 2
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)

        val player = MinecraftClient.getInstance().player ?: return
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)

        // ═══ 1. МОДЕЛЬ ИГРОКА В ЦЕНТРЕ ═══
        val playerModelX = centerX
        val playerModelY = centerY + 20
        
        InventoryScreen.drawEntity(
            context,
            playerModelX,
            playerModelY + 50,
            30,
            playerModelX - mouseX.toFloat(),
            playerModelY - mouseY.toFloat(),
            player
        )

        // ═══ 2. ЩИТЫ ХАРАКТЕРИСТИК ═══
        val leftStats = listOf(
            BbfStats.STRENGTH,
            BbfStats.CONSTITUTION,
            BbfStats.DEXTERITY
        )
        
        val rightStats = listOf(
            BbfStats.INTELLIGENCE,
            BbfStats.WISDOM,
            BbfStats.CHARISMA
        )

        // Левые характеристики
        leftStats.forEachIndexed { index, stat ->
            val statX = centerX - 120
            val statY = centerY - 80 + index * (STAT_SHIELD_HEIGHT + STAT_SPACING)
            
            drawStatShield(context, statX, statY, stat, statsData, isLeft = true)
        }

        // Правые характеристики
        rightStats.forEachIndexed { index, stat ->
            val statX = centerX + 70
            val statY = centerY - 80 + index * (STAT_SHIELD_HEIGHT + STAT_SPACING)
            
            drawStatShield(context, statX, statY, stat, statsData, isLeft = false)
        }

        // ═══ 3. НАВЫКИ ═══
        // Пока оставим место для навыков, добавим позже
        
        // ═══ 4. БАННЕРЫ СВЕРХУ ═══
        val topY = 20
        
        // Центральный баннер - имя игрока
        val nameX = centerX - BANNER_WIDTH / 2
        NineSliceRenderer.drawHeader(context, nameX, topY, BANNER_WIDTH, withHighlight = true)
        context.drawCenteredTextWithShadow(
            textRenderer,
            player.name,
            centerX,
            topY + 25,
            0xFFD700
        )

        // Левый баннер - класс
        val classX = centerX - BANNER_WIDTH - 20
        val classY = topY + 70
        NineSliceRenderer.drawHeader(context, classX, classY, BANNER_WIDTH, withHighlight = false)
        
        val classStr = classData?.classId?.path?.replaceFirstChar { it.uppercase() } ?: "Commoner"
        val classLevel = classData?.classLevel ?: 1
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal("$classStr $classLevel"),
            classX + BANNER_WIDTH / 2,
            classY + 25,
            0xD4AF37
        )

        // Правый баннер - раса
        val raceX = centerX + 20
        val raceY = topY + 70
        NineSliceRenderer.drawHeader(context, raceX, raceY, BANNER_WIDTH, withHighlight = false)
        
        val raceStr = raceData?.raceId?.path?.replaceFirstChar { it.uppercase() } ?: "Human"
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal(raceStr),
            raceX + BANNER_WIDTH / 2,
            raceY + 25,
            0xD4AF37
        )

        super.render(context, mouseX, mouseY, delta)
    }

    /**
     * Рисует щит характеристики с её значением.
     */
    private fun drawStatShield(
        context: DrawContext,
        x: Int, y: Int,
        stat: omc.boundbyfate.api.stat.StatDefinition,
        statsData: EntityStatsData?,
        isLeft: Boolean
    ) {
        // Рисуем щит (фон характеристики) с правильным соотношением сторон
        GuiAtlas.ICON_STAT_BG.draw(
            context,
            x, y,
            STAT_SHIELD_WIDTH, STAT_SHIELD_HEIGHT
        )

        // Получаем значения
        val value = statsData?.getStatValue(stat.id)?.total ?: 10
        val mod = statsData?.getStatValue(stat.id)?.dndModifier ?: 0

        // Короткое имя характеристики
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal(stat.shortName),
            x + STAT_SHIELD_WIDTH / 2,
            y + 15,
            0xD4AF37
        )

        // Значение
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal("$value"),
            x + STAT_SHIELD_WIDTH / 2,
            y + 40,
            0xFFFFFF
        )

        // Модификатор
        val modStr = if (mod >= 0) "+$mod" else "$mod"
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal(modStr),
            x + STAT_SHIELD_WIDTH / 2,
            y + 60,
            if (mod >= 0) 0x2ECC71 else 0xE74C3C
        )
    }

    override fun shouldPause() = false
}
