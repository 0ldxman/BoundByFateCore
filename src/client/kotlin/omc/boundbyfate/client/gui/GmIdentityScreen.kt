
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
import omc.boundbyfate.client.state.ClientGoalTask
import omc.boundbyfate.client.state.ClientIdeal
import omc.boundbyfate.client.state.ClientMotivation
import omc.boundbyfate.client.state.ClientProposal
import omc.boundbyfate.client.state.GmPlayerSnapshot
import omc.boundbyfate.network.BbfPackets
import java.util.UUID

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
    private var inputBuffer2Scroll = 0  // Scroll position for description field
    private var inputBuffer2Lines = mutableListOf<String>()  // Lines for description field
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
    private var lastClickTime: Long = 0
    private var lastClickedTaskIndex: Int? = null

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
            InputMode.EDIT_GOAL -> minOf(H - 40, 280)  // Increased for textarea
            InputMode.ADD_TASK, InputMode.EDIT_TASK -> 130  // Increased for textarea
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

                // Description field (textarea)
                lbl(context, "§7${tr("bbf.gm.identity.goal.description")}", ox + 5, curY - 6, 0.5f, 0x888888)
                val descHeight = 50
                inputBuffer2Scroll = renderTextArea(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, descHeight, inputBuffer2, inputFocusField == 1, inputBuffer2Scroll, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, descHeight) { inputFocusField = 1 })
                curY += descHeight + 6

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
                    val taskListH = overlayH - (curY - oy) - 30  // Dynamic height based on available space
                    val taskRowH = 14
                    val sortedTasks = goal?.tasks?.sortedBy { it.order } ?: emptyList()
                    val maxVisibleTasks = (taskListH / taskRowH).coerceAtLeast(1)
                    val visibleTasks = sortedTasks.drop(taskListScroll).take(maxVisibleTasks)

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
                            "PENDING" -> "§8□"
                            else -> "§e▶"
                        }
                        val hovered = mouseX in (ox + 4)..(ox + overlayW - 16) && mouseY in ty..(ty + taskRowH - 2)
                        val boxBg = if (hovered) 0xCC2a2a2a.toInt() else 0xCC1a1a1a.toInt()
                        val boxBd = if (hovered) 0xFF8a6a3a.toInt() else 0xFF3a3a3a.toInt()
                        box(context, ox + 4, ty, overlayW - 20, taskRowH - 2, boxBg, boxBd)
                        lbl(context, "§7$taskNum.", ox + 6, ty + 2, 0.55f, 0x888888)
                        lbl(context, statusIcon, ox + 16, ty + 2, 0.55f, 0xFFFFFF)
                        lbl(context, truncate(task.description, overlayW - 50, 0.55f), ox + 24, ty + 2, 0.55f, 0xCCCCCC)
                        
                        // Double-click to edit task (handled in mouseClicked)
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
                                "PENDING" -> "§8□"
                                else -> "§e▶"
                            }
                            // Render with transparency and different color
                            box(context, ox + 4, draggedTaskY, overlayW - 20, taskRowH - 2, 0xAA3a3a1a.toInt(), 0xFFFFD700.toInt())
                            lbl(context, "§7$taskNum.", ox + 6, draggedTaskY + 2, 0.55f, 0x888888)
                            lbl(context, statusIcon, ox + 16, draggedTaskY + 2, 0.55f, 0xFFFFFF)
                            lbl(context, truncate(draggedTask.description, overlayW - 50, 0.55f), ox + 24, draggedTaskY + 2, 0.55f, 0xFFFFFF)
                        }
                    }
                    
                    // Scroll buttons for tasks
                    if (sortedTasks.size > maxVisibleTasks) {
                        val scrollX = ox + overlayW - 14
                        if (taskListScroll > 0) {
                            btn(context, mouseX, mouseY, scrollX, curY, 10, 9, "§7▲") { taskListScroll-- }
                        }
                        if (taskListScroll < sortedTasks.size - maxVisibleTasks) {
                            btn(context, mouseX, mouseY, scrollX, curY + taskListH - 9, 10, 9, "§7▼") { taskListScroll++ }
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

                // Goal description when this task is active (textarea)
                lbl(context, "§7${tr("bbf.gm.identity.task.goal_desc")}", ox + 5, curY - 6, 0.5f, 0x888888)
                val goalDescHeight = 40
                inputBuffer2Scroll = renderTextArea(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, goalDescHeight, inputBuffer2, inputFocusField == 1, inputBuffer2Scroll, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, goalDescHeight) { inputFocusField = 1 })
                curY += goalDescHeight + 6

                // Status (edit only)
                if (inputMode == InputMode.EDIT_TASK) {
                    val statuses = listOf("PENDING", "CURRENT", "COMPLETED", "FAILED", "CANCELLED")
                    val sbw = (overlayW - 8) / statuses.size
                    statuses.forEachIndexed { i, s ->
                        val bx = ox + 4 + i * sbw
                        val sel = s == pendingGoalStatus
                        box(context, bx, curY, sbw - 1, 12, if (sel) 0xFF3a2a1a.toInt() else 0xFF222222.toInt(), if (sel) 0xFFFFD700.toInt() else 0xFF444444.toInt())
                        val m = context.matrices; m.push()
                        m.translate((bx + (sbw - 1) / 2).toFloat(), (curY + 3).toFloat(), 0f); m.scale(0.45f, 0.45f, 1f)
                        val label = when(s) {
                            "PENDING" -> "§8PND"
                            "CURRENT" -> "§eCUR"
                            "COMPLETED" -> "§aDON"
                            "FAILED" -> "§cFAIL"
                            "CANCELLED" -> "§7CAN"
                            else -> s.take(3)
                        }
                        val tw = textRenderer.getWidth(label)
                        context.drawTextWithShadow(textRenderer, label, -(tw / 2), 0, 0xFFFFFF)
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

    private fun renderTextArea(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, text: String, focused: Boolean, scroll: Int, placeholder: String): Int {
        val bd = if (focused) 0xFF8a6a3a.toInt() else 0xFF444444.toInt()
        box(context, x, y, w, h, 0xFF111111.toInt(), bd)
        
        // Split text into lines that fit the width
        val maxCharsPerLine = ((w - 20) / (textRenderer.getWidth("W") * 0.6f)).toInt().coerceAtLeast(10)
        val lines = mutableListOf<String>()
        
        if (text.isEmpty() && !focused) {
            lines.add("§8$placeholder")
        } else {
            // Split by newlines first, then wrap long lines
            text.split("\n").forEach { paragraph ->
                if (paragraph.isEmpty()) {
                    lines.add("")
                } else {
                    val words = paragraph.split(" ")
                    var currentLine = ""
                    
                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        if (testLine.length <= maxCharsPerLine) {
                            currentLine = testLine
                        } else {
                            if (currentLine.isNotEmpty()) lines.add(currentLine)
                            currentLine = word
                            // If single word is too long, split it
                            while (currentLine.length > maxCharsPerLine) {
                                lines.add(currentLine.substring(0, maxCharsPerLine))
                                currentLine = currentLine.substring(maxCharsPerLine)
                            }
                        }
                    }
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                }
            }
        }
        
        // Calculate visible lines
        val lineHeight = 9
        val visibleLines = ((h - 6) / lineHeight).coerceAtLeast(1)
        val maxScroll = (lines.size - visibleLines).coerceAtLeast(0)
        val actualScroll = scroll.coerceIn(0, maxScroll)
        
        // Render visible lines
        val m = context.matrices
        m.push()
        m.translate((x + 3).toFloat(), (y + 3).toFloat(), 0f)
        m.scale(0.6f, 0.6f, 1f)
        
        lines.drop(actualScroll).take(visibleLines).forEachIndexed { i, line ->
            val lineY = (i * lineHeight * 1.67f).toInt()
            val color = if (text.isEmpty() && !focused) 0x666666 else 0xCCCCCC
            context.drawTextWithShadow(textRenderer, line, 0, lineY, color)
        }
        
        // Cursor
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0L && lines.isNotEmpty()) {
            val cursorLine = lines.size - 1
            if (cursorLine >= actualScroll && cursorLine < actualScroll + visibleLines) {
                val cursorY = ((cursorLine - actualScroll) * lineHeight * 1.67f).toInt()
                val cursorX = textRenderer.getWidth(lines[cursorLine])
                context.drawTextWithShadow(textRenderer, "_", cursorX, cursorY, 0xFFFFFF)
            }
        }
        
        m.pop()
        
        // Scrollbar if needed
        if (lines.size > visibleLines) {
            val scrollbarX = x + w - 8
            val scrollbarH = h - 4
            val scrollbarThumbH = ((visibleLines.toFloat() / lines.size) * scrollbarH).toInt().coerceAtLeast(10)
            val scrollbarThumbY = y + 2 + ((actualScroll.toFloat() / maxScroll) * (scrollbarH - scrollbarThumbH)).toInt()
            
            // Scrollbar track
            context.fill(scrollbarX, y + 2, scrollbarX + 6, y + h - 2, 0xFF222222.toInt())
            // Scrollbar thumb
            context.fill(scrollbarX + 1, scrollbarThumbY, scrollbarX + 5, scrollbarThumbY + scrollbarThumbH, 0xFF666666.toInt())
            
            // Scroll buttons
            btns.add(Btn(scrollbarX, y + 2, 6, 8) { if (actualScroll > 0) inputBuffer2Scroll-- })
            btns.add(Btn(scrollbarX, y + h - 10, 6, 8) { if (actualScroll < maxScroll) inputBuffer2Scroll++ })
        }
        
        return actualScroll
    }

    private fun confirmInput() {
        val text = inputBuffer.trim()
        when (inputMode) {
            InputMode.ADD_IDEAL -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                sendAddIdeal(text, pendingAxis)
            }
            InputMode.ADD_FLAW -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                sendAddFlaw(text)
            }
            InputMode.ADD_MOTIVATION -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                sendAddMotivation(text)
            }
            InputMode.ADD_GOAL -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                sendAddGoal(text, inputBuffer2.trim(), pendingMotivationId)
            }
            InputMode.EDIT_GOAL -> {
                val goalId = editingGoalId ?: run { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                // Update goal in local state
                val idx = goals.indexOfFirst { it.id == goalId }
                if (idx >= 0) {
                    goals[idx] = goals[idx].copy(
                        title = text.ifEmpty { goals[idx].title }, 
                        description = inputBuffer2.trim(), 
                        status = pendingGoalStatus,
                        motivationId = pendingMotivationId
                    )
                }
            }
            InputMode.ADD_TASK -> {
                val goalId = editingGoalId ?: run { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                if (text.isEmpty()) { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                sendAddTask(goalId, text, inputBuffer2.trim())
                // Return to edit goal mode
                inputMode = InputMode.EDIT_GOAL
                inputBuffer2Scroll = 0
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
                val taskId = editingTaskId ?: run { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                val goalId = editingGoalId ?: run { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                if (text.isNotEmpty()) {
                    sendEditTask(goalId, taskId, text, inputBuffer2.trim(), pendingGoalStatus)
                }
                // Return to edit goal mode
                inputMode = InputMode.EDIT_GOAL
                inputBuffer2Scroll = 0
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
        inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""; inputFocusField = 0; inputBuffer2Scroll = 0
    }

    // ═════════════════════════════════════════════════════════════════════════
    // NETWORK
    // ═════════════════════════════════════════════════════════════════════════
    private fun applyAll() {
        // Send alignment
        val alignBuf = PacketByteBufs.create()
        alignBuf.writeString(snapshot.playerName); alignBuf.writeString("set")
        alignBuf.writeInt(alignLawChaos); alignBuf.writeInt(alignGoodEvil); alignBuf.writeString("GM edit")
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_ALIGNMENT, alignBuf)
        
        // Send ideals diff
        val originalIdeals = snapshot.ideals.map { it.id to it }.toMap()
        val currentIdeals = ideals.map { it.id to it }.toMap()
        
        // Removed ideals
        (originalIdeals.keys - currentIdeals.keys).forEach { id ->
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName); buf.writeString("remove"); buf.writeString(id); buf.writeString(""); buf.writeString("")
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDEAL, buf)
        }
        
        // Added ideals
        (currentIdeals.keys - originalIdeals.keys).forEach { id ->
            val ideal = currentIdeals[id]!!
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName); buf.writeString("add"); buf.writeString(""); buf.writeString(ideal.text); buf.writeString(ideal.alignmentAxis.name)
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDEAL, buf)
        }
        
        // Send flaws diff
        val originalFlaws = snapshot.flaws.map { it.id to it }.toMap()
        val currentFlaws = flaws.map { it.id to it }.toMap()
        
        // Removed flaws
        (originalFlaws.keys - currentFlaws.keys).forEach { id ->
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName); buf.writeString("remove"); buf.writeString(id); buf.writeString("")
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_FLAW, buf)
        }
        
        // Added flaws
        (currentFlaws.keys - originalFlaws.keys).forEach { id ->
            val flaw = currentFlaws[id]!!
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName); buf.writeString("add"); buf.writeString(""); buf.writeString(flaw.text)
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_FLAW, buf)
        }
        
        // Send motivations diff
        val originalMotivations = snapshot.motivations.map { it.id to it }.toMap()
        val currentMotivations = motivations.map { it.id to it }.toMap()
        
        // Removed motivations
        (originalMotivations.keys - currentMotivations.keys).forEach { id ->
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName); buf.writeString("remove"); buf.writeString(id); buf.writeString("")
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_MOTIVATION, buf)
        }
        
        // Added motivations
        (currentMotivations.keys - originalMotivations.keys).forEach { id ->
            val motivation = currentMotivations[id]!!
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName); buf.writeString("add"); buf.writeString(""); buf.writeString(motivation.text)
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_MOTIVATION, buf)
        }
        
        // Send proposals diff (only rejections, accepts are handled separately)
        val originalProposals = snapshot.proposals.map { it.id }.toSet()
        val currentProposals = proposals.map { it.id }.toSet()
        
        // Rejected proposals
        (originalProposals - currentProposals).forEach { id ->
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName); buf.writeString("reject"); buf.writeString(id)
            ClientPlayNetworking.send(BbfPackets.GM_HANDLE_PROPOSAL, buf)
        }
        
        // Send goals diff
        val originalGoals = snapshot.goals.map { it.id to it }.toMap()
        val currentGoals = goals.map { it.id to it }.toMap()
        
        // Removed goals
        (originalGoals.keys - currentGoals.keys).forEach { id ->
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName); buf.writeString("remove"); buf.writeString(id)
            buf.writeString(""); buf.writeString(""); buf.writeString(""); buf.writeString(""); buf.writeInt(0)
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
        }
        
        // Added goals
        (currentGoals.keys - originalGoals.keys).forEach { id ->
            val goal = currentGoals[id]!!
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName); buf.writeString("add"); buf.writeString("")
            buf.writeString(goal.title); buf.writeString(goal.description); buf.writeString(goal.motivationId ?: "")
            buf.writeString(""); buf.writeInt(goal.tasks.size)
            goal.tasks.sortedBy { it.order }.forEach { task ->
                buf.writeString(task.description)
            }
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
        }
        
        // Updated goals (check for changes in existing goals)
        (originalGoals.keys intersect currentGoals.keys).forEach { id ->
            val original = originalGoals[id]!!
            val current = currentGoals[id]!!
            
            // Check if goal itself changed
            if (original.title != current.title || original.description != current.description || 
                original.status != current.status || original.motivationId != current.motivationId) {
                val buf = PacketByteBufs.create()
                buf.writeString(snapshot.playerName); buf.writeString("update"); buf.writeString(id)
                buf.writeString(current.title); buf.writeString(current.description); buf.writeString(current.motivationId ?: "")
                buf.writeString(current.status); buf.writeInt(0)
                ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
            }
            
            // Check tasks diff
            val originalTasks = original.tasks.map { it.id to it }.toMap()
            val currentTasks = current.tasks.map { it.id to it }.toMap()
            
            // Removed tasks
            (originalTasks.keys - currentTasks.keys).forEach { taskId ->
                val buf = PacketByteBufs.create()
                buf.writeString(snapshot.playerName); buf.writeString("delete_task"); buf.writeString(id)
                buf.writeString(taskId); buf.writeString(""); buf.writeString(""); buf.writeString(""); buf.writeInt(0)
                ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
            }
            
            // Added tasks
            (currentTasks.keys - originalTasks.keys).forEach { taskId ->
                val task = currentTasks[taskId]!!
                val buf = PacketByteBufs.create()
                buf.writeString(snapshot.playerName); buf.writeString("add_task"); buf.writeString(id)
                buf.writeString(task.description); buf.writeString(task.goalDescriptionOverride); buf.writeString(""); buf.writeString("PENDING"); buf.writeInt(0)
                ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
            }
            
            // Updated tasks
            (originalTasks.keys intersect currentTasks.keys).forEach { taskId ->
                val originalTask = originalTasks[taskId]!!
                val currentTask = currentTasks[taskId]!!
                
                if (originalTask.description != currentTask.description || 
                    originalTask.goalDescriptionOverride != currentTask.goalDescriptionOverride ||
                    originalTask.status != currentTask.status ||
                    originalTask.order != currentTask.order) {
                    
                    // If only order changed, use reorder
                    if (originalTask.order != currentTask.order && 
                        originalTask.description == currentTask.description &&
                        originalTask.goalDescriptionOverride == currentTask.goalDescriptionOverride &&
                        originalTask.status == currentTask.status) {
                        val buf = PacketByteBufs.create()
                        buf.writeString(snapshot.playerName); buf.writeString("reorder_task"); buf.writeString(id)
                        buf.writeString(taskId); buf.writeString(""); buf.writeString(""); buf.writeString(""); buf.writeInt(currentTask.order)
                        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
                    } else {
                        // Otherwise use edit_task
                        val buf = PacketByteBufs.create()
                        buf.writeString(snapshot.playerName); buf.writeString("edit_task"); buf.writeString(id)
                        buf.writeString(taskId); buf.writeString(currentTask.description); buf.writeString(currentTask.goalDescriptionOverride)
                        buf.writeString(currentTask.status); buf.writeInt(0)
                        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
                    }
                }
            }
        }
        
        // Update snapshot to reflect current state so next Apply works correctly
        val updatedSnapshot = snapshot.copy(
            alignmentCoords = omc.boundbyfate.api.identity.AlignmentCoordinates(alignLawChaos, alignGoodEvil),
            ideals = ideals.toList(),
            flaws = flaws.toList(),
            motivations = motivations.toList(),
            proposals = proposals.toList(),
            goals = goals.toList()
        )
        // Replace the snapshot reference (this is a bit hacky but works for our use case)
        // Ideally we'd reload from server, but this is faster and avoids race conditions
        client?.setScreen(GmIdentityScreen(updatedSnapshot))
        
        statusMsg = "§a${tr("bbf.gm.status.applied")}"; statusTimer = 1f
    }

    // These methods now only modify local state, not send to server
    private fun sendAddIdeal(text: String, axis: IdealAlignment) {
        val id = java.util.UUID.randomUUID().toString()
        ideals.add(ClientIdeal(id, text, axis, axis.isCompatibleWith(currentAlignment())))
    }
    private fun sendRemoveIdeal(id: String) {
        ideals.removeIf { it.id == id }
    }
    private fun sendAddFlaw(text: String) {
        val id = java.util.UUID.randomUUID().toString()
        flaws.add(ClientFlaw(id, text))
    }
    private fun sendRemoveFlaw(id: String) {
        flaws.removeIf { it.id == id }
    }
    private fun sendAddMotivation(text: String) {
        val id = java.util.UUID.randomUUID().toString()
        motivations.add(ClientMotivation(id, text, true, true))
    }
    private fun sendRemoveMotivation(id: String) {
        motivations.removeIf { it.id == id }
    }
    private fun sendHandleProposal(proposalId: String, action: String) {
        if (action == "accept") {
            val proposal = proposals.find { it.id == proposalId }
            if (proposal != null) {
                val id = java.util.UUID.randomUUID().toString()
                motivations.add(ClientMotivation(id, proposal.text, false, true))
            }
        }
        proposals.removeIf { it.id == proposalId }
    }
    private fun sendAddGoal(title: String, description: String, motivationId: String?) {
        val id = java.util.UUID.randomUUID().toString()
        goals.add(ClientGoal(id, title, description, motivationId, "ACTIVE", 0, emptyList()))
    }
    private fun sendGoalAction(goalId: String, action: String, description: String, taskStatus: String) {
        val goalIndex = goals.indexOfFirst { it.id == goalId }
        if (goalIndex >= 0) {
            val goal = goals[goalIndex]
            when (action) {
                "remove" -> goals.removeAt(goalIndex)
                "complete" -> goals[goalIndex] = goal.copy(status = "COMPLETED")
                "fail" -> goals[goalIndex] = goal.copy(status = "FAILED")
            }
        }
    }
    private fun sendAddTask(goalId: String, description: String, goalDescOverride: String) {
        val goalIndex = goals.indexOfFirst { it.id == goalId }
        if (goalIndex >= 0) {
            val goal = goals[goalIndex]
            val taskId = java.util.UUID.randomUUID().toString()
            val newOrder = (goal.tasks.maxOfOrNull { it.order } ?: -1) + 1
            val newTask = ClientGoalTask(taskId, description, goalDescOverride, "PENDING", newOrder)
            goals[goalIndex] = goal.copy(tasks = goal.tasks + newTask)
        }
    }
    private fun sendEditTask(goalId: String, taskId: String, description: String, goalDescOverride: String, status: String) {
        val goalIndex = goals.indexOfFirst { it.id == goalId }
        if (goalIndex >= 0) {
            val goal = goals[goalIndex]
            val updatedTasks = goal.tasks.map { task ->
                if (task.id == taskId) task.copy(description = description, goalDescriptionOverride = goalDescOverride, status = status)
                else task
            }
            goals[goalIndex] = goal.copy(tasks = updatedTasks)
        }
    }
    private fun sendDeleteTask(goalId: String, taskId: String) {
        val goalIndex = goals.indexOfFirst { it.id == goalId }
        if (goalIndex >= 0) {
            val goal = goals[goalIndex]
            val newTasks = goal.tasks.filter { it.id != taskId }
            val reorderedTasks = newTasks.sortedBy { it.order }.mapIndexed { index, task -> task.copy(order = index) }
            goals[goalIndex] = goal.copy(tasks = reorderedTasks)
        }
    }
    private fun sendReorderTask(goalId: String, taskId: String, newOrder: Int) {
        val goalIndex = goals.indexOfFirst { it.id == goalId }
        if (goalIndex >= 0) {
            val goal = goals[goalIndex]
            val task = goal.tasks.find { it.id == taskId } ?: return
            val sortedTasks = goal.tasks.sortedBy { it.order }.toMutableList()
            sortedTasks.removeIf { it.id == taskId }
            sortedTasks.add(newOrder.coerceIn(0, sortedTasks.size), task)
            val reorderedTasks = sortedTasks.mapIndexed { index, t -> t.copy(order = index) }
            goals[goalIndex] = goal.copy(tasks = reorderedTasks)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═════════════════════════════════════════════════════════════════════════
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        
        // Check if clicking on a task in EDIT_GOAL mode
        if (inputMode == InputMode.EDIT_GOAL && button == 0) {
            val goal = editingGoalId?.let { gid -> goals.find { it.id == gid } }
            if (goal != null) {
                val W = width; val H = height
                val overlayW = (W * 0.55f).toInt().coerceAtMost(320)
                val overlayH = minOf(H - 40, 280)  // Match renderInputOverlay
                val ox = (W - overlayW) / 2
                val oy = (H - overlayH) / 2
                
                // Calculate task list position - MUST match renderInputOverlay EXACTLY
                // We need to replicate the EXACT same curY calculation from renderInputOverlay
                var curY = oy + 17  // Title label
                // Title field
                curY += 20  // field height (12) + spacing
                // Description label + textarea
                curY += 6   // label spacing
                curY += 50  // textarea height
                curY += 6   // spacing after textarea
                // Motivation label + selector
                curY += 6   // label spacing  
                curY += 10  // selector height
                curY += 16  // spacing after selector
                // Status selector (only in EDIT_GOAL mode)
                curY += 12  // selector height
                curY += 16  // spacing after selector
                // Tasks label + add button
                curY += 12  // label + button height + spacing
                // Now curY is at the start of task list - this MUST match renderInputOverlay
                
                val taskListH = overlayH - (curY - oy) - 30
                val taskRowH = 14
                val sortedTasks = goal.tasks.sortedBy { it.order }
                val maxVisibleTasks = (taskListH / taskRowH).coerceAtLeast(1)
                val visibleTasks = sortedTasks.drop(taskListScroll).take(maxVisibleTasks)
                
                visibleTasks.forEachIndexed { i, task ->
                    val taskIndex = sortedTasks.indexOf(task)
                    val ty = curY + i * taskRowH
                    val hitbox = (mx in (ox + 4)..(ox + overlayW - 16) && my in ty..(ty + taskRowH - 2))
                    
                    if (hitbox) {
                        val currentTime = System.currentTimeMillis()
                        // Double click detection (within 300ms)
                        if (lastClickedTaskIndex == taskIndex && (currentTime - lastClickTime) < 300) {
                            // Double click - open edit
                            editingTaskId = task.id
                            inputBuffer = task.description
                            inputBuffer2 = task.goalDescriptionOverride
                            pendingGoalStatus = task.status
                            inputFocusField = 0
                            inputMode = InputMode.EDIT_TASK
                            lastClickedTaskIndex = null
                            lastClickTime = 0
                        } else {
                            // Single click - prepare for potential drag
                            lastClickedTaskIndex = taskIndex
                            lastClickTime = currentTime
                            draggedTaskIndex = taskIndex
                            draggedTaskY = ty
                            dragStartY = my
                        }
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
        if (draggedTaskIndex != null && !isDragging) {
            // Start dragging if mouse moved more than 3 pixels
            val my = mouseY.toInt()
            if (Math.abs(my - dragStartY) > 3) {
                isDragging = true
            }
        }
        
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
                val overlayH = minOf(H - 40, 280)
                val ox = (W - overlayW) / 2
                val oy = (H - overlayH) / 2
                
                // Calculate task list position - MUST match mouseClicked and renderInputOverlay
                var curY = oy + 17  // Title label
                curY += 20  // Title field + spacing
                curY += 6   // Description label spacing
                curY += 50  // Description textarea height
                curY += 6   // Spacing after description
                curY += 6   // Motivation label spacing
                curY += 10  // Motivation selector height
                curY += 16  // Spacing after motivation
                curY += 12  // Status selector height
                curY += 16  // Spacing after status
                curY += 12  // Tasks label + add button
                
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        // Scroll textarea if focused on description field
        if (inputMode != InputMode.NONE && inputFocusField == 1) {
            if (amount > 0) {
                inputBuffer2Scroll = (inputBuffer2Scroll - 1).coerceAtLeast(0)
            } else if (amount < 0) {
                inputBuffer2Scroll++
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (inputMode != InputMode.NONE) {
            when (keyCode) {
                256 -> { inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""; inputFocusField = 0; inputBuffer2Scroll = 0; return true } // ESC
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
