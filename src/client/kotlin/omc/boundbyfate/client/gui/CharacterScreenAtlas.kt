package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfStats

class CharacterScreenAtlas : Screen(Text.translatable("screen.boundbyfate.character")) {

    // Щит: оригинал 109x172, ÷4
    private val shieldW = 27
    private val shieldH = 43

    // Баннер конец: оригинал 66x97, уменьшаем высоту ÷3, ширину ÷2
    private val bannerEndW = 33
    private val bannerEndH = 32

    // Тайл баннера: оригинал 53x53, ÷2 ширина, высота = bannerEndH
    private val bannerTileW = 26

    // Диагональный отступ щитов (каждый следующий щит смещается на это значение)
    private val shieldDiagStep = 12

    private var cx = 0
    private var cy = 0

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
            cx, cy + 70,
            70,
            cx - mouseX.toFloat(),
            cy - mouseY.toFloat(),
            player
        )

        // ═══ ЩИТЫ ХАРАКТЕРИСТИК ═══
        // Базовые X позиции для среднего (index=1) щита
        val leftBaseX = cx - 75
        val rightBaseX = cx + 75 - shieldW
        val shieldsTopY = cy - 55
        val shieldStep = shieldH + 6

        val leftStats = listOf(BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY)
        val rightStats = listOf(BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA)

        // Левые щиты: верхний (i=0) самый левый, нижний (i=2) ближе к центру
        // диагональ: i=0 → -2*step, i=1 → -1*step, i=2 → 0
        leftStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep  // верхний дальше от центра
            drawStatShield(context, leftBaseX - diagOffset, shieldsTopY + i * shieldStep, stat, statsData)
        }

        // Правые щиты: верхний (i=0) самый правый, нижний (i=2) ближе к центру
        rightStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            drawStatShield(context, rightBaseX + diagOffset, shieldsTopY + i * shieldStep, stat, statsData)
        }

        // ═══ БАННЕРЫ ═══
        val nameBannerW = 130
        val nameBannerX = cx - nameBannerW / 2
        val nameBannerY = 8
        drawBanner(context, nameBannerX, nameBannerY, nameBannerW)
        drawSmallCenteredText(context, player.name.string, cx, nameBannerY + 8, 0xFFD700)

        // Левый баннер (класс) — шире, чуть ниже
        val sideBannerW = 120
        val sideBannerY = nameBannerY + 14
        val classBannerX = cx - sideBannerW - 70
        drawBanner(context, classBannerX, sideBannerY, sideBannerW)
        val classStr = classData?.classId?.path?.replaceFirstChar { it.uppercase() } ?: "Commoner"
        val classLevel = classData?.classLevel ?: 1
        drawSmallCenteredText(context, "$classStr $classLevel", classBannerX + sideBannerW / 2, sideBannerY + 8, 0xD4AF37)

        val raceBannerX = cx + 70
        drawBanner(context, raceBannerX, sideBannerY, sideBannerW)
        val raceStr = raceData?.raceId?.path?.replaceFirstChar { it.uppercase() } ?: "Human"
        drawSmallCenteredText(context, raceStr, raceBannerX + sideBannerW / 2, sideBannerY + 8, 0xD4AF37)

        super.render(context, mouseX, mouseY, delta)
    }

    /** Рисует щит с уменьшенным текстом через MatrixStack scale */
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

        val midX = x + shieldW / 2

        drawScaledCenteredText(context, stat.shortName, midX, y + 9,  0xD4AF37, 0.6f)  // название: ниже
        drawScaledCenteredText(context, "$value",        midX, y + 17, 0xFFFFFF, 1.0f)  // число: крупнее
        drawScaledCenteredText(context, modStr,          midX, y + 29, if (mod >= 0) 0x2ECC71 else 0xE74C3C, 0.6f) // бонус: меньше, выше
    }

    /** Рисует баннер: левый конец + тайлы (квадратные, по верху) + правый конец */
    private fun drawBanner(context: DrawContext, x: Int, y: Int, totalWidth: Int) {
        // Тайл квадратный (53x53), рисуем 26x26 — сохраняем соотношение сторон
        val tileDrawH = y  // 26x26
        GuiAtlas.HEADER_LEFT.draw(context, x, y, bannerEndW, bannerEndH)
        var tx = x + bannerEndW
        var remaining = totalWidth - bannerEndW * 2
        while (remaining > 0) {
            val drawW = minOf(bannerTileW, remaining)
            GuiAtlas.HEADER_TILE.draw(context, tx, y, drawW, tileDrawH)
            tx += drawW
            remaining -= drawW
        }
        GuiAtlas.HEADER_RIGHT.draw(context, x + totalWidth - bannerEndW, y, bannerEndW, bannerEndH)
    }

    /** Текст с масштабированием через MatrixStack */
    private fun drawScaledCenteredText(context: DrawContext, text: String, cx: Int, y: Int, color: Int, scale: Float) {
        val matrices = context.matrices
        matrices.push()
        matrices.translate(cx.toFloat(), y.toFloat(), 0f)
        matrices.scale(scale, scale, 1f)
        val w = textRenderer.getWidth(text)
        context.drawTextWithShadow(textRenderer, text, -(w / 2), 0, color)
        matrices.pop()
    }

    /** Обычный текст чуть меньше стандартного */
    private fun drawSmallCenteredText(context: DrawContext, text: String, cx: Int, y: Int, color: Int) {
        drawScaledCenteredText(context, text, cx, y, color, 0.85f)
    }

    override fun shouldPause() = false
}
