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

    // ═══ АНИМАЦИИ ═══

    // Общий таймер открытия экрана (0→1)
    private var openTime = 0f

    // Tooltip
    private var pendingTooltip: Text? = null

    // Анимация щитов: hover tilt/scale + intro pop-in + skill slide
    private data class ShieldAnim(
        var tiltX: Float = 0f,
        var tiltY: Float = 0f,
        var scale: Float = 1f,
        var introScale: Float = 0f,   // 0→1 при открытии экрана
        var skillSlide: Float = 0f,   // 0→1 после introScale
        var textAlpha: Float = 0f     // 0→1 после skillSlide
    )
    private val shieldAnims = Array(6) { ShieldAnim() }

    private data class ShieldBounds(var x: Int = 0, var y: Int = 0)
    private val shieldBounds = Array(6) { ShieldBounds() }

    override fun init() {
        cx = width / 2
        cy = height / 2
        // Сбрасываем анимации при открытии
        openTime = 0f
        shieldAnims.forEach { it.introScale = 0f; it.skillSlide = 0f; it.textAlpha = 0f }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        pendingTooltip = null

        // Обновляем таймер открытия
        openTime = (openTime + delta * 0.05f).coerceAtMost(1f)

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
            shieldBounds[i].x = sx
            shieldBounds[i].y = sy
            updateShieldAnim(i, sx, sy, mouseX, mouseY)
            drawStatShield(context, sx, sy, stat, statsData, skillData, leftSaveDefs[i], mouseX, mouseY, shieldAnims[i])
            val skillShift = ((shieldAnims[i].scale - 1f) * 35f).toInt()
            drawSkillList(context, sx - skillIconSize - 2 - skillShift, sy + 5,
                leftSkillDefsByIndex[i], statsData, skillData, isLeft = true, mouseX, mouseY,
                shieldAnims[i].skillSlide, shieldAnims[i].textAlpha)
        }

        rightStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = rightBaseX + diagOffset
            val sy = shieldsTopY + i * shieldStep
            val idx = i + 3
            shieldBounds[idx].x = sx
            shieldBounds[idx].y = sy
            updateShieldAnim(idx, sx, sy, mouseX, mouseY)
            drawStatShield(context, sx, sy, stat, statsData, skillData, rightSaveDefs[i], mouseX, mouseY, shieldAnims[idx])
            val skillShift = ((shieldAnims[idx].scale - 1f) * 35f).toInt()
            drawSkillList(context, sx + shieldW + 2 + skillShift, sy + 5,
                rightSkillDefsByIndex[i], statsData, skillData, isLeft = false, mouseX, mouseY,
                shieldAnims[idx].skillSlide, shieldAnims[idx].textAlpha)
        }

        // ═══ БАННЕРЫ ═══
        val nameBannerW = 130
        val nameBannerX = cx - nameBannerW / 2
        val nameBannerBaseY = 8

        // Центральный баннер въезжает первым
        val nameProgress = easeOut(openTime.coerceIn(0f, 1f))
        val nameBannerY = nameBannerBaseY - ((1f - nameProgress) * (bannerEndH + 10)).toInt()
        // Параллакс: центральный почти не двигается
        val nameParallaxX = (mouseX - cx) * 0.005f
        drawBannerAnimated(context, nameBannerX + nameParallaxX.toInt(), nameBannerY, nameBannerW, nameProgress)
        if (nameProgress > 0.3f) {
            val playerName = player.name.string.replace('_', ' ')
            drawSmallCenteredText(context, playerName, cx + nameParallaxX.toInt(), nameBannerY + 2, 0xFFD700)
            val levelText = Text.translatable("bbf.level", level).string
            drawScaledCenteredText(context, levelText, cx + nameParallaxX.toInt(), nameBannerY + 11, 0xAAAAAA, 0.55f)
        }

        val sideBannerW = 120
        val sideBannerBaseY = nameBannerBaseY + 14

        // Боковые баннеры въезжают с задержкой
        val sideDelay = 0.15f
        val sideProgress = easeOut(((openTime - sideDelay) / (1f - sideDelay)).coerceIn(0f, 1f))
        val sideBannerY = sideBannerBaseY - ((1f - sideProgress) * (bannerEndH + 10)).toInt()

        // Параллакс боковых: двигаются в противоположную сторону от курсора
        val parallaxStrength = 0.03f
        val classParallaxX = -(mouseX - cx) * parallaxStrength
        val raceParallaxX = -(mouseX - cx) * parallaxStrength

        val classBannerX = cx - sideBannerW - 70
        drawBannerAnimated(context, classBannerX + classParallaxX.toInt(), sideBannerY, sideBannerW, sideProgress)
        if (sideProgress > 0.3f) {
            val classKey = classData?.classId?.let { "bbf.class.${it.namespace}.${it.path}" }
            val classStr = if (classKey != null) Text.translatable(classKey).string else "Commoner"
            val classCx = classBannerX + sideBannerW / 2 + classParallaxX.toInt()
            drawScaledCenteredText(context, classStr, classCx, sideBannerY + 2, 0xD4AF37, 0.85f)
            val subclassKey = classData?.subclassId?.let { "bbf.subclass.${it.namespace}.${it.path}" }
            if (subclassKey != null) {
                drawScaledCenteredText(context, Text.translatable(subclassKey).string, classCx, sideBannerY + 10, 0xAAAAAA, 0.55f)
            }
        }

        val raceBannerX = cx + 70
        drawBannerAnimated(context, raceBannerX + raceParallaxX.toInt(), sideBannerY, sideBannerW, sideProgress)
        if (sideProgress > 0.3f) {
            val raceKey = raceData?.raceId?.let { "bbf.race.${it.namespace}.${it.path}" }
            val raceStr = if (raceKey != null) Text.translatable(raceKey).string else "Human"
            val raceCx = raceBannerX + sideBannerW / 2 + raceParallaxX.toInt()
            drawScaledCenteredText(context, raceStr, raceCx, sideBannerY + 2, 0xD4AF37, 0.85f)
            val gender = ClientPlayerData.gender
            if (gender != null) {
                val genderStr = Text.translatable("bbf.gender.$gender").string
                drawScaledCenteredText(context, genderStr, raceCx, sideBannerY + 10, 0xAAAAAA, 0.55f)
            }
        }

        super.render(context, mouseX, mouseY, delta)
        pendingTooltip?.let { drawSmallTooltip(context, it, mouseX, mouseY) }
    }

    /** Easing функция для плавного замедления */
    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    /** Обновляет все анимации щита */
    private fun updateShieldAnim(idx: Int, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val anim = shieldAnims[idx]
        val hovered = mouseX in x..(x + shieldW) && mouseY in y..(y + shieldH)
        val lerpSpeed = 0.15f

        // Intro pop-in: каждый щит стартует с задержкой по индексу
        val introDelay = idx * 0.08f
        val introTarget = easeOut(((openTime - introDelay) / (1f - introDelay)).coerceIn(0f, 1f))
        anim.introScale = lerp(anim.introScale, introTarget, 0.2f)

        // Skill slide: начинается когда щит почти появился
        if (anim.introScale > 0.85f) {
            anim.skillSlide = lerp(anim.skillSlide, 1f, 0.1f)
        }

        // Text alpha: начинается когда навыки почти выехали
        if (anim.skillSlide > 0.7f) {
            anim.textAlpha = lerp(anim.textAlpha, 1f, 0.08f)
        }

        // Hover tilt/scale
        if (hovered) {
            val normX = ((mouseX - (x + shieldW / 2)).toFloat() / (shieldW / 2)).coerceIn(-1f, 1f)
            val normY = ((mouseY - (y + shieldH / 2)).toFloat() / (shieldH / 2)).coerceIn(-1f, 1f)
            anim.tiltX = lerp(anim.tiltX, normX, lerpSpeed)
            anim.tiltY = lerp(anim.tiltY, normY, lerpSpeed)
            anim.scale = lerp(anim.scale, 1.25f, lerpSpeed)
        } else {
            anim.tiltX = lerp(anim.tiltX, 0f, lerpSpeed)
            anim.tiltY = lerp(anim.tiltY, 0f, lerpSpeed)
            anim.scale = lerp(anim.scale, 1f, lerpSpeed)
        }
    }

    private fun drawStatShield(
        context: DrawContext,
        x: Int, y: Int,
        stat: StatDefinition,
        statsData: EntityStatData?,
        skillData: EntitySkillData?,
        saveDef: SkillDefinition,
        mouseX: Int, mouseY: Int,
        anim: ShieldAnim
    ) {
        val matrices = context.matrices
        matrices.push()

        val shieldCx = (x + shieldW / 2).toFloat()
        val shieldCy = (y + shieldH / 2).toFloat()

        // Intro pop-in: scale от 0 до 1
        val totalScale = anim.scale * anim.introScale
        matrices.translate(shieldCx, shieldCy, 0f)
        matrices.scale(totalScale, totalScale, 1f)
        val parallaxX = anim.tiltX * 1.5f
        val parallaxY = anim.tiltY * 1.5f
        matrices.translate(-shieldCx + parallaxX, -shieldCy + parallaxY, 0f)

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

        val shortKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.short"
        val shortName = Text.translatable(shortKey).string

        drawScaledCenteredText(context, shortName, midX, y + 9,  0xD4AF37, 0.6f)
        drawScaledCenteredText(context, "$value",   midX, y + 17, 0xFFFFFF, 1.0f)
        drawScaledCenteredText(context, modStr,     midX, y + 29, if (mod >= 0) 0x2ECC71 else 0xE74C3C, 0.6f)

        matrices.pop()

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
        mouseX: Int, mouseY: Int,
        slideProgress: Float,
        textAlpha: Float
    ) {
        if (slideProgress <= 0.01f) return

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

            val nameKey = "bbf.skill.${def.id.namespace}.${def.id.path}.name"
            val name = Text.translatable(nameKey).string.let { if (it == nameKey) def.displayName else it }
            val label = "$name $bonusStr"

            // Иконка выезжает из щита: начальная позиция = у щита, конечная = anchorX
            val slideOffset = if (isLeft) {
                ((1f - slideProgress) * 20f).toInt()  // выезжает влево
            } else {
                -((1f - slideProgress) * 20f).toInt() // выезжает вправо
            }
            val iconX = anchorX + slideOffset

            // Цвет текста с alpha
            val alpha = (textAlpha * 255).toInt().coerceIn(0, 255)
            val textColor = (alpha shl 24) or 0xCCCCCC

            if (isLeft) {
                GuiAtlas.ICON_SKILL_BG.draw(context, iconX, y, skillIconSize, skillIconSize)
                if ((skillData?.getProficiency(def.id)?.multiplier ?: 0) > 0) {
                    GuiAtlas.ICON_PROFICIENCY.draw(context, iconX + 1, y + 1, 4, 4)
                }
                if (textAlpha > 0.05f) {
                    val textX = iconX - gap
                    val matrices = context.matrices
                    matrices.push()
                    matrices.translate(textX.toFloat(), (y + 1).toFloat(), 0f)
                    matrices.scale(textScale, textScale, 1f)
                    val w = textRenderer.getWidth(label)
                    context.drawTextWithShadow(textRenderer, label, -w, 0, textColor)
                    matrices.pop()
                }
            } else {
                GuiAtlas.ICON_SKILL_BG.draw(context, iconX, y, skillIconSize, skillIconSize)
                if ((skillData?.getProficiency(def.id)?.multiplier ?: 0) > 0) {
                    GuiAtlas.ICON_PROFICIENCY.draw(context, iconX + 1, y + 1, 4, 4)
                }
                if (textAlpha > 0.05f) {
                    val textX = iconX + skillIconSize + gap
                    val matrices = context.matrices
                    matrices.push()
                    matrices.translate(textX.toFloat(), (y + 1).toFloat(), 0f)
                    matrices.scale(textScale, textScale, 1f)
                    context.drawTextWithShadow(textRenderer, label, 0, 0, textColor)
                    matrices.pop()
                }
            }

            if (mouseX in anchorX..(anchorX + skillIconSize) && mouseY in y..(y + skillIconSize)) {
                val tooltipKey = "bbf.skill.${def.id.namespace}.${def.id.path}.tooltip"
                pendingTooltip = Text.translatable(tooltipKey)
            }
        }
    }

    /** Рисует баннер с анимацией появления (alpha через scissor не поддерживается, используем scale) */
    private fun drawBannerAnimated(context: DrawContext, x: Int, y: Int, totalWidth: Int, progress: Float) {
        if (progress <= 0.01f) return
        drawBanner(context, x, y, totalWidth)
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
        matrices.translate(0f, 0f, 500f)
        matrices.scale(scale, scale, 1f)
        val x0 = tx.toInt()
        val y0 = ty.toInt()
        val x1 = x0 + maxW + pad * 2
        val y1 = y0 + totalH + pad * 2
        context.fill(x0, y0, x1, y1, bgColor)
        context.fill(x0, y0, x1, y0 + 1, brdColor)
        context.fill(x0, y1 - 1, x1, y1, brdColor)
        context.fill(x0, y0, x0 + 1, y1, brdColor)
        context.fill(x1 - 1, y0, x1, y1, brdColor)
        lines.forEachIndexed { i, line ->
            context.drawTextWithShadow(textRenderer, Text.literal(line), x0 + pad, y0 + pad + i * lineH, 0xFFFFFF)
        }
        matrices.pop()
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

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun shouldPause() = false
}
