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
    private var openTime = 0f

    // Tooltip
    private var pendingTooltip: Text? = null
    private var lastTooltipKey: String = ""
    private var tooltipAnimW = 0f
    private var tooltipAnimH = 0f
    private var tooltipWidthTimer = 0f  // задержка перед раскрытием высоты

    // Анимация щитов
    private data class ShieldAnim(
        var tiltX: Float = 0f,
        var tiltY: Float = 0f,
        var scale: Float = 1f,
        var slideX: Float = 0f,
        var slideY: Float = 0f,
        var introProgress: Float = 0f,
        var skillSlide: Float = 0f,
        var textAlpha: Float = 0f,
        var profScale: Float = 0f
    )
    private val shieldAnims = Array(6) { ShieldAnim() }

    // Анимация баннеров: 0=имя, 1=класс, 2=раса
    private val bannerScales = FloatArray(3) { 1f }

    // Направления въезда для каждого щита (нормализованные векторы * дистанция)
    // 0=STR(лево), 1=CON(низ-лево), 2=DEX(низ), 3=INT(право), 4=WIS(низ-право), 5=CHA(низ)
    private val slideStartX = floatArrayOf(-200f, -150f, 0f, 200f, 150f, 0f)
    private val slideStartY = floatArrayOf(0f, 150f, 200f, 0f, 150f, 200f)

    override fun init() {
        cx = width / 2
        cy = height / 2
        openTime = 0f
        shieldAnims.forEach {
            it.introProgress = 0f; it.skillSlide = 0f
            it.textAlpha = 0f; it.profScale = 0f
            it.slideX = 0f; it.slideY = 0f
        }
        tooltipAnimW = 0f; tooltipAnimH = 0f; tooltipWidthTimer = 0f; lastTooltipKey = ""
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        pendingTooltip = null

        openTime = (openTime + delta * 0.045f).coerceAtMost(1f)

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
            updateShieldAnim(i, sx, sy, mouseX, mouseY)
            val anim = shieldAnims[i]
            val skillShift = ((anim.scale - 1f) * 35f).toInt()

            // Навыки рисуются ДО щита (под ним по Z)
            drawSkillList(context, sx - skillIconSize - 2 - skillShift, sy + 5,
                leftSkillDefsByIndex[i], statsData, skillData, isLeft = true, mouseX, mouseY,
                anim.skillSlide, anim.textAlpha, anim.profScale)

            drawStatShield(context, sx, sy, stat, statsData, skillData, leftSaveDefs[i], mouseX, mouseY, anim)
        }

        rightStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = rightBaseX + diagOffset
            val sy = shieldsTopY + i * shieldStep
            val idx = i + 3
            updateShieldAnim(idx, sx, sy, mouseX, mouseY)
            val anim = shieldAnims[idx]
            val skillShift = ((anim.scale - 1f) * 35f).toInt()

            drawSkillList(context, sx + shieldW + 2 + skillShift, sy + 5,
                rightSkillDefsByIndex[i], statsData, skillData, isLeft = false, mouseX, mouseY,
                anim.skillSlide, anim.textAlpha, anim.profScale)

            drawStatShield(context, sx, sy, stat, statsData, skillData, rightSaveDefs[i], mouseX, mouseY, anim)
        }

        // ═══ БАННЕРЫ ═══
        val nameBannerW = 130
        val nameBannerX = cx - nameBannerW / 2
        val nameBannerBaseY = 8

        val nameProgress = easeOut(openTime.coerceIn(0f, 1f))
        val nameBannerOffY = ((1f - nameProgress) * (bannerEndH + 20)).toInt()
        val nameBannerY = nameBannerBaseY - nameBannerOffY

        val sideBannerW = 120
        val sideBannerBaseY = nameBannerBaseY + 14
        val sideDelay = 0.18f
        val sideProgress = easeOut(((openTime - sideDelay) / (1f - sideDelay)).coerceIn(0f, 1f))
        val sideBannerOffY = ((1f - sideProgress) * (bannerEndH + 20)).toInt()
        val sideBannerY = sideBannerBaseY - sideBannerOffY
        val classBannerX = cx - sideBannerW - 70
        val raceBannerX = cx + 70

        // Hit-test баннеров для hover
        val nameHovered = mouseX in nameBannerX..(nameBannerX + nameBannerW) && mouseY in nameBannerY..(nameBannerY + bannerEndH)
        val classHovered = mouseX in classBannerX..(classBannerX + sideBannerW) && mouseY in sideBannerY..(sideBannerY + bannerEndH)
        val raceHovered = mouseX in raceBannerX..(raceBannerX + sideBannerW) && mouseY in sideBannerY..(sideBannerY + bannerEndH)

        // Обновляем scale баннеров
        bannerScales[0] = lerp(bannerScales[0], if (nameHovered) 1.08f else 1f, 0.15f)
        bannerScales[1] = lerp(bannerScales[1], if (classHovered) 1.08f else 1f, 0.15f)
        bannerScales[2] = lerp(bannerScales[2], if (raceHovered) 1.08f else 1f, 0.15f)

        // Расталкивание: боковые баннеры отодвигаются когда центральный увеличивается
        // и наоборот — центральный не двигается, боковые расходятся
        val namePush = (bannerScales[0] - 1f) * 30f  // пикселей расталкивания
        val classPushX = -(bannerScales[1] - 1f) * 20f - namePush * 0.5f
        val racePushX = (bannerScales[2] - 1f) * 20f + namePush * 0.5f

        if (nameProgress > 0.01f) {
            val m = context.matrices
            m.push()
            m.translate((nameBannerX + nameBannerW / 2).toFloat(), (nameBannerY + bannerEndH / 2).toFloat(), 0f)
            m.scale(bannerScales[0], bannerScales[0], 1f)
            m.translate(-(nameBannerX + nameBannerW / 2).toFloat(), -(nameBannerY + bannerEndH / 2).toFloat(), 0f)
            drawBanner(context, nameBannerX, nameBannerY, nameBannerW)
            m.pop()
            if (nameProgress > 0.4f) {
                val playerName = player.name.string.replace('_', ' ')
                drawSmallCenteredText(context, playerName, cx, nameBannerY + 2, 0xFFD700)
                val levelText = Text.translatable("bbf.level", level).string
                drawScaledCenteredText(context, levelText, cx, nameBannerY + 11, 0xAAAAAA, 0.55f)
            }
        }

        if (sideProgress > 0.01f) {
            val classOffX = classPushX.toInt()
            val raceOffX = racePushX.toInt()

            val m = context.matrices
            m.push()
            val classCenterX = (classBannerX + classOffX + sideBannerW / 2).toFloat()
            m.translate(classCenterX, (sideBannerY + bannerEndH / 2).toFloat(), 0f)
            m.scale(bannerScales[1], bannerScales[1], 1f)
            m.translate(-classCenterX, -(sideBannerY + bannerEndH / 2).toFloat(), 0f)
            drawBanner(context, classBannerX + classOffX, sideBannerY, sideBannerW)
            m.pop()

            if (sideProgress > 0.4f) {
                val classKey = classData?.classId?.let { "bbf.class.${it.namespace}.${it.path}" }
                val classStr = if (classKey != null) Text.translatable(classKey).string else "Commoner"
                val classCx = classBannerX + classOffX + sideBannerW / 2
                drawScaledCenteredText(context, classStr, classCx, sideBannerY + 2, 0xD4AF37, 0.85f)
                val subclassKey = classData?.subclassId?.let { "bbf.subclass.${it.namespace}.${it.path}" }
                if (subclassKey != null) {
                    drawScaledCenteredText(context, Text.translatable(subclassKey).string, classCx, sideBannerY + 10, 0xAAAAAA, 0.55f)
                }
            }

            m.push()
            val raceCenterX = (raceBannerX + raceOffX + sideBannerW / 2).toFloat()
            m.translate(raceCenterX, (sideBannerY + bannerEndH / 2).toFloat(), 0f)
            m.scale(bannerScales[2], bannerScales[2], 1f)
            m.translate(-raceCenterX, -(sideBannerY + bannerEndH / 2).toFloat(), 0f)
            drawBanner(context, raceBannerX + raceOffX, sideBannerY, sideBannerW)
            m.pop()

            if (sideProgress > 0.4f) {
                val raceKey = raceData?.raceId?.let { "bbf.race.${it.namespace}.${it.path}" }
                val raceStr = if (raceKey != null) Text.translatable(raceKey).string else "Human"
                val raceCx = raceBannerX + raceOffX + sideBannerW / 2
                drawScaledCenteredText(context, raceStr, raceCx, sideBannerY + 2, 0xD4AF37, 0.85f)
                val gender = ClientPlayerData.gender
                if (gender != null) {
                    val genderStr = Text.translatable("bbf.gender.$gender").string
                    drawScaledCenteredText(context, genderStr, raceCx, sideBannerY + 10, 0xAAAAAA, 0.55f)
                }
            }
        }

        super.render(context, mouseX, mouseY, delta)

        // Тултип с анимацией
        pendingTooltip?.let { drawSmallTooltip(context, it, mouseX, mouseY) }
        updateTooltipAnim()
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun updateShieldAnim(idx: Int, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val anim = shieldAnims[idx]
        val hovered = mouseX in x..(x + shieldW) && mouseY in y..(y + shieldH)
        val lerpSpeed = 0.15f

        // Въезд с задержкой по индексу
        val introDelay = idx * 0.07f
        val rawProgress = ((openTime - introDelay) / (1f - introDelay)).coerceIn(0f, 1f)
        val introTarget = easeOut(rawProgress)
        anim.introProgress = lerp(anim.introProgress, introTarget, 0.18f)

        // Смещение въезда: lerp от startOffset до 0
        anim.slideX = lerp(anim.slideX, slideStartX[idx] * (1f - anim.introProgress), 0.25f)
        anim.slideY = lerp(anim.slideY, slideStartY[idx] * (1f - anim.introProgress), 0.25f)

        // Навыки выезжают только когда щит полностью на месте
        if (anim.introProgress > 0.97f) {
            anim.skillSlide = lerp(anim.skillSlide, 1f, 0.1f)
        }

        // Текст fade-in
        if (anim.skillSlide > 0.7f) {
            anim.textAlpha = lerp(anim.textAlpha, 1f, 0.08f)
        }

        // Иконки владения появляются после всего остального
        val allDone = shieldAnims.all { it.skillSlide > 0.9f }
        if (allDone) {
            anim.profScale = lerp(anim.profScale, 1f, 0.1f)
        }

        // Hover
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

    private fun updateTooltipAnim() {
        val currentKey = pendingTooltip?.string ?: ""
        if (currentKey != lastTooltipKey) {
            // Новый тултип — сбрасываем анимацию
            if (currentKey.isNotEmpty()) {
                tooltipAnimW = 0f
                tooltipAnimH = 0f
            }
            lastTooltipKey = currentKey
        }
        if (currentKey.isNotEmpty()) {
            tooltipAnimW = lerp(tooltipAnimW, 1f, 0.2f)
            // Задержка перед раскрытием высоты: ждём пока ширина почти готова
            if (tooltipAnimW > 0.85f) {
                tooltipWidthTimer = (tooltipWidthTimer + 0.04f).coerceAtMost(1f)
            }
            if (tooltipWidthTimer > 0.6f) {
                tooltipAnimH = lerp(tooltipAnimH, 1f, 0.15f)
            }
        } else {
            tooltipAnimW = 0f
            tooltipAnimH = 0f
            tooltipWidthTimer = 0f
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

        // Scale: только hover, без pop-in
        val totalScale = anim.scale
        matrices.translate(shieldCx + anim.slideX, shieldCy + anim.slideY, 0f)
        matrices.scale(totalScale, totalScale, 1f)
        val parallaxX = anim.tiltX * 1.5f
        val parallaxY = anim.tiltY * 1.5f
        matrices.translate(-shieldCx + parallaxX, -shieldCy + parallaxY, 0f)

        GuiAtlas.ICON_STAT_BG.draw(context, x, y, shieldW, shieldH)

        // Иконка владения спасброском — с отдельной анимацией scale
        val hasSaveProf = (skillData?.getProficiency(saveDef.id)?.multiplier ?: 0) > 0
        if (hasSaveProf && anim.profScale > 0.01f) {
            val profSize = 4
            val profCx = (x + shieldW / 2).toFloat()
            val profCy = (y + 3 + profSize / 2).toFloat()
            matrices.push()
            matrices.translate(profCx, profCy, 0f)
            matrices.scale(anim.profScale, anim.profScale, 1f)
            matrices.translate(-profCx, -profCy, 0f)
            GuiAtlas.ICON_PROFICIENCY.draw(context, x + shieldW / 2 - profSize / 2, y + 3, profSize, profSize)
            matrices.pop()
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
        textAlpha: Float,
        profScale: Float
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

            // Иконка выезжает из-под щита
            val slideOffset = if (isLeft) {
                ((1f - slideProgress) * 18f).toInt()
            } else {
                -((1f - slideProgress) * 18f).toInt()
            }
            val iconX = anchorX + slideOffset

            val alpha = (textAlpha * 255).toInt().coerceIn(0, 255)
            val textColor = (alpha shl 24) or 0xCCCCCC

            // Рисуем иконку навыка (под щитом — Z уже ниже т.к. рисуется раньше)
            GuiAtlas.ICON_SKILL_BG.draw(context, iconX, y, skillIconSize, skillIconSize)

            // Иконка владения навыком — с анимацией scale
            if ((skillData?.getProficiency(def.id)?.multiplier ?: 0) > 0 && profScale > 0.01f) {
                val profCx = (iconX + skillIconSize / 2).toFloat()
                val profCy = (y + skillIconSize / 2).toFloat()
                val matrices = context.matrices
                matrices.push()
                matrices.translate(profCx, profCy, 0f)
                matrices.scale(profScale, profScale, 1f)
                matrices.translate(-profCx, -profCy, 0f)
                GuiAtlas.ICON_PROFICIENCY.draw(context, iconX + 1, y + 1, 4, 4)
                matrices.pop()
            }

            if (textAlpha > 0.05f) {
                if (isLeft) {
                    val textX = iconX - gap
                    val matrices = context.matrices
                    matrices.push()
                    matrices.translate(textX.toFloat(), (y + 1).toFloat(), 0f)
                    matrices.scale(textScale, textScale, 1f)
                    val w = textRenderer.getWidth(label)
                    context.drawTextWithShadow(textRenderer, label, -w, 0, textColor)
                    matrices.pop()
                } else {
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
        if (tooltipAnimW < 0.02f) return

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
        // Анимированные размеры
        val animW = ((maxW + pad * 2) * tooltipAnimW).toInt()
        val animH = ((totalH + pad * 2) * tooltipAnimH).toInt()
        val x1 = x0 + animW
        val y1 = y0 + animH

        if (animW > 2) {
            context.fill(x0, y0, x1, y1, bgColor)
            context.fill(x0, y0, x1, y0 + 1, brdColor)
            if (animH > 1) context.fill(x0, y1 - 1, x1, y1, brdColor)
            context.fill(x0, y0, x0 + 1, y1, brdColor)
            context.fill(x1 - 1, y0, x1, y1, brdColor)

            // Текст показываем только когда высота достаточная
            if (tooltipAnimH > 0.6f) {
                val textAlpha = ((tooltipAnimH - 0.6f) / 0.4f).coerceIn(0f, 1f)
                val alpha = (textAlpha * 255).toInt()
                lines.forEachIndexed { i, line ->
                    val lineY = y0 + pad + i * lineH
                    if (lineY + lineH < y1) {
                        val color = (alpha shl 24) or 0xFFFFFF
                        context.drawTextWithShadow(textRenderer, Text.literal(line), x0 + pad, lineY, color)
                    }
                }
            }
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
