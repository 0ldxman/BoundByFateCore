package omc.boundbyfate.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import omc.boundbyfate.client.state.ClientMotivation
import omc.boundbyfate.client.state.ClientPlayerData
import kotlin.math.sin
import kotlin.math.cos

/**
 * Player personality screen — shows alignment, ideals, flaws, motivations.
 *
 * Layout:
 *   LEFT  : Alignment name + Ideals list (scrollable)
 *   RIGHT : Flaws list (scrollable)
 *   CENTER: Motivations as floating ambient text
 */
class PersonalityScreen(private val parent: Screen) :
    Screen(Text.translatable("screen.boundbyfate.personality")) {

    private var idealScroll = 0
    private var flawScroll = 0
    private var time = 0f

    // Floating motivation particles
    private data class MotivationParticle(
        val text: String,
        val x: Float,
        val y: Float,
        val scale: Float,
        val alpha: Float,
        val speed: Float,
        val phase: Float,
        val color: Int
    )
    private val particles = mutableListOf<MotivationParticle>()

    override fun init() {
        buildParticles()
    }

    private fun buildParticles() {
        particles.clear()
        val motivations = ClientPlayerData.motivations
        if (motivations.isEmpty()) return

        val cx = width / 2f
        val cy = height / 2f
        val rand = java.util.Random(42)

        motivations.forEachIndexed { i, mot ->
            // Distribute around center, avoiding left/right panels
            val panelW = (width * 0.28f).toInt()
            val safeLeft = panelW + 20f
            val safeRight = width - panelW - 20f
            val x = safeLeft + rand.nextFloat() * (safeRight - safeLeft)
            val y = 30f + rand.nextFloat() * (height - 60f)
            val scale = 0.55f + rand.nextFloat() * 0.45f  // 0.55..1.0
            val alpha = 0.5f + rand.nextFloat() * 0.4f    // 0.5..0.9
            val speed = 0.3f + rand.nextFloat() * 0.4f
            val phase = rand.nextFloat() * Math.PI.toFloat() * 2f
            // Warm golden/amber colors
            val colors = listOf(0xD4AF37, 0xC8A96E, 0xE8C97A, 0xB8956A, 0xFFD700)
            val color = colors[i % colors.size]
            particles.add(MotivationParticle(mot.text, x, y, scale, alpha, speed, phase, color))
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        time += delta * 0.016f

        val W = width; val H = height
        val panelW = (W * 0.28f).toInt()
        val pad = 10

        // ── FLOATING MOTIVATIONS (center, behind panels) ──────────────────────
        renderMotivations(context)

        // ── LEFT PANEL: Alignment + Ideals ────────────────────────────────────
        renderLeftPanel(context, mouseX, mouseY, pad, pad, panelW, H - pad * 2)

        // ── RIGHT PANEL: Flaws ────────────────────────────────────────────────
        renderRightPanel(context, mouseX, mouseY, W - panelW - pad, pad, panelW, H - pad * 2)

        // ── BACK BUTTON ───────────────────────────────────────────────────────
        val backText = "§7${net.minecraft.client.resource.language.I18n.translate("bbf.gm.button.back")}"
        val bw = 60; val bh = 12
        val bx = W / 2 - bw / 2; val by = H - bh - 6
        val backHov = mouseX in bx..(bx + bw) && mouseY in by..(by + bh)
        context.fill(bx, by, bx + bw, by + bh, if (backHov) 0xCC4a3a2a.toInt() else 0xCC1a1a1a.toInt())
        context.fill(bx, by, bx + bw, by + 1, 0xFF8a6a3a.toInt())
        context.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFF8a6a3a.toInt())
        context.fill(bx, by, bx + 1, by + bh, 0xFF8a6a3a.toInt())
        context.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFF8a6a3a.toInt())
        val m = context.matrices; m.push()
        m.translate((bx + bw / 2).toFloat(), (by + 2).toFloat(), 0f); m.scale(0.75f, 0.75f, 1f)
        val tw = textRenderer.getWidth(backText)
        context.drawTextWithShadow(textRenderer, backText, -(tw / 2), 0, 0xFFFFFF)
        m.pop()

        super.render(context, mouseX, mouseY, delta)
    }

    private fun renderMotivations(context: DrawContext) {
        particles.forEach { p ->
            val floatY = sin((time * p.speed + p.phase).toDouble()).toFloat() * 4f
            val floatX = cos((time * p.speed * 0.7f + p.phase).toDouble()).toFloat() * 2f
            val alpha = ((p.alpha + sin((time * 0.5f + p.phase).toDouble()).toFloat() * 0.1f)
                .coerceIn(0.2f, 1f) * 255).toInt()
            val color = (alpha shl 24) or p.color

            val m = context.matrices; m.push()
            m.translate(p.x + floatX, p.y + floatY, 0f)
            m.scale(p.scale, p.scale, 1f)
            val tw = textRenderer.getWidth(p.text)
            context.drawTextWithShadow(textRenderer, p.text, -(tw / 2), 0, color)
            m.pop()
        }
    }

    private fun renderLeftPanel(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        // Panel background
        context.fill(x, y, x + w, y + h, 0xCC0d0d0d.toInt())
        context.fill(x, y, x + w, y + 1, 0xFF8a6a3a.toInt())
        context.fill(x, y + h - 1, x + w, y + h, 0xFF8a6a3a.toInt())
        context.fill(x, y, x + 1, y + h, 0xFF8a6a3a.toInt())
        context.fill(x + w - 1, y, x + w, y + h, 0xFF8a6a3a.toInt())

        var curY = y + 8

        // Alignment
        val alignText = ClientPlayerData.alignmentText.ifEmpty {
            net.minecraft.client.resource.language.I18n.translate("bbf.alignment.true_neutral")
        }
        drawCenteredScaled(context, net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.alignment"),
            x + w / 2, curY, 0.6f, 0x888888)
        curY += 10
        drawCenteredScaled(context, alignText, x + w / 2, curY, 0.85f, 0xD4AF37)
        curY += 14

        // Divider
        context.fill(x + 8, curY, x + w - 8, curY + 1, 0xFF4a3a2a.toInt())
        curY += 6

        // Ideals header
        drawCenteredScaled(context, net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.ideals"),
            x + w / 2, curY, 0.65f, 0xD4AF37)
        curY += 12

        // Ideals list
        val ideals = ClientPlayerData.ideals
        val listH = y + h - curY - 20
        renderScrollableList(context, mouseX, mouseY, x + 4, curY, w - 8, listH,
            ideals.map { ideal ->
                val compat = ideal.isCompatible
                Pair(ideal.text, if (compat) 0xCCCCCC else 0xFF6666)
            }, idealScroll)
    }

    private fun renderRightPanel(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        context.fill(x, y, x + w, y + h, 0xCC0d0d0d.toInt())
        context.fill(x, y, x + w, y + 1, 0xFF8a6a3a.toInt())
        context.fill(x, y + h - 1, x + w, y + h, 0xFF8a6a3a.toInt())
        context.fill(x, y, x + 1, y + h, 0xFF8a6a3a.toInt())
        context.fill(x + w - 1, y, x + w, y + h, 0xFF8a6a3a.toInt())

        var curY = y + 8

        drawCenteredScaled(context, net.minecraft.client.resource.language.I18n.translate("bbf.gm.identity.flaws"),
            x + w / 2, curY, 0.65f, 0xD4AF37)
        curY += 12

        val flaws = ClientPlayerData.flaws
        val listH = y + h - curY - 20
        renderScrollableList(context, mouseX, mouseY, x + 4, curY, w - 8, listH,
            flaws.map { Pair(it.text, 0xCCCCCC) }, flawScroll)
    }

    private fun renderScrollableList(
        context: DrawContext, mouseX: Int, mouseY: Int,
        x: Int, y: Int, w: Int, h: Int,
        items: List<Pair<String, Int>>,
        scroll: Int
    ) {
        if (items.isEmpty()) {
            drawCenteredScaled(context, "§8—", x + w / 2, y + h / 2 - 4, 0.7f, 0x555555)
            return
        }

        val lineH = 9
        val textScale = 0.65f
        val maxChars = ((w - 8) / (textRenderer.getWidth("W") * textScale)).toInt().coerceAtLeast(10)

        // Build wrapped lines with item index
        data class Line(val text: String, val color: Int, val isFirst: Boolean)
        val lines = mutableListOf<Line>()
        items.forEach { (text, color) ->
            val words = text.split(" ")
            var current = ""
            var first = true
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (test.length <= maxChars) {
                    current = test
                } else {
                    if (current.isNotEmpty()) { lines.add(Line(current, color, first)); first = false }
                    current = word
                }
            }
            if (current.isNotEmpty()) lines.add(Line(current, color, first))
            lines.add(Line("", color, false))  // gap between items
        }

        val visibleCount = (h / lineH).coerceAtLeast(1)
        val maxScroll = (lines.size - visibleCount).coerceAtLeast(0)
        val actualScroll = scroll.coerceIn(0, maxScroll)

        // Clip rendering
        val visibleLines = lines.drop(actualScroll).take(visibleCount)
        visibleLines.forEachIndexed { i, line ->
            if (line.text.isEmpty()) return@forEachIndexed
            val ly = y + i * lineH
            val prefix = if (line.isFirst) "§6• §r" else "  "
            val m = context.matrices; m.push()
            m.translate((x + 4).toFloat(), ly.toFloat(), 0f)
            m.scale(textScale, textScale, 1f)
            context.drawTextWithShadow(textRenderer, "$prefix${line.text}", 0, 0, line.color)
            m.pop()
        }

        // Scrollbar
        if (lines.size > visibleCount) {
            val sbX = x + w - 4
            val sbH = h - 4
            val thumbH = ((visibleCount.toFloat() / lines.size) * sbH).toInt().coerceAtLeast(8)
            val thumbY = y + 2 + ((actualScroll.toFloat() / maxScroll) * (sbH - thumbH)).toInt()
            context.fill(sbX, y + 2, sbX + 3, y + h - 2, 0xFF222222.toInt())
            context.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF8a6a3a.toInt())
        }
    }

    private fun drawCenteredScaled(context: DrawContext, text: String, cx: Int, y: Int, scale: Float, color: Int) {
        val m = context.matrices; m.push()
        m.translate(cx.toFloat(), y.toFloat(), 0f); m.scale(scale, scale, 1f)
        val tw = textRenderer.getWidth(text)
        context.drawTextWithShadow(textRenderer, text, -(tw / 2), 0, color)
        m.pop()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val W = width; val panelW = (W * 0.28f).toInt(); val pad = 10
        val mx = mouseX.toInt()
        if (mx < pad + panelW) {
            idealScroll = (idealScroll - amount.toInt()).coerceAtLeast(0)
            return true
        }
        if (mx > W - panelW - pad) {
            flawScroll = (flawScroll - amount.toInt()).coerceAtLeast(0)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val W = width; val H = height
        val bw = 60; val bh = 12
        val bx = W / 2 - bw / 2; val by = H - bh - 6
        if (mouseX.toInt() in bx..(bx + bw) && mouseY.toInt() in by..(by + bh)) {
            client?.setScreen(parent)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause() = false
}
