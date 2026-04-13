package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.registry.BbfSkills
import omc.boundbyfate.registry.BbfStats

class CharacterScreenAtlas : Screen(Text.translatable("screen.boundbyfate.character")) {

    // Щит: оригинал 109x172, ÷4
    private val shieldW = 27
    private val shieldH = 43

    // Баннер конец: оригинал 66x97
    private val bannerEndW = 33
    private val bannerEndH = 32

    // Тайл баннера
    private val bannerTileW = 17

    // Диагональный отступ щитов
    private val shieldDiagStep = 12

    // Иконка навыка: оригинал 24x24, рисуем 10x10
    private val skillIconSize = 10

    // Навыки по характеристикам (shortName для отображения)
    private val strSkills = listOf("Атлетика")
    private val conSkills = emptyList<String>()
    private val dexSkills = listOf("Акробатика", "Лов. рук", "Скрытность")
    private val intSkills = listOf("Магия", "История", "Анализ", "Природа", "Религия")
    private val wisSkills = listOf("Животные", "Проницат.", "Медицина", "Внимат.", "Выживание")
    private val chaSkills = listOf("Обман", "Запугив.", "Выступл.", "Убеждение")

    private val leftSkillsByIndex = listOf(strSkills, conSkills, dexSkills)
    private val rightSkillsByIndex = listOf(intSkills, wisSkills, chaSkills)

    // Соответствие навыков для получения бонуса
    private val strSkillDefs = listOf(BbfSkills.ATHLETICS)
    private val conSkillDefs = emptyList<omc.boundbyfate.api.skill.SkillDefinition>()
    private val dexSkillDefs = listOf(BbfSkills.ACROBATICS, BbfSkills.SLEIGHT_OF_HAND, BbfSkills.STEALTH)
    private val intSkillDefs = listOf(BbfSkills.ARCANA, BbfSkills.HISTORY, BbfSkills.INVESTIGATION, BbfSkills.NATURE, BbfSkills.RELIGION)
    private val wisSkillDefs = listOf(BbfSkills.ANIMAL_HANDLING, BbfSkills.INSIGHT, BbfSkills.MEDICINE, BbfSkills.PERCEPTION, BbfSkills.SURVIVAL)
    private val chaSkillDefs = listOf(BbfSkills.DECEPTION, BbfSkills.INTIMIDATION, BbfSkills.PERFORMANCE, BbfSkills.PERSUASION)

    private val leftSkillDefsByIndex = listOf(strSkillDefs, conSkillDefs, dexSkillDefs)
    private val rightSkillDefsByIndex = listOf(intSkillDefs, wisSkillDefs, chaSkillDefs)

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
        val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, null)
        val classData = player.getAttachedOrElse(BbfAttachments.PLAYER_CLASS, null)
        val raceData = player.getAttachedOrElse(BbfAttachments.PLAYER_RACE, null)

        // ═══ МОДЕЛЬ ИГРОКА ═══
        InventoryScreen.drawEntity(
            context,
            cx, cy + 85,
            70,
            cx - mouseX.toFloat(),
            cy - mouseY.toFloat(),
            player
        )

        // ═══ ЩИТЫ + НАВЫКИ ═══
        val leftBaseX = cx - 75
        val rightBaseX = cx + 75 - shieldW
        val shieldsTopY = cy - 55
        val shieldStep = shieldH + 6

        val leftStats = listOf(BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY)
        val rightStats = listOf(BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA)

        leftStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = leftBaseX - diagOffset
            val sy = shieldsTopY + i * shieldStep
            drawStatShield(context, sx, sy, stat, statsData)
            // Навыки левее щита, текст ещё левее иконки
            drawSkillList(context, sx - skillIconSize - 2, sy, leftSkillsByIndex[i], leftSkillDefsByIndex[i], statsData, skillData, isLeft = true)
        }

        rightStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = rightBaseX + diagOffset
            val sy = shieldsTopY + i * shieldStep
            drawStatShield(context, sx, sy, stat, statsData)
            // Навыки правее щита, текст ещё правее иконки
            drawSkillList(context, sx + shieldW + 2, sy, rightSkillsByIndex[i], rightSkillDefsByIndex[i], statsData, skillData, isLeft = false)
        }

        // ═══ БАННЕРЫ ═══
        val nameBannerW = 130
        val nameBannerX = cx - nameBannerW / 2
        val nameBannerY = 8
        drawBanner(context, nameBannerX, nameBannerY, nameBannerW)
        drawSmallCenteredText(context, player.name.string, cx, nameBannerY + 8, 0xFFD700)

        val sideBannerW = 120
        val sideBannerY = nameBannerY + 14
        val classBannerX = cx - sideBannerW - 70
        drawBanner(context, classBannerX, sideBannerY, sideBannerW)
        val classStr = classData?.classId?.path?.let { it[0].uppercaseChar() + it.substring(1) } ?: "Commoner"
        val classLevel = classData?.classLevel ?: 1
        drawSmallCenteredText(context, "$classStr $classLevel", classBannerX + sideBannerW / 2, sideBannerY + 8, 0xD4AF37)

        val raceBannerX = cx + 70
        drawBanner(context, raceBannerX, sideBannerY, sideBannerW)
        val raceStr = raceData?.raceId?.path?.let { it[0].uppercaseChar() + it.substring(1) } ?: "Human"
        drawSmallCenteredText(context, raceStr, raceBannerX + sideBannerW / 2, sideBannerY + 8, 0xD4AF37)

        super.render(context, mouseX, mouseY, delta)
    }

    /**
     * Рисует список навыков рядом со щитом.
     * isLeft=true: иконка справа, текст слева от иконки
     * isLeft=false: иконка слева, текст справа от иконки
     */
    private fun drawSkillList(
        context: DrawContext,
        anchorX: Int, anchorY: Int,
        names: List<String>,
        defs: List<omc.boundbyfate.api.skill.SkillDefinition>,
        statsData: EntityStatData?,
        skillData: EntitySkillData?,
        isLeft: Boolean
    ) {
        val rowH = 12
        names.forEachIndexed { i, name ->
            val y = anchorY + i * rowH
            val def = defs.getOrNull(i)

            // Бонус навыка = модификатор стата + бонус владения (если есть)
            val bonus = if (def != null && statsData != null) {
                val statMod = statsData.getStatValue(def.linkedStat).dndModifier
                val profLevel = skillData?.getProficiency(def.id)?.multiplier ?: 0
                val profBonus = 2 // базовый бонус владения
                statMod + profBonus * profLevel
            } else 0
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"

            if (isLeft) {
                // Иконка у правого края (anchorX = левый край иконки)
                GuiAtlas.ICON_SKILL_BG.draw(context, anchorX, y, skillIconSize, skillIconSize)
                // Текст левее иконки
                drawScaledCenteredText(context, "$name $bonusStr", anchorX - 20, y + 3, 0xCCCCCC, 0.6f)
            } else {
                // Иконка у левого края (anchorX = левый край иконки)
                GuiAtlas.ICON_SKILL_BG.draw(context, anchorX, y, skillIconSize, skillIconSize)
                // Текст правее иконки
                drawScaledCenteredText(context, "$name $bonusStr", anchorX + skillIconSize + 20, y + 3, 0xCCCCCC, 0.6f)
            }
        }
    }

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

        drawScaledCenteredText(context, stat.shortName, midX, y + 9,  0xD4AF37, 0.6f)
        drawScaledCenteredText(context, "$value",        midX, y + 17, 0xFFFFFF, 1.0f)
        drawScaledCenteredText(context, modStr,          midX, y + 29, if (mod >= 0) 0x2ECC71 else 0xE74C3C, 0.6f)
    }

    private fun drawBanner(context: DrawContext, x: Int, y: Int, totalWidth: Int) {
        val tileDrawH = 17
        GuiAtlas.HEADER_LEFT.draw(context, x, y, bannerEndW, bannerEndH)
        var tx = x + bannerEndW
        var remaining = totalWidth - bannerEndW * 2
        while (remaining > 0) {
            val drawW = if (bannerTileW < remaining) bannerTileW else remaining
            GuiAtlas.HEADER_TILE.draw(context, tx, y, drawW, tileDrawH)
            tx += drawW
            remaining -= drawW
        }
        GuiAtlas.HEADER_RIGHT.draw(context, x + totalWidth - bannerEndW, y, bannerEndW, bannerEndH)
    }

    private fun drawScaledCenteredText(context: DrawContext, text: String, cx: Int, y: Int, color: Int, scale: Float) {
        val matrices = context.matrices
        matrices.push()
        matrices.translate(cx.toFloat(), y.toFloat(), 0f)
        matrices.scale(scale, scale, 1f)
        val w = textRenderer.getWidth(text)
        context.drawTextWithShadow(textRenderer, text, -(w / 2), 0, color)
        matrices.pop()
    }

    private fun drawSmallCenteredText(context: DrawContext, text: String, cx: Int, y: Int, color: Int) {
        drawScaledCenteredText(context, text, cx, y, color, 0.85f)
    }

    override fun shouldPause() = false
}
