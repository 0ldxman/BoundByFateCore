package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.api.identity.AlignmentCoordinates
import omc.boundbyfate.client.state.ClientPlayerData
import omc.boundbyfate.network.BbfPackets
import org.lwjgl.glfw.GLFW
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Player personality screen.
 *
 * Анимации при открытии (последовательные):
 *   0.00–0.25  Мировоззрение вылетает сверху вниз
 *   0.15–0.40  Заголовки УБЕЖДЕНИЯ (слева) и СЛАБОСТИ (справа) въезжают
 *   0.38–0.55  Divider-ы рисуются от центра к краям
 *   0.50+      Строки убеждений/слабостей выезжают по одной (с задержкой по индексу)
 *   строки+0.1 Иконки появляются fade-in после текста
 *
 * Мотивации: орбитальное движение вокруг персонажа, чередование Z (за/перед).
 */
class PersonalityScreen(private val parent: Screen) :
    Screen(Text.translatable("screen.boundbyfate.personality")) {

    private var idealScroll = 0
    private var flawScroll = 0
    private var time = 0f
    private var openTime = 0f   // 0→1 за ~1.8 сек (delta * 0.018)
    
    // ── Оверлей мотиваций ─────────────────────────────────────────────────────
    private var motivationsOverlayOpen = false
    private var overlayAnimTime = 0f  // 0→1 анимация открытия
    private var overlayScroll = 0
    private val motivationTypewriterProgress = mutableMapOf<Int, Float>()  // прогресс печати для каждой мотивации
    private val motivationDividerProgress = mutableMapOf<Int, Float>()     // прогресс divider для каждой мотивации
    
    // ── Оверлей предложения мотивации ────────────────────────────────────────
    private var suggestMotivationOverlayOpen = false
    private var suggestOverlayAnimTime = 0f
    private var suggestMotivationText = ""
    private var cursorPosition = 0
    private var cursorBlinkTime = 0f

    // ── Оверлей мировоззрения ─────────────────────────────────────────────────
    private var alignmentOverlayOpen = false
    private var alignmentOverlayAnimTime = 0f
    private var alignmentHovered = false
    private var alignmentScale = 1f
    private var hoveredAlignmentCell: Int? = null
    private val alignmentCellScales = mutableMapOf<Int, Float>()
    private var alignmentCellTooltip: net.minecraft.text.Text? = null

    // ── Hover состояния ───────────────────────────────────────────────────────
    private var hoveredIdealIdx: Int? = null
    private var hoveredFlawIdx: Int? = null
    private var hoveredMotivationIdx: Int? = null
    private var modelHovered = false
    
    // Hover для заголовков
    private var idealsHeaderHovered = false
    private var flawsHeaderHovered = false
    private var motivationsHeaderHovered = false
    private var idealsHeaderScale = 1f
    private var flawsHeaderScale = 1f
    private var motivationsHeaderScale = 1f
    
    // Tooltip
    private var pendingTooltip: net.minecraft.text.Text? = null
    private var lastTooltipKey: String = ""
    private var tooltipAnimW = 0f
    private var tooltipAnimH = 0f
    private var tooltipWidthTimer = 0f
    
    // Lerp-анимации для hover
    private val idealHoverScales = mutableMapOf<Int, Float>()      // текст scale
    private val idealIconScales = mutableMapOf<Int, Float>()       // иконка scale
    private val idealTextOffsets = mutableMapOf<Int, Float>()      // смещение текста
    private val idealRowOffsets = mutableMapOf<Int, Float>()       // смещение строки вниз
    private val idealExpandProgress = mutableMapOf<Int, Float>()   // прогресс раскрытия текста (0→1)
    
    private val flawHoverScales = mutableMapOf<Int, Float>()
    private val flawIconScales = mutableMapOf<Int, Float>()
    private val flawTextOffsets = mutableMapOf<Int, Float>()
    private val flawRowOffsets = mutableMapOf<Int, Float>()
    private val flawExpandProgress = mutableMapOf<Int, Float>()    // прогресс раскрытия текста (0→1)
    
    private val motivationScales = mutableMapOf<Int, Float>()
    private val motivationAlphas = mutableMapOf<Int, Float>()
    private val motivationFrozenAngles = mutableMapOf<Int, Float>()  // угол в момент начала hover
    private val motivationResumePhases = mutableMapOf<Int, Float>()  // скорректированный phase после hover
    private var modelAlpha = 1f
    private var motivationsBaseAlpha = 1f
    
    // Ротация иконок: хранится текущий угол для плавного возврата к 0
    private val idealIconRotations = mutableMapOf<Int, Float>()
    private val flawIconRotations = mutableMapOf<Int, Float>()

    // ── Мотивации: орбитальные частицы ───────────────────────────────────────
    private data class MotivationParticle(
        val text: String,
        val orbitRadius: Float,   // радиус орбиты
        val orbitSpeed: Float,    // угловая скорость
        val orbitPhase: Float,    // начальная фаза
        val orbitTilt: Float,     // наклон эллипса (0=горизонталь, 1=вертикаль)
        val scale: Float,
        val baseAlpha: Float,
        val color: Int,
        val zLayer: Float         // -1..1: отрицательный = за персонажем
    )
    private val particles = mutableListOf<MotivationParticle>()
    private var lastMotivationSnapshot: List<String> = emptyList()  // id+isActive для отслеживания изменений

    override fun init() {
        openTime = 0f
        hoveredIdealIdx = null
        hoveredFlawIdx = null
        hoveredMotivationIdx = null
        modelHovered = false
        idealsHeaderHovered = false
        flawsHeaderHovered = false
        motivationsHeaderHovered = false
        idealsHeaderScale = 1f
        flawsHeaderScale = 1f
        motivationsHeaderScale = 1f
        idealHoverScales.clear()
        idealIconScales.clear()
        idealTextOffsets.clear()
        idealRowOffsets.clear()
        flawHoverScales.clear()
        flawIconScales.clear()
        flawTextOffsets.clear()
        flawRowOffsets.clear()
        motivationScales.clear()
        motivationAlphas.clear()
        motivationFrozenAngles.clear()
        motivationResumePhases.clear()
        modelAlpha = 1f
        motivationsBaseAlpha = 1f
        idealIconRotations.clear()
        flawIconRotations.clear()
        motivationsOverlayOpen = false
        overlayAnimTime = 0f
        overlayScroll = 0
        motivationTypewriterProgress.clear()
        motivationDividerProgress.clear()
        tooltipAnimW = 0f
        tooltipAnimH = 0f
        tooltipWidthTimer = 0f
        lastTooltipKey = ""
        buildParticles()
    }

    private fun buildParticles() {
        particles.clear()
        val motivations = ClientPlayerData.motivations
        if (motivations.isEmpty()) return

        val count = motivations.size
        val rand = java.util.Random(42L)

        motivations.forEachIndexed { i, mot ->
            // Равномерно распределяем по орбите, добавляем небольшой разброс
            val baseAngle = (i.toFloat() / count) * Math.PI.toFloat() * 2f
            val phase = baseAngle + (rand.nextFloat() - 0.5f) * 0.4f

            val orbitRadius = 55f + rand.nextFloat() * 20f
            val orbitSpeed = 0.18f + rand.nextFloat() * 0.12f
            val orbitTilt = 0.35f + rand.nextFloat() * 0.25f  // эллипс
            val scale = 0.42f + rand.nextFloat() * 0.12f      // чуть меньше чем раньше
            val baseAlpha = 0.55f + rand.nextFloat() * 0.25f
            
            // Pending мотивации (не одобренные ГМом) - серые
            val colors = if (mot.isActive) {
                listOf(0xD4AF37, 0xC8A96E, 0xFFD700, 0xB8956A, 0xE8C97A)  // Золотые цвета
            } else {
                listOf(0x666666, 0x777777, 0x888888, 0x555555, 0x999999)  // Серые цвета для pending
            }
            val color = colors[i % colors.size]
            // Z-слой: чередуем за/перед персонажем по фазе
            val zLayer = sin(phase.toDouble()).toFloat()

            particles.add(MotivationParticle(mot.text, orbitRadius, orbitSpeed, phase, orbitTilt, scale, baseAlpha, color, zLayer))
        }
    }

    // ── Временная шкала анимации ──────────────────────────────────────────────
    // openTime идёт от 0 до 1 со скоростью delta*0.018 (~55 кадров = ~1 сек)
    private fun alignProgress()   = easeOut(((openTime - 0.00f) / 0.25f).coerceIn(0f, 1f))
    private fun headerProgress()  = easeOut(((openTime - 0.15f) / 0.25f).coerceIn(0f, 1f))
    private fun dividerProgress() = easeOut(((openTime - 0.38f) / 0.17f).coerceIn(0f, 1f))
    private fun lineProgress(lineIdx: Int): Float {
        val delay = 0.50f + lineIdx * 0.06f
        return easeOut(((openTime - delay) / 0.18f).coerceIn(0f, 1f))
    }
    private fun iconProgress(lineIdx: Int): Float {
        // Иконка появляется только после того как текст полностью вылетел
        val textDone = 0.50f + lineIdx * 0.06f + 0.18f  // конец анимации текста
        val delay = textDone + 0.05f
        return ((openTime - delay) / 0.15f).coerceIn(0f, 1f)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        pendingTooltip = null
        time += delta * 0.016f
        openTime = (openTime + delta * 0.018f).coerceAtMost(1f)
        
        // Обновляем мигание курсора
        if (suggestMotivationOverlayOpen) {
            cursorBlinkTime += delta * 0.05f
            if (cursorBlinkTime > 1f) cursorBlinkTime = 0f
        }

        val W = width; val H = height
        val panelW = (W * 0.27f).toInt()
        val pad = 8
        val sideMargin = 18
        val panelStartY = H / 3
        val cx = W / 2; val cy = H / 2
        
        // Hover для мировоззрения
        val alignText = ClientPlayerData.alignmentText.ifEmpty { "?" }
        val alignBaseScale = 0.9f
        val alignW = (textRenderer.getWidth(alignText) * alignBaseScale * alignmentScale).toInt()
        val alignH = (textRenderer.fontHeight * alignBaseScale * alignmentScale).toInt()
        val alignX = cx - alignW / 2
        val alignY = pad + 11
        alignmentHovered = alignProgress() > 0.9f && mouseX in alignX..(alignX + alignW) && mouseY in alignY..(alignY + alignH)
        alignmentScale = lerp(alignmentScale, if (alignmentHovered) 1.15f else 1f, 0.15f)
        
        // Перестраиваем частицы если список мотиваций изменился
        val currentSnapshot = ClientPlayerData.motivations.map { "${it.id}:${it.isActive}" }
        if (currentSnapshot != lastMotivationSnapshot) {
            lastMotivationSnapshot = currentSnapshot
            buildParticles()
        }

        // ── Обновление hover-анимаций ─────────────────────────────────────────
        // Модель и мотивации - зона по центру, умеренная ширина
        // Уменьшаем высоту снизу чтобы не перекрывать кнопку "Назад"
        val modelX = cx - 65..cx + 65
        val modelY = cy - 60..cy + 100
        modelHovered = mouseX in modelX && mouseY in modelY
        modelAlpha = lerp(modelAlpha, if (modelHovered) 0.20f else 1f, 0.15f)
        motivationsBaseAlpha = lerp(motivationsBaseAlpha, if (modelHovered) 1f else 0.7f, 0.15f)
        
        // Мотивации hover — вычисляем текущие позиции с учётом замороженных углов
        var newHoveredMotivation: Int? = null
        if (modelHovered) {
            particles.forEachIndexed { idx, p ->
                // Используем тот же угол что и при рендере
                val frozenAngle = motivationFrozenAngles[idx]
                val phase = motivationResumePhases.getOrDefault(idx, p.orbitPhase)
                val angle = frozenAngle ?: (time * p.orbitSpeed + phase)
                val ox = cos(angle.toDouble()).toFloat() * p.orbitRadius
                val oy = sin(angle.toDouble()).toFloat() * p.orbitRadius * p.orbitTilt
                val px = cx + ox
                val py = cy + oy - 30f
                val currentScale = motivationScales.getOrDefault(idx, 1f)
                val tw = (textRenderer.getWidth(p.text) * p.scale * currentScale).toInt()
                val th = (textRenderer.fontHeight * p.scale * currentScale).toInt()
                if (mouseX in (px - tw/2).toInt()..(px + tw/2).toInt() && 
                    mouseY in (py - th/2).toInt()..(py + th/2).toInt()) {
                    newHoveredMotivation = idx
                }
            }
        }
        
        // Обновляем замороженные углы: фиксируем в момент начала hover
        val prevHovered = hoveredMotivationIdx
        hoveredMotivationIdx = newHoveredMotivation
        particles.forEachIndexed { idx, p ->
            if (hoveredMotivationIdx == idx && prevHovered != idx) {
                // Только что навели — фиксируем текущий угол
                val currentPhase = motivationResumePhases.getOrDefault(idx, p.orbitPhase)
                motivationFrozenAngles[idx] = time * p.orbitSpeed + currentPhase
            } else if (hoveredMotivationIdx != idx && motivationFrozenAngles.containsKey(idx)) {
                // Убрали курсор — вычисляем phase так чтобы орбита продолжилась с замороженной позиции
                // angle(t) = t * speed + phase  =>  phase = frozenAngle - time * speed
                val frozenAngle = motivationFrozenAngles[idx]!!
                motivationResumePhases[idx] = frozenAngle - time * p.orbitSpeed
                motivationFrozenAngles.remove(idx)
            }
        }
        
        particles.forEachIndexed { idx, _ ->
            val target = when {
                hoveredMotivationIdx == idx -> 1.35f
                hoveredMotivationIdx != null -> 0.85f
                else -> 1f
            }
            motivationScales[idx] = lerp(motivationScales.getOrDefault(idx, 1f), target, 0.12f)
            
            val alphaTarget = when {
                hoveredMotivationIdx == idx -> 1f
                hoveredMotivationIdx != null -> 0.4f
                else -> 1f
            }
            motivationAlphas[idx] = lerp(motivationAlphas.getOrDefault(idx, 1f), alphaTarget, 0.12f)
        }

        // Убеждения и слабости hover (будет обновлено в renderIdealsPanel/renderFlawsPanel)
        // Здесь только обновляем lerp-анимации на основе текущего hoveredIdealIdx/hoveredFlawIdx

        // ── PLAYER MODEL ─────────────────────────────────────────────────────
        val player = MinecraftClient.getInstance().player
        if (player != null) {
            // Без hover: мотивации "за" рендерятся ДО модели (перекрываются ею)
            if (!modelHovered) {
                renderMotivations(context, cx.toFloat(), cy.toFloat(), behindOnly = true, forceOnTop = false)
            }

            // Модель
            com.mojang.blaze3d.systems.RenderSystem.enableBlend()
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc()
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, modelAlpha)
            InventoryScreen.drawEntity(
                context, cx, cy + 85, 70,
                (cx - mouseX).toFloat(), (cy - mouseY).toFloat(),
                player
            )
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            com.mojang.blaze3d.systems.RenderSystem.disableBlend()

            // Мотивации "перед" — поверх модели
            renderMotivations(context, cx.toFloat(), cy.toFloat(), behindOnly = false, forceOnTop = true)
            
            // При hover — "за" мотивации тоже рисуем поверх модели
            if (modelHovered) {
                renderMotivations(context, cx.toFloat(), cy.toFloat(), behindOnly = true, forceOnTop = true)
            }
        } else {
            renderMotivations(context, cx.toFloat(), cy.toFloat(), behindOnly = true, forceOnTop = false)
            renderMotivations(context, cx.toFloat(), cy.toFloat(), behindOnly = false, forceOnTop = false)
        }

        // ── ALIGNMENT (top center, вылет сверху) ─────────────────────────────
        renderAlignmentTop(context, W, pad, mouseX, mouseY)

        // ── PANELS ───────────────────────────────────────────────────────────
        renderIdealsPanel(context, mouseX, mouseY, pad + sideMargin, panelStartY, panelW, H - panelStartY - 20)
        renderFlawsPanel(context, mouseX, mouseY, W - panelW - pad - sideMargin, panelStartY, panelW, H - panelStartY - 20)

        // ── BACK BUTTON (верх справа) ────────────────────────────────────────
        val backText = net.minecraft.client.resource.language.I18n.translate("bbf.personality.back.right")
        val bw = 70; val bh = 12
        val bx = W - bw - 8; val by = 8
        val backHov = mouseX in bx..(bx + bw) && mouseY in by..(by + bh)
        context.fill(bx, by, bx + bw, by + bh, if (backHov) 0xCC4a3a2a.toInt() else 0xCC1a1a1a.toInt())
        context.fill(bx, by, bx + bw, by + 1, 0xFF8a6a3a.toInt())
        context.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFF8a6a3a.toInt())
        context.fill(bx, by, bx + 1, by + bh, 0xFF8a6a3a.toInt())
        context.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFF8a6a3a.toInt())
        val bm = context.matrices; bm.push()
        bm.translate((bx + bw / 2).toFloat(), (by + 2).toFloat(), 0f); bm.scale(0.75f, 0.75f, 1f)
        val btw = textRenderer.getWidth(backText)
        context.drawTextWithShadow(textRenderer, backText, -(btw / 2), 0, if (backHov) 0xFFD700 else 0xCCCCCC)
        bm.pop()
        
        // ── SUGGEST MOTIVATION BUTTON (низ по центру) ────────────────────────
        val suggestText = net.minecraft.client.resource.language.I18n.translate("bbf.personality.suggest_motivation")
        val suggestScale = 0.75f
        val sw = (textRenderer.getWidth(suggestText) * suggestScale + 16).toInt(); val sh = 12
        val sx = W / 2 - sw / 2; val sy = H - sh - 6
        val suggestHov = mouseX in sx..(sx + sw) && mouseY in sy..(sy + sh)
        context.fill(sx, sy, sx + sw, sy + sh, if (suggestHov) 0xCC4a3a2a.toInt() else 0xCC1a1a1a.toInt())
        context.fill(sx, sy, sx + sw, sy + 1, 0xFF8a6a3a.toInt())
        context.fill(sx, sy + sh - 1, sx + sw, sy + sh, 0xFF8a6a3a.toInt())
        context.fill(sx, sy, sx + 1, sy + sh, 0xFF8a6a3a.toInt())
        context.fill(sx + sw - 1, sy, sx + sw, sy + sh, 0xFF8a6a3a.toInt())
        val sm = context.matrices; sm.push()
        sm.translate((sx + sw / 2).toFloat(), (sy + 2).toFloat(), 0f); sm.scale(suggestScale, suggestScale, 1f)
        val stw = textRenderer.getWidth(suggestText)
        context.drawTextWithShadow(textRenderer, suggestText, -(stw / 2), 0, if (suggestHov) 0xFFD700 else 0xCCCCCC)
        sm.pop()

        // ── MOTIVATIONS OVERLAY ───────────────────────────────────────────────
        if (motivationsOverlayOpen || overlayAnimTime > 0.01f) {
            renderMotivationsOverlay(context, mouseX, mouseY)
        }
        updateOverlayAnimation()
        
        // ── SUGGEST MOTIVATION OVERLAY ────────────────────────────────────────
        if (suggestMotivationOverlayOpen || suggestOverlayAnimTime > 0.01f) {
            renderSuggestMotivationOverlay(context, mouseX, mouseY)
        }
        updateSuggestOverlayAnimation()

        // ── TOOLTIP ───────────────────────────────────────────────────────────
        pendingTooltip?.let { drawSmallTooltip(context, it, mouseX, mouseY) }
        updateTooltipAnim()

        super.render(context, mouseX, mouseY, delta)
    }

    // ── МОТИВАЦИИ: орбитальное движение ──────────────────────────────────────
    private fun renderMotivations(context: DrawContext, centerX: Float, centerY: Float, behindOnly: Boolean, forceOnTop: Boolean = false) {
        val introAlpha = easeOut(openTime)
        
        if (forceOnTop) {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest()
        }
        
        particles.forEachIndexed { idx, p ->
            // Вычисляем текущий угол (тот же что и при рендере позиции)
            val phase = motivationResumePhases.getOrDefault(idx, p.orbitPhase)
            val angle = motivationFrozenAngles[idx] ?: (time * p.orbitSpeed + phase)
            
            // Динамический Z-слой: sin(angle) > 0 = перед персонажем, < 0 = за персонажем
            val dynamicZLayer = sin(angle.toDouble()).toFloat()
            val isBehind = dynamicZLayer < 0f
            if (!forceOnTop && isBehind != behindOnly) return@forEachIndexed

            val isHovered = hoveredMotivationIdx == idx
            val hoverScale = motivationScales.getOrDefault(idx, 1f)
            val hoverAlpha = motivationAlphas.getOrDefault(idx, 1f)
            
            val ox = cos(angle.toDouble()).toFloat() * p.orbitRadius
            val oy = sin(angle.toDouble()).toFloat() * p.orbitRadius * p.orbitTilt

            val px = centerX + ox
            val py = centerY + oy - 30f

            // Пульсация scale при hover
            val pulseScale = if (isHovered) {
                1f + sin((time * 2f).toDouble()).toFloat() * 0.08f
            } else 1f

            // Альфа: за персонажем — тусклее, + hover состояние
            val depthFade = if (isBehind && !forceOnTop) 0.45f else if (isBehind) 0.7f else 1.0f
            val pulseAlpha = p.baseAlpha + sin((time * 0.5f + p.orbitPhase).toDouble()).toFloat() * 0.07f
            val alpha = ((pulseAlpha * introAlpha * depthFade * motivationsBaseAlpha * hoverAlpha).coerceIn(0f, 1f) * 255).toInt()
            val color = (alpha shl 24) or p.color

            val m = context.matrices; m.push()
            m.translate(px, py, 200f)  // z=200 гарантирует рендер поверх GUI элементов
            val finalScale = p.scale * hoverScale * pulseScale
            m.scale(finalScale, finalScale, 1f)
            
            // При hover показываем полный текст многострочно, иначе сокращённый
            if (isHovered) {
                // Разбиваем на строки (макс 20 символов на строку)
                val lines = wrapMotivationText(p.text, 20)
                val lineHeight = textRenderer.fontHeight + 1
                val totalHeight = lines.size * lineHeight
                val startY = -(totalHeight / 2)
                
                lines.forEachIndexed { lineIdx, line ->
                    val tw = textRenderer.getWidth(line)
                    val lineY = startY + lineIdx * lineHeight
                    context.drawTextWithShadow(textRenderer, line, -(tw / 2), lineY, color)
                }
            } else {
                // Сокращённый текст (первые 18 символов + "...")
                val displayText = if (p.text.length > 18) {
                    p.text.substring(0, 18) + "..."
                } else {
                    p.text
                }
                val tw = textRenderer.getWidth(displayText)
                context.drawTextWithShadow(textRenderer, displayText, -(tw / 2), 0, color)
            }
            
            m.pop()
        }
        
        if (forceOnTop) {
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest()
        }
    }
    
    /**
     * Разбивает текст мотивации на строки с учётом максимальной длины.
     * Старается разбивать по словам.
     */
    private fun wrapMotivationText(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (test.length <= maxChars) {
                current = test
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        
        return lines.ifEmpty { listOf(text) }
    }

    // ── МИРОВОЗЗРЕНИЕ: вылет сверху вниз ─────────────────────────────────────
    private fun renderAlignmentTop(context: DrawContext, W: Int, pad: Int, mouseX: Int, mouseY: Int) {
        val prog = alignProgress()
        if (prog <= 0f) return

        val alignText = ClientPlayerData.alignmentText.ifEmpty {
            net.minecraft.client.resource.language.I18n.translate("bbf.alignment.true_neutral")
        }
        val labelText = net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.alignment")
        val cx = W / 2

        // Вылет сверху: смещение по Y от -30 до 0
        val offY = ((1f - prog) * -30f).toInt()
        val alpha = (prog * 255).toInt()

        val m = context.matrices; m.push()
        m.translate(0f, offY.toFloat(), 0f)
        // "Мировоззрение" появляется fade-in после того как основной текст вылетел
        val labelAlpha = (((prog - 0.7f) / 0.3f).coerceIn(0f, 1f) * 255).toInt()
        drawCenteredScaledAlpha(context, labelText, cx, pad + 2, 0.55f, 0x888888, labelAlpha)
        val alignColor = if (alignmentHovered) 0xFFE844 else 0xD4AF37
        drawCenteredScaledAlpha(context, alignText, cx, pad + 11, 0.9f * alignmentScale, alignColor, alpha)
        m.pop()

        // Divider под мировоззрением
        val divProg = ((prog - 0.7f) / 0.3f).coerceIn(0f, 1f)
        if (divProg > 0f) {
            drawFadeDividerAnimated(context, cx, pad + 22, 120, divProg)
        }
        
        // Оверлей мировоззрения
        if (alignmentOverlayOpen || alignmentOverlayAnimTime > 0.01f) {
            renderAlignmentOverlay(context, mouseX, mouseY)
        }
        if (alignmentOverlayOpen && alignmentOverlayAnimTime < 1f) {
            alignmentOverlayAnimTime = (alignmentOverlayAnimTime + 0.07f).coerceAtMost(1f)
        } else if (!alignmentOverlayOpen && alignmentOverlayAnimTime > 0f) {
            alignmentOverlayAnimTime = (alignmentOverlayAnimTime - 0.07f).coerceAtLeast(0f)
        }
    }

    // ── ОВЕРЛЕЙ МИРОВОЗЗРЕНИЯ ─────────────────────────────────────────────────
    private fun renderAlignmentOverlay(context: DrawContext, mouseX: Int, mouseY: Int) {
        val W = width; val H = height
        val prog = easeOut(alignmentOverlayAnimTime)
        if (prog < 0.01f) return

        val targetW = 200; val targetH = 180
        val ocx = W / 2; val ocy = H / 2
        val animW = (targetW * prog).toInt(); val animH = (targetH * prog).toInt()
        val x0 = ocx - animW / 2; val y0 = ocy - animH / 2
        val x1 = x0 + animW; val y1 = y0 + animH

        val m = context.matrices; m.push()
        m.translate(0f, 0f, 400f)

        context.fill(0, 0, W, H, ((prog * 0x99).toInt() shl 24) or 0x000000)
        context.fill(x0, y0, x1, y1, 0xEE1a1a1a.toInt())
        context.fill(x0, y0, x1, y0 + 1, 0xFF8a6a3a.toInt())
        context.fill(x0, y1 - 1, x1, y1, 0xFF8a6a3a.toInt())
        context.fill(x0, y0, x0 + 1, y1, 0xFF8a6a3a.toInt())
        context.fill(x1 - 1, y0, x1, y1, 0xFF8a6a3a.toInt())

        if (prog > 0.6f) {
            val contentAlpha = ((prog - 0.6f) / 0.4f).coerceIn(0f, 1f)
            renderAlignmentOverlayContent(context, x0, y0, animW, animH, contentAlpha, mouseX, mouseY)
        }
        m.pop()
    }

    private fun renderAlignmentOverlayContent(context: DrawContext, x: Int, y: Int, w: Int, h: Int, alpha: Float, mouseX: Int, mouseY: Int) {
        val pad = 10
        val iAlpha = (alpha * 255).toInt()

        // Заголовок
        val titleText = net.minecraft.client.resource.language.I18n.translate("bbf.personality.alignment.overlay.title")
        val tm = context.matrices; tm.push()
        tm.translate((x + w / 2).toFloat(), (y + pad).toFloat(), 0f); tm.scale(0.7f, 0.7f, 1f)
        val ttw = textRenderer.getWidth(titleText)
        context.drawTextWithShadow(textRenderer, titleText, -(ttw / 2), 0, (iAlpha shl 24) or 0xD4AF37)
        tm.pop()

        // Описание
        val descText = net.minecraft.client.resource.language.I18n.translate("bbf.personality.alignment.overlay.desc")
        val descScale = 0.5f
        var descY = y + pad + 14
        val dm = context.matrices; dm.push()
        dm.translate((x + w / 2).toFloat(), descY.toFloat(), 0f); dm.scale(descScale, descScale, 1f)
        val dlw = textRenderer.getWidth(descText)
        context.drawTextWithShadow(textRenderer, descText, -(dlw / 2), 0, (iAlpha shl 24) or 0x888888)
        dm.pop()
        descY += (textRenderer.fontHeight * descScale + 2).toInt()

        // Диаграмма — фиксированный маленький размер
        val diagPad = 8
        val diagTop = descY + 4
        val diagSize = 72  // фиксированный размер ~1/3 от оверлея
        val diagX = x + (w - diagSize) / 2
        val diagY = diagTop

        context.fill(diagX, diagY, diagX + diagSize, diagY + diagSize, (iAlpha shl 24) or 0x111111)

        val third = diagSize / 3
        val lineColor = ((iAlpha / 2) shl 24) or 0x333333
        context.fill(diagX + third, diagY, diagX + third + 1, diagY + diagSize, lineColor)
        context.fill(diagX + third * 2, diagY, diagX + third * 2 + 1, diagY + diagSize, lineColor)
        context.fill(diagX, diagY + third, diagX + diagSize, diagY + third + 1, lineColor)
        context.fill(diagX, diagY + third * 2, diagX + diagSize, diagY + third * 2 + 1, lineColor)

        val axisColor = ((iAlpha / 3) shl 24) or 0x555555
        val acx = diagX + diagSize / 2; val acy = diagY + diagSize / 2
        context.fill(acx, diagY, acx + 1, diagY + diagSize, axisColor)
        context.fill(diagX, acy, diagX + diagSize, acy + 1, axisColor)

        val borderColor = (iAlpha shl 24) or 0x6b5a3e
        context.fill(diagX, diagY, diagX + diagSize, diagY + 1, borderColor)
        context.fill(diagX, diagY + diagSize - 1, diagX + diagSize, diagY + diagSize, borderColor)
        context.fill(diagX, diagY, diagX + 1, diagY + diagSize, borderColor)
        context.fill(diagX + diagSize - 1, diagY, diagX + diagSize, diagY + diagSize, borderColor)

        val alignments = listOf(
            omc.boundbyfate.api.identity.Alignment.LAWFUL_GOOD,    omc.boundbyfate.api.identity.Alignment.NEUTRAL_GOOD,  omc.boundbyfate.api.identity.Alignment.CHAOTIC_GOOD,
            omc.boundbyfate.api.identity.Alignment.LAWFUL_NEUTRAL, omc.boundbyfate.api.identity.Alignment.TRUE_NEUTRAL,  omc.boundbyfate.api.identity.Alignment.CHAOTIC_NEUTRAL,
            omc.boundbyfate.api.identity.Alignment.LAWFUL_EVIL,    omc.boundbyfate.api.identity.Alignment.NEUTRAL_EVIL,  omc.boundbyfate.api.identity.Alignment.CHAOTIC_EVIL
        )

        val lawChaos = ClientPlayerData.alignmentLawChaos
        val goodEvil = ClientPlayerData.alignmentGoodEvil
        val currentAlignment = AlignmentCoordinates(lawChaos, goodEvil).getAlignment()

        fun alignmentFillColor(al: omc.boundbyfate.api.identity.Alignment): Int = when {
            al == omc.boundbyfate.api.identity.Alignment.LAWFUL_GOOD -> 0x4488FF
            al == omc.boundbyfate.api.identity.Alignment.NEUTRAL_GOOD -> 0x44CC44
            al == omc.boundbyfate.api.identity.Alignment.CHAOTIC_GOOD -> 0x88FF44
            al == omc.boundbyfate.api.identity.Alignment.LAWFUL_NEUTRAL -> 0x8888CC
            al == omc.boundbyfate.api.identity.Alignment.TRUE_NEUTRAL -> 0xAAAAAA
            al == omc.boundbyfate.api.identity.Alignment.CHAOTIC_NEUTRAL -> 0xCC8844
            al == omc.boundbyfate.api.identity.Alignment.LAWFUL_EVIL -> 0x884444
            al == omc.boundbyfate.api.identity.Alignment.NEUTRAL_EVIL -> 0xAA4444
            else -> 0xFF4444
        }

        var newHoveredCell: Int? = null
        alignmentCellTooltip = null

        alignments.forEachIndexed { i, al ->
            val col = i % 3; val row = i / 3
            val cellX = diagX + col * third
            val cellY = diagY + row * third
            val cellCx = cellX + third / 2
            val cellCy = cellY + third / 2

            val isCurrent = al == currentAlignment
            val isHovered = mouseX in cellX..(cellX + third) && mouseY in cellY..(cellY + third)
            if (isHovered) newHoveredCell = i

            val cellScale = alignmentCellScales.getOrDefault(i, 1f)

            if (isCurrent) {
                val fillColor = alignmentFillColor(al)
                val fillAlpha = (iAlpha * 0.35f).toInt()
                context.fill(cellX + 1, cellY + 1, cellX + third - 1, cellY + third - 1, (fillAlpha shl 24) or fillColor)
                val rimAlpha = (iAlpha * 0.8f).toInt()
                context.fill(cellX, cellY, cellX + third, cellY + 1, (rimAlpha shl 24) or fillColor)
                context.fill(cellX, cellY + third - 1, cellX + third, cellY + third, (rimAlpha shl 24) or fillColor)
                context.fill(cellX, cellY, cellX + 1, cellY + third, (rimAlpha shl 24) or fillColor)
                context.fill(cellX + third - 1, cellY, cellX + third, cellY + third, (rimAlpha shl 24) or fillColor)
            }

            val shortName = net.minecraft.client.resource.language.I18n.translate(al.getShortKey())
            val textColor = if (isCurrent) {
                val c = alignmentFillColor(al)
                (iAlpha shl 24) or ((c shr 1) and 0x7F7F7F)
            } else if (al.name.contains("EVIL")) {
                (iAlpha shl 24) or 0x884444
            } else if (al.name.contains("GOOD")) {
                (iAlpha shl 24) or 0x448844
            } else {
                (iAlpha shl 24) or 0x666666
            }

            val sm = context.matrices; sm.push()
            sm.translate(cellCx.toFloat(), cellCy.toFloat(), 0f)
            sm.scale(0.6f * cellScale, 0.6f * cellScale, 1f)
            val tw = textRenderer.getWidth(shortName)
            context.drawTextWithShadow(textRenderer, shortName, -(tw / 2), -3, textColor)
            sm.pop()

            if (isHovered) {
                alignmentCellTooltip = net.minecraft.text.Text.translatable("${al.translationKey}.tooltip")
            }
        }

        hoveredAlignmentCell = newHoveredCell
        alignments.forEachIndexed { i, _ ->
            val target = if (hoveredAlignmentCell == i) 1.2f else 1f
            alignmentCellScales[i] = lerp(alignmentCellScales.getOrDefault(i, 1f), target, 0.15f)
        }

        // Tooltip ячейки
        alignmentCellTooltip?.let { tooltip ->
            val tooltipStr = tooltip.string
            if (tooltipStr.isNotEmpty()) {
                val ttScale = 0.6f
                val ttW = (textRenderer.getWidth(tooltipStr) * ttScale + 8).toInt()
                val ttH = (textRenderer.fontHeight * ttScale + 6).toInt()
                val ttX = (mouseX + 6).coerceAtMost(x + w - ttW - 2)
                val ttY = (mouseY - ttH - 2).coerceAtLeast(y + 2)
                context.fill(ttX, ttY, ttX + ttW, ttY + ttH, 0xEE1a1a1a.toInt())
                context.fill(ttX, ttY, ttX + ttW, ttY + 1, 0xFF8a6a3a.toInt())
                context.fill(ttX, ttY + ttH - 1, ttX + ttW, ttY + ttH, 0xFF8a6a3a.toInt())
                context.fill(ttX, ttY, ttX + 1, ttY + ttH, 0xFF8a6a3a.toInt())
                context.fill(ttX + ttW - 1, ttY, ttX + ttW, ttY + ttH, 0xFF8a6a3a.toInt())
                val ttm = context.matrices; ttm.push()
                ttm.translate((ttX + 4).toFloat(), (ttY + 3).toFloat(), 0f); ttm.scale(ttScale, ttScale, 1f)
                context.drawTextWithShadow(textRenderer, tooltipStr, 0, 0, 0xFFFFFFFF.toInt())
                ttm.pop()
            }
        }
    }

    // ── ПАНЕЛЬ УБЕЖДЕНИЙ ──────────────────────────────────────────────────────
    private fun renderIdealsPanel(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val ideals = ClientPlayerData.ideals
        if (ideals.isEmpty()) return

        val headerProg = headerProgress()
        val headerCx = x + w / 2

        // Hover для заголовка
        val headerText = net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.ideals")
        val headerScale = 0.6f
        val headerW = (textRenderer.getWidth(headerText) * headerScale).toInt()
        val headerH = (textRenderer.fontHeight * headerScale).toInt()
        val headerX = headerCx - headerW / 2
        val headerY = y + 4
        idealsHeaderHovered = mouseX in headerX..(headerX + headerW) && mouseY in headerY..(headerY + headerH) && headerProg > 0.9f
        idealsHeaderScale = lerp(idealsHeaderScale, if (idealsHeaderHovered) 1.15f else 1f, 0.15f)
        
        if (idealsHeaderHovered) {
            pendingTooltip = net.minecraft.text.Text.translatable("bbf.personality.ideals.tooltip")
        }

        // Заголовок въезжает слева
        if (headerProg > 0f) {
            val offX = ((1f - headerProg) * -(x + w + 20)).toInt()
            val alpha = (headerProg * 255).toInt()
            val m = context.matrices; m.push()
            m.translate(offX.toFloat(), 0f, 0f)
            drawCenteredScaledAlpha(context, headerText,
                headerCx, headerY, headerScale * idealsHeaderScale, 0xD4AF37, alpha)
            m.pop()
        }

        val divProg = dividerProgress()
        val headerBottomY = y + 4 + 9
        if (divProg > 0f) {
            drawFadeDividerAnimated(context, headerCx, headerBottomY, w, divProg)
        }

        var curY = y + 4 + 9 + 5

        val textScale = 0.65f
        val maxChars = ((w - 12) / (textRenderer.getWidth("W") * textScale)).toInt().coerceAtLeast(10)
        val lineH = 9
        val listH = h - (curY - y) - 20

        val visibleCount = (listH / lineH).coerceAtLeast(1)

        // Hit-test для hover
        var newHoveredIdeal: Int? = null
        var testY = curY
        ideals.forEachIndexed { idx, _ ->
            val hitH = lineH + 2
            if (mouseX in x..(x + w) && mouseY in testY..(testY + hitH)) {
                newHoveredIdeal = idx
            }
            testY += lineH + 2  // Пропуск между убеждениями
        }
        hoveredIdealIdx = newHoveredIdeal

        // Обновляем lerp-анимации
        ideals.forEachIndexed { idx, _ ->
            val isHovered = hoveredIdealIdx == idx
            idealHoverScales[idx] = lerp(idealHoverScales.getOrDefault(idx, 1f), if (isHovered) 1.1f else 1f, 0.15f)
            idealIconScales[idx] = lerp(idealIconScales.getOrDefault(idx, 1f), if (isHovered) 1.2f else 1f, 0.12f)
            idealTextOffsets[idx] = lerp(idealTextOffsets.getOrDefault(idx, 0f), if (isHovered) 12f else 0f, 0.15f)
            idealExpandProgress[idx] = lerp(idealExpandProgress.getOrDefault(idx, 0f), if (isHovered) 1f else 0f, 0.18f)  // Анимация раскрытия
            // Ротация: при hover крутится медленно, при уходе нормализуем и плавно возвращаем к 0
            val currentRot = idealIconRotations.getOrDefault(idx, 0f)
            if (isHovered) {
                idealIconRotations[idx] = currentRot + 0.7f
            } else {
                val normalized = ((currentRot % 360f) + 360f) % 360f
                val wrapped = if (normalized > 180f) normalized - 360f else normalized
                idealIconRotations[idx] = lerp(wrapped, 0f, 0.12f)
            }
        }

        // Рассчитываем смещения для раздвигания при hover (с учётом прогресса анимации и задержки)
        val idealOffsets = mutableMapOf<Int, Float>()
        var accumulatedOffset = 0f
        ideals.forEachIndexed { idx, ideal ->
            idealOffsets[idx] = accumulatedOffset
            
            // Добавляем пространство пропорционально прогрессу раскрытия
            // Используем задержку: схлопывание начинается только когда expandProg < 0.3
            val expandProg = idealExpandProgress.getOrDefault(idx, 0f)
            val collapseProg = if (expandProg < 0.3f) 0f else expandProg  // Задержка схлопывания
            if (collapseProg > 0.01f) {
                val lines = wrapText(ideal.text, maxChars)
                val extraLines = (lines.size - 1).coerceAtLeast(0)
                accumulatedOffset += extraLines * lineH * collapseProg  // Плавное раздвигание
            }
        }

        // Рендер убеждений
        ideals.forEachIndexed { idx, ideal ->
            val lp = lineProgress(idx)
            if (lp <= 0f) return@forEachIndexed

            val isHovered = hoveredIdealIdx == idx
            val offset = idealOffsets.getOrDefault(idx, 0f)
            val ly = curY + idx * (lineH + 2) + offset.toInt()
            
            val hoverScale = idealHoverScales.getOrDefault(idx, 1f)
            val textOffset = idealTextOffsets.getOrDefault(idx, 0f)
            val color = if (ideal.isCompatible) 0xCCCCCC else 0xFF7777

            // Текст выезжает слева направо
            val textOffX = ((1f - lp) * -(w + 30)).toInt()
            val textAlpha = (lp * 255).toInt()
            val iconSize = 5
            val iconY = ly + (lineH * textScale / 2 - iconSize / 2).toInt()
            val textX = x + iconSize + 2 + textOffset.toInt()

            val m = context.matrices; m.push()
            m.translate(textOffX.toFloat(), 0f, 0f)
            
            // Рендер текста: анимированное раскрытие
            val expandProg = idealExpandProgress.getOrDefault(idx, 0f)
            val lines = wrapText(ideal.text, maxChars)
            
            if (expandProg > 0.3f && lines.size > 1) {
                // Раскрытие: показываем первую строку полностью + дополнительные строки с альфой
                // Первая строка (полная, без "...")
                val tm1 = context.matrices; tm1.push()
                tm1.translate(textX.toFloat(), ly.toFloat(), 0f)
                val finalScale = textScale * hoverScale
                tm1.scale(finalScale, finalScale, 1f)
                val tc1 = (textAlpha shl 24) or (color and 0xFFFFFF)
                context.drawTextWithShadow(textRenderer, lines[0], 0, 0, tc1)
                tm1.pop()
                
                // Дополнительные строки с fade-in (только когда expandProg > 0.3)
                // Нормализуем прогресс: 0.3→1.0 становится 0→1
                val normalizedProg = ((expandProg - 0.3f) / 0.7f).coerceIn(0f, 1f)
                
                // Рендерим дополнительные строки только если normalizedProg достаточно высокий
                if (normalizedProg > 0.05f) {
                    lines.drop(1).forEachIndexed { lineIdx, lineText ->
                        val lineY = ly + (lineIdx + 1) * lineH
                        val lineAlpha = (normalizedProg * textAlpha).toInt()
                        val tm = context.matrices; tm.push()
                        tm.translate(textX.toFloat(), lineY.toFloat(), 0f)
                        tm.scale(finalScale, finalScale, 1f)
                        val tc = (lineAlpha shl 24) or (color and 0xFFFFFF)
                        context.drawTextWithShadow(textRenderer, lineText, 0, 0, tc)
                        tm.pop()
                    }
                }
            } else {
                // Обрезанный текст (без раскрытия) - обрезаем по словам с "..."
                val displayText = truncateTextByWords(ideal.text, maxChars)
                val tm = context.matrices; tm.push()
                tm.translate(textX.toFloat(), ly.toFloat(), 0f)
                val finalScale = textScale * hoverScale
                tm.scale(finalScale, finalScale, 1f)
                val tc = (textAlpha shl 24) or (color and 0xFFFFFF)
                context.drawTextWithShadow(textRenderer, displayText, 0, 0, tc)
                tm.pop()
            }
            
            m.pop()

            // Иконка появляется fade-in после текста
            val ip = iconProgress(idx)
            if (ip > 0f) {
                val iconAlpha = (ip * 255).toInt().coerceIn(0, 255)
                val iconScale = idealIconScales.getOrDefault(idx, 1f)
                val iconRotation = idealIconRotations.getOrDefault(idx, 0f)
                
                val im = context.matrices; im.push()
                val icx = x + iconSize / 2f
                val icy = iconY + iconSize / 2f
                im.translate(icx, icy, 0f)
                im.scale(iconScale, iconScale, 1f)
                im.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(iconRotation))
                im.translate(-icx, -icy, 0f)
                drawIconWithAlpha(context, x, iconY, iconSize, iconSize, iconAlpha)
                im.pop()
            }
        }
    }

    // ── ПАНЕЛЬ СЛАБОСТЕЙ ──────────────────────────────────────────────────────
    private fun renderFlawsPanel(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val flaws = ClientPlayerData.flaws
        if (flaws.isEmpty()) return

        val headerProg = headerProgress()
        val headerCx = x + w / 2
        val W = width

        // Hover для заголовка
        val headerText = net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.flaws")
        val headerScale = 0.6f
        val headerW = (textRenderer.getWidth(headerText) * headerScale).toInt()
        val headerH = (textRenderer.fontHeight * headerScale).toInt()
        val headerX = headerCx - headerW / 2
        val headerY = y + 4
        flawsHeaderHovered = mouseX in headerX..(headerX + headerW) && mouseY in headerY..(headerY + headerH) && headerProg > 0.9f
        flawsHeaderScale = lerp(flawsHeaderScale, if (flawsHeaderHovered) 1.15f else 1f, 0.15f)
        
        if (flawsHeaderHovered) {
            pendingTooltip = net.minecraft.text.Text.translatable("bbf.personality.flaws.tooltip")
        }

        // Заголовок въезжает справа
        if (headerProg > 0f) {
            val offX = ((1f - headerProg) * (W - x + 20)).toInt()
            val alpha = (headerProg * 255).toInt()
            val m = context.matrices; m.push()
            m.translate(offX.toFloat(), 0f, 0f)
            drawCenteredScaledAlpha(context, headerText,
                headerCx, headerY, headerScale * flawsHeaderScale, 0xD4AF37, alpha)
            m.pop()
        }

        val divProg = dividerProgress()
        val headerBottomY = y + 4 + 9
        if (divProg > 0f) {
            drawFadeDividerAnimated(context, headerCx, headerBottomY, w, divProg)
        }

        var curY = y + 4 + 9 + 5

        val textScale = 0.65f
        val maxChars = ((w - 12) / (textRenderer.getWidth("W") * textScale)).toInt().coerceAtLeast(10)
        val lineH = 9
        val listH = h - (curY - y) - 20

        val visibleCount = (listH / lineH).coerceAtLeast(1)

        // Hit-test для hover
        var newHoveredFlaw: Int? = null
        var testY = curY
        flaws.forEachIndexed { idx, _ ->
            val hitH = lineH + 2
            if (mouseX in x..(x + w) && mouseY in testY..(testY + hitH)) {
                newHoveredFlaw = idx
            }
            testY += lineH + 2  // Пропуск между слабостями
        }
        hoveredFlawIdx = newHoveredFlaw

        // Обновляем lerp-анимации
        flaws.forEachIndexed { idx, _ ->
            val isHovered = hoveredFlawIdx == idx
            flawHoverScales[idx] = lerp(flawHoverScales.getOrDefault(idx, 1f), if (isHovered) 1.1f else 1f, 0.15f)
            flawIconScales[idx] = lerp(flawIconScales.getOrDefault(idx, 1f), if (isHovered) 1.2f else 1f, 0.12f)
            flawTextOffsets[idx] = lerp(flawTextOffsets.getOrDefault(idx, 0f), if (isHovered) -12f else 0f, 0.15f)
            flawExpandProgress[idx] = lerp(flawExpandProgress.getOrDefault(idx, 0f), if (isHovered) 1f else 0f, 0.18f)  // Анимация раскрытия
            // Ротация: при hover крутится медленно, при уходе нормализуем и плавно возвращаем к 0
            val currentRot = flawIconRotations.getOrDefault(idx, 0f)
            if (isHovered) {
                flawIconRotations[idx] = currentRot + 0.7f
            } else {
                val normalized = ((currentRot % 360f) + 360f) % 360f
                val wrapped = if (normalized > 180f) normalized - 360f else normalized
                flawIconRotations[idx] = lerp(wrapped, 0f, 0.12f)
            }
        }

        // Рассчитываем смещения для раздвигания при hover (с учётом прогресса анимации и задержки)
        val flawOffsets = mutableMapOf<Int, Float>()
        var accumulatedOffset = 0f
        flaws.forEachIndexed { idx, flaw ->
            flawOffsets[idx] = accumulatedOffset
            
            // Добавляем пространство пропорционально прогрессу раскрытия
            // Используем задержку: схлопывание начинается только когда expandProg < 0.3
            val expandProg = flawExpandProgress.getOrDefault(idx, 0f)
            val collapseProg = if (expandProg < 0.3f) 0f else expandProg  // Задержка схлопывания
            if (collapseProg > 0.01f) {
                val lines = wrapText(flaw.text, maxChars)
                val extraLines = (lines.size - 1).coerceAtLeast(0)
                accumulatedOffset += extraLines * lineH * collapseProg  // Плавное раздвигание
            }
        }

        // Рендер слабостей
        flaws.forEachIndexed { idx, flaw ->
            val lp = lineProgress(idx)
            if (lp <= 0f) return@forEachIndexed

            val isHovered = hoveredFlawIdx == idx
            val offset = flawOffsets.getOrDefault(idx, 0f)
            val ly = curY + idx * (lineH + 2) + offset.toInt()
            
            val hoverScale = flawHoverScales.getOrDefault(idx, 1f)
            val textOffset = flawTextOffsets.getOrDefault(idx, 0f)

            // Текст выезжает справа налево
            val textOffX = ((1f - lp) * (W - x + 30)).toInt()
            val textAlpha = (lp * 255).toInt()
            val iconSize = 5
            val iconY = ly + (lineH * textScale / 2 - iconSize / 2).toInt()
            val rightEdge = x + w
            val textRightEdge = rightEdge - iconSize - 2 + textOffset.toInt()

            val m = context.matrices; m.push()
            m.translate(textOffX.toFloat(), 0f, 0f)
            
            // Рендер текста: анимированное раскрытие
            val expandProg = flawExpandProgress.getOrDefault(idx, 0f)
            val lines = wrapText(flaw.text, maxChars)
            
            if (expandProg > 0.3f && lines.size > 1) {
                // Раскрытие: показываем первую строку полностью + дополнительные строки с альфой
                // Первая строка (полная, без "...")
                val tm1 = context.matrices; tm1.push()
                tm1.translate(textRightEdge.toFloat(), ly.toFloat(), 0f)
                val finalScale = textScale * hoverScale
                tm1.scale(finalScale, finalScale, 1f)
                val tw1 = textRenderer.getWidth(lines[0])
                val tc1 = (textAlpha shl 24) or 0xCCCCCC
                context.drawTextWithShadow(textRenderer, lines[0], -tw1, 0, tc1)
                tm1.pop()
                
                // Дополнительные строки с fade-in (только когда expandProg > 0.3)
                // Нормализуем прогресс: 0.3→1.0 становится 0→1
                val normalizedProg = ((expandProg - 0.3f) / 0.7f).coerceIn(0f, 1f)
                
                // Рендерим дополнительные строки только если normalizedProg достаточно высокий
                if (normalizedProg > 0.05f) {
                    lines.drop(1).forEachIndexed { lineIdx, lineText ->
                        val lineY = ly + (lineIdx + 1) * lineH
                        val lineAlpha = (normalizedProg * textAlpha).toInt()
                        val tm = context.matrices; tm.push()
                        tm.translate(textRightEdge.toFloat(), lineY.toFloat(), 0f)
                        tm.scale(finalScale, finalScale, 1f)
                        val tw = textRenderer.getWidth(lineText)
                        val tc = (lineAlpha shl 24) or 0xCCCCCC
                        context.drawTextWithShadow(textRenderer, lineText, -tw, 0, tc)
                        tm.pop()
                    }
                }
            } else {
                // Обрезанный текст (без раскрытия) - обрезаем по словам с "..."
                val displayText = truncateTextByWords(flaw.text, maxChars)
                val tm = context.matrices; tm.push()
                tm.translate(textRightEdge.toFloat(), ly.toFloat(), 0f)
                val finalScale = textScale * hoverScale
                tm.scale(finalScale, finalScale, 1f)
                val tw = textRenderer.getWidth(displayText)
                val tc = (textAlpha shl 24) or 0xCCCCCC
                context.drawTextWithShadow(textRenderer, displayText, -tw, 0, tc)
                tm.pop()
            }
            
            m.pop()

            // Иконка fade-in
            val ip = iconProgress(idx)
            if (ip > 0f) {
                val iconAlpha = (ip * 255).toInt().coerceIn(0, 255)
                val iconScale = flawIconScales.getOrDefault(idx, 1f)
                val iconRotation = flawIconRotations.getOrDefault(idx, 0f)
                
                val im = context.matrices; im.push()
                val icx = rightEdge - iconSize / 2f
                val icy = iconY + iconSize / 2f
                im.translate(icx, icy, 0f)
                im.scale(iconScale, iconScale, 1f)
                im.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(iconRotation))
                im.translate(-icx, -icy, 0f)
                drawIconWithAlpha(context, rightEdge - iconSize, iconY, iconSize, iconSize, iconAlpha)
                im.pop()
            }
        }
    }

    // ── ВСПОМОГАТЕЛЬНЫЕ ──────────────────────────────────────────────────────

    /**
     * Divider с анимацией раскрытия от центра к краям.
     * progress 0→1: линия растёт от cx в обе стороны.
     */
    private fun drawFadeDividerAnimated(context: DrawContext, cx: Int, y: Int, totalW: Int, progress: Float) {
        val halfW = (totalW / 2 * progress).toInt()
        if (halfW <= 0) return
        drawFadeDividerAlpha(context, cx, y, halfW * 2, 0xAA)
    }

    /**
     * Divider с fade-out к краям и заданной альфой.
     */
    private fun drawFadeDividerAlpha(context: DrawContext, cx: Int, y: Int, totalW: Int, maxAlpha: Int) {
        val halfW = totalW / 2
        val solidHalf = (halfW * 0.35f).toInt()
        val fadeSteps = halfW - solidHalf
        val baseColor = 0xD4AF37
        val r = (baseColor shr 16) and 0xFF
        val g = (baseColor shr 8) and 0xFF
        val b = baseColor and 0xFF

        val solidColor = (maxAlpha shl 24) or baseColor
        context.fill(cx - solidHalf, y, cx + solidHalf, y + 1, solidColor)

        for (i in 0 until fadeSteps) {
            val alpha = (maxAlpha * (1f - i.toFloat() / fadeSteps)).toInt()
            val c = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            context.fill(cx - solidHalf - i - 1, y, cx - solidHalf - i, y + 1, c)
            context.fill(cx + solidHalf + i, y, cx + solidHalf + i + 1, y + 1, c)
        }
    }

    private fun drawFadeDivider(context: DrawContext, cx: Int, y: Int, totalW: Int) =
        drawFadeDividerAlpha(context, cx, y, totalW, 0xAA)

    /** Рисует иконку PROFICIENCY с заданной альфой через RenderSystem color. */
    private fun drawIconWithAlpha(context: DrawContext, x: Int, y: Int, w: Int, h: Int, alpha: Int) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f)
        GuiAtlas.ICON_PROFICIENCY.draw(context, x, y, w, h)
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    private fun drawCenteredScaledAlpha(context: DrawContext, text: String, cx: Int, y: Int, scale: Float, color: Int, alpha: Int) {
        val m = context.matrices; m.push()
        m.translate(cx.toFloat(), y.toFloat(), 0f); m.scale(scale, scale, 1f)
        val tw = textRenderer.getWidth(text)
        val c = (alpha shl 24) or (color and 0xFFFFFF)
        context.drawTextWithShadow(textRenderer, text, -(tw / 2), 0, c)
        m.pop()
    }

    private fun drawCenteredScaled(context: DrawContext, text: String, cx: Int, y: Int, scale: Float, color: Int) =
        drawCenteredScaledAlpha(context, text, cx, y, scale, color, 255)

    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)
    
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (test.length <= maxChars) current = test
            else { if (current.isNotEmpty()) lines.add(current); current = word }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines.ifEmpty { listOf(text) }
    }
    
    /**
     * Обрезает текст по словам до указанной длины, добавляя "..." если текст обрезан.
     */
    private fun truncateTextByWords(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        
        val words = text.split(" ")
        var result = ""
        
        for (word in words) {
            val test = if (result.isEmpty()) word else "$result $word"
            if (test.length + 3 <= maxChars) {  // +3 для "..."
                result = test
            } else {
                break
            }
        }
        
        return if (result.isEmpty()) {
            // Если даже первое слово не влезает, обрезаем его
            text.substring(0, (maxChars - 3).coerceAtLeast(1)) + "..."
        } else {
            "$result..."
        }
    }
    
    // ── MOTIVATIONS OVERLAY ───────────────────────────────────────────────────
    
    private fun updateOverlayAnimation() {
        if (motivationsOverlayOpen) {
            overlayAnimTime = (overlayAnimTime + 0.01f).coerceAtMost(1f)
        } else {
            overlayAnimTime = (overlayAnimTime - 0.01f).coerceAtLeast(0f)  // Такая же скорость как открытие
        }
    }
    
    private fun updateSuggestOverlayAnimation() {
        if (suggestMotivationOverlayOpen) {
            suggestOverlayAnimTime = (suggestOverlayAnimTime + 0.015f).coerceAtMost(1f)
        } else {
            suggestOverlayAnimTime = (suggestOverlayAnimTime - 0.015f).coerceAtLeast(0f)
        }
    }
    
    private fun renderSuggestMotivationOverlay(context: DrawContext, mouseX: Int, mouseY: Int) {
        if (suggestOverlayAnimTime < 0.01f) return
        
        val W = width
        val H = height
        
        // Затемнение фона
        val m0 = context.matrices
        m0.push()
        m0.translate(0f, 0f, 400f)
        val dimAlpha = (suggestOverlayAnimTime * 0xAA).toInt()
        context.fill(0, 0, W, H, (dimAlpha shl 24) or 0x000000)
        m0.pop()
        
        // Размеры оверлея (меньше чем у мотиваций)
        val targetW = 300
        val targetH = 150
        val cx = W / 2
        val cy = H / 2
        
        // Анимация: простое раскрытие от центра
        val prog = easeOut(suggestOverlayAnimTime)
        val animW = (targetW * prog).toInt()
        val animH = (targetH * prog).toInt()
        
        val x0 = cx - animW / 2
        val y0 = cy - animH / 2
        val x1 = x0 + animW
        val y1 = y0 + animH
        
        // Рендер с высоким Z
        val m = context.matrices
        m.push()
        m.translate(0f, 0f, 500f)
        
        // Фон оверлея
        context.fill(x0, y0, x1, y1, 0xDD1a1a1a.toInt())
        // Рамка
        context.fill(x0, y0, x1, y0 + 1, 0xFF8a6a3a.toInt())
        context.fill(x0, y1 - 1, x1, y1, 0xFF8a6a3a.toInt())
        context.fill(x0, y0, x0 + 1, y1, 0xFF8a6a3a.toInt())
        context.fill(x1 - 1, y0, x1, y1, 0xFF8a6a3a.toInt())
        
        // Контент появляется когда анимация почти завершена
        if (prog > 0.6f) {
            val contentAlpha = ((prog - 0.6f) / 0.4f).coerceIn(0f, 1f)
            renderSuggestMotivationContent(context, x0, y0, animW, animH, contentAlpha, mouseX, mouseY)
        }
        
        m.pop()
    }
    
    private fun renderSuggestMotivationContent(context: DrawContext, x: Int, y: Int, w: Int, h: Int, alpha: Float, mouseX: Int, mouseY: Int) {
        val pad = 12
        
        // Заголовок
        val headerText = net.minecraft.client.resource.language.I18n.translate("bbf.personality.suggest_motivation")
        val headerScale = 0.75f
        val headerColor = ((alpha * 255).toInt() shl 24) or 0xD4AF37
        val hm = context.matrices
        hm.push()
        hm.translate((x + w / 2).toFloat(), (y + pad).toFloat(), 0f)
        hm.scale(headerScale, headerScale, 1f)
        val htw = textRenderer.getWidth(headerText)
        context.drawTextWithShadow(textRenderer, headerText, -(htw / 2), 0, headerColor)
        hm.pop()
        
        // Текстовое поле (простой прямоугольник с текстом)
        val fieldY = y + pad + 20
        val fieldH = 40
        val fieldX = x + pad
        val fieldW = w - pad * 2
        
        val fieldColor = 0xCC2a2a2a.toInt()
        val borderColor = 0xFF8a6a3a.toInt()
        context.fill(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH, fieldColor)
        context.fill(fieldX, fieldY, fieldX + fieldW, fieldY + 1, borderColor)
        context.fill(fieldX, fieldY + fieldH - 1, fieldX + fieldW, fieldY + fieldH, borderColor)
        context.fill(fieldX, fieldY, fieldX + 1, fieldY + fieldH, borderColor)
        context.fill(fieldX + fieldW - 1, fieldY, fieldX + fieldW, fieldY + fieldH, borderColor)
        
        // Текст в поле
        val textColor = ((alpha * 255).toInt() shl 24) or 0xFFFFFF
        val textScale = 0.7f
        val tm = context.matrices
        tm.push()
        tm.translate((fieldX + 4).toFloat(), (fieldY + 4).toFloat(), 0f)
        tm.scale(textScale, textScale, 1f)
        
        if (suggestMotivationText.isEmpty()) {
            val placeholder = net.minecraft.client.resource.language.I18n.translate("bbf.personality.motivation_placeholder")
            val placeholderColor = ((alpha * 128).toInt() shl 24) or 0x888888
            context.drawTextWithShadow(textRenderer, placeholder, 0, 0, placeholderColor)
        } else {
            // Рендер текста с курсором
            val beforeCursor = suggestMotivationText.substring(0, cursorPosition)
            val afterCursor = suggestMotivationText.substring(cursorPosition)
            
            context.drawTextWithShadow(textRenderer, beforeCursor, 0, 0, textColor)
            val cursorX = textRenderer.getWidth(beforeCursor)
            
            // Мигающий курсор
            if (cursorBlinkTime < 0.5f) {
                context.fill(cursorX, 0, cursorX + 1, textRenderer.fontHeight, textColor)
            }
            
            context.drawTextWithShadow(textRenderer, afterCursor, cursorX + 2, 0, textColor)
        }
        tm.pop()
        
        // Кнопки
        val buttonY = y + h - pad - 16
        val buttonW = 60
        val buttonH = 14
        val buttonSpacing = 8
        
        // Кнопка "Отправить"
        val submitX = x + w / 2 - buttonW - buttonSpacing / 2
        val submitHov = mouseX in submitX..(submitX + buttonW) && mouseY in buttonY..(buttonY + buttonH)
        context.fill(submitX, buttonY, submitX + buttonW, buttonY + buttonH, if (submitHov) 0xCC4a3a2a.toInt() else 0xCC1a1a1a.toInt())
        context.fill(submitX, buttonY, submitX + buttonW, buttonY + 1, borderColor)
        context.fill(submitX, buttonY + buttonH - 1, submitX + buttonW, buttonY + buttonH, borderColor)
        context.fill(submitX, buttonY, submitX + 1, buttonY + buttonH, borderColor)
        context.fill(submitX + buttonW - 1, buttonY, submitX + buttonW, buttonY + buttonH, borderColor)
        
        val submitText = net.minecraft.client.resource.language.I18n.translate("bbf.personality.submit")
        val sbm = context.matrices
        sbm.push()
        sbm.translate((submitX + buttonW / 2).toFloat(), (buttonY + 3).toFloat(), 0f)
        sbm.scale(0.7f, 0.7f, 1f)
        val stw = textRenderer.getWidth(submitText)
        val submitColor = ((alpha * 255).toInt() shl 24) or (if (submitHov) 0xFFD700 else 0xCCCCCC)
        context.drawTextWithShadow(textRenderer, submitText, -(stw / 2), 0, submitColor)
        sbm.pop()
        
        // Кнопка "Отмена"
        val cancelX = x + w / 2 + buttonSpacing / 2
        val cancelHov = mouseX in cancelX..(cancelX + buttonW) && mouseY in buttonY..(buttonY + buttonH)
        context.fill(cancelX, buttonY, cancelX + buttonW, buttonY + buttonH, if (cancelHov) 0xCC4a3a2a.toInt() else 0xCC1a1a1a.toInt())
        context.fill(cancelX, buttonY, cancelX + buttonW, buttonY + 1, borderColor)
        context.fill(cancelX, buttonY + buttonH - 1, cancelX + buttonW, buttonY + buttonH, borderColor)
        context.fill(cancelX, buttonY, cancelX + 1, buttonY + buttonH, borderColor)
        context.fill(cancelX + buttonW - 1, buttonY, cancelX + buttonW, buttonY + buttonH, borderColor)
        
        val cancelText = net.minecraft.client.resource.language.I18n.translate("bbf.gm.button.cancel")
        val cbm = context.matrices
        cbm.push()
        cbm.translate((cancelX + buttonW / 2).toFloat(), (buttonY + 3).toFloat(), 0f)
        cbm.scale(0.7f, 0.7f, 1f)
        val ctw = textRenderer.getWidth(cancelText)
        val cancelColor = ((alpha * 255).toInt() shl 24) or (if (cancelHov) 0xFFD700 else 0xCCCCCC)
        context.drawTextWithShadow(textRenderer, cancelText, -(ctw / 2), 0, cancelColor)
        cbm.pop()
    }
    
    private fun renderMotivationsOverlay(context: DrawContext, mouseX: Int, mouseY: Int) {
        if (overlayAnimTime < 0.01f) return
        
        val W = width
        val H = height
        val motivations = ClientPlayerData.motivations
        if (motivations.isEmpty()) return
        
        // Затемнение фона с высоким Z (fade-in/out вместе с оверлеем)
        val m0 = context.matrices
        m0.push()
        m0.translate(0f, 0f, 400f)  // Z=400 чтобы быть поверх модели и текста
        val dimAlpha = (overlayAnimTime * 0xAA).toInt()
        context.fill(0, 0, W, H, (dimAlpha shl 24) or 0x000000)
        m0.pop()
        
        // Размеры оверлея
        val targetW = (W * 0.5f).toInt().coerceAtMost(400)
        val targetH = (H * 0.7f).toInt().coerceAtMost(500)
        val cx = W / 2
        val cy = H / 2
        
        // Анимация: сначала ширина (0→0.4), затем подъём вверх (0.4→0.6), затем высота (0.6→1.0)
        val widthProg = ((overlayAnimTime - 0.0f) / 0.4f).coerceIn(0f, 1f)
        val riseProg = ((overlayAnimTime - 0.4f) / 0.2f).coerceIn(0f, 1f)
        val heightProg = ((overlayAnimTime - 0.6f) / 0.4f).coerceIn(0f, 1f)
        
        val animW = (targetW * easeOut(widthProg)).toInt()
        val animH = (targetH * easeOut(heightProg)).toInt()
        
        // Позиция: начинаем в центре, поднимаемся вверх
        val startY = cy
        val endY = cy - targetH / 2
        val currentY = (startY + (endY - startY) * easeOut(riseProg)).toInt()
        
        val x0 = cx - animW / 2
        val y0 = currentY
        val x1 = x0 + animW
        val y1 = y0 + animH
        
        // Рендер с высоким Z чтобы быть поверх всего
        val m = context.matrices
        m.push()
        m.translate(0f, 0f, 500f)  // Z=500 для рендера поверх затемнения
        
        // Фон оверлея
        context.fill(x0, y0, x1, y1, 0xDD1a1a1a.toInt())
        // Рамка
        context.fill(x0, y0, x1, y0 + 1, 0xFF8a6a3a.toInt())
        context.fill(x0, y1 - 1, x1, y1, 0xFF8a6a3a.toInt())
        context.fill(x0, y0, x0 + 1, y1, 0xFF8a6a3a.toInt())
        context.fill(x1 - 1, y0, x1, y1, 0xFF8a6a3a.toInt())
        
        // Контент появляется только когда высота раскрылась
        if (heightProg > 0.3f) {
            val contentAlpha = ((heightProg - 0.3f) / 0.7f).coerceIn(0f, 1f)
            renderOverlayContent(context, x0, y0, animW, animH, contentAlpha, mouseX, mouseY)
        }
        
        m.pop()
    }
    
    private fun renderOverlayContent(context: DrawContext, x: Int, y: Int, w: Int, h: Int, alpha: Float, mouseX: Int, mouseY: Int) {
        val motivations = ClientPlayerData.motivations
        val pad = 12
        val contentX = x + pad
        val contentY = y + pad
        val contentW = w - pad * 2
        val contentH = h - pad * 2
        
        // Заголовок "Мотивации" с hover
        val headerText = net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.motivations")
        val headerScale = 0.75f
        val headerW = (textRenderer.getWidth(headerText) * headerScale).toInt()
        val headerH = (textRenderer.fontHeight * headerScale).toInt()
        val headerX = (x + w / 2) - headerW / 2
        val headerY = contentY + 2
        motivationsHeaderHovered = mouseX in headerX..(headerX + headerW) && mouseY in headerY..(headerY + headerH)
        motivationsHeaderScale = lerp(motivationsHeaderScale, if (motivationsHeaderHovered) 1.15f else 1f, 0.15f)
        
        if (motivationsHeaderHovered) {
            pendingTooltip = net.minecraft.text.Text.translatable("bbf.personality.motivations.tooltip")
        }
        
        val headerColor = ((alpha * 255).toInt() shl 24) or 0xD4AF37
        val hm = context.matrices
        hm.push()
        hm.translate((x + w / 2).toFloat(), headerY.toFloat(), 0f)
        hm.scale(headerScale * motivationsHeaderScale, headerScale * motivationsHeaderScale, 1f)
        val htw = textRenderer.getWidth(headerText)
        context.drawTextWithShadow(textRenderer, headerText, -(htw / 2), 0, headerColor)
        hm.pop()
        
        val textScale = 0.7f
        val lineH = (textRenderer.fontHeight * textScale + 4).toInt()
        val dividerH = 8
        
        val visibleCount = motivations.size.coerceAtMost(10)
        val maxScroll = (motivations.size - visibleCount).coerceAtLeast(0)
        overlayScroll = overlayScroll.coerceIn(0, maxScroll)
        
        // Рендер мотиваций с typewriter эффектом (начинаем после заголовка)
        val headerTotalH = (textRenderer.fontHeight * headerScale + 10).toInt()
        var curY = contentY + headerTotalH
        motivations.drop(overlayScroll).take(visibleCount).forEachIndexed { idx, mot ->
            val globalIdx = idx + overlayScroll
            
            // Typewriter прогресс для этой мотивации
            val typewriterProg = motivationTypewriterProgress.getOrDefault(globalIdx, 0f)
            val dividerProg = motivationDividerProgress.getOrDefault(globalIdx, 0f)
            
            // Обновляем прогресс: каждая мотивация начинает печататься после предыдущей
            val prevDividerDone = if (globalIdx == 0) 1f else motivationDividerProgress.getOrDefault(globalIdx - 1, 0f)
            if (prevDividerDone >= 0.99f) {
                motivationTypewriterProgress[globalIdx] = (typewriterProg + 0.02f).coerceAtMost(1f)  // Медленнее: было 0.03
                
                // Divider появляется после завершения печати
                if (typewriterProg >= 0.99f) {
                    motivationDividerProgress[globalIdx] = (dividerProg + 0.03f).coerceAtMost(1f)  // Медленнее: было 0.05
                }
            }
            
            // Рендер текста с typewriter эффектом
            // Добавляем кавычки к тексту мотивации
            val quotedText = "\"${mot.text}\""
            val lines = wrapMotivationText(quotedText, 35)
            
            // Вычисляем общее количество символов во всех строках
            val totalChars = lines.sumOf { it.length }
            val visibleChars = (totalChars * typewriterProg).toInt()
            
            // Отслеживаем сколько символов уже отрисовано
            var charsRendered = 0
            
            lines.forEach { line ->
                val lineStartChar = charsRendered
                val lineEndChar = charsRendered + line.length
                
                if (visibleChars > lineStartChar) {
                    // Сколько символов этой строки нужно показать
                    val visibleInLine = (visibleChars - lineStartChar).coerceAtMost(line.length)
                    val displayText = line.substring(0, visibleInLine)
                    
                    val m = context.matrices
                    m.push()
                    m.translate(contentX.toFloat() + contentW / 2f, curY.toFloat(), 0f)
                    m.scale(textScale, textScale, 1f)
                    val tw = textRenderer.getWidth(displayText)
                    // Pending мотивации (не одобренные ГМом) - темно-серые
                    val textColor = if (mot.isActive) 0xFFFFFF else 0x666666
                    val color = ((alpha * 255).toInt() shl 24) or textColor
                    context.drawTextWithShadow(textRenderer, displayText, -(tw / 2), 0, color)
                    m.pop()
                    
                    curY += lineH
                }
                
                charsRendered += line.length
            }
            
            // Divider после текста (только если текст полностью напечатан)
            if (typewriterProg >= 0.99f && dividerProg > 0.01f) {
                curY += 2
                val dividerAlpha = (alpha * dividerProg * 0.8f * 255).toInt()
                drawFadeDividerAnimated(context, x + w / 2, curY, (contentW * 0.8f).toInt(), dividerProg, dividerAlpha)
                curY += dividerH
            }
        }
        
        // Кнопка закрытия (X) в правом верхнем углу
        val closeSize = 16
        val closeX = x + w - closeSize - 6
        val closeY = y + 6
        val closeHovered = mouseX in closeX..(closeX + closeSize) && mouseY in closeY..(closeY + closeSize)
        
        val closeBg = if (closeHovered) 0xCC8a3a3a.toInt() else 0xCC3a1a1a.toInt()
        val closeFg = if (closeHovered) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt()
        
        context.fill(closeX, closeY, closeX + closeSize, closeY + closeSize, closeBg)
        context.fill(closeX, closeY, closeX + closeSize, closeY + 1, 0xFF8a6a3a.toInt())
        context.fill(closeX, closeY + closeSize - 1, closeX + closeSize, closeY + closeSize, 0xFF8a6a3a.toInt())
        context.fill(closeX, closeY, closeX + 1, closeY + closeSize, 0xFF8a6a3a.toInt())
        context.fill(closeX + closeSize - 1, closeY, closeX + closeSize, closeY + closeSize, 0xFF8a6a3a.toInt())
        
        // Рисуем X
        val xm = context.matrices
        xm.push()
        xm.translate((closeX + closeSize / 2).toFloat(), (closeY + closeSize / 2 - 1).toFloat(), 0f)
        xm.scale(0.8f, 0.8f, 1f)
        val xText = "✕"
        val xtw = textRenderer.getWidth(xText)
        context.drawTextWithShadow(textRenderer, xText, -(xtw / 2), 0, closeFg)
        xm.pop()
        
        // Scrollbar если нужен
        if (motivations.size > visibleCount) {
            val sbX = x + w - 6
            val sbH = contentH
            val thumbH = ((visibleCount.toFloat() / motivations.size) * sbH).toInt().coerceAtLeast(10)
            val thumbY = contentY + ((overlayScroll.toFloat() / maxScroll) * (sbH - thumbH)).toInt()
            context.fill(sbX, contentY, sbX + 2, contentY + sbH, ((alpha * 0.3f * 255).toInt() shl 24) or 0xFFFFFF)
            context.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, ((alpha * 255).toInt() shl 24) or 0x8a6a3a)
        }
    }
    
    private fun drawFadeDividerAnimated(context: DrawContext, cx: Int, y: Int, totalW: Int, progress: Float, maxAlpha: Int = 0xAA) {
        val halfW = (totalW / 2 * progress).toInt()
        if (halfW <= 0) return
        drawFadeDividerAlpha(context, cx, y, halfW * 2, maxAlpha)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        // Scroll в оверлее мотиваций
        if (motivationsOverlayOpen) {
            val delta = if (amount > 0) -1 else 1
            overlayScroll = (overlayScroll + delta).coerceAtLeast(0)
            return true
        }
        
        val W = width; val panelW = (W * 0.27f).toInt(); val pad = 8; val sideMargin = 18
        val mx = mouseX.toInt()
        val delta = if (amount > 0) -1 else 1
        if (mx < pad + sideMargin + panelW) { idealScroll = (idealScroll + delta).coerceAtLeast(0); return true }
        if (mx > W - panelW - pad - sideMargin) { flawScroll = (flawScroll + delta).coerceAtLeast(0); return true }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val W = width; val H = height
        
        // Обработка кликов в оверлее мировоззрения
        if (alignmentOverlayOpen && button == 0) {
            val prog = easeOut(alignmentOverlayAnimTime)
            if (prog > 0.6f) {
                val targetW = 200; val targetH = 180
                val ocx = W / 2; val ocy = H / 2
                val x0 = ocx - (targetW * prog).toInt() / 2
                val y0 = ocy - (targetH * prog).toInt() / 2
                val x1 = x0 + (targetW * prog).toInt()
                val y1 = y0 + (targetH * prog).toInt()
                if (mouseX.toInt() !in x0..x1 || mouseY.toInt() !in y0..y1) {
                    alignmentOverlayOpen = false
                    return true
                }
            }
            return true
        }
        
        // Обработка кликов в оверлее предложения мотивации
        if (suggestMotivationOverlayOpen && button == 0) {
            // Проверяем что анимация завершена (контент виден)
            val prog = easeOut(suggestOverlayAnimTime)
            if (prog < 0.6f) {
                return true  // Поглощаем клики во время анимации
            }
            
            val targetW = 300
            val targetH = 150
            val cx = W / 2
            val cy = H / 2
            val x0 = cx - targetW / 2
            val y0 = cy - targetH / 2
            val x1 = x0 + targetW
            val y1 = y0 + targetH
            
            val pad = 12
            val buttonY = y0 + targetH - pad - 16
            val buttonW = 60
            val buttonH = 14
            val buttonSpacing = 8
            
            // Кнопка "Отправить" - проверяем первой
            val submitX = x0 + targetW / 2 - buttonW - buttonSpacing / 2
            if (mouseX.toInt() in submitX..(submitX + buttonW) && mouseY.toInt() in buttonY..(buttonY + buttonH)) {
                if (suggestMotivationText.isNotEmpty()) {
                    // Отправить мотивацию на сервер
                    val buf = PacketByteBufs.create()
                    buf.writeString(suggestMotivationText)
                    ClientPlayNetworking.send(BbfPackets.PLAYER_PROPOSE_MOTIVATION, buf)
                    
                    suggestMotivationOverlayOpen = false
                    suggestMotivationText = ""
                    cursorPosition = 0
                }
                return true
            }
            
            // Кнопка "Отмена" - проверяем второй
            val cancelX = x0 + targetW / 2 + buttonSpacing / 2
            if (mouseX.toInt() in cancelX..(cancelX + buttonW) && mouseY.toInt() in buttonY..(buttonY + buttonH)) {
                suggestMotivationOverlayOpen = false
                suggestMotivationText = ""
                cursorPosition = 0
                return true
            }
            
            // Клик вне оверлея закрывает его
            if (mouseX.toInt() !in x0..x1 || mouseY.toInt() !in y0..y1) {
                suggestMotivationOverlayOpen = false
                suggestMotivationText = ""
                cursorPosition = 0
                return true
            }
            
            // Текстовое поле - если клик внутри оверлея но не по кнопкам
            val fieldY = y0 + pad + 20
            val fieldH = 40
            val fieldX = x0 + pad
            val fieldW = targetW - pad * 2
            
            // Клик по текстовому полю - устанавливаем курсор
            if (mouseX.toInt() in fieldX..(fieldX + fieldW) && mouseY.toInt() in fieldY..(fieldY + fieldH)) {
                // Вычисляем позицию курсора по клику
                val textScale = 0.7f
                val relativeX = ((mouseX.toInt() - fieldX - 4) / textScale).toInt()
                
                // Находим ближайшую позицию в тексте
                var bestPos = 0
                var bestDist = Int.MAX_VALUE
                for (i in 0..suggestMotivationText.length) {
                    val substr = suggestMotivationText.substring(0, i)
                    val w = textRenderer.getWidth(substr)
                    val dist = kotlin.math.abs(w - relativeX)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestPos = i
                    }
                }
                cursorPosition = bestPos
                return true
            }
            
            // Любой другой клик внутри оверлея - просто поглощаем
            return true
        }
        
        // Обработка кликов в оверлее мотиваций
        if (motivationsOverlayOpen && button == 0) {
            val targetW = (W * 0.5f).toInt().coerceAtMost(400)
            val targetH = (H * 0.7f).toInt().coerceAtMost(500)
            val cx = W / 2
            val cy = H / 2
            val x0 = cx - targetW / 2
            val y0 = cy - targetH / 2
            val x1 = x0 + targetW
            val y1 = y0 + targetH
            
            // Кнопка закрытия
            val closeSize = 16
            val closeX = x0 + targetW - closeSize - 6
            val closeY = y0 + 6
            if (mouseX.toInt() in closeX..(closeX + closeSize) && mouseY.toInt() in closeY..(closeY + closeSize)) {
                motivationsOverlayOpen = false
                return true
            }
            
            // Клик вне оверлея закрывает его
            if (mouseX.toInt() !in x0..x1 || mouseY.toInt() !in y0..y1) {
                motivationsOverlayOpen = false
                return true
            }
            
            return true  // Поглощаем все клики внутри оверлея
        }
        
        // Открытие оверлея при клике по модели или мотивации
        if (button == 0 && !motivationsOverlayOpen && !suggestMotivationOverlayOpen) {
            val cx = W / 2
            val cy = H / 2
            val modelX = cx - 65..cx + 65
            val modelY = cy - 60..cy + 100  // Уменьшена нижняя граница чтобы не перекрывать кнопку
            
            if (mouseX.toInt() in modelX && mouseY.toInt() in modelY) {
                motivationsOverlayOpen = true
                overlayAnimTime = 0f
                motivationTypewriterProgress.clear()
                motivationDividerProgress.clear()
                return true
            }
        }
        
        // Back button (верх справа)
        val bw = 70; val bh = 12
        val bx = W - bw - 8; val by = 8
        if (mouseX.toInt() in bx..(bx + bw) && mouseY.toInt() in by..(by + bh)) {
            client?.setScreen(parent); return true
        }
        
        // Клик на мировоззрение — открываем/закрываем оверлей
        if (button == 0 && alignProgress() > 0.9f && !motivationsOverlayOpen && !suggestMotivationOverlayOpen) {
            val alignText = ClientPlayerData.alignmentText.ifEmpty { "?" }
            val alignBaseScale = 0.9f * alignmentScale
            val alignW = (textRenderer.getWidth(alignText) * alignBaseScale).toInt()
            val alignH = (textRenderer.fontHeight * alignBaseScale).toInt()
            val alignX = W / 2 - alignW / 2
            val alignY = 8 + 11
            if (mouseX.toInt() in alignX..(alignX + alignW) && mouseY.toInt() in alignY..(alignY + alignH)) {
                alignmentOverlayOpen = !alignmentOverlayOpen
                return true
            }
        }
        
        // Suggest motivation button (низ по центру)
        if (button == 0 && !motivationsOverlayOpen && !suggestMotivationOverlayOpen) {
            val suggestText = net.minecraft.client.resource.language.I18n.translate("bbf.personality.suggest_motivation")
            val suggestScale = 0.75f
            val sw = (textRenderer.getWidth(suggestText) * suggestScale + 16).toInt(); val sh = 12
            val sx = W / 2 - sw / 2; val sy = H - sh - 6
            if (mouseX.toInt() in sx..(sx + sw) && mouseY.toInt() in sy..(sy + sh)) {
                suggestMotivationOverlayOpen = true
                suggestOverlayAnimTime = 0f
                suggestMotivationText = ""
                cursorPosition = 0
                return true
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (suggestMotivationOverlayOpen) {
            when (keyCode) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    suggestMotivationOverlayOpen = false
                    suggestMotivationText = ""
                    cursorPosition = 0
                    return true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (suggestMotivationText.isNotEmpty()) {
                        val buf = PacketByteBufs.create()
                        buf.writeString(suggestMotivationText)
                        ClientPlayNetworking.send(BbfPackets.PLAYER_PROPOSE_MOTIVATION, buf)
                        
                        suggestMotivationOverlayOpen = false
                        suggestMotivationText = ""
                        cursorPosition = 0
                    }
                    return true
                }
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (cursorPosition > 0) {
                        suggestMotivationText = suggestMotivationText.substring(0, cursorPosition - 1) + 
                                               suggestMotivationText.substring(cursorPosition)
                        cursorPosition--
                    }
                    return true
                }
                GLFW.GLFW_KEY_DELETE -> {
                    if (cursorPosition < suggestMotivationText.length) {
                        suggestMotivationText = suggestMotivationText.substring(0, cursorPosition) + 
                                               suggestMotivationText.substring(cursorPosition + 1)
                    }
                    return true
                }
                GLFW.GLFW_KEY_LEFT -> {
                    if (cursorPosition > 0) cursorPosition--
                    return true
                }
                GLFW.GLFW_KEY_RIGHT -> {
                    if (cursorPosition < suggestMotivationText.length) cursorPosition++
                    return true
                }
                GLFW.GLFW_KEY_HOME -> {
                    cursorPosition = 0
                    return true
                }
                GLFW.GLFW_KEY_END -> {
                    cursorPosition = suggestMotivationText.length
                    return true
                }
            }
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (suggestMotivationOverlayOpen) {
            if (chr.code >= 32 && suggestMotivationText.length < 200) {  // Ограничение 200 символов
                suggestMotivationText = suggestMotivationText.substring(0, cursorPosition) + chr + 
                                       suggestMotivationText.substring(cursorPosition)
                cursorPosition++
            }
            return true
        }
        return super.charTyped(chr, modifiers)
    }

    // ── TOOLTIP ───────────────────────────────────────────────────────────────
    
    private fun updateTooltipAnim() {
        val currentKey = pendingTooltip?.string ?: ""
        if (currentKey != lastTooltipKey) {
            // Новый тултип — сбрасываем анимацию
            if (currentKey.isNotEmpty()) {
                tooltipAnimW = 0f
                tooltipAnimH = 0f
                tooltipWidthTimer = 0f
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
    
    private fun drawSmallTooltip(context: DrawContext, text: net.minecraft.text.Text, mouseX: Int, mouseY: Int) {
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
        matrices.translate(0f, 0f, 600f)  // Z=600 чтобы быть поверх всего
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
                        context.drawTextWithShadow(textRenderer, net.minecraft.text.Text.literal(line), x0 + pad, lineY, color)
                    }
                }
            }
        }

        matrices.pop()
    }

    override fun shouldPause() = false
}
