package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.client.state.ClientPlayerData
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

    // ── Hover состояния ───────────────────────────────────────────────────────
    private var hoveredIdealIdx: Int? = null
    private var hoveredFlawIdx: Int? = null
    private var hoveredMotivationIdx: Int? = null
    private var modelHovered = false
    
    // Lerp-анимации для hover
    private val idealHoverScales = mutableMapOf<Int, Float>()      // текст scale
    private val idealIconScales = mutableMapOf<Int, Float>()       // иконка scale
    private val idealTextOffsets = mutableMapOf<Int, Float>()      // смещение текста
    private val idealRowOffsets = mutableMapOf<Int, Float>()       // смещение строки вниз
    
    private val flawHoverScales = mutableMapOf<Int, Float>()
    private val flawIconScales = mutableMapOf<Int, Float>()
    private val flawTextOffsets = mutableMapOf<Int, Float>()
    private val flawRowOffsets = mutableMapOf<Int, Float>()
    
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

    override fun init() {
        openTime = 0f
        hoveredIdealIdx = null
        hoveredFlawIdx = null
        hoveredMotivationIdx = null
        modelHovered = false
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
            val colors = listOf(0xD4AF37, 0xC8A96E, 0xFFD700, 0xB8956A, 0xE8C97A)
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
        time += delta * 0.016f
        openTime = (openTime + delta * 0.018f).coerceAtMost(1f)

        val W = width; val H = height
        val panelW = (W * 0.27f).toInt()
        val pad = 8
        val sideMargin = 18
        val panelStartY = H / 3
        val cx = W / 2; val cy = H / 2

        // ── Обновление hover-анимаций ─────────────────────────────────────────
        // Модель и мотивации - зона по центру, умеренная ширина
        val modelX = cx - 65..cx + 65
        val modelY = cy - 60..cy + 160
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
            // Без hover: мотивации "за" рендерятся с depth test (могут перекрываться моделью)
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

            // Мотивации "перед" всегда поверх модели
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
        renderAlignmentTop(context, W, pad)

        // ── PANELS ───────────────────────────────────────────────────────────
        renderIdealsPanel(context, mouseX, mouseY, pad + sideMargin, panelStartY, panelW, H - panelStartY - 20)
        renderFlawsPanel(context, mouseX, mouseY, W - panelW - pad - sideMargin, panelStartY, panelW, H - panelStartY - 20)

        // ── BACK BUTTON ───────────────────────────────────────────────────────
        val backText = net.minecraft.client.resource.language.I18n.translate("bbf.gm.button.back")
        val bw = 60; val bh = 12
        val bx = W / 2 - bw / 2; val by = H - bh - 6
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

        // ── MOTIVATIONS OVERLAY ───────────────────────────────────────────────
        if (motivationsOverlayOpen) {
            renderMotivationsOverlay(context, mouseX, mouseY)
        }
        updateOverlayAnimation()

        super.render(context, mouseX, mouseY, delta)
    }

    // ── МОТИВАЦИИ: орбитальное движение ──────────────────────────────────────
    private fun renderMotivations(context: DrawContext, centerX: Float, centerY: Float, behindOnly: Boolean, forceOnTop: Boolean = false) {
        val introAlpha = easeOut(openTime)
        
        if (forceOnTop) {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest()
        }
        
        particles.forEachIndexed { idx, p ->
            val isBehind = p.zLayer < 0f
            if (isBehind != behindOnly) return@forEachIndexed

            val isHovered = hoveredMotivationIdx == idx
            val hoverScale = motivationScales.getOrDefault(idx, 1f)
            val hoverAlpha = motivationAlphas.getOrDefault(idx, 1f)

            // Если наведена — используем замороженный угол, иначе обычная орбита
            val phase = motivationResumePhases.getOrDefault(idx, p.orbitPhase)
            val angle = motivationFrozenAngles[idx] ?: (time * p.orbitSpeed + phase)
            
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
    private fun renderAlignmentTop(context: DrawContext, W: Int, pad: Int) {
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
        drawCenteredScaledAlpha(context, alignText, cx, pad + 11, 0.9f, 0xD4AF37, alpha)
        m.pop()

        // Divider под мировоззрением — такая же анимация раскрытия от центра к краям
        val divProg = ((prog - 0.7f) / 0.3f).coerceIn(0f, 1f)
        if (divProg > 0f) {
            drawFadeDividerAnimated(context, cx, pad + 22, 120, divProg)
        }
    }

    // ── ПАНЕЛЬ УБЕЖДЕНИЙ ──────────────────────────────────────────────────────
    private fun renderIdealsPanel(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val ideals = ClientPlayerData.ideals
        if (ideals.isEmpty()) return

        val headerProg = headerProgress()
        val headerCx = x + w / 2

        // Заголовок въезжает слева
        if (headerProg > 0f) {
            val offX = ((1f - headerProg) * -(x + w + 20)).toInt()
            val alpha = (headerProg * 255).toInt()
            val m = context.matrices; m.push()
            m.translate(offX.toFloat(), 0f, 0f)
            drawCenteredScaledAlpha(context, net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.ideals"),
                headerCx, y + 4, 0.6f, 0xD4AF37, alpha)
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

        data class Line(val text: String, val color: Int, val isFirst: Boolean, val idealIdx: Int)
        val lines = mutableListOf<Line>()
        ideals.forEachIndexed { idealIdx, ideal ->
            val color = if (ideal.isCompatible) 0xCCCCCC else 0xFF7777
            wrapText(ideal.text, maxChars).forEachIndexed { i, line ->
                lines.add(Line(line, color, i == 0, idealIdx))
            }
            lines.add(Line("", 0, false, -1))
        }

        val visibleCount = (listH / lineH).coerceAtLeast(1)
        val maxScroll = (lines.size - visibleCount).coerceAtLeast(0)
        idealScroll = idealScroll.coerceIn(0, maxScroll)

        // Hit-test для hover
        var newHoveredIdeal: Int? = null
        lines.drop(idealScroll).take(visibleCount).forEachIndexed { i, line ->
            if (line.text.isEmpty() || !line.isFirst) return@forEachIndexed
            val ly = curY + i * lineH
            val hitH = lineH + 2
            if (mouseX in x..(x + w) && mouseY in ly..(ly + hitH)) {
                newHoveredIdeal = line.idealIdx
            }
        }
        hoveredIdealIdx = newHoveredIdeal

        // Обновляем lerp-анимации
        ideals.forEachIndexed { idx, _ ->
            val isHovered = hoveredIdealIdx == idx
            idealHoverScales[idx] = lerp(idealHoverScales.getOrDefault(idx, 1f), if (isHovered) 1.1f else 1f, 0.15f)
            idealIconScales[idx] = lerp(idealIconScales.getOrDefault(idx, 1f), if (isHovered) 1.2f else 1f, 0.12f)
            idealTextOffsets[idx] = lerp(idealTextOffsets.getOrDefault(idx, 0f), if (isHovered) 12f else 0f, 0.15f)
            // Ротация: при hover крутится медленно, при уходе нормализуем и плавно возвращаем к 0
            val currentRot = idealIconRotations.getOrDefault(idx, 0f)
            if (isHovered) {
                idealIconRotations[idx] = currentRot + 0.7f  // ~42°/сек при 60fps
            } else {
                // Нормализуем в (-180, 180] чтобы lerp шёл по короткому пути
                val normalized = ((currentRot % 360f) + 360f) % 360f
                val wrapped = if (normalized > 180f) normalized - 360f else normalized
                idealIconRotations[idx] = lerp(wrapped, 0f, 0.12f)
            }
        }

        // Рассчитываем смещения строк (hover раздвигает)
        var accumulatedOffset = 0f
        lines.forEachIndexed { i, line ->
            if (line.isFirst && line.idealIdx >= 0) {
                val hoverScale = idealHoverScales.getOrDefault(line.idealIdx, 1f)
                val extraSpace = (lineH * (hoverScale - 1f)).coerceAtLeast(0f)
                idealRowOffsets[i] = accumulatedOffset
                accumulatedOffset += extraSpace
            } else {
                idealRowOffsets[i] = accumulatedOffset
            }
        }

        // Рендер строк
        lines.drop(idealScroll).take(visibleCount).forEachIndexed { i, line ->
            if (line.text.isEmpty()) return@forEachIndexed
            val baseY = curY + i * lineH
            val rowOffset = idealRowOffsets.getOrDefault(i + idealScroll, 0f)
            val ly = baseY + rowOffset.toInt()
            val lp = lineProgress(i)
            if (lp <= 0f) return@forEachIndexed

            val isHovered = line.isFirst && hoveredIdealIdx == line.idealIdx
            val hoverScale = if (line.isFirst) idealHoverScales.getOrDefault(line.idealIdx, 1f) else 1f
            // Применяем textOffset ко всем строкам этого ideal, а не только к первой
            val textOffset = if (line.idealIdx >= 0) idealTextOffsets.getOrDefault(line.idealIdx, 0f) else 0f

            // Текст выезжает слева направо
            val textOffX = ((1f - lp) * -(w + 30)).toInt()
            val textAlpha = (lp * 255).toInt()
            val iconSize = 5
            val iconY = ly + (lineH * textScale / 2 - iconSize / 2).toInt()
            val textX = x + iconSize + 2 + textOffset.toInt()

            val m = context.matrices; m.push()
            m.translate(textOffX.toFloat(), 0f, 0f)
            val tm = context.matrices; tm.push()
            tm.translate(textX.toFloat(), ly.toFloat(), 0f)
            val finalScale = textScale * hoverScale
            tm.scale(finalScale, finalScale, 1f)
            val tc = (textAlpha shl 24) or (line.color and 0xFFFFFF)
            context.drawTextWithShadow(textRenderer, line.text, 0, 0, tc)
            tm.pop()
            m.pop()

            // Иконка появляется fade-in после текста
            if (line.isFirst) {
                val ip = iconProgress(i)
                if (ip > 0f) {
                    val iconAlpha = (ip * 255).toInt().coerceIn(0, 255)
                    val iconScale = idealIconScales.getOrDefault(line.idealIdx, 1f)
                    val iconRotation = idealIconRotations.getOrDefault(line.idealIdx, 0f)
                    
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

        if (lines.size > visibleCount && maxScroll > 0) {
            val sbX = x + w - 3
            val sbH = listH - 4
            val thumbH = ((visibleCount.toFloat() / lines.size) * sbH).toInt().coerceAtLeast(6)
            val thumbY = curY + 2 + ((idealScroll.toFloat() / maxScroll) * (sbH - thumbH)).toInt()
            context.fill(sbX, curY + 2, sbX + 2, curY + sbH, 0x44FFFFFF.toInt())
            context.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF8a6a3a.toInt())
        }
    }

    // ── ПАНЕЛЬ СЛАБОСТЕЙ ──────────────────────────────────────────────────────
    private fun renderFlawsPanel(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val flaws = ClientPlayerData.flaws
        if (flaws.isEmpty()) return

        val headerProg = headerProgress()
        val headerCx = x + w / 2
        val W = width

        // Заголовок въезжает справа
        if (headerProg > 0f) {
            val offX = ((1f - headerProg) * (W - x + 20)).toInt()
            val alpha = (headerProg * 255).toInt()
            val m = context.matrices; m.push()
            m.translate(offX.toFloat(), 0f, 0f)
            drawCenteredScaledAlpha(context, net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.flaws"),
                headerCx, y + 4, 0.6f, 0xD4AF37, alpha)
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

        data class Line(val text: String, val isFirst: Boolean, val flawIdx: Int)
        val lines = mutableListOf<Line>()
        flaws.forEachIndexed { flawIdx, flaw ->
            wrapText(flaw.text, maxChars).forEachIndexed { i, line ->
                lines.add(Line(line, i == 0, flawIdx))
            }
            lines.add(Line("", false, -1))
        }

        val visibleCount = (listH / lineH).coerceAtLeast(1)
        val maxScroll = (lines.size - visibleCount).coerceAtLeast(0)
        flawScroll = flawScroll.coerceIn(0, maxScroll)

        // Hit-test для hover
        var newHoveredFlaw: Int? = null
        lines.drop(flawScroll).take(visibleCount).forEachIndexed { i, line ->
            if (line.text.isEmpty() || !line.isFirst) return@forEachIndexed
            val ly = curY + i * lineH
            val hitH = lineH + 2
            if (mouseX in x..(x + w) && mouseY in ly..(ly + hitH)) {
                newHoveredFlaw = line.flawIdx
            }
        }
        hoveredFlawIdx = newHoveredFlaw

        // Обновляем lerp-анимации
        flaws.forEachIndexed { idx, _ ->
            val isHovered = hoveredFlawIdx == idx
            flawHoverScales[idx] = lerp(flawHoverScales.getOrDefault(idx, 1f), if (isHovered) 1.1f else 1f, 0.15f)
            flawIconScales[idx] = lerp(flawIconScales.getOrDefault(idx, 1f), if (isHovered) 1.2f else 1f, 0.12f)
            flawTextOffsets[idx] = lerp(flawTextOffsets.getOrDefault(idx, 0f), if (isHovered) -12f else 0f, 0.15f)
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

        // Рассчитываем смещения строк
        var accumulatedOffset = 0f
        lines.forEachIndexed { i, line ->
            if (line.isFirst && line.flawIdx >= 0) {
                val hoverScale = flawHoverScales.getOrDefault(line.flawIdx, 1f)
                val extraSpace = (lineH * (hoverScale - 1f)).coerceAtLeast(0f)
                flawRowOffsets[i] = accumulatedOffset
                accumulatedOffset += extraSpace
            } else {
                flawRowOffsets[i] = accumulatedOffset
            }
        }

        // Рендер строк
        lines.drop(flawScroll).take(visibleCount).forEachIndexed { i, line ->
            if (line.text.isEmpty()) return@forEachIndexed
            val baseY = curY + i * lineH
            val rowOffset = flawRowOffsets.getOrDefault(i + flawScroll, 0f)
            val ly = baseY + rowOffset.toInt()
            val lp = lineProgress(i)
            if (lp <= 0f) return@forEachIndexed

            val isHovered = line.isFirst && hoveredFlawIdx == line.flawIdx
            val hoverScale = if (line.isFirst) flawHoverScales.getOrDefault(line.flawIdx, 1f) else 1f
            // Применяем textOffset ко всем строкам этого flaw, а не только к первой
            val textOffset = if (line.flawIdx >= 0) flawTextOffsets.getOrDefault(line.flawIdx, 0f) else 0f

            // Текст выезжает справа налево
            val textOffX = ((1f - lp) * (W - x + 30)).toInt()
            val textAlpha = (lp * 255).toInt()
            val iconSize = 5
            val iconY = ly + (lineH * textScale / 2 - iconSize / 2).toInt()
            val rightEdge = x + w
            val textRightEdge = rightEdge - iconSize - 2 + textOffset.toInt()

            val m = context.matrices; m.push()
            m.translate(textOffX.toFloat(), 0f, 0f)
            val tm = context.matrices; tm.push()
            tm.translate(textRightEdge.toFloat(), ly.toFloat(), 0f)
            val finalScale = textScale * hoverScale
            tm.scale(finalScale, finalScale, 1f)
            val tw = textRenderer.getWidth(line.text)
            val tc = (textAlpha shl 24) or 0xCCCCCC
            context.drawTextWithShadow(textRenderer, line.text, -tw, 0, tc)
            tm.pop()
            m.pop()

            // Иконка fade-in
            if (line.isFirst) {
                val ip = iconProgress(i)
                if (ip > 0f) {
                    val iconAlpha = (ip * 255).toInt().coerceIn(0, 255)
                    val iconScale = flawIconScales.getOrDefault(line.flawIdx, 1f)
                    val iconRotation = flawIconRotations.getOrDefault(line.flawIdx, 0f)
                    
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

        if (lines.size > visibleCount && maxScroll > 0) {
            val sbX = x
            val sbH = listH - 4
            val thumbH = ((visibleCount.toFloat() / lines.size) * sbH).toInt().coerceAtLeast(6)
            val thumbY = curY + 2 + ((flawScroll.toFloat() / maxScroll) * (sbH - thumbH)).toInt()
            context.fill(sbX, curY + 2, sbX + 2, curY + sbH, 0x44FFFFFF.toInt())
            context.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF8a6a3a.toInt())
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
    
    // ── MOTIVATIONS OVERLAY ───────────────────────────────────────────────────
    
    private fun updateOverlayAnimation() {
        if (motivationsOverlayOpen) {
            overlayAnimTime = (overlayAnimTime + 0.025f).coerceAtMost(1f)
        } else {
            overlayAnimTime = (overlayAnimTime - 0.04f).coerceAtLeast(0f)
        }
    }
    
    private fun renderMotivationsOverlay(context: DrawContext, mouseX: Int, mouseY: Int) {
        if (overlayAnimTime < 0.01f) return
        
        val W = width
        val H = height
        val motivations = ClientPlayerData.motivations
        if (motivations.isEmpty()) return
        
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
            renderOverlayContent(context, x0, y0, animW, animH, contentAlpha)
        }
    }
    
    private fun renderOverlayContent(context: DrawContext, x: Int, y: Int, w: Int, h: Int, alpha: Float) {
        val motivations = ClientPlayerData.motivations
        val pad = 12
        val contentX = x + pad
        val contentY = y + pad
        val contentW = w - pad * 2
        val contentH = h - pad * 2
        
        val textScale = 0.7f
        val lineH = (textRenderer.fontHeight * textScale + 4).toInt()
        val dividerH = 8
        
        val visibleCount = motivations.size.coerceAtMost(10)
        val maxScroll = (motivations.size - visibleCount).coerceAtLeast(0)
        overlayScroll = overlayScroll.coerceIn(0, maxScroll)
        
        // Рендер мотиваций с typewriter эффектом
        var curY = contentY
        motivations.drop(overlayScroll).take(visibleCount).forEachIndexed { idx, mot ->
            val globalIdx = idx + overlayScroll
            
            // Typewriter прогресс для этой мотивации
            val typewriterProg = motivationTypewriterProgress.getOrDefault(globalIdx, 0f)
            val dividerProg = motivationDividerProgress.getOrDefault(globalIdx, 0f)
            
            // Обновляем прогресс: каждая мотивация начинает печататься после предыдущей
            val prevDividerDone = if (globalIdx == 0) 1f else motivationDividerProgress.getOrDefault(globalIdx - 1, 0f)
            if (prevDividerDone >= 0.99f) {
                motivationTypewriterProgress[globalIdx] = (typewriterProg + 0.03f).coerceAtMost(1f)
                
                // Divider появляется после завершения печати
                if (typewriterProg >= 0.99f) {
                    motivationDividerProgress[globalIdx] = (dividerProg + 0.05f).coerceAtMost(1f)
                }
            }
            
            // Рендер текста с typewriter эффектом
            val lines = wrapMotivationText(mot.text, 35)
            
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
                    val color = ((alpha * 255).toInt() shl 24) or 0xD4AF37
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
        
        // Закрытие оверлея при клике вне его
        if (motivationsOverlayOpen && button == 0) {
            val targetW = (W * 0.5f).toInt().coerceAtMost(400)
            val targetH = (H * 0.7f).toInt().coerceAtMost(500)
            val cx = W / 2
            val cy = H / 2
            val x0 = cx - targetW / 2
            val y0 = cy - targetH / 2
            val x1 = x0 + targetW
            val y1 = y0 + targetH
            
            if (mouseX.toInt() !in x0..x1 || mouseY.toInt() !in y0..y1) {
                motivationsOverlayOpen = false
                motivationTypewriterProgress.clear()
                motivationDividerProgress.clear()
                return true
            }
        }
        
        // Открытие оверлея при клике по модели или мотивации
        if (button == 0 && !motivationsOverlayOpen) {
            val cx = W / 2
            val cy = H / 2
            val modelX = cx - 65..cx + 65
            val modelY = cy - 60..cy + 160
            
            if (mouseX.toInt() in modelX && mouseY.toInt() in modelY) {
                motivationsOverlayOpen = true
                overlayAnimTime = 0f
                motivationTypewriterProgress.clear()
                motivationDividerProgress.clear()
                return true
            }
        }
        
        // Back button
        val bw = 60; val bh = 12
        val bx = W / 2 - bw / 2; val by = H - bh - 6
        if (mouseX.toInt() in bx..(bx + bw) && mouseY.toInt() in by..(by + bh)) {
            client?.setScreen(parent); return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause() = false
}
