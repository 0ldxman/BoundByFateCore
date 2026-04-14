package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.api.skill.SkillDefinition
import omc.boundbyfate.api.stat.StatDefinition
import omc.boundbyfate.client.state.ClientPlayerData
import omc.boundbyfate.component.EntitySkillData
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.registry.BbfSkills
import omc.boundbyfate.registry.BbfStats

class CharacterScreenAtlas : Screen(Text.translatable("screen.boundbyfate.character")) {

    private val shieldW = 28
    private val shieldH = 43
    private val bannerEndW = 33
    private val bannerEndH = 32
    private val bannerTileW = 18
    private val shieldDiagStep = 12
    private val skillIconSize = 6

    // Навыки по характеристикам
    private val strSkillDefs = listOf(BbfSkills.ATHLETICS)
    private val conSkillDefs = emptyList<SkillDefinition>()
    private val dexSkillDefs = listOf(BbfSkills.ACROBATICS, BbfSkills.SLEIGHT_OF_HAND, BbfSkills.STEALTH)
    private val intSkillDefs = listOf(BbfSkills.ARCANA, BbfSkills.HISTORY, BbfSkills.INVESTIGATION, BbfSkills.NATURE, BbfSkills.RELIGION)
    private val wisSkillDefs = listOf(BbfSkills.ANIMAL_HANDLING, BbfSkills.INSIGHT, BbfSkills.MEDICINE, BbfSkills.PERCEPTION, BbfSkills.SURVIVAL)
    private val chaSkillDefs = listOf(BbfSkills.DECEPTION, BbfSkills.INTIMIDATION, BbfSkills.PERFORMANCE, BbfSkills.PERSUASION)

    private val leftSkillDefsByIndex = listOf(strSkillDefs, conSkillDefs, dexSkillDefs)
    private val rightSkillDefsByIndex = listOf(intSkillDefs, wisSkillDefs, chaSkillDefs)

    private val leftSaveDefs = listOf(BbfSkills.SAVE_STRENGTH, BbfSkills.SAVE_CONSTITUTION, BbfSkills.SAVE_DEXTERITY)
    private val rightSaveDefs = listOf(BbfSkills.SAVE_INTELLIGENCE, BbfSkills.SAVE_WISDOM, BbfSkills.SAVE_CHARISMA)

    private var cx = 0
    private var cy = 0

    // Tooltip state — заполняется во время render, рисуется в конце
    private var pendingTooltip: Text? = null

    override fun init() {
        cx = width / 2
        cy = height / 2
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        pendingTooltip = null

        val player = MinecraftClient.getInstance().player ?: return
        val statsData = ClientPlayerData.statsData
        val skillData = ClientPlayerData.skillData
        val classData = ClientPlayerData.classData
        val raceData = ClientPlayerData.raceData
        val level = ClientPlayerData.level

        // ═══ МОДЕЛЬ ИГРОКА ═══
        InventoryScreen.drawEntity(context, cx, cy + 85, 70, cx - mouseX.toFloat(), cy - mouseY.toFloat(), player)

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
            drawStatShield(context, sx, sy, stat, statsData, skillData, leftSaveDefs[i], mouseX, mouseY)
            drawSkillList(context, sx - skillIconSize - 2, sy + 5, leftSkillDefsByIndex[i], statsData, skillData, isLeft = true, mouseX, mouseY)
        }

        rightStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = rightBaseX + diagOffset
            val sy = shieldsTopY + i * shieldStep
            drawStatShield(context, sx, sy, stat, statsData, skillData, rightSaveDefs[i], mouseX, mouseY)
            drawSkillList(context, sx + shieldW + 2, sy + 5, rightSkillDefsByIndex[i], statsData, skillData, isLeft = false, mouseX, mouseY)
        }

        // ═══ БАННЕРЫ ═══
        val nameBannerW = 130
        val nameBannerX = cx - nameBannerW / 2
        val nameBannerY = 8
        drawBanner(context, nameBannerX, nameBannerY, nameBannerW)
        // Имя: заменяем _ на пробел
        val playerName = player.name.string.replace('_', ' ')
        drawSmallCenteredText(context, playerName, cx, nameBannerY + 2, 0xFFD700)
        // Уровень под именем
        val levelText = Text.translatable("bbf.level", level).string
        drawScaledCenteredText(context, levelText, cx, nameBannerY + 11, 0xAAAAAA, 0.55f)

        val sideBannerW = 120
        val sideBannerY = nameBannerY + 14
        val classBannerX = cx - sideBannerW - 70
        drawBanner(context, classBannerX, sideBannerY, sideBannerW)
        val classKey = classData?.classId?.let { "bbf.class.${it.namespace}.${it.path}" }
        val classStr = if (classKey != null) Text.translatable(classKey).string else "Commoner"
        drawScaledCenteredText(context, classStr, classBannerX + sideBannerW / 2, sideBannerY + 4, 0xD4AF37, 0.55f)
        // Подкласс под классом
        val subclassKey = classData?.subclassId?.let { "bbf.subclass.${it.namespace}.${it.path}" }
        if (subclassKey != null) {
            val subclassStr = Text.translatable(subclassKey).string
            drawScaledCenteredText(context, subclassStr, classBannerX + sideBannerW / 2, sideBannerY + 12, 0xAAAAAA, 0.55f)
        }

        val raceBannerX = cx + 70
        drawBanner(context, raceBannerX, sideBannerY, sideBannerW)
        val raceKey = raceData?.raceId?.let { "bbf.race.${it.namespace}.${it.path}" }
        val raceStr = if (raceKey != null) Text.translatable(raceKey).string else "Human"
        drawScaledCenteredText(context, raceStr, raceBannerX + sideBannerW / 2, sideBannerY + 4, 0xD4AF37, 0.55f)
        // Пол под расой
        val gender = ClientPlayerData.gender
        if (gender != null) {
            val genderKey = "bbf.gender.$gender"
            val genderStr = Text.translatable(genderKey).string
            drawScaledCenteredText(context, genderStr, raceBannerX + sideBannerW / 2, sideBannerY + 12, 0xAAAAAA, 0.55f)
        }

        super.render(context, mouseX, mouseY, delta)

        // Рисуем тултип поверх всего — кастомный маленький
        pendingTooltip?.let { drawSmallTooltip(context, it, mouseX, mouseY) }
    }

    /** Рисует тултип уменьшенного размера с поддержкой \n и §-форматирования */
    private fun drawSmallTooltip(context: DrawContext, text: Text, mouseX: Int, mouseY: Int) {
        val scale = 0.75f
        val lines = text.string.split("\n")
        val lineH = textRenderer.fontHeight + 1
        val pad = 4

        val maxW = lines.maxOf { textRenderer.getWidth(it) }
        val totalH = lines.size * lineH

        val tx = (mouseX + 6) / scale
        val ty = (mouseY - 4) / scale

        val bgColor  = 0xFF2b2321.toInt()
        val brdColor = 0xFFb08a66.toInt()

        val matrices = context.matrices
        matrices.push()
        // Поднимаем на высокий Z чтобы рисоваться поверх всего
        matrices.translate(0f, 0f, 500f)
        matrices.scale(scale, scale, 1f)

        val x0 = tx.toInt()
        val y0 = ty.toInt()
        val x1 = x0 + maxW + pad * 2
        val y1 = y0 + totalH + pad * 2

        // Фон
        context.fill(x0, y0, x1, y1, bgColor)
        // Рамка (1px)
        context.fill(x0,     y0,     x1,     y0 + 1, brdColor)
        context.fill(x0,     y1 - 1, x1,     y1,     brdColor)
        context.fill(x0,     y0,     x0 + 1, y1,     brdColor)
        context.fill(x1 - 1, y0,     x1,     y1,     brdColor)

        // Текст
        lines.forEachIndexed { i, line ->
            context.drawTextWithShadow(textRenderer, Text.literal(line), x0 + pad, y0 + pad + i * lineH, 0xFFFFFF)
        }

        matrices.pop()
    }

    private fun drawStatShield(
        context: DrawContext,
        x: Int, y: Int,
        stat: StatDefinition,
        statsData: EntityStatData?,
        skillData: EntitySkillData?,
        saveDef: SkillDefinition,
        mouseX: Int, mouseY: Int
    ) {
        GuiAtlas.ICON_STAT_BG.draw(context, x, y, shieldW, shieldH)

        val hasSaveProf = (skillData?.getProficiency(saveDef.id)?.multiplier ?: 0) > 0
        if (hasSaveProf) {
            val profSize = 4
            GuiAtlas.ICON_PROFICIENCY.draw(context, x + shieldW / 2 - profSize / 2, y + 3, profSize, profSize)
        }

        val value = statsData?.getStatValue(stat.id)?.total ?: 10
        val mod = statsData?.getStatValue(stat.id)?.dndModifier ?: 0
        val modStr = if (mod >= 0) "+$mod" else "$mod"
        val midX = x + shieldW / 2

        // Короткое название из локализации
        val shortKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.short"
        val shortName = Text.translatable(shortKey).string

        drawScaledCenteredText(context, shortName, midX, y + 9,  0xD4AF37, 0.6f)
        drawScaledCenteredText(context, "$value",   midX, y + 17, 0xFFFFFF, 1.0f)
        drawScaledCenteredText(context, modStr,     midX, y + 29, if (mod >= 0) 0x2ECC71 else 0xE74C3C, 0.6f)

        // Тултип при наведении на щит
        if (mouseX in x..(x + shieldW) && mouseY in y..(y + shieldH)) {
            val tooltipKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.tooltip"
            pendingTooltip = Text.translatable(tooltipKey)
        }
    }

    private fun drawSkillList(
        context: DrawContext,
        anchorX: Int, anchorY: Int,
        defs: List<SkillDefinition>,
        statsData: EntityStatData?,
        skillData: EntitySkillData?,
        isLeft: Boolean,
        mouseX: Int, mouseY: Int
    ) {
        val rowH = 8
        val gap = 2
        val textScale = 0.48f

        defs.forEachIndexed { i, def ->
            val y = anchorY + i * rowH
            val bonus = if (statsData != null) {
                val statMod = statsData.getStatValue(def.linkedStat).dndModifier
                val profLevel = skillData?.getProficiency(def.id)?.multiplier ?: 0
                statMod + 2 * profLevel
            } else 0
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"

            // Название навыка из локализации
            val nameKey = "bbf.skill.${def.id.namespace}.${def.id.path}.name"
            val name = Text.translatable(nameKey).string.ifBlank { def.displayName }
            val label = "$name $bonusStr"

            if (isLeft) {
                GuiAtlas.ICON_SKILL_BG.draw(context, anchorX, y, skillIconSize, skillIconSize)
                if ((skillData?.getProficiency(def.id)?.multiplier ?: 0) > 0) {
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
                GuiAtlas.ICON_SKILL_BG.draw(context, anchorX, y, skillIconSize, skillIconSize)
                if ((skillData?.getProficiency(def.id)?.multiplier ?: 0) > 0) {
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

            // Тултип при наведении на иконку навыка
            if (mouseX in anchorX..(anchorX + skillIconSize) && mouseY in y..(y + skillIconSize)) {
                val tooltipKey = "bbf.skill.${def.id.namespace}.${def.id.path}.tooltip"
                pendingTooltip = Text.translatable(tooltipKey)
            }
        }
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
