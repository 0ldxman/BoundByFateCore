package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.client.state.ClientPlayerData
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.registry.BbfSkills
import omc.boundbyfate.registry.BbfStats

class CharacterScreenAtlas : Screen(Text.translatable("screen.boundbyfate.character")) {

    // Щит: оригинал 109x172, ÷4 (чётная ширина для точного центрирования)
    private val shieldW = 28
    private val shieldH = 43

    // Баннер конец: оригинал 66x97
    private val bannerEndW = 33
    private val bannerEndH = 32

    // Тайл баннера
    private val bannerTileW = 18

    // Диагональный отступ щитов
    private val shieldDiagStep = 12

    // Иконка навыка: 6x6
    private val skillIconSize = 6

    // Навыки по характеристикам — полные названия
    private val strSkills = listOf("Атлетика")
    private val conSkills = emptyList<String>()
    private val dexSkills = listOf("Акробатика", "Ловкость рук", "Скрытность")
    private val intSkills = listOf("Магия", "История", "Анализ", "Природа", "Религия")
    private val wisSkills = listOf("Уход за животными", "Проницательность", "Медицина", "Внимательность", "Выживание")
    private val chaSkills = listOf("Обман", "Запугивание", "Выступление", "Убеждение")

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

    // Спасброски по характеристикам
    private val leftSaveDefs = listOf(BbfSkills.SAVE_STRENGTH, BbfSkills.SAVE_CONSTITUTION, BbfSkills.SAVE_DEXTERITY)
    private val rightSaveDefs = listOf(BbfSkills.SAVE_INTELLIGENCE, BbfSkills.SAVE_WISDOM, BbfSkills.SAVE_CHARISMA)

    private var cx = 0
    private var cy = 0

    override fun init() {
        cx = width / 2
        cy = height / 2
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)

        val player = MinecraftClient.getInstance().player ?: return
        val statsData = ClientPlayerData.statsData
        val skillData = ClientPlayerData.skillData
        val classData = ClientPlayerData.classData
        val raceData = ClientPlayerData.raceData

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
            drawStatShield(context, sx, sy, stat, statsData, skillData, leftSaveDefs[i])
            drawSkillList(context, sx - skillIconSize - 2, sy + 5, leftSkillsByIndex[i], leftSkillDefsByIndex[i], statsData, skillData, isLeft = true)
        }

        rightStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = rightBaseX + diagOffset
            val sy = shieldsTopY + i * shieldStep
            drawStatShield(context, sx, sy, stat, statsData, skillData, rightSaveDefs[i])
            drawSkillList(context, sx + shieldW + 2, sy + 5, rightSkillsByIndex[i], rightSkillDefsByIndex[i], statsData, skillData, isLeft = false)
        }

        // ═══ БАННЕРЫ ═══
        val nameBannerW = 130
        val nameBannerX = cx - nameBannerW / 2
        val nameBannerY = 8
        drawBanner(context, nameBannerX, nameBannerY, nameBannerW)
        drawSmallCenteredText(context, player.name.string, cx, nameBannerY + 6, 0xFFD700)

        val sideBannerW = 120
        val sideBannerY = nameBannerY + 14
        val classBannerX = cx - sideBannerW - 70
        drawBanner(context, classBannerX, sideBannerY, sideBannerW)
        val classStr = classData?.classId?.path?.let { it[0].uppercaseChar() + it.substring(1) } ?: "Commoner"
        val classLevel = classData?.classLevel ?: 1
        drawSmallCenteredText(context, "$classStr $classLevel", classBannerX + sideBannerW / 2, sideBannerY + 6, 0xD4AF37)

        val raceBannerX = cx + 70
        drawBanner(context, raceBannerX, sideBannerY, sideBannerW)
        val raceStr = raceData?.raceId?.path?.let { it[0].uppercaseChar() + it.substring(1) } ?: "Human"
        drawSmallCenteredText(context, raceStr, raceBannerX + sideBannerW / 2, sideBannerY + 6, 0xD4AF37)

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawSkillList(
        context: DrawContext,
        anchorX: Int, anchorY: Int,
        names: List<String>,
        defs: List<omc.boundbyfate.api.skill.SkillDefinition>,
        statsData: EntityStatData?,
        skillData: EntitySkillData?,
        isLeft: Boolean
    ) {
        val rowH = 8  // расстояние между навыками
        val gap = 2   // зазор между иконкой и текстом
        val textScale = 0.48f

        names.forEachIndexed { i, name ->
            val y = anchorY + i * rowH
            val def = defs.getOrNull(i)
            val bonus = if (def != null && statsData != null) {
                val statMod = statsData.getStatValue(def.linkedStat).dndModifier
                val profLevel = skillData?.getProficiency(def.id)?.multiplier ?: 0
                statMod + 2 * profLevel
            } else 0
            val label = "$name ${if (bonus >= 0) "+$bonus" else "$bonus"}"

            if (isLeft) {
                // Иконка справа от anchorX, текст прилегает правым краем к иконке
                GuiAtlas.ICON_SKILL_BG.draw(context, anchorX, y, skillIconSize, skillIconSize)
                if (def != null && (skillData?.getProficiency(def.id)?.multiplier ?: 0) > 0) {
                    GuiAtlas.ICON_PROFICIENCY.draw(context, anchorX + 1, y + 1, 4, 4)
                }
                val textX = anchorX - gap
                val matrices = context.matrices
                matrices.push()
                matrices.translate(textX.toFloat(), (y + 1).toFloat(), 0f)
                matrices.scale(textScale, textScale, 1f)
                val w = textRenderer.getWidth(label)
                context.drawTextWithShadow(textRenderer, label, -w, 0, 0xCCCCCC)
                matrices.pop()
            } else {
                // Иконка слева от anchorX, текст прилегает левым краем к иконке
                GuiAtlas.ICON_SKILL_BG.draw(context, anchorX, y, skillIconSize, skillIconSize)
                if (def != null && (skillData?.getProficiency(def.id)?.multiplier ?: 0) > 0) {
                    GuiAtlas.ICON_PROFICIENCY.draw(context, anchorX + 1, y + 1, 4, 4)
                }
                val textX = anchorX + skillIconSize + gap
                val matrices = context.matrices
                matrices.push()
                matrices.translate(textX.toFloat(), (y + 1).toFloat(), 0f)
                matrices.scale(textScale, textScale, 1f)
                context.drawTextWithShadow(textRenderer, label, 0, 0, 0xCCCCCC)
                matrices.pop()
            }
        }
    }

    private fun drawStatShield(
        context: DrawContext,
        x: Int, y: Int,
        stat: omc.boundbyfate.api.stat.StatDefinition,
        statsData: EntityStatData?,
        skillData: EntitySkillData?,
        saveDef: omc.boundbyfate.api.skill.SkillDefinition
    ) {
        GuiAtlas.ICON_STAT_BG.draw(context, x, y, shieldW, shieldH)

        // Иконка владения спасброском — сверху по центру щита
        val hasSaveProf = (skillData?.getProficiency(saveDef.id)?.multiplier ?: 0) > 0
        if (hasSaveProf) {
            val profSize = 4
            // Смещаем ниже (+1) и на 1px ближе к центру (центр щита)
            GuiAtlas.ICON_PROFICIENCY.draw(context, x + shieldW / 2 - profSize / 2+1, y + 3, profSize, profSize)
        }

        val value = statsData?.getStatValue(stat.id)?.total ?: 10
        val mod = statsData?.getStatValue(stat.id)?.dndModifier ?: 0
        val modStr = if (mod >= 0) "+$mod" else "$mod"
        val midX = x + shieldW / 2

        drawScaledCenteredText(context, stat.shortName, midX, y + 9,  0xD4AF37, 0.6f)
        drawScaledCenteredText(context, "$value",        midX, y + 17, 0xFFFFFF, 1.0f)
        drawScaledCenteredText(context, modStr,          midX, y + 29, if (mod >= 0) 0x2ECC71 else 0xE74C3C, 0.6f)
    }

    private fun drawBanner(context: DrawContext, x: Int, y: Int, totalWidth: Int) {
        val tileDrawH = bannerTileW
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
