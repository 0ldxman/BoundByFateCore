package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats

/**
 * Character sheet screen с использованием атласа.
 *
 * Все размеры в GUI-координатах (scaled pixels).
 * Стандартный экран Minecraft ~427x240 при GUI scale 2 на 1080p.
 *
 * Щит (ICON_STAT_BG): оригинал 109x172 → рисуем 22x34 (÷5)
 * Баннер конец (HEADER_LEFT/RIGHT): оригинал 66x97 → рисуем 33x48 (÷2)
 * Баннер тайл (HEADER_TILE): оригинал 53x53 → рисуем 26x48 (÷2, высота = конец)
 */
class CharacterScreenAtlas : Screen(Text.translatable("screen.boundbyfate.character")) {

    // Размеры щита (оригинал 109x172, ÷5)
    private val shieldW = 22
    private val shieldH = 34

    // Размеры баннера (оригинал конец 66x97, ÷2)
    private val bannerEndW = 33
    private val bannerEndH = 48

    // Тайл баннера (оригинал 53x53, ÷2)
    private val bannerTileW = 26

    private var cx = 0  // center X
    private var cy = 0  // center Y

    override fun init() {
        cx = width / 2
        cy = height / 2
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)

        val player = MinecraftClient.getInstance().player ?: return
        val statsData = player.getAttachedOrElse(BbfAttachments.ENTITY_STATS, null)
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)

        // ═══ МОДЕЛЬ ИГРОКА ═══
        InventoryScreen.drawEntity(
            context,
            cx, cy + 40,
            30,
            cx - mouseX.toFloat(),
            cy - mouseY.toFloat(),
            player
        )

        // ═══ ЩИТЫ ХАРАКТЕРИСТИК ═══
        // Левые: STR, CON, DEX — колонка слева от игрока
        val leftX = cx - 80
        val rightX = cx + 80 - shieldW
        val shieldsTopY = cy - 50
        val shieldStep = shieldH + 8

        val leftStats = listOf(BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY)
        val rightStats = listOf(BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA)

        leftStats.forEachIndexed { i, stat ->
            drawStatShield(context, leftX, shieldsTopY + i * shieldStep, stat, statsData)
        }
        rightStats.forEachIndexed { i, stat ->
            drawStatShield(context, rightX, shieldsTopY + i * shieldStep, stat, statsData)
        }

        // ═══ БАННЕРЫ ═══
        // Центральный баннер (имя) — ширина 120px
        val nameBannerW = 120
        val nameBannerX = cx - nameBannerW / 2
        val nameBannerY = 10
        drawBanner(context, nameBannerX, nameBannerY, nameBannerW)
        context.drawCenteredTextWithShadow(textRenderer, player.name, cx, nameBannerY + 18, 0xFFD700)

        // Левый баннер (класс) — ширина 90px, ниже центрального
        val sideBannerW = 90
        val sideBannerY = nameBannerY + bannerEndH + 4
        val classBannerX = cx - sideBannerW - 8
        drawBanner(context, classBannerX, sideBannerY, sideBannerW)
        val classStr = classData?.classId?.path?.replaceFirstChar { it.uppercase() } ?: "Commoner"
        val classLevel = classData?.classLevel ?: 1
        context.drawCenteredTextWithShadow(
            textRenderer, Text.literal("$classStr $classLevel"),
            classBannerX + sideBannerW / 2, sideBannerY + 18, 0xD4AF37
        )

        // Правый баннер (раса)
        val raceBannerX = cx + 8
        drawBanner(context, raceBannerX, sideBannerY, sideBannerW)
        val raceStr = raceData?.raceId?.path?.replaceFirstChar { it.uppercase() } ?: "Human"
        context.drawCenteredTextWithShadow(
            textRenderer, Text.literal(raceStr),
            raceBannerX + sideBannerW / 2, sideBannerY + 18, 0xD4AF37
        )

        super.render(context, mouseX, mouseY, delta)
    }

    /** Рисует щит характеристики размером shieldW x shieldH */
    private fun drawStatShield(
        context: DrawContext,
        x: Int, y: Int,
        stat: omc.boundbyfate.api.stat.StatDefinition,
        statsData: EntityStatData?
    ) {
        GuiAtlas.ICON_STAT_BG.draw(context, x, y, shieldW, shieldH)

        val value = statsData?.getStatValue(stat.id)?.total ?: 10
        val mod = statsData?.getStatValue(stat.id)?.dndModifier ?: 0
        val modStr = if (mod >= 0) "+$mod" else "$mod"

        context.drawCenteredTextWithShadow(textRenderer, Text.literal(stat.shortName), x + shieldW / 2, y + 3, 0xD4AF37)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("$value"), x + shieldW / 2, y + 14, 0xFFFFFF)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(modStr), x + shieldW / 2, y + 24, if (mod >= 0) 0x2ECC71 else 0xE74C3C)
    }

    /** Рисует баннер заданной ширины: левый конец + тайлы + правый конец */
    private fun drawBanner(context: DrawContext, x: Int, y: Int, totalWidth: Int) {
        // Концы: оригинал 66x97, рисуем 33x48
        // Тайл: оригинал 53x53, рисуем 26x26 — своя высота, центрируем по вертикали внутри концов
        val tileH = 26  // оригинал 53 ÷ 2
        val tileOffsetY = 0  // верхний край тайла = верхний край концов

        // Левый конец
        GuiAtlas.HEADER_LEFT.draw(context, x, y, bannerEndW, bannerEndH)
        // Тайлы (на своей высоте, центрированы)
        var tx = x + bannerEndW
        var remaining = totalWidth - bannerEndW * 2
        while (remaining > 0) {
            val drawW = minOf(bannerTileW, remaining)
            GuiAtlas.HEADER_TILE.draw(context, tx, y + tileOffsetY, drawW, tileH)
            tx += drawW
            remaining -= drawW
        }
        // Правый конец
        GuiAtlas.HEADER_RIGHT.draw(context, x + totalWidth - bannerEndW, y, bannerEndW, bannerEndH)
    }

    override fun shouldPause() = false
}
