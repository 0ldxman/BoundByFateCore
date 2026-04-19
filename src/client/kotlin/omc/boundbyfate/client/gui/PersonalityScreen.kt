package omc.boundbyfate.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.client.state.ClientPlayerData
import omc.boundbyfate.client.gui.GuiAtlas
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs

/**
 * Player personality screen.
 *
 * Layout:
 *   LEFT  : Ideals list (no background box, plain text with bullet)
 *   RIGHT : Flaws list (right-aligned, bullet on right)
 *   CENTER: Player model + floating motivation text
 *   TOP   : Alignment name + divider (centered)
 */
class PersonalityScreen(private val parent: Screen) :
    Screen(Text.translatable("screen.boundbyfate.personality")) {

    private var idealScroll = 0
    private var flawScroll = 0
    private var time = 0f
    private var openTime = 0f

    // Floating motivation particles
    private data class MotivationParticle(
        val text: String,
        val baseX: Float,
        val baseY: Float,
        val scale: Float,
        val baseAlpha: Float,
        val speed: Float,
        val phase: Float,
        val color: Int
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

        val panelW = (width * 0.27f).toInt()
        val safeLeft = panelW + 15f
        val safeRight = width - panelW - 15f
        val rand = java.util.Random(12345L)

        motivations.forEachIndexed { i, mot ->
            val x = safeLeft + rand.nextFloat() * (safeRight - safeLeft)
            val y = 55f + rand.nextFloat() * (height - 100f)
            val scale = 0.6f + rand.nextFloat() * 0.5f
            val baseAlpha = 0.45f + rand.nextFloat() * 0.35f
            val speed = 0.25f + rand.nextFloat() * 0.35f
            val phase = rand.nextFloat() * Math.PI.toFloat() * 2f
            val colors = listOf(0xD4AF37, 0xC8A96E, 0xFFD700, 0xB8956A, 0xE8C97A)
            val color = colors[i % colors.size]
            particles.add(MotivationParticle(mot.text, x, y, scale, baseAlpha, speed, phase, color))
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        time += delta * 0.016f
        openTime = (openTime + delta * 0.018f).coerceAtMost(1f)

        val W = width; val H = height
        val panelW = (W * 0.27f).toInt()
        val pad = 8
        val sideMargin = 18  // дополнительный отступ от края экрана

        // ── PLAYER MODEL (та же позиция что в CharacterScreenAtlas) ──────────
        val player = MinecraftClient.getInstance().player
        if (player != null) {
            val cx = W / 2; val cy = H / 2
            InventoryScreen.drawEntity(
                context, cx, cy + 85, 70,
                (cx - mouseX).toFloat(), (cy - mouseY).toFloat(),
                player
            )
        }

        // ── FLOATING MOTIVATIONS ─────────────────────────────────────────────
        renderMotivations(context)

        // ── ALIGNMENT (top center) ────────────────────────────────────────────
        renderAlignmentTop(context, W, pad)

        // ── LEFT PANEL: Ideals (no box) ───────────────────────────────────────
        val panelStartY = H / 3
        renderIdealsPanel(context, mouseX, mouseY, pad + sideMargin, panelStartY, panelW, H - panelStartY - 20)

        // ── RIGHT PANEL: Flaws (no box, right-aligned) ────────────────────────
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

    /**
     * Рисует горизонтальный разделитель с fade-out к краям (градиент по альфа-каналу).
     * Цвет совпадает с цветом заголовков (0xD4AF37).
     */
    private fun drawFadeDivider(context: DrawContext, cx: Int, y: Int, totalW: Int) {
        val halfW = totalW / 2
        val solidHalf = (halfW * 0.35f).toInt()   // центральная монотонная часть
        val fadeSteps = halfW - solidHalf           // количество пикселей градиента

        val baseColor = 0xD4AF37
        val r = (baseColor shr 16) and 0xFF
        val g = (baseColor shr 8) and 0xFF
        val b = baseColor and 0xFF
        val maxAlpha = 0xAA  // максимальная непрозрачность центра

        // Центральная монотонная часть
        val solidColor = (maxAlpha shl 24) or baseColor
        context.fill(cx - solidHalf, y, cx + solidHalf, y + 1, solidColor)

        // Градиент влево и вправо
        for (i in 0 until fadeSteps) {
            val alpha = (maxAlpha * (1f - i.toFloat() / fadeSteps)).toInt()
            val c = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            context.fill(cx - solidHalf - i - 1, y, cx - solidHalf - i, y + 1, c)
            context.fill(cx + solidHalf + i, y, cx + solidHalf + i + 1, y + 1, c)
        }
    }

    private fun renderAlignmentTop(context: DrawContext, W: Int, pad: Int) {
        val alignText = ClientPlayerData.alignmentText.ifEmpty {
            net.minecraft.client.resource.language.I18n.translate("bbf.alignment.true_neutral")
        }
        val labelText = net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.alignment")
        val cx = W / 2

        drawCenteredScaled(context, labelText, cx, pad + 2, 0.55f, 0x888888)
        drawCenteredScaled(context, alignText, cx, pad + 11, 0.9f, 0xD4AF37)

        drawFadeDivider(context, cx, pad + 22, 120)
    }

    private fun renderMotivations(context: DrawContext) {
        val introAlpha = easeOut(openTime)
        particles.forEach { p ->
            val floatY = sin((time * p.speed + p.phase).toDouble()).toFloat() * 5f
            val floatX = cos((time * p.speed * 0.6f + p.phase + 1f).toDouble()).toFloat() * 2.5f
            val pulseAlpha = p.baseAlpha + sin((time * 0.4f + p.phase).toDouble()).toFloat() * 0.08f
            val alpha = ((pulseAlpha * introAlpha).coerceIn(0f, 1f) * 255).toInt()
            val color = (alpha shl 24) or p.color

            val m = context.matrices; m.push()
            m.translate(p.baseX + floatX, p.baseY + floatY, 0f)
            m.scale(p.scale, p.scale, 1f)
            val tw = textRenderer.getWidth(p.text)
            context.drawTextWithShadow(textRenderer, p.text, -(tw / 2), 0, color)
            m.pop()
        }
    }

    private fun renderIdealsPanel(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val ideals = ClientPlayerData.ideals
        if (ideals.isEmpty()) return

        var curY = y + 4
        val headerText = net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.ideals")
        // Центрируем заголовок по ширине панели
        val headerCx = x + w / 2
        drawCenteredScaled(context, headerText, headerCx, curY, 0.6f, 0xD4AF37)
        curY += 9
        drawFadeDivider(context, headerCx, curY, w)
        curY += 5

        val textScale = 0.65f
        val maxChars = ((w - 12) / (textRenderer.getWidth("W") * textScale)).toInt().coerceAtLeast(10)
        val lineH = 9
        val listH = h - (curY - y) - 20

        // Build wrapped lines
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
            val iconSize = 5
            val iconY = ly + (lineH * textScale / 2 - iconSize / 2).toInt()
            if (line.isFirst) {
                GuiAtlas.ICON_PROFICIENCY.draw(context, x, iconY, iconSize, iconSize)
            }
            val textX = x + iconSize + 2
            val m = context.matrices; m.push()
            m.translate(textX.toFloat(), ly.toFloat(), 0f)
            m.scale(textScale, textScale, 1f)
            context.drawTextWithShadow(textRenderer, line.text, 0, 0, line.color)
            m.pop()
        }

        // Scrollbar
        if (lines.size > visibleCount && maxScroll > 0) {
            val sbX = x + w - 3
            val sbH = listH - 4
            val thumbH = ((visibleCount.toFloat() / lines.size) * sbH).toInt().coerceAtLeast(6)
            val thumbY = curY + 2 + ((idealScroll.toFloat() / maxScroll) * (sbH - thumbH)).toInt()
            context.fill(sbX, curY + 2, sbX + 2, curY + sbH, 0x44FFFFFF.toInt())
            context.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF8a6a3a.toInt())
        }
    }

    private fun renderFlawsPanel(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val flaws = ClientPlayerData.flaws
        if (flaws.isEmpty()) return

        var curY = y + 4
        val headerText = net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.flaws")
        // Центрируем заголовок по ширине панели
        val headerCx = x + w / 2
        drawCenteredScaled(context, headerText, headerCx, curY, 0.6f, 0xD4AF37)
        curY += 9
        drawFadeDivider(context, headerCx, curY, w)
        curY += 5

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
            val iconSize = 5
            val iconY = ly + (lineH * textScale / 2 - iconSize / 2).toInt()
            val rightEdge = x + w
            if (line.isFirst) {
                GuiAtlas.ICON_PROFICIENCY.draw(context, rightEdge - iconSize, iconY, iconSize, iconSize)
            }
            val textRightEdge = rightEdge - iconSize - 2
            val m = context.matrices; m.push()
            m.translate(textRightEdge.toFloat(), ly.toFloat(), 0f)
            m.scale(textScale, textScale, 1f)
            val tw = textRenderer.getWidth(line.text)
            context.drawTextWithShadow(textRenderer, line.text, -(tw), 0, 0xCCCCCC)
            m.pop()
        }

        // Scrollbar (left side of right panel)
        if (lines.size > visibleCount && maxScroll > 0) {
            val sbX = x
            val sbH = listH - 4
            val thumbH = ((visibleCount.toFloat() / lines.size) * sbH).toInt().coerceAtLeast(6)
            val thumbY = curY + 2 + ((flawScroll.toFloat() / maxScroll) * (sbH - thumbH)).toInt()
            context.fill(sbX, curY + 2, sbX + 2, curY + sbH, 0x44FFFFFF.toInt())
            context.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF8a6a3a.toInt())
        }
    }

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

    private fun drawScaled(context: DrawContext, text: String, x: Int, y: Int, scale: Float, color: Int) {
        val m = context.matrices; m.push()
        m.translate(x.toFloat(), y.toFloat(), 0f); m.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        m.pop()
    }

    private fun drawCenteredScaled(context: DrawContext, text: String, cx: Int, y: Int, scale: Float, color: Int) {
        val m = context.matrices; m.push()
        m.translate(cx.toFloat(), y.toFloat(), 0f); m.scale(scale, scale, 1f)
        val tw = textRenderer.getWidth(text)
        context.drawTextWithShadow(textRenderer, text, -(tw / 2), 0, color)
        m.pop()
    }

    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)

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
