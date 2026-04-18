
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

    val playerName: String get() = snapshot.playerName

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
    private enum class InputMode { NONE, ADD_IDEAL, ADD_FLAW, ADD_MOTIVATION, ADD_GOAL, EDIT_GOAL, ADD_TASK, EDIT_TASK }
    private var inputMode = InputMode.NONE
    private var inputBuffer = ""
    private var inputBuffer2 = ""
    private var inputFocusField = 0  // 0 = field1, 1 = field2
    private var pendingAxis: IdealAlignment = IdealAlignment.ANY
    private var editingGoalId: String? = null
    private var editingTaskId: String? = null
    private var pendingGoalStatus: String = "ACTIVE"
    private var pendingMotivationId: String? = null
    private var taskListScroll = 0
    private var draggedTaskIndex: Int? = null
    private var draggedTaskY: Int = 0
    private var dragStartY: Int = 0
    private var isDragging: Boolean = false

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
            box(context, x, fy, w - 13, rowH - 1, 0xCC1a1a1a.toInt(), 0xFF3a3a3a.toInt())
            lbl(context, "[$axisShort]", x + 2, fy + 1, 0.5f, axisCol)
            lbl(context, truncate(ideal.text, w - 36, 0.6f), x + 22, fy + 1, 0.6f, textColor)
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
            box(context, x, fy, w - 13, rowH - 1, 0xCC1a1a1a.toInt(), 0xFF3a3a3a.toInt())
            lbl(context, truncate(flaw.text, w - 22, 0.6f), x + 2, fy + 1, 0.6f, 0xCCCCCC)
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
            box(context, x, fy, w - 13, rowH - 1, 0xCC1a1a1a.toInt(), 0xFF3a3a3a.toInt())
            lbl(context, tag, x + 2, fy + 1, 0.5f, if (mot.addedByGm) 0x666666 else 0x55AAFF)
            lbl(context, truncate(mot.text, w - 36, 0.6f), x + 22, fy + 1, 0.6f, 0xCCCCCC)
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
            inputFocusField = 0; pendingMotivationId = null
        }
        val listY = y + 11; val rowH = 22
        val visible = goals.drop(goalScroll).take((h - 11) / rowH)
        visible.forEachIndexed { i, goal ->
            val gy = listY + i * rowH
            val statusColor = when (goal.status) { "COMPLETED" -> 0x55FF55; "FAILED" -> 0xFF5555; "CANCELLED" -> 0x888888; else -> 0xFFFFFF }
            val statusIcon = when (goal.status) { "COMPLETED" -> "§a✓"; "FAILED" -> "§c✗"; "CANCELLED" -> "§7○"; else -> "§e▶" }
            val hovered = mouseX in x..(x + w - 14) && mouseY in gy..(gy + rowH - 1)
            val boxBg = if (hovered) 0xCC2a2a2a.toInt() else 0xCC1a1a1a.toInt()
            val boxBd = if (hovered) 0xFF8a6a3a.toInt() else 0xFF3a3a3a.toInt()
            box(context, x, gy, w - 13, rowH - 2, boxBg, boxBd)
            lbl(context, statusIcon, x + 2, gy + 2, 0.65f, statusColor)
            lbl(context, truncate(goal.title, w - 30, 0.65f), x + 12, gy + 2, 0.65f, statusColor)
            val task = goal.currentTask
            if (task != null) lbl(context, "§8□ ${truncate(task.description, w - 20, 0.5f)}", x + 4, gy + 12, 0.5f, 0x666666)
            // Click on box → open edit
            btns.add(Btn(x, gy, w - 13, rowH - 2) {
                editingGoalId = goal.id
                inputBuffer = goal.title
                inputBuffer2 = goal.description
                pendingGoalStatus = goal.status
                pendingMotivationId = goal.motivationId
                inputFocusField = 0
                inputMode = InputMode.EDIT_GOAL
            })
            // Delete button
            btn(context, mouseX, mouseY, x + w - 11, gy + 2, 10, 9, "§7X") {
                sendGoalAction(goal.id, "remove", "", "")
                goals.removeIf { it.id == goal.id }
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
        val overlayW = (W * 0.55f).toInt().coerceAtMost(320)
        val overlayH = when (inputMode) {
            InputMode.ADD_IDEAL -> 78
            InputMode.ADD_GOAL -> 160
            InputMode.EDIT_GOAL -> minOf(H - 40, 240)  // Adaptive height, max 240
            InputMode.ADD_TASK, InputMode.EDIT_TASK -> 100
            else -> 62
        }
        val ox = (W - overlayW) / 2
        val oy = (H - overlayH) / 2

        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)

        context.fill(0, 0, W, H, 0xAA000000.toInt())
        box(context, ox, oy, overlayW, overlayH, 0xFF1a1a1a.toInt(), 0xFF8a6a3a.toInt())

        val title = when (inputMode) {
            InputMode.ADD_IDEAL      -> tr("bbf.gm.identity.add_ideal")
            InputMode.ADD_FLAW       -> tr("bbf.gm.identity.add_flaw")
            InputMode.ADD_MOTIVATION -> tr("bbf.gm.identity.add_motivation")
            InputMode.ADD_GOAL       -> tr("bbf.gm.identity.add_goal")
            InputMode.EDIT_GOAL      -> tr("bbf.gm.identity.edit_goal")
            InputMode.ADD_TASK       -> tr("bbf.gm.identity.add_task")
            InputMode.EDIT_TASK      -> tr("bbf.gm.identity.edit_task")
            else -> ""
        }
        lbl(context, title, ox + 5, oy + 5, 0.7f, 0xD4AF37)

        var curY = oy + 17

        when (inputMode) {
            InputMode.ADD_IDEAL -> {
                // Text field
                renderField(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, inputBuffer, inputFocusField == 0, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, 12) { inputFocusField = 0 })
                curY += 18

                // Axis selector — without TRUE_NEUTRAL, with distinct neutral labels
                val axes = IdealAlignment.values().filter { it != IdealAlignment.TRUE_NEUTRAL }
                val bw = (overlayW - 8) / axes.size
                axes.forEachIndexed { i, axis ->
                    val bx = ox + 4 + i * bw
                    val sel = axis == pendingAxis
                    box(context, bx, curY, bw - 1, 12, if (sel) 0xFF3a2a1a.toInt() else 0xFF222222.toInt(), if (sel) 0xFFFFD700.toInt() else 0xFF444444.toInt())
                    val m = context.matrices; m.push()
                    m.translate((bx + (bw - 1) / 2).toFloat(), (curY + 3).toFloat(), 0f); m.scale(0.5f, 0.5f, 1f)
                    val axisLabel = axisShortLabel(axis)
                    val tw = textRenderer.getWidth(axisLabel)
                    context.drawTextWithShadow(textRenderer, axisLabel, -(tw / 2), 0, axisColor(axis))
                    m.pop()
                    btns.add(Btn(bx, curY, bw - 1, 12) { pendingAxis = axis })
                }
                curY += 16
            }

            InputMode.ADD_GOAL, InputMode.EDIT_GOAL -> {
                // Title field
                lbl(context, "§7${tr("bbf.gm.identity.goal.title")}", ox + 5, curY - 6, 0.5f, 0x888888)
                renderField(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, inputBuffer, inputFocusField == 0, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, 12) { inputFocusField = 0 })
                curY += 20

                // Description field
                lbl(context, "§7${tr("bbf.gm.identity.goal.description")}", ox + 5, curY - 6, 0.5f, 0x888888)
                renderField(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, inputBuffer2, inputFocusField == 1, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, 12) { inputFocusField = 1 })
                curY += 20

                // Motivation selector
                lbl(context, "§7${tr("bbf.gm.identity.goal.motivation")}", ox + 5, curY - 6, 0.5f, 0x888888)
                val motBtnW = (overlayW - 8)
                val motName = pendingMotivationId?.let { mid -> motivations.find { it.id == mid }?.text }
                    ?: tr("bbf.gm.none")
                btn(context, mouseX, mouseY, ox + 4, curY, motBtnW, 10, "§7${truncate(motName, motBtnW - 10, 0.65f)}") {
                    // Cycle through motivations
                    val active = motivations.filter { it.isActive }
                    val idx = active.indexOfFirst { it.id == pendingMotivationId }
                    pendingMotivationId = if (idx < active.size - 1) active[idx + 1].id else null
                }
                curY += 16

                // Status selector (edit only)
                if (inputMode == InputMode.EDIT_GOAL) {
                    val statuses = listOf("ACTIVE", "COMPLETED", "FAILED", "CANCELLED")
                    val statusLabels = listOf("§e▶ ${tr("bbf.gm.identity.status.active")}", "§a✓ ${tr("bbf.gm.identity.status.completed")}", "§c✗ ${tr("bbf.gm.identity.status.failed")}", "§7○ ${tr("bbf.gm.identity.status.cancelled")}")
                    val sbw = (overlayW - 8) / statuses.size
                    statuses.forEachIndexed { i, s ->
                        val bx = ox + 4 + i * sbw
                        val sel = s == pendingGoalStatus
                        box(context, bx, curY, sbw - 1, 12, if (sel) 0xFF3a2a1a.toInt() else 0xFF222222.toInt(), if (sel) 0xFFFFD700.toInt() else 0xFF444444.toInt())
                        val m = context.matrices; m.push()
                        m.translate((bx + (sbw - 1) / 2).toFloat(), (curY + 3).toFloat(), 0f); m.scale(0.5f, 0.5f, 1f)
                        val tw = textRenderer.getWidth(statusLabels[i])
                        context.drawTextWithShadow(textRenderer, statusLabels[i], -(tw / 2), 0, 0xFFFFFF)
                        m.pop()
                        btns.add(Btn(bx, curY, sbw - 1, 12) { pendingGoalStatus = s })
                    }
                    curY += 16

                    // Task list
                    lbl(context, "§7${tr("bbf.gm.identity.tasks")}", ox + 5, curY, 0.6f, 0x888888)
                    btn(context, mouseX, mouseY, ox + overlayW - 15, curY - 1, 11, 9, "§a+") {
                        inputMode = InputMode.ADD_TASK
                        inputBuffer = ""
                        inputBuffer2 = ""
                        inputFocusField = 0
                    }
                    curY += 12

                    val goal = editingGoalId?.let { gid -> goals.find { it.id == gid } }
                    val taskListH = overlayH - curY + oy - 30  // Dynamic height based on available space
                    val taskRowH = 14
                    val sortedTasks = goal?.tasks?.sortedBy { it.order } ?: emptyList()
                    val visibleTasks = sortedTasks.drop(taskListScroll).take((taskListH / taskRowH).coerceAtLeast(1))

                    visibleTasks.forEachIndexed { i, task ->
                        val taskIndex = sortedTasks.indexOf(task)
                        val ty = curY + i * taskRowH
                        val taskNum = taskIndex + 1
                        
                        // Skip rendering if this is the dragged task (will render it separately)
                        if (isDragging && draggedTaskIndex == taskIndex) {
                            return@forEachIndexed
                        }
                        
                        val statusIcon = when (task.status) {
                            "COMPLETED" -> "§a✓"
                            "FAILED" -> "§c✗"
                            "CANCELLED" -> "§7○"
                            else -> "§e▶"
                        }
                        val hovered = mouseX in (ox + 4)..(ox + overlayW - 16) && mouseY in ty..(ty + taskRowH - 2)
                        val boxBg = if (hovered) 0xCC2a2a2a.toInt() else 0xCC1a1a1a.toInt()
                        val boxBd = if (hovered) 0xFF8a6a3a.toInt() else 0xFF3a3a3a.toInt()
                        box(context, ox + 4, ty, overlayW - 20, taskRowH - 2, boxBg, boxBd)
                        lbl(context, "§7$taskNum.", ox + 6, ty + 2, 0.55f, 0x888888)
                        lbl(context, statusIcon, ox + 16, ty + 2, 0.55f, 0xFFFFFF)
                        lbl(context, truncate(task.description, overlayW - 50, 0.55f), ox + 24, ty + 2, 0.55f, 0xCCCCCC)
                        
                        // Click to edit task (only if not dragging)
                        if (!isDragging) {
                            btns.add(Btn(ox + 4, ty, overlayW - 20, taskRowH - 2) {
                                editingTaskId = task.id
                                inputBuffer = task.description
                                inputBuffer2 = task.goalDescriptionOverride
                                pendingGoalStatus = task.status
                                inputFocusField = 0
                                inputMode = InputMode.EDIT_TASK
                            })
                        }
                    }

                    // Render dragged task on top with visual feedback
                    if (isDragging && draggedTaskIndex != null) {
                        val draggedTask = sortedTasks.getOrNull(draggedTaskIndex!!)
                        if (draggedTask != null) {
                            val taskNum = draggedTaskIndex!! + 1
                            val statusIcon = when (draggedTask.status) {
                                "COMPLETED" -> "§a✓"
                                "FAILED" -> "§c✗"
                                "CANCELLED" -> "§7○"
                                else -> "§e▶"
                            }
                            // Render with transparency and different color
                            box(context, ox + 4, draggedTaskY, overlayW - 20, taskRowH - 2, 0xAA3a3a1a.toInt(), 0xFFFFD700.toInt())
                            lbl(context, "§7$taskNum.", ox + 6, draggedTaskY + 2, 0.55f, 0x888888)
                            lbl(context, statusIcon, ox + 16, draggedTaskY + 2, 0.55f, 0xFFFFFF)
                            lbl(context, truncate(draggedTask.description, overlayW - 50, 0.55f), ox + 24, draggedTaskY + 2, 0.55f, 0xFFFFFF)
                        }
                    }

                    curY += taskListH + 4
                }
            }

            InputMode.ADD_TASK, InputMode.EDIT_TASK -> {
                // Task title
                lbl(context, "§7${tr("bbf.gm.identity.goal.title")}", ox + 5, curY - 6, 0.5f, 0x888888)
                renderField(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, inputBuffer, inputFocusField == 0, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, 12) { inputFocusField = 0 })
                curY += 20

                // Goal description when this task is active
                lbl(context, "§7${tr("bbf.gm.identity.task.goal_desc")}", ox + 5, curY - 6, 0.5f, 0x888888)
                renderField(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, inputBuffer2, inputFocusField == 1, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, 12) { inputFocusField = 1 })
                curY += 20

                // Status (edit only)
                if (inputMode == InputMode.EDIT_TASK) {
                    val statuses = listOf("CURRENT", "COMPLETED", "FAILED", "CANCELLED")
                    val sbw = (overlayW - 8) / statuses.size
                    statuses.forEachIndexed { i, s ->
                        val bx = ox + 4 + i * sbw
                        val sel = s == pendingGoalStatus
                        box(context, bx, curY, sbw - 1, 12, if (sel) 0xFF3a2a1a.toInt() else 0xFF222222.toInt(), if (sel) 0xFFFFD700.toInt() else 0xFF444444.toInt())
                        val m = context.matrices; m.push()
                        m.translate((bx + (sbw - 1) / 2).toFloat(), (curY + 3).toFloat(), 0f); m.scale(0.5f, 0.5f, 1f)
                        val tw = textRenderer.getWidth(s.take(4))
                        context.drawTextWithShadow(textRenderer, s.take(4), -(tw / 2), 0, 0xCCCCCC)
                        m.pop()
                        btns.add(Btn(bx, curY, sbw - 1, 12) { pendingGoalStatus = s })
                    }
                    curY += 16

                    // Delete button
                    btn(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, 10, "§c${tr("bbf.gm.button.delete")}") {
                        val taskId = editingTaskId
                        val goalId = editingGoalId
                        if (taskId != null && goalId != null) {
                            sendDeleteTask(goalId, taskId)
                            // Return to edit goal mode
                            inputMode = InputMode.EDIT_GOAL
                            val goal = goals.find { it.id == goalId }
                            if (goal != null) {
                                inputBuffer = goal.title
                                inputBuffer2 = goal.description
                                pendingGoalStatus = goal.status
                                pendingMotivationId = goal.motivationId
                            }
                        }
                    }
                    curY += 14
                }
            }

            else -> {
                // Simple single field
                renderField(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, inputBuffer, true, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, 12) { inputFocusField = 0 })
                curY += 16
            }
        }

        // Confirm / Cancel
        btn(context, mouseX, mouseY, ox + 4, curY + 2, 50, 10, "§a${tr("bbf.gm.button.apply")}") { confirmInput() }
        btn(context, mouseX, mouseY, ox + overlayW - 54, curY + 2, 50, 10, "§c${tr("bbf.gm.button.cancel")}") {
            inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""
        }

        context.matrices.pop()
    }

    private fun renderField(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, value: String, focused: Boolean, placeholder: String) {
        val bd = if (focused) 0xFF8a6a3a.toInt() else 0xFF444444.toInt()
        box(context, x, y, w, 12, 0xFF111111.toInt(), bd)
        val display = if (focused) "${value}_" else value.ifEmpty { "§8$placeholder" }
        lbl(context, display, x + 2, y + 2, 0.7f, if (focused) 0xFFFFFF else 0xAAAAAA)
    }

    private fun confirmInput() {
        val text = inputBuffer.trim()
        when (inputMode) {
            InputMode.ADD_IDEAL -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; return }
                sendAddIdeal(text, pendingAxis)
                ideals.add(omc.boundbyfate.client.state.ClientIdeal("pending_${System.currentTimeMillis()}", text, pendingAxis, pendingAxis.isCompatibleWith(currentAlignment())))
            }
            InputMode.ADD_FLAW -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; return }
                sendAddFlaw(text)
                flaws.add(omc.boundbyfate.client.state.ClientFlaw("pending_${System.currentTimeMillis()}", text))
            }
            InputMode.ADD_MOTIVATION -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; return }
                sendAddMotivation(text)
                motivations.add(omc.boundbyfate.client.state.ClientMotivation("pending_${System.currentTimeMillis()}", text, true, true))
            }
            InputMode.ADD_GOAL -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; return }
                sendAddGoal(text, inputBuffer2.trim(), pendingMotivationId)
                goals.add(omc.boundbyfate.client.state.ClientGoal("pending_${System.currentTimeMillis()}", text, inputBuffer2.trim(), pendingMotivationId, "ACTIVE", 0, emptyList()))
            }
            InputMode.EDIT_GOAL -> {
                val goalId = editingGoalId ?: run { inputMode = InputMode.NONE; return }
                // Send status change if needed
                val goal = goals.find { it.id == goalId }
                if (goal != null && goal.status != pendingGoalStatus) {
                    val action = when (pendingGoalStatus) { "COMPLETED" -> "complete"; "FAILED" -> "fail"; else -> "task" }
                    sendGoalAction(goalId, action, inputBuffer2.trim(), pendingGoalStatus)
                }
                // Update title/description
                if (text.isNotEmpty()) {
                    sendGoalAction(goalId, "update", inputBuffer2.trim(), "")
                }
                val idx = goals.indexOfFirst { it.id == goalId }
                if (idx >= 0) goals[idx] = goals[idx].copy(title = text.ifEmpty { goals[idx].title }, description = inputBuffer2.trim(), status = pendingGoalStatus)
            }
            InputMode.ADD_TASK -> {
                val goalId = editingGoalId ?: run { inputMode = InputMode.NONE; return }
                if (text.isEmpty()) { inputMode = InputMode.NONE; return }
                sendAddTask(goalId, text, inputBuffer2.trim())
                // Return to edit goal mode
                inputMode = InputMode.EDIT_GOAL
                val goal = goals.find { it.id == goalId }
                if (goal != null) {
                    inputBuffer = goal.title
                    inputBuffer2 = goal.description
                    pendingGoalStatus = goal.status
                    pendingMotivationId = goal.motivationId
                }
                return
            }
            InputMode.EDIT_TASK -> {
                val taskId = editingTaskId ?: run { inputMode = InputMode.NONE; return }
                val goalId = editingGoalId ?: run { inputMode = InputMode.NONE; return }
                if (text.isNotEmpty()) {
                    sendEditTask(goalId, taskId, text, inputBuffer2.trim(), pendingGoalStatus)
                }
                // Return to edit goal mode
                inputMode = InputMode.EDIT_GOAL
                val goal = goals.find { it.id == goalId }
                if (goal != null) {
                    inputBuffer = goal.title
                    inputBuffer2 = goal.description
                    pendingGoalStatus = goal.status
                    pendingMotivationId = goal.motivationId
                }
                return
            }
            else -> {}
        }
        inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""; inputFocusField = 0
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
    private fun sendAddTask(goalId: String, description: String, goalDescOverride: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("add_task"); buf.writeString(goalId)
        buf.writeString(description); buf.writeString(goalDescOverride); buf.writeString(""); buf.writeString("CURRENT"); buf.writeInt(0)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
    }
    private fun sendEditTask(goalId: String, taskId: String, description: String, goalDescOverride: String, status: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("edit_task"); buf.writeString(goalId)
        buf.writeString(taskId); buf.writeString(description); buf.writeString(goalDescOverride); buf.writeString(status); buf.writeInt(0)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
    }
    private fun sendDeleteTask(goalId: String, taskId: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("delete_task"); buf.writeString(goalId)
        buf.writeString(taskId); buf.writeString(""); buf.writeString(""); buf.writeString(""); buf.writeInt(0)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
    }
    private fun sendReorderTask(goalId: String, taskId: String, newOrder: Int) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName); buf.writeString("reorder_task"); buf.writeString(goalId)
        buf.writeString(taskId); buf.writeString(""); buf.writeString(""); buf.writeString(""); buf.writeInt(newOrder)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═════════════════════════════════════════════════════════════════════════
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        
        // Check if clicking on a task in EDIT_GOAL mode to start drag
        if (inputMode == InputMode.EDIT_GOAL && button == 0) {
            val goal = editingGoalId?.let { gid -> goals.find { it.id == gid } }
            if (goal != null) {
                val W = width; val H = height
                val overlayW = (W * 0.55f).toInt().coerceAtMost(320)
                val overlayH = 280
                val ox = (W - overlayW) / 2
                val oy = (H - overlayH) / 2
                
                // Calculate task list position
                var curY = oy + 17 + 20 + 20 + 16 + 16 + 12  // Title + Desc + Motivation + Status + "Tasks" label
                val taskListH = 80
                val taskRowH = 14
                val sortedTasks = goal.tasks.sortedBy { it.order }
                val visibleTasks = sortedTasks.drop(taskListScroll).take(taskListH / taskRowH)
                
                visibleTasks.forEachIndexed { i, task ->
                    val ty = curY + i * taskRowH
                    if (mx in (ox + 4)..(ox + overlayW - 16) && my in ty..(ty + taskRowH - 2)) {
                        // Start dragging this task
                        draggedTaskIndex = sortedTasks.indexOf(task)
                        draggedTaskY = ty
                        dragStartY = my
                        isDragging = true
                        return true
                    }
                }
            }
        }
        
        for (b in btns.reversed()) {
            if (mx in b.x..(b.x + b.w) && my in b.y..(b.y + b.h)) { b.action(); return true }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDragging && draggedTaskIndex != null) {
            val my = mouseY.toInt()
            draggedTaskY += (my - dragStartY)
            dragStartY = my
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDragging && draggedTaskIndex != null) {
            val goal = editingGoalId?.let { gid -> goals.find { it.id == gid } }
            if (goal != null) {
                val W = width; val H = height
                val overlayW = (W * 0.55f).toInt().coerceAtMost(320)
                val overlayH = 280
                val ox = (W - overlayW) / 2
                val oy = (H - overlayH) / 2
                
                // Calculate task list position
                var curY = oy + 17 + 20 + 20 + 16 + 16 + 12
                val taskRowH = 14
                val sortedTasks = goal.tasks.sortedBy { it.order }
                
                // Calculate new position based on draggedTaskY
                val relativeY = draggedTaskY - curY
                val newOrder = (relativeY / taskRowH).coerceIn(0, sortedTasks.size - 1)
                
                // Send reorder if position changed
                if (newOrder != draggedTaskIndex) {
                    val draggedTask = sortedTasks[draggedTaskIndex!!]
                    sendReorderTask(goal.id, draggedTask.id, newOrder)
                    
                    // Update local state immediately for responsive UI
                    val updatedTasks = sortedTasks.toMutableList()
                    updatedTasks.removeAt(draggedTaskIndex!!)
                    updatedTasks.add(newOrder, draggedTask)
                    val reorderedTasks = updatedTasks.mapIndexed { index, task -> task.copy(order = index) }
                    
                    val goalIndex = goals.indexOfFirst { it.id == goal.id }
                    if (goalIndex >= 0) {
                        goals[goalIndex] = goals[goalIndex].copy(tasks = reorderedTasks)
                    }
                }
            }
            
            // Reset drag state
            isDragging = false
            draggedTaskIndex = null
            draggedTaskY = 0
            dragStartY = 0
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (inputMode != InputMode.NONE) {
            when (keyCode) {
                256 -> { inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""; inputFocusField = 0; return true } // ESC
                257, 335 -> { confirmInput(); return true } // Enter
                258 -> { // Tab — switch fields
                    inputFocusField = if (inputFocusField == 0) 1 else 0
                    return true
                }
                259 -> { // Backspace
                    if (inputFocusField == 0) { if (inputBuffer.isNotEmpty()) inputBuffer = inputBuffer.dropLast(1) }
                    else { if (inputBuffer2.isNotEmpty()) inputBuffer2 = inputBuffer2.dropLast(1) }
                    return true
                }
            }
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (inputMode != InputMode.NONE) {
            if (inputFocusField == 0 && inputBuffer.length < 120) inputBuffer += chr
            else if (inputFocusField == 1 && inputBuffer2.length < 200) inputBuffer2 += chr
            return true
        }
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

    private fun axisShortLabel(axis: IdealAlignment): String = when (axis) {
        IdealAlignment.GOOD -> tr("bbf.alignment.axis.good")
        IdealAlignment.EVIL -> tr("bbf.alignment.axis.evil")
        IdealAlignment.LAWFUL -> tr("bbf.alignment.axis.lawful")
        IdealAlignment.CHAOTIC -> tr("bbf.alignment.axis.chaotic")
        IdealAlignment.NEUTRAL_GE -> tr("bbf.alignment.axis.neutral_ge.short")
        IdealAlignment.NEUTRAL_LC -> tr("bbf.alignment.axis.neutral_lc.short")
        IdealAlignment.TRUE_NEUTRAL -> tr("bbf.alignment.axis.true_neutral")
        IdealAlignment.ANY -> tr("bbf.alignment.axis.any")
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
