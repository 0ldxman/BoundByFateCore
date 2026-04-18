
package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import omc.boundbyfate.api.identity.Alignment
import omc.boundbyfate.api.identity.AlignmentCoordinates
import omc.boundbyfate.api.identity.IdealAlignment
import omc.boundbyfate.client.state.ClientFlaw
import omc.boundbyfate.client.state.ClientGoal
import omc.boundbyfate.client.state.ClientIdeal
import omc.boundbyfate.client.state.ClientMotivation
import omc.boundbyfate.client.state.ClientProposal
import omc.boundbyfate.client.state.GmPlayerSnapshot
import omc.boundbyfate.network.BbfPackets

/**
 * GM Identity screen.
 *
 * Layout (3 columns):
 *   LEFT  : Ideals (top) + Flaws (bottom)
 *   CENTER: Alignment diagram with coordinate dot
 *   RIGHT : Motivations (top) + Goals (bottom)
 *
 * Input overlay renders at Z+400 so nothing bleeds through.
 */
class GmIdentityScreen(private val snapshot: GmPlayerSnapshot) :
    Screen(Text.literal("Identity: ${snapshot.playerName}")) {

    private fun tr(key: String, vararg args: Any): String =
        net.minecraft.client.resource.language.I18n.translate(key, *args)

    // ── State ─────────────────────────────────────────────────────────────────
    private var alignLawChaos: Int = snapshot.alignmentCoords.lawChaos
    private var alignGoodEvil: Int = snapshot.alignmentCoords.goodEvil

    private val ideals: MutableList<ClientIdeal> = snapshot.ideals.toMutableList()
    private val flaws: MutableList<ClientFlaw> = snapshot.flaws.toMutableList()
    private val motivations: MutableList<ClientMotivation> = snapshot.motivations.toMutableList()
    private val proposals: MutableList<ClientProposal> = snapshot.proposals.toMutableList()
    private val goals: MutableList<ClientGoal> = snapshot.goals.toMutableList()

    private var idealScroll = 0
    private var flawScroll = 0
    private var motivationScroll = 0
    private var goalScroll = 0

    // ── Input overlay ─────────────────────────────────────────────────────────
    private enum class InputMode { NONE, ADD_IDEAL, ADD_FLAW, ADD_MOTIVATION, ADD_GOAL }
    private var inputMode = InputMode.NONE
    private var inputBuffer = ""
    private var inputBuffer2 = ""
    private var pendingAxis: IdealAlignment = IdealAlignment.ANY

    private var statusMsg = ""; private var statusTimer = 0f

    // ── Button registry ───────────────────────────────────────────────────────
    private data class Btn(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit)
    private val btns = mutableListOf<Btn>()

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun currentAlignment() = AlignmentCoordinates(alignLawChaos, alignGoodEvil).getAlignment()

    override fun init() {}

    // ═════════════════════════════════════════════════════════════════════════
    // RENDER
    // ═════════════════════════════════════════════════════════════════════════
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        btns.clear()
        if (statusTimer > 0f) statusTimer -= delta * 0.05f

        val W = width; val H = height; val pad = 5

        // Header
        btn(context, mouseX, mouseY, pad, pad, 38, 11, "§7← ${tr("bbf.gm.button.back")}") {
            client?.setScreen(GmPlayerEditScreen(snapshot))
        }
        btn(context, mouseX, mouseY, W - 46, pad, 40, 11, "§a${tr("bbf.gm.button.apply")}") { applyAll() }

        val bodyY = pad + 16
        val bodyH = H - bodyY - pad

        // Three equal columns
        val colW = (W - pad * 2 - 8) / 3
        val leftX   = pad
        val centerX = pad + colW + 4
        val rightX  = pad + (colW + 4) * 2

        // Column boxes
        box(context, leftX,   bodyY, colW, bodyH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        box(context, centerX, bodyY, colW, bodyH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        box(context, rightX,  bodyY, colW, bodyH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())

        // Left: ideals (top half) + flaws (bottom half)
        val leftHalf = (bodyH - 4) / 2
        renderIdeals(context, mouseX, mouseY, leftX + 3, bodyY + 3, colW - 6, leftHalf - 3)
        renderFlaws(context, mouseX, mouseY, leftX + 3, bodyY + leftHalf + 4, colW - 6, bodyH - leftHalf - 7)

        // Center: alignment diagram
        renderAlignmentDiagram(context, mouseX, mouseY, centerX + 3, bodyY + 3, colW - 6, bodyH - 6)

        // Right: motivations (top half) + goals (bottom half)
        val rightHalf = (bodyH - 4) / 2
        renderMotivations(context, mouseX, mouseY, rightX + 3, bodyY + 3, colW - 6, rightHalf - 3)
        renderGoals(context, mouseX, mouseY, rightX + 3, bodyY + rightHalf + 4, colW - 6, bodyH - rightHalf - 7)

        // Input overlay — rendered last at high Z
        if (inputMode != InputMode.NONE) renderInputOverlay(context, mouseX, mouseY)

        // Status
        if (statusTimer > 0f) {
            val a = (statusTimer * 255).toInt().coerceIn(0, 255)
            context.drawCenteredTextWithShadow(textRenderer, statusMsg, W / 2, H - 10, (a shl 24) or 0x55FF55)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CENTER: ALIGNMENT DIAGRAM
    // ═════════════════════════════════════════════════════════════════════════
    private fun renderAlignmentDiagram(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.alignment"), x, y, 0.65f, 0xD4AF37)

        // Diagram area — square, centered
        val diagSize = minOf(w, h - 60).coerceAtLeast(60)
        val diagX = x + (w - diagSize) / 2
        val diagY = y + 12

        // Background
        context.fill(diagX, diagY, diagX + diagSize, diagY + diagSize, 0xFF111111.toInt())

        // Zone dividers (3x3 grid lines)
        val third = diagSize / 3
        val lineColor = 0xFF333333.toInt()
        context.fill(diagX + third,     diagY, diagX + third + 1,     diagY + diagSize, lineColor)
        context.fill(diagX + third * 2, diagY, diagX + third * 2 + 1, diagY + diagSize, lineColor)
        context.fill(diagX, diagY + third,     diagX + diagSize, diagY + third + 1,     lineColor)
        context.fill(diagX, diagY + third * 2, diagX + diagSize, diagY + third * 2 + 1, lineColor)

        // Axis lines (center cross) — brighter
        val cx = diagX + diagSize / 2
        val cy = diagY + diagSize / 2
        val axisColor = 0xFF555555.toInt()
        context.fill(cx, diagY, cx + 1, diagY + diagSize, axisColor)
        context.fill(diagX, cy, diagX + diagSize, cy + 1, axisColor)

        // Zone labels (short names in each cell)
        val alignments = listOf(
            Alignment.LAWFUL_GOOD,    Alignment.NEUTRAL_GOOD,  Alignment.CHAOTIC_GOOD,
            Alignment.LAWFUL_NEUTRAL, Alignment.TRUE_NEUTRAL,  Alignment.CHAOTIC_NEUTRAL,
            Alignment.LAWFUL_EVIL,    Alignment.NEUTRAL_EVIL,  Alignment.CHAOTIC_EVIL
        )
        val current = currentAlignment()
        alignments.forEachIndexed { i, al ->
            val col = i % 3; val row = i / 3
            val cellX = diagX + col * third + third / 2
            val cellY = diagY + row * third + third / 2
            val shortName = net.minecraft.client.resource.language.I18n.translate(al.getShortKey())
            val isActive = al == current
            val color = when {
                isActive -> 0xFFD700
                al.name.contains("EVIL") -> 0x663333
                al.name.contains("GOOD") -> 0x336633
                else -> 0x444444
            }
            val m = context.matrices; m.push()
            m.translate(cellX.toFloat(), cellY.toFloat(), 0f)
            m.scale(0.55f, 0.55f, 1f)
            val tw = textRenderer.getWidth(shortName)
            context.drawTextWithShadow(textRenderer, shortName, -(tw / 2), -3, color)
            m.pop()
        }

        // Diagram border
        val borderColor = 0xFF6b5a3e.toInt()
        context.fill(diagX, diagY, diagX + diagSize, diagY + 1, borderColor)
        context.fill(diagX, diagY + diagSize - 1, diagX + diagSize, diagY + diagSize, borderColor)
        context.fill(diagX, diagY, diagX + 1, diagY + diagSize, borderColor)
        context.fill(diagX + diagSize - 1, diagY, diagX + diagSize, diagY + diagSize, borderColor)

        // Axis labels outside diagram
        val labelScale = 0.55f
        // Top: Good
        val goodLabel = tr("bbf.alignment.axis.good")
        val goodW = (textRenderer.getWidth(goodLabel) * labelScale).toInt()
        lbl(context, "§a$goodLabel", cx - goodW / 2, diagY - 9, labelScale, 0x55FF55)
        // Bottom: Evil
        val evilLabel = tr("bbf.alignment.axis.evil")
        val evilW = (textRenderer.getWidth(evilLabel) * labelScale).toInt()
        lbl(context, "§c$evilLabel", cx - evilW / 2, diagY + diagSize + 2, labelScale, 0xFF5555)
        // Left: Lawful
        val lawLabel = tr("bbf.alignment.axis.lawful")
        lbl(context, "§9$lawLabel", diagX - (textRenderer.getWidth(lawLabel) * labelScale).toInt() - 2, cy - 3, labelScale, 0x5555FF)
        // Right: Chaotic
        val chaosLabel = tr("bbf.alignment.axis.chaotic")
        lbl(context, "§6$chaosLabel", diagX + diagSize + 2, cy - 3, labelScale, 0xFFAA00)

        // ── Position dot ──────────────────────────────────────────────────────
        // Map coords (-6..6) to pixel position within diagram
        // lawChaos: -6=left(Lawful), +6=right(Chaotic)
        // goodEvil: +6=top(Good), -6=bottom(Evil)
        val dotX = diagX + ((alignLawChaos + 6).toFloat() / 12f * diagSize).toInt()
        val dotY = diagY + ((6 - alignGoodEvil).toFloat() / 12f * diagSize).toInt()

        // Glow ring
        context.fill(dotX - 3, dotY - 3, dotX + 4, dotY + 4, 0x88FFD700.toInt())
        // Dot
        context.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, 0xFFFFD700.toInt())

        // ── Coordinate controls ───────────────────────────────────────────────
        val ctrlY = diagY + diagSize + 12

        // Law-Chaos row
        lbl(context, "§7${tr("bbf.gm.identity.law_chaos")}: §f$alignLawChaos", x, ctrlY, 0.6f, 0x888888)
        btn(context, mouseX, mouseY, x + w - 28, ctrlY - 1, 12, 9, "§c-") { alignLawChaos = (alignLawChaos - 1).coerceIn(-6, 6) }
        btn(context, mouseX, mouseY, x + w - 14, ctrlY - 1, 12, 9, "§a+") { alignLawChaos = (alignLawChaos + 1).coerceIn(-6, 6) }

        // Good-Evil row
        val geY = ctrlY + 12
        lbl(context, "§7${tr("bbf.gm.identity.good_evil")}: §f$alignGoodEvil", x, geY, 0.6f, 0x888888)
        btn(context, mouseX, mouseY, x + w - 28, geY - 1, 12, 9, "§c-") { alignGoodEvil = (alignGoodEvil - 1).coerceIn(-6, 6) }
        btn(context, mouseX, mouseY, x + w - 14, geY - 1, 12, 9, "§a+") { alignGoodEvil = (alignGoodEvil + 1).coerceIn(-6, 6) }

        // Current alignment name
        val alName = net.minecraft.client.resource.language.I18n.translate(current.translationKey)
        val alNameW = (textRenderer.getWidth(alName) * 0.7f).toInt()
        lbl(context, alName, x + w / 2 - alNameW / 2, geY + 12, 0.7f, 0xFFD700)

        // Border warning
        val (lcBorder, geBorder) = AlignmentCoordinates(alignLawChaos, alignGoodEvil).isOnBorder()
        if (lcBorder || geBorder) {
            lbl(context, "§6⚠ ${tr("bbf.gm.identity.wavering")}", x, geY + 24, 0.55f, 0xFFAA00)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LEFT: IDEALS + FLAWS
    // ═════════════════════════════════════════════════════════════════════════
    private fun renderIdeals(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.ideals"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 11, y - 1, 11, 9, "§a+") {
            inputMode = InputMode.ADD_IDEAL; inputBuffer = ""; pendingAxis = IdealAlignment.ANY
        }
        val listY = y + 11; val rowH = 11
        val visible = ideals.drop(idealScroll).take((h - 11) / rowH)
        visible.forEachIndexed { i, ideal ->
            val fy = listY + i * rowH
            val compatible = ideal.alignmentAxis.isCompatibleWith(currentAlignment())
            val textColor = if (compatible) 0xCCCCCC else 0xFF5555
            val axisShort = ideal.alignmentAxis.name.take(3)
            val axisCol = axisColor(ideal.alignmentAxis)
            lbl(context, "[$axisShort]", x, fy, 0.5f, axisCol)
            lbl(context, truncate(ideal.text, w - 36, 0.6f), x + 20, fy, 0.6f, textColor)
            btn(context, mouseX, mouseY, x + w - 11, fy, 10, 9, "§cX") {
                sendRemoveIdeal(ideal.id); ideals.removeIf { it.id == ideal.id }
            }
        }
        if (idealScroll > 0) btn(context, mouseX, mouseY, x + w - 10, listY, 10, 9, "§7▲") { idealScroll-- }
        val maxS = (ideals.size - (h - 11) / rowH).coerceAtLeast(0)
        if (idealScroll < maxS) btn(context, mouseX, mouseY, x + w - 10, listY + h - 20, 10, 9, "§7▼") { idealScroll++ }
    }

    private fun renderFlaws(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        // Divider line
        context.fill(x, y - 2, x + w, y - 1, 0xFF4a3a2a.toInt())
        lbl(context, tr("bbf.gm.identity.flaws"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 11, y - 1, 11, 9, "§a+") {
            inputMode = InputMode.ADD_FLAW; inputBuffer = ""
        }
        val listY = y + 11; val rowH = 11
        val visible = flaws.drop(flawScroll).take((h - 11) / rowH)
        visible.forEachIndexed { i, flaw ->
            val fy = listY + i * rowH
            lbl(context, truncate(flaw.text, w - 20, 0.6f), x, fy, 0.6f, 0xCCCCCC)
            btn(context, mouseX, mouseY, x + w - 11, fy, 10, 9, "§cX") {
                sendRemoveFlaw(flaw.id); flaws.removeIf { it.id == flaw.id }
            }
        }
        if (flawScroll > 0) btn(context, mouseX, mouseY, x + w - 10, listY, 10, 9, "§7▲") { flawScroll-- }
        val maxS = (flaws.size - (h - 11) / rowH).coerceAtLeast(0)
        if (flawScroll < maxS) btn(context, mouseX, mouseY, x + w - 10, listY + h - 20, 10, 9, "§7▼") { flawScroll++ }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // RIGHT: MOTIVATIONS + GOALS
    // ═════════════════════════════════════════════════════════════════════════
    private fun renderMotivations(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.motivations"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 11, y - 1, 11, 9, "§a+") {
            inputMode = InputMode.ADD_MOTIVATION; inputBuffer = ""
        }
        val listY = y + 11; val rowH = 11
        val active = motivations.filter { it.isActive }
        val visible = active.drop(motivationScroll).take((h - 11) / rowH)
        visible.forEachIndexed { i, mot ->
            val fy = listY + i * rowH
            val tag = if (mot.addedByGm) "§8[GM]" else "§b[P]"
            lbl(context, tag, x, fy, 0.5f, if (mot.addedByGm) 0x666666 else 0x55AAFF)
            lbl(context, truncate(mot.text, w - 36, 0.6f), x + 20, fy, 0.6f, 0xCCCCCC)
            btn(context, mouseX, mouseY, x + w - 11, fy, 10, 9, "§cX") {
                sendRemoveMotivation(mot.id); motivations.removeIf { it.id == mot.id }
            }
        }
        // Proposals
        if (proposals.isNotEmpty()) {
            val propY = listY + visible.size * rowH + 4
            lbl(context, "§6? ${tr("bbf.gm.identity.proposals")} (${proposals.size})", x, propY, 0.55f, 0xFFAA00)
            proposals.take(2).forEachIndexed { i, prop ->
                val fy = propY + 9 + i * rowH
                lbl(context, truncate(prop.text, w - 28, 0.55f), x, fy, 0.55f, 0xFFCC55)
                btn(context, mouseX, mouseY, x + w - 22, fy, 10, 9, "§a✓") {
                    sendHandleProposal(prop.id, "accept"); proposals.removeIf { it.id == prop.id }
                }
                btn(context, mouseX, mouseY, x + w - 11, fy, 10, 9, "§cX") {
                    sendHandleProposal(prop.id, "reject"); proposals.removeIf { it.id == prop.id }
                }
            }
        }
        if (motivationScroll > 0) btn(context, mouseX, mouseY, x + w - 10, listY, 10, 9, "§7▲") { motivationScroll-- }
        val maxS = (active.size - (h - 11) / rowH).coerceAtLeast(0)
        if (motivationScroll < maxS) btn(context, mouseX, mouseY, x + w - 10, listY + h - 20, 10, 9, "§7▼") { motivationScroll++ }
    }

    private fun renderGoals(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        context.fill(x, y - 2, x + w, y - 1, 0xFF4a3a2a.toInt())
        lbl(context, tr("bbf.gm.identity.goals"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 11, y - 1, 11, 9, "§a+") {
            inputMode = InputMode.ADD_GOAL; inputBuffer = ""; inputBuffer2 = ""
        }
        val listY = y + 11; val rowH = 24
        val visible = goals.drop(goalScroll).take((h - 11) / rowH)
        visible.forEachIndexed { i, goal ->
            val gy = listY + i * rowH
            val statusIcon = when (goal.status) { "COMPLETED" -> "§a✓"; "FAILED" -> "§c✗"; "CANCELLED" -> "§7○"; else -> "§e▶" }
            val statusColor = when (goal.status) { "COMPLETED" -> 0x55FF55; "FAILED" -> 0xFF5555; "CANCELLED" -> 0x888888; else -> 0xFFFFFF }
            lbl(context, statusIcon, x, gy, 0.65f, statusColor)
            lbl(context, truncate(goal.title, w - 28, 0.65f), x + 10, gy, 0.65f, statusColor)
            val task = goal.currentTask
            if (task != null) lbl(context, "§7□ ${truncate(task.description, w - 14, 0.55f)}", x + 4, gy + 11, 0.55f, 0x888888)
            if (goal.isActive) {
                btn(context, mouseX, mouseY, x + w - 22, gy, 10, 9, "§a✓") {
                    sendGoalAction(goal.id, "complete", "", "")
                    val idx = goals.indexOfFirst { it.id == goal.id }
                    if (idx >= 0) goals[idx] = goals[idx].copy(status = "COMPLETED")
                }
                btn(context, mouseX, mouseY, x + w - 11, gy, 10, 9, "§c✗") {
                    sendGoalAction(goal.id, "fail", "", "")
                    val idx = goals.indexOfFirst { it.id == goal.id }
                    if (idx >= 0) goals[idx] = goals[idx].copy(status = "FAILED")
                }
            } else {
                btn(context, mouseX, mouseY, x + w - 11, gy, 10, 9, "§7X") {
                    sendGoalAction(goal.id, "remove", "", "")
                    goals.removeIf { it.id == goal.id }
                }
            }
        }
        if (goalScroll > 0) btn(context, mouseX, mouseY, x + w - 10, listY, 10, 9, "§7▲") { goalScroll-- }
        val maxS = (goals.size - (h - 11) / rowH).coerceAtLeast(0)
        if (goalScroll < maxS) btn(context, mouseX, mouseY, x + w - 10, listY + h - 20, 10, 9, "§7▼") { goalScroll++ }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // INPUT OVERLAY — rendered at Z+400 so nothing bleeds through
    // ═════════════════════════════════════════════════════════════════════════
    private fun renderInputOverlay(context: DrawContext, mouseX: Int, mouseY: Int) {
        val W = width; val H = height
        val overlayW = (W * 0.55f).toInt().coerceAtMost(300)
        val overlayH = when (inputMode) { InputMode.ADD_IDEAL -> 82; InputMode.ADD_GOAL -> 100; else -> 62 }
        val ox = (W - overlayW) / 2
        val oy = (H - overlayH) / 2

        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)

        // Dim background
        context.fill(0, 0, W, H, 0xAA000000.toInt())
        // Panel
        box(context, ox, oy, overlayW, overlayH, 0xFF1a1a1a.toInt(), 0xFF8a6a3a.toInt())

        val title = when (inputMode) {
            InputMode.ADD_IDEAL      -> tr("bbf.gm.identity.add_ideal")
            InputMode.ADD_FLAW       -> tr("bbf.gm.identity.add_flaw")
            InputMode.ADD_MOTIVATION -> tr("bbf.gm.identity.add_motivation")
            InputMode.ADD_GOAL       -> tr("bbf.gm.identity.add_goal")
            else -> ""
        }
        lbl(context, title, ox + 5, oy + 5, 0.7f, 0xD4AF37)

        // Field 1
        val f1Y = oy + 17
        if (inputMode == InputMode.ADD_GOAL) lbl(context, "§7${tr("bbf.gm.identity.goal.title")}", ox + 5, f1Y - 7, 0.5f, 0x888888)
        box(context, ox + 4, f1Y, overlayW - 8, 12, 0xFF111111.toInt(), 0xFF555555.toInt())
        lbl(context, "${inputBuffer}_", ox + 6, f1Y + 2, 0.7f, 0xFFFFFF)

        // Field 2 (goals only)
        if (inputMode == InputMode.ADD_GOAL) {
            val f2Y = f1Y + 20
            lbl(context, "§7${tr("bbf.gm.identity.goal.description")}", ox + 5, f2Y - 7, 0.5f, 0x888888)
            box(context, ox + 4, f2Y, overlayW - 8, 12, 0xFF111111.toInt(), 0xFF555555.toInt())
            lbl(context, "${inputBuffer2}_", ox + 6, f2Y + 2, 0.7f, 0xCCCCCC)
        }

        // Axis selector (ideals only)
        if (inputMode == InputMode.ADD_IDEAL) {
            val axY = f1Y + 16
            lbl(context, "§7${tr("bbf.gm.identity.axis")}:", ox + 4, axY + 1, 0.55f, 0x888888)
            val axes = IdealAlignment.values()
            val bw = (overlayW - 8) / axes.size
            axes.forEachIndexed { i, axis ->
                val bx = ox + 4 + i * bw
                val sel = axis == pendingAxis
                box(context, bx, axY, bw - 1, 10, if (sel) 0xFF3a2a1a.toInt() else 0xFF1a1a1a.toInt(), if (sel) 0xFFFFD700.toInt() else 0xFF555555.toInt())
                val m = context.matrices; m.push()
                m.translate((bx + (bw - 1) / 2).toFloat(), (axY + 2).toFloat(), 0f); m.scale(0.5f, 0.5f, 1f)
                val lbl = axis.name.take(4); val tw = textRenderer.getWidth(lbl)
                context.drawTextWithShadow(textRenderer, lbl, -(tw / 2), 0, axisColor(axis))
                m.pop()
                btns.add(Btn(bx, axY, bw - 1, 10) { pendingAxis = axis })
            }
        }

        // Buttons
        val btnY = oy + overlayH - 13
        btn(context, mouseX, mouseY, ox + 4, btnY, 50, 10, "§a${tr("bbf.gm.button.apply")}") { confirmInput() }
        btn(context, mouseX, mouseY, ox + overlayW - 54, btnY, 50, 10, "§c${tr("bbf.gm.button.cancel")}") {
            inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""
        }

        context.matrices.pop()
    }

    private fun confirmInput() {
        val text = inputBuffer.trim()
        if (text.isEmpty()) { inputMode = InputMode.NONE; return }
        when (inputMode) {
            InputMode.ADD_IDEAL -> {
                sendAddIdeal(text, pendingAxis)
                ideals.add(omc.boundbyfate.client.state.ClientIdeal("pending_${System.currentTimeMillis()}", text, pendingAxis, pendingAxis.isCompatibleWith(currentAlignment())))
            }
            InputMode.ADD_FLAW -> {
                sendAddFlaw(text)
                flaws.add(omc.boundbyfate.client.state.ClientFlaw("pending_${System.currentTimeMillis()}", text))
            }
            InputMode.ADD_MOTIVATION -> {
                sendAddMotivation(text)
                motivations.add(omc.boundbyfate.client.state.ClientMotivation("pending_${System.currentTimeMillis()}", text, true, true))
            }
            InputMode.ADD_GOAL -> {
                sendAddGoal(text, inputBuffer2.trim(), null)
                goals.add(omc.boundbyfate.client.state.ClientGoal("pending_${System.currentTimeMillis()}", text, inputBuffer2.trim(), null, "ACTIVE", 0, emptyList()))
            }
            else -> {}
        }
        inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""
    }

    // ═════════════════════════════════════════════════════════════════════════
    // NETWORK
    // ═════════════════════════════════════════════════════════════════════════
    private fun applyAll() {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("set")
        buf.writeInt(alignLawChaos); buf.writeInt(alignGoodEvil); buf.writeString("GM edit")
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_ALIGNMENT, buf)
        statusMsg = "§a${tr("bbf.gm.status.applied")}"; statusTimer = 1f
    }

    private fun sendAddIdeal(text: String, axis: IdealAlignment) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("add"); buf.writeString(""); buf.writeString(text); buf.writeString(axis.name)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDEAL, buf)
    }
    private fun sendRemoveIdeal(id: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("remove"); buf.writeString(id); buf.writeString(""); buf.writeString("")
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDEAL, buf)
    }
    private fun sendAddFlaw(text: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("add"); buf.writeString(""); buf.writeString(text)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_FLAW, buf)
    }
    private fun sendRemoveFlaw(id: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("remove"); buf.writeString(id); buf.writeString("")
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_FLAW, buf)
    }
    private fun sendAddMotivation(text: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("add"); buf.writeString(""); buf.writeString(text)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_MOTIVATION, buf)
    }
    private fun sendRemoveMotivation(id: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("remove"); buf.writeString(id); buf.writeString("")
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_MOTIVATION, buf)
    }
    private fun sendHandleProposal(proposalId: String, action: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString(action); buf.writeString(proposalId)
        ClientPlayNetworking.send(BbfPackets.GM_HANDLE_PROPOSAL, buf)
    }
    private fun sendAddGoal(title: String, description: String, motivationId: String?) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("add"); buf.writeString("")
        buf.writeString(title); buf.writeString(description); buf.writeString(motivationId ?: "")
        buf.writeString(""); buf.writeInt(0)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
    }
    private fun sendGoalAction(goalId: String, action: String, description: String, taskStatus: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString(action); buf.writeString(goalId)
        buf.writeString(""); buf.writeString(description); buf.writeString(""); buf.writeString(taskStatus); buf.writeInt(0)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═════════════════════════════════════════════════════════════════════════
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        for (b in btns.reversed()) {
            if (mx in b.x..(b.x + b.w) && my in b.y..(b.y + b.h)) { b.action(); return true }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (inputMode != InputMode.NONE) {
            when (keyCode) {
                256 -> { inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""; return true }
                257, 335 -> { confirmInput(); return true }
                259 -> { if (inputBuffer.isNotEmpty()) inputBuffer = inputBuffer.dropLast(1); return true }
            }
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (inputMode != InputMode.NONE && inputBuffer.length < 120) { inputBuffer += chr; return true }
        return super.charTyped(chr, modifiers)
    }

    override fun shouldPause() = false

    // ═════════════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ═════════════════════════════════════════════════════════════════════════
    private fun axisColor(axis: IdealAlignment): Int = when (axis) {
        IdealAlignment.GOOD -> 0x55FF55; IdealAlignment.EVIL -> 0xFF5555
        IdealAlignment.LAWFUL -> 0x5555FF; IdealAlignment.CHAOTIC -> 0xFFAA00
        IdealAlignment.NEUTRAL_GE -> 0xAAAAAA; IdealAlignment.NEUTRAL_LC -> 0xCCCCCC
        IdealAlignment.TRUE_NEUTRAL -> 0x888888; IdealAlignment.ANY -> 0x666666
    }

    private fun truncate(text: String, maxPx: Int, scale: Float): String {
        val max = (maxPx / scale).toInt()
        if (textRenderer.getWidth(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && textRenderer.getWidth("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private fun btn(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, label: String, action: () -> Unit) {
        btns.add(Btn(x, y, w, h, action))
        val hov = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = if (hov) 0xCC4a3a2a.toInt() else 0xCC2b2321.toInt()
        val bd = if (hov) 0xFFd4a96a.toInt() else 0xFF6b5a3e.toInt()
        context.fill(x, y, x + w, y + h, bg)
        context.fill(x, y, x + w, y + 1, bd); context.fill(x, y + h - 1, x + w, y + h, bd)
        context.fill(x, y, x + 1, y + h, bd); context.fill(x + w - 1, y, x + w, y + h, bd)
        val m = context.matrices; m.push()
        m.translate((x + w / 2).toFloat(), (y + h / 2 - 3).toFloat(), 0f); m.scale(0.75f, 0.75f, 1f)
        val tw = textRenderer.getWidth(label)
        context.drawTextWithShadow(textRenderer, label, -(tw / 2), 0, 0xFFFFFF)
        m.pop()
    }

    private fun lbl(context: DrawContext, text: String, x: Int, y: Int, scale: Float, color: Int) {
        val m = context.matrices; m.push()
        m.translate(x.toFloat(), y.toFloat(), 0f); m.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color); m.pop()
    }

    private fun box(context: DrawContext, x: Int, y: Int, w: Int, h: Int, bg: Int, border: Int) {
        context.fill(x, y, x + w, y + h, bg)
        context.fill(x, y, x + w, y + 1, border); context.fill(x, y + h - 1, x + w, y + h, border)
        context.fill(x, y, x + 1, y + h, border); context.fill(x + w - 1, y, x + w, y + h, border)
    }
}
