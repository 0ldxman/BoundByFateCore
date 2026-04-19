
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
    private enum class InputMode { NONE, ADD_IDEAL, EDIT_IDEAL, ADD_FLAW, EDIT_FLAW, ADD_MOTIVATION, EDIT_MOTIVATION, ADD_GOAL, EDIT_GOAL, ADD_TASK, EDIT_TASK, PICK_MOTIVATION }
    private var inputMode = InputMode.NONE
    private var inputBuffer = ""
    private var inputBuffer2 = ""
    private var inputFocusField = 0  // 0 = field1, 1 = field2
    private var inputBuffer2Scroll = 0  // Scroll position for description field
    private var inputBuffer2Lines = mutableListOf<String>()  // Lines for description field
    private var pendingAxis: IdealAlignment = IdealAlignment.ANY
    private var editingGoalId: String? = null
    private var editingTaskId: String? = null
    private var editingIdealId: String? = null
    private var editingFlawId: String? = null
    private var editingMotivationId: String? = null
    private var pendingGoalStatus: String = "ACTIVE"
    private var pendingMotivationId: String? = null
    private var prevInputMode: InputMode = InputMode.NONE  // for overlay-on-overlay
    private var taskListScroll = 0
    private var draggedTaskIndex: Int? = null
    private var draggedTaskY: Int = 0
    private var dragStartY: Int = 0
    private var taskListStartY: Int = 0  // absolute Y where task list starts (set during render)
    private var isDragging: Boolean = false
    private var lastClickTime: Long = 0
    private var lastClickedTaskIndex: Int? = null

    private var statusMsg = ""; private var statusTimer = 0f
    private var scrollAnimTime = 0f  // for marquee text animation

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
        scrollAnimTime += delta * 0.016f  // ~1 unit per second

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

        // Left: ideals (top half) + flaws (bottom half) — separate boxes with gap
        val gap = 4
        val leftHalf = (bodyH - gap) / 2
        val idealsBoxH = leftHalf
        val flawsBoxH  = bodyH - leftHalf - gap
        box(context, leftX, bodyY, colW, idealsBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        box(context, leftX, bodyY + idealsBoxH + gap, colW, flawsBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderIdeals(context, mouseX, mouseY, leftX + 3, bodyY + 3, colW - 6, idealsBoxH - 6)
        renderFlaws(context, mouseX, mouseY, leftX + 3, bodyY + idealsBoxH + gap + 3, colW - 6, flawsBoxH - 6)

        // Center: alignment diagram
        renderAlignmentDiagram(context, mouseX, mouseY, centerX + 3, bodyY + 3, colW - 6, bodyH - 6)

        // Right: motivations (top half) + goals (bottom half) — separate boxes with gap
        val rightHalf = (bodyH - gap) / 2
        val motivBoxH = rightHalf
        val goalsBoxH = bodyH - rightHalf - gap
        box(context, rightX, bodyY, colW, motivBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        box(context, rightX, bodyY + motivBoxH + gap, colW, goalsBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderMotivations(context, mouseX, mouseY, rightX + 3, bodyY + 3, colW - 6, motivBoxH - 6)
        renderGoals(context, mouseX, mouseY, rightX + 3, bodyY + motivBoxH + gap + 3, colW - 6, goalsBoxH - 6)

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

        // Border warning — only if any ideals are actually incompatible with current alignment
        val hasConflictingIdeals = ideals.any { !it.alignmentAxis.isCompatibleWith(current) }
        if (hasConflictingIdeals) {
            val warnText = "§6⚠ ${tr("bbf.gm.identity.wavering")}"
            val warnW = (textRenderer.getWidth(warnText) * 0.55f).toInt()
            lbl(context, warnText, x + w / 2 - warnW / 2, geY + 24, 0.55f, 0xFFAA00)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LEFT: IDEALS + FLAWS
    // ═════════════════════════════════════════════════════════════════════════
    private fun renderIdeals(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.ideals"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 11, y - 1, 11, 9, "§a+") {
            inputMode = InputMode.ADD_IDEAL; inputBuffer = ""; inputBuffer2Scroll = 0; pendingAxis = IdealAlignment.ANY
        }
        val listY = y + 11; val rowH = 11
        val visible = ideals.drop(idealScroll).take((h - 11) / rowH)
        visible.forEachIndexed { i, ideal ->
            val fy = listY + i * rowH
            val compatible = ideal.alignmentAxis.isCompatibleWith(currentAlignment())
            val textColor = if (compatible) 0xCCCCCC else 0xFF5555
            val axisShort = ideal.alignmentAxis.name.take(3)
            val axisCol = axisColor(ideal.alignmentAxis)
            val hovered = mouseX in x..(x + w - 14) && mouseY in fy..(fy + rowH - 1)
            box(context, x, fy, w - 13, rowH - 1, if (hovered) 0xCC2a2a2a.toInt() else 0xCC1a1a1a.toInt(), if (hovered) 0xFF8a6a3a.toInt() else 0xFF3a3a3a.toInt())
            lbl(context, "[$axisShort]", x + 2, fy + 1, 0.5f, axisCol)
            lbl(context, marquee(ideal.text, w - 36, 0.6f), x + 22, fy + 1, 0.6f, textColor)
            // Click to edit
            btns.add(Btn(x, fy, w - 13, rowH - 1) {
                editingIdealId = ideal.id
                inputBuffer = ideal.text
                pendingAxis = ideal.alignmentAxis
                inputFocusField = 0; inputBuffer2Scroll = 0
                inputMode = InputMode.EDIT_IDEAL
            })
            btn(context, mouseX, mouseY, x + w - 11, fy, 10, 9, "§cX") {
                ideals.removeIf { it.id == ideal.id }
            }
        }
        if (idealScroll > 0) btn(context, mouseX, mouseY, x + w - 10, listY, 10, 9, "§7▲") { idealScroll-- }
        val maxS = (ideals.size - (h - 11) / rowH).coerceAtLeast(0)
        if (idealScroll < maxS) btn(context, mouseX, mouseY, x + w - 10, listY + h - 20, 10, 9, "§7▼") { idealScroll++ }
    }

    private fun renderFlaws(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.flaws"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 11, y - 1, 11, 9, "§a+") {
            inputMode = InputMode.ADD_FLAW; inputBuffer = ""; inputBuffer2Scroll = 0
        }
        val listY = y + 11; val rowH = 11
        val visible = flaws.drop(flawScroll).take((h - 11) / rowH)
        visible.forEachIndexed { i, flaw ->
            val fy = listY + i * rowH
            val hovered = mouseX in x..(x + w - 14) && mouseY in fy..(fy + rowH - 1)
            box(context, x, fy, w - 13, rowH - 1, if (hovered) 0xCC2a2a2a.toInt() else 0xCC1a1a1a.toInt(), if (hovered) 0xFF8a6a3a.toInt() else 0xFF3a3a3a.toInt())
            lbl(context, marquee(flaw.text, w - 22, 0.6f), x + 2, fy + 1, 0.6f, 0xCCCCCC)
            // Click to edit
            btns.add(Btn(x, fy, w - 13, rowH - 1) {
                editingFlawId = flaw.id
                inputBuffer = flaw.text
                inputFocusField = 0; inputBuffer2Scroll = 0
                inputMode = InputMode.EDIT_FLAW
            })
            btn(context, mouseX, mouseY, x + w - 11, fy, 10, 9, "§cX") {
                flaws.removeIf { it.id == flaw.id }
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
            inputMode = InputMode.ADD_MOTIVATION; inputBuffer = ""; inputBuffer2Scroll = 0
        }
        val listY = y + 11; val rowH = 11
        val active = motivations.filter { it.isActive }
        val visible = active.drop(motivationScroll).take((h - 11) / rowH)
        visible.forEachIndexed { i, mot ->
            val fy = listY + i * rowH
            val tag = if (mot.addedByGm) "§8[GM]" else "§b[P]"
            val hovered = mouseX in x..(x + w - 14) && mouseY in fy..(fy + rowH - 1)
            box(context, x, fy, w - 13, rowH - 1, if (hovered) 0xCC2a2a2a.toInt() else 0xCC1a1a1a.toInt(), if (hovered) 0xFF8a6a3a.toInt() else 0xFF3a3a3a.toInt())
            lbl(context, tag, x + 2, fy + 1, 0.5f, if (mot.addedByGm) 0x666666 else 0x55AAFF)
            lbl(context, marquee(mot.text, w - 36, 0.6f), x + 22, fy + 1, 0.6f, 0xCCCCCC)
            // Click to edit
            btns.add(Btn(x, fy, w - 13, rowH - 1) {
                editingMotivationId = mot.id
                inputBuffer = mot.text
                inputFocusField = 0; inputBuffer2Scroll = 0
                inputMode = InputMode.EDIT_MOTIVATION
            })
            btn(context, mouseX, mouseY, x + w - 11, fy, 10, 9, "§cX") {
                motivations.removeIf { it.id == mot.id }
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
        val isSecondLayer = inputMode == InputMode.ADD_TASK || inputMode == InputMode.EDIT_TASK || inputMode == InputMode.PICK_MOTIVATION

        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)

        if (isSecondLayer) {
            // Draw the EDIT_GOAL overlay as background layer (Z+400)
            val savedMode = inputMode
            inputMode = InputMode.EDIT_GOAL
            renderOverlayContent(context, -9999, -9999, W, H, overlayW)
            inputMode = savedMode
            // Dim AFTER drawing background content so it covers text too
            context.fill(0, 0, W, H, 0xBB000000.toInt())
            context.matrices.push()
            context.matrices.translate(0f, 0f, 200f)  // Z+600 total for top overlay
            renderOverlayContent(context, mouseX, mouseY, W, H, overlayW)
            context.matrices.pop()
        } else {
            // Full dim for first-level overlays
            context.fill(0, 0, W, H, 0xAA000000.toInt())
            renderOverlayContent(context, mouseX, mouseY, W, H, overlayW)
        }

        context.matrices.pop()
    }

    private fun renderOverlayContent(context: DrawContext, mouseX: Int, mouseY: Int, W: Int, H: Int, overlayW: Int) {
        // EDIT_GOAL uses a special two-panel layout — wider
        if (inputMode == InputMode.EDIT_GOAL || inputMode == InputMode.ADD_GOAL) {
            renderGoalOverlay(context, mouseX, mouseY, W, H)
            return
        }

        val overlayH = when (inputMode) {
            InputMode.ADD_IDEAL, InputMode.EDIT_IDEAL -> 120
            InputMode.ADD_FLAW, InputMode.EDIT_FLAW -> 100
            InputMode.ADD_MOTIVATION, InputMode.EDIT_MOTIVATION -> 100
            InputMode.ADD_TASK, InputMode.EDIT_TASK -> 140
            InputMode.PICK_MOTIVATION -> minOf(H - 80, 200)
            else -> 62
        }
        val ox = (W - overlayW) / 2
        val oy = when (inputMode) {
            InputMode.ADD_TASK, InputMode.EDIT_TASK -> (H - overlayH) / 2 - 20
            InputMode.PICK_MOTIVATION -> (H - overlayH) / 2 + 20
            else -> (H - overlayH) / 2
        }

        box(context, ox, oy, overlayW, overlayH, 0xFF1a1a1a.toInt(), 0xFF8a6a3a.toInt())

        val title = when (inputMode) {
            InputMode.ADD_IDEAL      -> tr("bbf.gm.identity.add_ideal")
            InputMode.EDIT_IDEAL     -> tr("bbf.gm.identity.edit_ideal")
            InputMode.ADD_FLAW       -> tr("bbf.gm.identity.add_flaw")
            InputMode.EDIT_FLAW      -> tr("bbf.gm.identity.edit_flaw")
            InputMode.ADD_MOTIVATION -> tr("bbf.gm.identity.add_motivation")
            InputMode.EDIT_MOTIVATION -> tr("bbf.gm.identity.edit_motivation")
            InputMode.ADD_GOAL       -> tr("bbf.gm.identity.add_goal")
            InputMode.EDIT_GOAL      -> tr("bbf.gm.identity.edit_goal")
            InputMode.ADD_TASK       -> tr("bbf.gm.identity.add_task")
            InputMode.EDIT_TASK      -> tr("bbf.gm.identity.edit_task")
            InputMode.PICK_MOTIVATION -> tr("bbf.gm.identity.pick_motivation")
            else -> ""
        }
        lbl(context, title, ox + 5, oy + 5, 0.7f, 0xD4AF37)

        // Small delete button in top-right corner for edit modes
        when (inputMode) {
            InputMode.EDIT_IDEAL -> btn(context, mouseX, mouseY, ox + overlayW - 22, oy + 3, 18, 9, "§c✗") {
                val id = editingIdealId; if (id != null) ideals.removeIf { it.id == id }
                inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2Scroll = 0
            }
            InputMode.EDIT_FLAW -> btn(context, mouseX, mouseY, ox + overlayW - 22, oy + 3, 18, 9, "§c✗") {
                val id = editingFlawId; if (id != null) flaws.removeIf { it.id == id }
                inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2Scroll = 0
            }
            InputMode.EDIT_MOTIVATION -> btn(context, mouseX, mouseY, ox + overlayW - 22, oy + 3, 18, 9, "§c✗") {
                val id = editingMotivationId; if (id != null) motivations.removeIf { it.id == id }
                inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2Scroll = 0
            }
            InputMode.EDIT_GOAL -> btn(context, mouseX, mouseY, ox + overlayW - 22, oy + 3, 18, 9, "§c✗") {
                val id = editingGoalId; if (id != null) { goals.removeIf { it.id == id } }
                inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""; inputBuffer2Scroll = 0
            }
            else -> {}
        }

        var curY = oy + 17

        when (inputMode) {
            InputMode.ADD_IDEAL, InputMode.EDIT_IDEAL -> {
                // Text textarea
                val taH = 50
                inputBuffer2Scroll = renderTextArea(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, taH, inputBuffer, inputFocusField == 0, inputBuffer2Scroll, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, taH) { inputFocusField = 0 })
                curY += taH + 6

                // Axis selector
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

            InputMode.ADD_FLAW, InputMode.EDIT_FLAW -> {
                val taH = 50
                inputBuffer2Scroll = renderTextArea(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, taH, inputBuffer, inputFocusField == 0, inputBuffer2Scroll, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, taH) { inputFocusField = 0 })
                curY += taH + 6
            }

            InputMode.ADD_MOTIVATION, InputMode.EDIT_MOTIVATION -> {
                val taH = 50
                inputBuffer2Scroll = renderTextArea(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, taH, inputBuffer, inputFocusField == 0, inputBuffer2Scroll, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, taH) { inputFocusField = 0 })
                curY += taH + 6
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

            InputMode.PICK_MOTIVATION -> {
                // List of active motivations + "None" option
                val active = listOf(null) + motivations.filter { it.isActive }
                val rowH = 12
                active.forEachIndexed { i, mot ->
                    val fy = curY + i * rowH
                    val isSelected = mot?.id == pendingMotivationId
                    val hovered = mouseX in (ox + 4)..(ox + overlayW - 8) && mouseY in fy..(fy + rowH - 2)
                    val bg = when {
                        isSelected -> 0xFF3a2a1a.toInt()
                        hovered -> 0xCC2a2a2a.toInt()
                        else -> 0xCC1a1a1a.toInt()
                    }
                    val bd = if (isSelected) 0xFFFFD700.toInt() else if (hovered) 0xFF8a6a3a.toInt() else 0xFF3a3a3a.toInt()
                    box(context, ox + 4, fy, overlayW - 8, rowH - 2, bg, bd)
                    val label = if (mot == null) "§8— ${tr("bbf.gm.none")} —" else {
                        val tag = if (mot.addedByGm) "§8[GM] " else "§b[P] "
                        "$tag§7${truncate(mot.text, overlayW - 30, 0.6f)}"
                    }
                    lbl(context, label, ox + 6, fy + 2, 0.6f, 0xCCCCCC)
                    btns.add(Btn(ox + 4, fy, overlayW - 8, rowH - 2) {
                        pendingMotivationId = mot?.id
                        inputMode = prevInputMode
                    })
                }
                curY += active.size * rowH + 4
            }

            else -> {
                // Simple single field
                renderField(context, mouseX, mouseY, ox + 4, curY, overlayW - 8, inputBuffer, true, "")
                btns.add(Btn(ox + 4, curY, overlayW - 8, 12) { inputFocusField = 0 })
                curY += 16
            }
        }

        // Confirm / Cancel — not shown for PICK_MOTIVATION (selection is immediate)
        if (inputMode != InputMode.PICK_MOTIVATION) {
            btn(context, mouseX, mouseY, ox + 4, curY + 2, 50, 10, "§a${tr("bbf.gm.button.apply")}") { confirmInput() }
            btn(context, mouseX, mouseY, ox + overlayW - 54, curY + 2, 50, 10, "§c${tr("bbf.gm.button.cancel")}") {
                if (inputMode == InputMode.ADD_TASK || inputMode == InputMode.EDIT_TASK) {
                    // Return to EDIT_GOAL
                    inputMode = InputMode.EDIT_GOAL
                    val goal = editingGoalId?.let { gid -> goals.find { it.id == gid } }
                    if (goal != null) { inputBuffer = goal.title; inputBuffer2 = goal.description; pendingGoalStatus = goal.status; pendingMotivationId = goal.motivationId }
                } else {
                    inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""
                }
            }
        } else {
            // Cancel for PICK_MOTIVATION
            btn(context, mouseX, mouseY, ox + overlayW - 54, curY + 2, 50, 10, "§c${tr("bbf.gm.button.cancel")}") {
                inputMode = prevInputMode
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GOAL OVERLAY — two-panel layout (left: details, right: tasks)
    // ═════════════════════════════════════════════════════════════════════════
    private fun renderGoalOverlay(context: DrawContext, mouseX: Int, mouseY: Int, W: Int, H: Int) {
        val panelW = (W * 0.38f).toInt().coerceAtMost(280)
        val gap = 6
        val totalW = panelW * 2 + gap
        val panelH = minOf(H - 40, 300)
        val startX = (W - totalW) / 2
        val startY = (H - panelH) / 2

        val lx = startX          // left panel x
        val rx = startX + panelW + gap  // right panel x

        // Draw both panels
        box(context, lx, startY, panelW, panelH, 0xFF1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        box(context, rx, startY, panelW, panelH, 0xFF1a1a1a.toInt(), 0xFF8a6a3a.toInt())

        // ── LEFT PANEL ────────────────────────────────────────────────────────
        val title = if (inputMode == InputMode.EDIT_GOAL) tr("bbf.gm.identity.edit_goal") else tr("bbf.gm.identity.add_goal")
        lbl(context, title, lx + 5, startY + 5, 0.7f, 0xD4AF37)

        // Delete button (edit only)
        if (inputMode == InputMode.EDIT_GOAL) {
            btn(context, mouseX, mouseY, lx + panelW - 22, startY + 3, 18, 9, "§c✗") {
                val id = editingGoalId; if (id != null) goals.removeIf { it.id == id }
                inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""; inputBuffer2Scroll = 0
            }
        }

        var ly = startY + 17

        // Title field
        lbl(context, "§7${tr("bbf.gm.identity.goal.title")}", lx + 5, ly - 6, 0.5f, 0x888888)
        renderField(context, mouseX, mouseY, lx + 4, ly, panelW - 8, inputBuffer, inputFocusField == 0, "")
        btns.add(Btn(lx + 4, ly, panelW - 8, 12) { inputFocusField = 0 })
        ly += 20

        // Description textarea
        lbl(context, "§7${tr("bbf.gm.identity.goal.description")}", lx + 5, ly - 6, 0.5f, 0x888888)
        val descH = 60
        inputBuffer2Scroll = renderTextArea(context, mouseX, mouseY, lx + 4, ly, panelW - 8, descH, inputBuffer2, inputFocusField == 1, inputBuffer2Scroll, "")
        btns.add(Btn(lx + 4, ly, panelW - 8, descH) { inputFocusField = 1 })
        ly += descH + 6

        // Motivation selector
        lbl(context, "§7${tr("bbf.gm.identity.goal.motivation")}", lx + 5, ly - 6, 0.5f, 0x888888)
        val motName = pendingMotivationId?.let { mid -> motivations.find { it.id == mid }?.text } ?: tr("bbf.gm.none")
        btn(context, mouseX, mouseY, lx + 4, ly, panelW - 8, 10, "§7${truncate(motName, panelW - 18, 0.65f)}") {
            prevInputMode = inputMode; inputMode = InputMode.PICK_MOTIVATION
        }
        ly += 16

        // Status selector (edit only)
        if (inputMode == InputMode.EDIT_GOAL) {
            val statuses = listOf("ACTIVE", "COMPLETED", "FAILED", "CANCELLED")
            val statusLabels = listOf("§e▶ ${tr("bbf.gm.identity.status.active")}", "§a✓ ${tr("bbf.gm.identity.status.completed")}", "§c✗ ${tr("bbf.gm.identity.status.failed")}", "§7○ ${tr("bbf.gm.identity.status.cancelled")}")
            val sbw = (panelW - 8) / statuses.size
            statuses.forEachIndexed { i, s ->
                val bx = lx + 4 + i * sbw
                val sel = s == pendingGoalStatus
                box(context, bx, ly, sbw - 1, 12, if (sel) 0xFF3a2a1a.toInt() else 0xFF222222.toInt(), if (sel) 0xFFFFD700.toInt() else 0xFF444444.toInt())
                val m = context.matrices; m.push()
                m.translate((bx + (sbw - 1) / 2).toFloat(), (ly + 3).toFloat(), 0f); m.scale(0.5f, 0.5f, 1f)
                val tw = textRenderer.getWidth(statusLabels[i])
                context.drawTextWithShadow(textRenderer, statusLabels[i], -(tw / 2), 0, 0xFFFFFF)
                m.pop()
                btns.add(Btn(bx, ly, sbw - 1, 12) { pendingGoalStatus = s })
            }
            ly += 16
        }

        // Apply / Cancel buttons at bottom of left panel
        val btnY = startY + panelH - 14
        btn(context, mouseX, mouseY, lx + 4, btnY, 50, 10, "§a${tr("bbf.gm.button.apply")}") { confirmInput() }
        btn(context, mouseX, mouseY, lx + panelW - 54, btnY, 50, 10, "§c${tr("bbf.gm.button.cancel")}") {
            inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = ""
        }

        // ── RIGHT PANEL ───────────────────────────────────────────────────────
        lbl(context, "§7${tr("bbf.gm.identity.tasks")}", rx + 5, startY + 5, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, rx + panelW - 15, startY + 3, 11, 9, "§a+") {
            inputMode = InputMode.ADD_TASK; inputBuffer = ""; inputBuffer2 = ""; inputFocusField = 0
        }

        val taskAreaY = startY + 17
        val taskAreaH = panelH - 17 - 4
        val taskRowH = 14
        val goal = editingGoalId?.let { gid -> goals.find { it.id == gid } }
        val sortedTasks = goal?.tasks?.sortedBy { it.order } ?: emptyList()
        val maxVisibleTasks = (taskAreaH / taskRowH).coerceAtLeast(1)
        val visibleTasks = sortedTasks.drop(taskListScroll).take(maxVisibleTasks)

        taskListStartY = taskAreaY

        visibleTasks.forEachIndexed { i, task ->
            val taskIndex = sortedTasks.indexOf(task)
            val ty = taskAreaY + i * taskRowH
            if (isDragging && draggedTaskIndex == taskIndex) return@forEachIndexed

            val statusIcon = when (task.status) {
                "COMPLETED" -> "§a✓"; "FAILED" -> "§c✗"; "CANCELLED" -> "§7○"; "PENDING" -> "§8□"; else -> "§e▶"
            }
            val hovered = mouseX in rx..(rx + panelW - 4) && mouseY in ty..(ty + taskRowH - 2)
            box(context, rx + 4, ty, panelW - 8, taskRowH - 2, if (hovered) 0xCC2a2a2a.toInt() else 0xCC1a1a1a.toInt(), if (hovered) 0xFF8a6a3a.toInt() else 0xFF3a3a3a.toInt())
            lbl(context, "§7${taskIndex + 1}.", rx + 6, ty + 2, 0.55f, 0x888888)
            lbl(context, statusIcon, rx + 16, ty + 2, 0.55f, 0xFFFFFF)
            lbl(context, truncate(task.description, panelW - 36, 0.55f), rx + 24, ty + 2, 0.55f, 0xCCCCCC)
        }

        // Dragged task
        if (isDragging && draggedTaskIndex != null) {
            val draggedTask = sortedTasks.getOrNull(draggedTaskIndex!!)
            if (draggedTask != null) {
                val statusIcon = when (draggedTask.status) {
                    "COMPLETED" -> "§a✓"; "FAILED" -> "§c✗"; "CANCELLED" -> "§7○"; "PENDING" -> "§8□"; else -> "§e▶"
                }
                box(context, rx + 4, draggedTaskY, panelW - 8, taskRowH - 2, 0xAA3a3a1a.toInt(), 0xFFFFD700.toInt())
                lbl(context, "§7${draggedTaskIndex!! + 1}.", rx + 6, draggedTaskY + 2, 0.55f, 0x888888)
                lbl(context, statusIcon, rx + 16, draggedTaskY + 2, 0.55f, 0xFFFFFF)
                lbl(context, truncate(draggedTask.description, panelW - 36, 0.55f), rx + 24, draggedTaskY + 2, 0.55f, 0xFFFFFF)
            }
        }

        // Scroll buttons
        if (sortedTasks.size > maxVisibleTasks) {
            if (taskListScroll > 0) btn(context, mouseX, mouseY, rx + panelW - 14, taskAreaY, 10, 9, "§7▲") { taskListScroll-- }
            if (taskListScroll < sortedTasks.size - maxVisibleTasks) btn(context, mouseX, mouseY, rx + panelW - 14, taskAreaY + taskAreaH - 9, 10, 9, "§7▼") { taskListScroll++ }
        }
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
            InputMode.EDIT_IDEAL -> {
                val id = editingIdealId ?: run { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                val idx = ideals.indexOfFirst { it.id == id }
                if (idx >= 0) ideals[idx] = ideals[idx].copy(text = text.ifEmpty { ideals[idx].text }, alignmentAxis = pendingAxis)
            }
            InputMode.ADD_FLAW -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                sendAddFlaw(text)
            }
            InputMode.EDIT_FLAW -> {
                val id = editingFlawId ?: run { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                val idx = flaws.indexOfFirst { it.id == id }
                if (idx >= 0) flaws[idx] = flaws[idx].copy(text = text.ifEmpty { flaws[idx].text })
            }
            InputMode.ADD_MOTIVATION -> {
                if (text.isEmpty()) { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                sendAddMotivation(text)
            }
            InputMode.EDIT_MOTIVATION -> {
                val id = editingMotivationId ?: run { inputMode = InputMode.NONE; inputBuffer2Scroll = 0; return }
                val idx = motivations.indexOfFirst { it.id == id }
                if (idx >= 0) motivations[idx] = motivations[idx].copy(text = text.ifEmpty { motivations[idx].text })
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
                if (text.isEmpty()) { inputMode = InputMode.EDIT_GOAL; inputBuffer2Scroll = 0; return }
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
                val taskId = editingTaskId ?: run { inputMode = InputMode.EDIT_GOAL; inputBuffer2Scroll = 0; return }
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
        // Send COMPLETE identity data - no delta calculation, just send everything
        val buf = PacketByteBufs.create()
        
        // Player name
        buf.writeString(snapshot.playerName)
        
        // Alignment coordinates
        buf.writeInt(alignLawChaos)
        buf.writeInt(alignGoodEvil)
        
        // Ideals
        buf.writeInt(ideals.size)
        ideals.forEach { ideal ->
            buf.writeString(ideal.id)
            buf.writeString(ideal.text)
            buf.writeString(ideal.alignmentAxis.name)
        }
        
        // Flaws
        buf.writeInt(flaws.size)
        flaws.forEach { flaw ->
            buf.writeString(flaw.id)
            buf.writeString(flaw.text)
        }
        
        // Motivations
        buf.writeInt(motivations.size)
        motivations.forEach { motivation ->
            buf.writeString(motivation.id)
            buf.writeString(motivation.text)
            buf.writeBoolean(motivation.addedByGm)
            buf.writeBoolean(motivation.isActive)
        }
        
        // Proposals (we don't modify these in this screen, but send them anyway)
        buf.writeInt(proposals.size)
        proposals.forEach { proposal ->
            buf.writeString(proposal.id)
            buf.writeString(proposal.text)
            buf.writeString(proposal.proposedBy)
        }
        
        // Goals
        buf.writeInt(goals.size)
        goals.forEach { goal ->
            buf.writeString(goal.id)
            buf.writeString(goal.title)
            buf.writeString(goal.description)
            buf.writeString(goal.motivationId ?: "")
            buf.writeString(goal.status)
            buf.writeInt(goal.currentTaskIndex)
            
            // Tasks
            buf.writeInt(goal.tasks.size)
            goal.tasks.sortedBy { it.order }.forEach { task ->
                buf.writeString(task.id)
                buf.writeString(task.description)
                buf.writeString(task.goalDescriptionOverride)
                buf.writeString(task.status)
                buf.writeInt(task.order)
            }
        }
        
        ClientPlayNetworking.send(BbfPackets.GM_SET_PLAYER_IDENTITY, buf)
        
        // Update local snapshot to match what we just sent
        val updatedSnapshot = snapshot.copy(
            alignmentCoords = omc.boundbyfate.client.state.ClientAlignmentData(alignLawChaos, alignGoodEvil),
            ideals = ideals.toList(),
            flaws = flaws.toList(),
            motivations = motivations.toList(),
            proposals = proposals.toList(),
            goals = goals.toList()
        )
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
                val panelW = (W * 0.38f).toInt().coerceAtMost(280)
                val gap = 6
                val totalW = panelW * 2 + gap
                val panelH = minOf(H - 40, 300)
                val startX = (W - totalW) / 2
                val rx = startX + panelW + gap  // right panel x

                val taskAreaY = (H - panelH) / 2 + 17
                val taskAreaH = panelH - 17 - 4
                val taskRowH = 14
                val sortedTasks = goal.tasks.sortedBy { it.order }
                val maxVisibleTasks = (taskAreaH / taskRowH).coerceAtLeast(1)
                val visibleTasks = sortedTasks.drop(taskListScroll).take(maxVisibleTasks)

                visibleTasks.forEachIndexed { i, task ->
                    val taskIndex = sortedTasks.indexOf(task)
                    val ty = taskAreaY + i * taskRowH
                    val hitbox = (mx in rx..(rx + panelW - 4) && my in ty..(ty + taskRowH - 2))

                    if (hitbox) {
                        val currentTime = System.currentTimeMillis()
                        if (lastClickedTaskIndex == taskIndex && (currentTime - lastClickTime) < 300) {
                            editingTaskId = task.id
                            inputBuffer = task.description
                            inputBuffer2 = task.goalDescriptionOverride
                            pendingGoalStatus = task.status
                            inputFocusField = 0
                            inputMode = InputMode.EDIT_TASK
                            lastClickedTaskIndex = null
                            lastClickTime = 0
                        } else {
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
                val taskRowH = 14
                val sortedTasks = goal.tasks.sortedBy { it.order }
                
                // Use taskListStartY saved during render — no need to recalculate
                val relativeY = draggedTaskY - taskListStartY
                val newOrder = (relativeY / taskRowH).coerceIn(0, sortedTasks.size - 1)
                
                if (newOrder != draggedTaskIndex) {
                    val draggedTask = sortedTasks[draggedTaskIndex!!]
                    sendReorderTask(goal.id, draggedTask.id, newOrder)
                    
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
                256 -> { // ESC
                    when (inputMode) {
                        InputMode.ADD_TASK, InputMode.EDIT_TASK -> {
                            inputMode = InputMode.EDIT_GOAL
                            val goal = editingGoalId?.let { gid -> goals.find { it.id == gid } }
                            if (goal != null) { inputBuffer = goal.title; inputBuffer2 = goal.description; pendingGoalStatus = goal.status; pendingMotivationId = goal.motivationId }
                        }
                        InputMode.PICK_MOTIVATION -> inputMode = prevInputMode
                        else -> { inputMode = InputMode.NONE; inputBuffer = ""; inputBuffer2 = "" }
                    }
                    inputFocusField = 0; inputBuffer2Scroll = 0; return true
                }
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

    /** Marquee: if text doesn't fit, scroll it horizontally using scrollAnimTime. */
    private fun marquee(text: String, maxPx: Int, scale: Float): String {
        val max = (maxPx / scale).toInt()
        if (textRenderer.getWidth(text) <= max) return text
        // Build padded string and cycle through it
        val padded = "$text    "
        val totalW = textRenderer.getWidth(padded)
        val speed = 30f  // pixels per second
        val offset = ((scrollAnimTime * speed).toInt() % totalW).coerceAtLeast(0)
        // Find char index corresponding to offset
        var charOffset = 0
        var px = 0
        for (c in padded) {
            if (px >= offset) break
            px += textRenderer.getWidth(c.toString())
            charOffset++
        }
        val shifted = padded.substring(charOffset) + padded.substring(0, charOffset)
        // Truncate to fit
        var result = ""
        var w = 0
        for (c in shifted) {
            val cw = textRenderer.getWidth(c.toString())
            if (w + cw > max) break
            result += c
            w += cw
        }
        return result
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
