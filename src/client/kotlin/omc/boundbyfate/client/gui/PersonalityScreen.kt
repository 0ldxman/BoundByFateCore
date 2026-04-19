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
        val delay = 0.60f + lineIdx * 0.06f
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

        // ── PLAYER MODEL ─────────────────────────────────────────────────────
        val player = MinecraftClient.getInstance().player
        if (player != null) {
            // Мотивации ЗА персонажем (zLayer < 0)
            renderMotivations(context, cx.toFloat(), cy.toFloat(), behindOnly = true)

            InventoryScreen.drawEntity(
                context, cx, cy + 85, 70,
                (cx - mouseX).toFloat(), (cy - mouseY).toFloat(),
                player
            )

            // Мотивации ПЕРЕД персонажем (zLayer >= 0)
            renderMotivations(context, cx.toFloat(), cy.toFloat(), behindOnly = false)
        } else {
            renderMotivations(context, cx.toFloat(), cy.toFloat(), behindOnly = true)
            renderMotivations(context, cx.toFloat(), cy.toFloat(), behindOnly = false)
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

        super.render(context, mouseX, mouseY, delta)
    }

    // ── МОТИВАЦИИ: орбитальное движение ──────────────────────────────────────
    private fun renderMotivations(context: DrawContext, centerX: Float, centerY: Float, behindOnly: Boolean) {
        val introAlpha = easeOut(openTime)
        particles.forEach { p ->
            val isBehind = p.zLayer < 0f
            if (isBehind != behindOnly) return@forEach

            val angle = time * p.orbitSpeed + p.orbitPhase
            val ox = cos(angle.toDouble()).toFloat() * p.orbitRadius
            val oy = sin(angle.toDouble()).toFloat() * p.orbitRadius * p.orbitTilt

            val px = centerX + ox
            val py = centerY + oy - 30f  // чуть выше центра персонажа

            // Альфа: за персонажем — тусклее
            val depthFade = if (isBehind) 0.45f else 1.0f
            val pulseAlpha = p.baseAlpha + sin((time * 0.5f + p.orbitPhase).toDouble()).toFloat() * 0.07f
            val alpha = ((pulseAlpha * introAlpha * depthFade).coerceIn(0f, 1f) * 255).toInt()
            val color = (alpha shl 24) or p.color

            val m = context.matrices; m.push()
            m.translate(px, py, 0f)
            m.scale(p.scale, p.scale, 1f)
            val tw = textRenderer.getWidth(p.text)
            context.drawTextWithShadow(textRenderer, p.text, -(tw / 2), 0, color)
            m.pop()
        }
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
        drawCenteredScaledAlpha(context, labelText, cx, pad + 2, 0.55f, 0x888888, alpha)
        drawCenteredScaledAlpha(context, alignText, cx, pad + 11, 0.9f, 0xD4AF37, alpha)
        m.pop()

        // Divider под мировоззрением (появляется вместе с ним)
        if (prog > 0.5f) {
            val divAlpha = ((prog - 0.5f) / 0.5f * 0xAA).toInt()
            drawFadeDividerAlpha(context, cx, pad + 22, 120, divAlpha)
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

        data class Line(val text: String, val color: Int, val isFirst: Boolean)
        val lines = mutableListOf<Line>()
        ideals.forEach { ideal ->
            val color = if (ideal.isCompatible) 0xCCCCCC else 0xFF7777
            wrapText(ideal.text, maxChars).forEachIndexed { i, line ->
                lines.add(Line(line, color, i == 0))
            }
            lines.add(Line("", 0, false))
        }

        val visibleCount = (listH / lineH).coerceAtLeast(1)
        val maxScroll = (lines.size - visibleCount).coerceAtLeast(0)
        idealScroll = idealScroll.coerceIn(0, maxScroll)

        lines.drop(idealScroll).take(visibleCount).forEachIndexed { i, line ->
            if (line.text.isEmpty()) return@forEachIndexed
            val ly = curY + i * lineH
            val lp = lineProgress(i)
            if (lp <= 0f) return@forEachIndexed

            // Текст выезжает слева направо
            val textOffX = ((1f - lp) * -(w + 30)).toInt()
            val textAlpha = (lp * 255).toInt()
            val iconSize = 5
            val iconY = ly + (lineH * textScale / 2 - iconSize / 2).toInt()
            val textX = x + iconSize + 2

            val m = context.matrices; m.push()
            m.translate(textOffX.toFloat(), 0f, 0f)
            val tm = context.matrices; tm.push()
            tm.translate(textX.toFloat(), ly.toFloat(), 0f)
            tm.scale(textScale, textScale, 1f)
            val tc = (textAlpha shl 24) or (line.color and 0xFFFFFF)
            context.drawTextWithShadow(textRenderer, line.text, 0, 0, tc)
            tm.pop()
            m.pop()

            // Иконка появляется fade-in после текста
            if (line.isFirst) {
                val ip = iconProgress(i)
                if (ip > 0f) {
                    val iconAlpha = (ip * 255).toInt().coerceIn(0, 255)
                    // Рисуем иконку с альфой через матрицы (нет прямого alpha в draw, используем RenderSystem)
                    drawIconWithAlpha(context, x, iconY, iconSize, iconSize, iconAlpha)
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

        data class Line(val text: String, val isFirst: Boolean)
        val lines = mutableListOf<Line>()
        flaws.forEach { flaw ->
            wrapText(flaw.text, maxChars).forEachIndexed { i, line ->
                lines.add(Line(line, i == 0))
            }
            lines.add(Line("", false))
        }

        val visibleCount = (listH / lineH).coerceAtLeast(1)
        val maxScroll = (lines.size - visibleCount).coerceAtLeast(0)
        flawScroll = flawScroll.coerceIn(0, maxScroll)

        lines.drop(flawScroll).take(visibleCount).forEachIndexed { i, line ->
            if (line.text.isEmpty()) return@forEachIndexed
            val ly = curY + i * lineH
            val lp = lineProgress(i)
            if (lp <= 0f) return@forEachIndexed

            // Текст выезжает справа налево
            val textOffX = ((1f - lp) * (W - x + 30)).toInt()
            val textAlpha = (lp * 255).toInt()
            val iconSize = 5
            val iconY = ly + (lineH * textScale / 2 - iconSize / 2).toInt()
            val rightEdge = x + w
            val textRightEdge = rightEdge - iconSize - 2

            val m = context.matrices; m.push()
            m.translate(textOffX.toFloat(), 0f, 0f)
            val tm = context.matrices; tm.push()
            tm.translate(textRightEdge.toFloat(), ly.toFloat(), 0f)
            tm.scale(textScale, textScale, 1f)
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
                    drawIconWithAlpha(context, rightEdge - iconSize, iconY, iconSize, iconSize, iconAlpha)
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val W = width; val panelW = (W * 0.27f).toInt(); val pad = 8; val sideMargin = 18
        val mx = mouseX.toInt()
        val delta = if (amount > 0) -1 else 1
        if (mx < pad + sideMargin + panelW) { idealScroll = (idealScroll + delta).coerceAtLeast(0); return true }
        if (mx > W - panelW - pad - sideMargin) { flawScroll = (flawScroll + delta).coerceAtLeast(0); return true }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val W = width; val H = height
        val bw = 60; val bh = 12
        val bx = W / 2 - bw / 2; val by = H - bh - 6
        if (mouseX.toInt() in bx..(bx + bw) && mouseY.toInt() in by..(by + bh)) {
            client?.setScreen(parent); return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause() = false
}
