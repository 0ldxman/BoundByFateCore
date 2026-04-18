package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import omc.boundbyfate.api.identity.Alignment
import omc.boundbyfate.api.identity.AlignmentCoordinates
import omc.boundbyfate.api.identity.IdealAlignment
import omc.boundbyfate.client.state.ClientAlignmentData
import omc.boundbyfate.client.state.ClientFlaw
import omc.boundbyfate.client.state.ClientIdeal
import omc.boundbyfate.client.state.GmPlayerSnapshot
import omc.boundbyfate.network.BbfPackets

/**
 * GM screen for editing player identity:
 * - Alignment grid (3x3 with coordinate display)
 * - Ideals with alignment axis binding
 * - Flaws
 */
class GmIdentityScreen(private val snapshot: GmPlayerSnapshot) :
    Screen(Text.literal("Identity: ${snapshot.playerName}")) {

    private fun tr(key: String, vararg args: Any): String =
        net.minecraft.client.resource.language.I18n.translate(key, *args)

    // ── Editable state ────────────────────────────────────────────────────────
    private var alignLawChaos: Int = snapshot.alignmentCoords.lawChaos
    private var alignGoodEvil: Int = snapshot.alignmentCoords.goodEvil
    private val ideals: MutableList<ClientIdeal> = snapshot.ideals.toMutableList()
    private val flaws: MutableList<ClientFlaw> = snapshot.flaws.toMutableList()
    private val motivations: MutableList<omc.boundbyfate.client.state.ClientMotivation> = snapshot.motivations.toMutableList()
    private val proposals: MutableList<omc.boundbyfate.client.state.ClientProposal> = snapshot.proposals.toMutableList()
    private val goals: MutableList<omc.boundbyfate.client.state.ClientGoal> = snapshot.goals.toMutableList()

    // ── UI state ──────────────────────────────────────────────────────────────
    private enum class Tab { ALIGNMENT_IDEALS, MOTIVATIONS_GOALS }
    private var activeTab = Tab.ALIGNMENT_IDEALS

    private data class Btn(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, val action: () -> Unit)
    private val btns = mutableListOf<Btn>()
    private var idealScroll = 0
    private var flawScroll = 0
    private var motivationScroll = 0
    private var goalScroll = 0
    private var editingIdealId: String? = null
    private var editingFlawId: String? = null
    private var inputBuffer = ""
    private var inputBuffer2 = ""  // second field (e.g. description for goals)
    private var inputMode = InputMode.NONE
    private var pendingAxis: IdealAlignment = IdealAlignment.ANY
    private var pendingMotivationId: String? = null  // for linking goal to motivation
    private var statusMsg = ""; private var statusTimer = 0f

    private enum class InputMode {
        NONE, ADD_IDEAL, ADD_FLAW, ADD_MOTIVATION, ADD_GOAL
    }

    // ── Alignment helpers ─────────────────────────────────────────────────────
    private fun currentAlignment(): Alignment =
        AlignmentCoordinates(alignLawChaos, alignGoodEvil).getAlignment()

    private fun isIdealCompatible(ideal: ClientIdeal): Boolean =
        ideal.alignmentAxis.isCompatibleWith(currentAlignment())

    override fun init() { /* dynamic layout */ }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        btns.clear()
        if (statusTimer > 0f) statusTimer -= delta * 0.05f

        val W = width; val H = height; val pad = 5

        // ── Header ────────────────────────────────────────────────────────────
        btn(context, mouseX, mouseY, pad, pad, 38, 11, "§7← ${tr("bbf.gm.button.back")}") {
            client?.setScreen(GmPlayerEditScreen(snapshot))
        }
        btn(context, mouseX, mouseY, W - 46, pad, 40, 11, "§a${tr("bbf.gm.button.apply")}") { applyAll() }

        val bodyY = pad + 16

        // ── Tabs ──────────────────────────────────────────────────────────────
        val tabW = 100; val tabH = 11; val tabY = bodyY
        val tab1Selected = activeTab == Tab.ALIGNMENT_IDEALS
        val tab2Selected = activeTab == Tab.MOTIVATIONS_GOALS
        btn(context, mouseX, mouseY, pad, tabY, tabW, tabH,
            if (tab1Selected) "§e${tr("bbf.gm.identity.tab.alignment")}" else "§7${tr("bbf.gm.identity.tab.alignment")}"
        ) { activeTab = Tab.ALIGNMENT_IDEALS }
        btn(context, mouseX, mouseY, pad + tabW + 2, tabY, tabW, tabH,
            if (tab2Selected) "§e${tr("bbf.gm.identity.tab.motivations")}" else "§7${tr("bbf.gm.identity.tab.motivations")}"
        ) { activeTab = Tab.MOTIVATIONS_GOALS }

        val contentY = tabY + tabH + 3

        when (activeTab) {
            Tab.ALIGNMENT_IDEALS -> renderAlignmentIdealsTab(context, mouseX, mouseY, pad, contentY, W - pad * 2, H - contentY - pad)
            Tab.MOTIVATIONS_GOALS -> renderMotivationsGoalsTab(context, mouseX, mouseY, pad, contentY, W - pad * 2, H - contentY - pad)
        }

        // ── Input overlay ─────────────────────────────────────────────────────
        if (inputMode != InputMode.NONE) {
            renderInputOverlay(context, mouseX, mouseY)
        }

        // ── Status message ────────────────────────────────────────────────────
        if (statusTimer > 0f) {
            val a = (statusTimer * 255).toInt().coerceIn(0, 255)
            context.drawCenteredTextWithShadow(textRenderer, statusMsg, W / 2, H - 10, (a shl 24) or 0x55FF55)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    // ── Tab renderers ─────────────────────────────────────────────────────────

    private fun renderAlignmentIdealsTab(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val alignBoxW = 130
        val alignBoxH = h
        box(context, x, y, alignBoxW, alignBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderAlignmentSection(context, mouseX, mouseY, x + 4, y + 4, alignBoxW - 8, alignBoxH - 8)

        val rightX = x + alignBoxW + 4
        val rightW = w - alignBoxW - 4
        val halfH = (h - 4) / 2

        box(context, rightX, y, rightW, halfH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderIdealsSection(context, mouseX, mouseY, rightX + 4, y + 4, rightW - 8, halfH - 8)

        val flawY = y + halfH + 4
        val flawH = h - halfH - 4
        box(context, rightX, flawY, rightW, flawH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderFlawsSection(context, mouseX, mouseY, rightX + 4, flawY + 4, rightW - 8, flawH - 8)
    }

    private fun renderMotivationsGoalsTab(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val motW = (w - 4) / 2
        val goalW = w - motW - 4

        // Motivations box (left)
        box(context, x, y, motW, h, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderMotivationsSection(context, mouseX, mouseY, x + 4, y + 4, motW - 8, h - 8)

        // Goals box (right)
        val goalX = x + motW + 4
        box(context, goalX, y, goalW, h, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderGoalsSection(context, mouseX, mouseY, goalX + 4, y + 4, goalW - 8, h - 8)
    }

    // ── Motivations section ───────────────────────────────────────────────────

    private fun renderMotivationsSection(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.motivations"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 12, y - 1, 12, 9, "§a+") {
            inputMode = InputMode.ADD_MOTIVATION
            inputBuffer = ""
        }

        val listY = y + 12
        val rowH = 11
        val visibleCount = (h - 12) / rowH

        // Active motivations
        val active = motivations.filter { it.isActive }
        val visibleMotivations = active.drop(motivationScroll).take(visibleCount)
        visibleMotivations.forEachIndexed { i, mot ->
            val fy = listY + i * rowH
            val icon = if (mot.addedByGm) "§7[GM]" else "§b[P]"
            lbl(context, icon, x, fy, 0.55f, if (mot.addedByGm) 0x888888 else 0x55AAFF)
            val truncated = truncateText(mot.text, w - 40, 0.6f)
            lbl(context, truncated, x + 22, fy, 0.6f, 0xCCCCCC)
            btn(context, mouseX, mouseY, x + w - 12, fy, 10, 9, "§cX") {
                sendRemoveMotivation(mot.id)
                motivations.removeIf { it.id == mot.id }
            }
        }

        // Proposals section
        if (proposals.isNotEmpty()) {
            val propY = listY + visibleMotivations.size * rowH + 6
            lbl(context, "§6${tr("bbf.gm.identity.proposals")} (${proposals.size})", x, propY, 0.6f, 0xFFAA00)
            proposals.take(3).forEachIndexed { i, prop ->
                val fy = propY + 10 + i * rowH
                val truncated = truncateText("? ${prop.text}", w - 40, 0.55f)
                lbl(context, truncated, x, fy, 0.55f, 0xFFCC55)
                btn(context, mouseX, mouseY, x + w - 24, fy, 10, 9, "§a✓") {
                    sendHandleProposal(prop.id, "accept")
                    proposals.removeIf { it.id == prop.id }
                }
                btn(context, mouseX, mouseY, x + w - 12, fy, 10, 9, "§cX") {
                    sendHandleProposal(prop.id, "reject")
                    proposals.removeIf { it.id == prop.id }
                }
            }
        }

        if (motivationScroll > 0) btn(context, mouseX, mouseY, x + w - 10, listY, 10, 9, "§7▲") { motivationScroll-- }
        val maxScroll = (active.size - visibleCount).coerceAtLeast(0)
        if (motivationScroll < maxScroll) btn(context, mouseX, mouseY, x + w - 10, listY + h - 22, 10, 9, "§7▼") { motivationScroll++ }
    }

    // ── Goals section ─────────────────────────────────────────────────────────

    private fun renderGoalsSection(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.goals"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 12, y - 1, 12, 9, "§a+") {
            inputMode = InputMode.ADD_GOAL
            inputBuffer = ""
            inputBuffer2 = ""
            pendingMotivationId = null
        }

        val listY = y + 12
        val goalRowH = 28  // each goal takes more space (title + current task)
        val visibleCount = (h - 12) / goalRowH

        val visibleGoals = goals.drop(goalScroll).take(visibleCount)
        visibleGoals.forEachIndexed { i, goal ->
            val gy = listY + i * goalRowH
            val statusColor = when (goal.status) {
                "ACTIVE" -> 0xFFFFFF
                "COMPLETED" -> 0x55FF55
                "FAILED" -> 0xFF5555
                "CANCELLED" -> 0x888888
                else -> 0xCCCCCC
            }
            val statusIcon = when (goal.status) {
                "COMPLETED" -> "§a✓"
                "FAILED" -> "§c✗"
                "CANCELLED" -> "§7○"
                else -> "§e▶"
            }

            // Title row
            lbl(context, statusIcon, x, gy, 0.65f, statusColor)
            val titleTrunc = truncateText(goal.title, w - 30, 0.65f)
            lbl(context, titleTrunc, x + 10, gy, 0.65f, statusColor)

            // Linked motivation
            val motName = goal.motivationId?.let { mid ->
                motivations.find { it.id == mid }?.text?.let { truncateText(it, w - 10, 0.5f) }
            }
            if (motName != null) {
                lbl(context, "§8→ $motName", x + 4, gy + 9, 0.5f, 0x666666)
            }

            // Current task
            val task = goal.currentTask
            if (task != null) {
                val taskTrunc = truncateText("  □ ${task.description}", w - 20, 0.55f)
                lbl(context, "§7$taskTrunc", x + 4, gy + 17, 0.55f, 0x888888)
            } else if (goal.isActive) {
                lbl(context, "§a  ${tr("bbf.gm.identity.goal.all_done")}", x + 4, gy + 17, 0.55f, 0x55FF55)
            }

            // Action buttons (only for active goals)
            if (goal.isActive) {
                btn(context, mouseX, mouseY, x + w - 24, gy, 10, 9, "§a✓") {
                    sendGoalAction(goal.id, "complete", "", "")
                    val idx = goals.indexOfFirst { it.id == goal.id }
                    if (idx >= 0) goals[idx] = goals[idx].copy(status = "COMPLETED")
                }
                btn(context, mouseX, mouseY, x + w - 12, gy, 10, 9, "§c✗") {
                    sendGoalAction(goal.id, "fail", "", "")
                    val idx = goals.indexOfFirst { it.id == goal.id }
                    if (idx >= 0) goals[idx] = goals[idx].copy(status = "FAILED")
                }
            } else {
                btn(context, mouseX, mouseY, x + w - 12, gy, 10, 9, "§7X") {
                    sendGoalAction(goal.id, "remove", "", "")
                    goals.removeIf { it.id == goal.id }
                }
            }
        }

        if (goalScroll > 0) btn(context, mouseX, mouseY, x + w - 10, listY, 10, 9, "§7▲") { goalScroll-- }
        val maxScroll = (goals.size - visibleCount).coerceAtLeast(0)
        if (goalScroll < maxScroll) btn(context, mouseX, mouseY, x + w - 10, listY + h - 22, 10, 9, "§7▼") { goalScroll++ }
    }

    // ── Alignment section ─────────────────────────────────────────────────────

    private fun renderAlignmentSection(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.alignment"), x, y, 0.65f, 0xD4AF37)

        val gridY = y + 12
        val cellW = (w - 4) / 3
        val cellH = 18
        val gridW = cellW * 3

        // Draw 3x3 alignment grid
        val alignments = listOf(
            Alignment.LAWFUL_GOOD, Alignment.NEUTRAL_GOOD, Alignment.CHAOTIC_GOOD,
            Alignment.LAWFUL_NEUTRAL, Alignment.TRUE_NEUTRAL, Alignment.CHAOTIC_NEUTRAL,
            Alignment.LAWFUL_EVIL, Alignment.NEUTRAL_EVIL, Alignment.CHAOTIC_EVIL
        )
        val current = currentAlignment()

        alignments.forEachIndexed { i, al ->
            val col = i % 3
            val row = i / 3
            val cx = x + col * cellW
            val cy = gridY + row * cellH
            val isSelected = al == current
            val bg = if (isSelected) 0xCC3a2a1a.toInt() else 0xCC1a1a1a.toInt()
            val border = if (isSelected) 0xFFFFD700.toInt() else 0xFF6b5a3e.toInt()
            box(context, cx, cy, cellW - 1, cellH - 1, bg, border)
            val shortKey = al.getShortKey()
            val shortName = net.minecraft.client.resource.language.I18n.translate(shortKey)
            val m = context.matrices; m.push()
            m.translate((cx + (cellW - 1) / 2).toFloat(), (cy + (cellH - 1) / 2 - 3).toFloat(), 0f)
            m.scale(0.65f, 0.65f, 1f)
            val tw = textRenderer.getWidth(shortName)
            val color = when {
                isSelected -> 0xFFD700
                al.name.contains("EVIL") -> 0xFF5555
                al.name.contains("GOOD") -> 0x55FF55
                else -> 0xAAAAAA
            }
            context.drawTextWithShadow(textRenderer, shortName, -(tw / 2), 0, color)
            m.pop()
        }

        // Coordinate display and fine-tune buttons
        val coordY = gridY + 3 * cellH + 6
        val cx = x + w / 2

        // Law-Chaos axis
        lbl(context, "§7${tr("bbf.gm.identity.law_chaos")}: $alignLawChaos", x, coordY, 0.6f, 0x888888)
        btn(context, mouseX, mouseY, x + w - 30, coordY - 1, 12, 9, "§c-") {
            alignLawChaos = (alignLawChaos - 1).coerceIn(-6, 6)
        }
        btn(context, mouseX, mouseY, x + w - 16, coordY - 1, 12, 9, "§a+") {
            alignLawChaos = (alignLawChaos + 1).coerceIn(-6, 6)
        }

        // Good-Evil axis
        val geY = coordY + 12
        lbl(context, "§7${tr("bbf.gm.identity.good_evil")}: $alignGoodEvil", x, geY, 0.6f, 0x888888)
        btn(context, mouseX, mouseY, x + w - 30, geY - 1, 12, 9, "§c-") {
            alignGoodEvil = (alignGoodEvil - 1).coerceIn(-6, 6)
        }
        btn(context, mouseX, mouseY, x + w - 16, geY - 1, 12, 9, "§a+") {
            alignGoodEvil = (alignGoodEvil + 1).coerceIn(-6, 6)
        }

        // Current alignment name
        val alName = net.minecraft.client.resource.language.I18n.translate(current.translationKey)
        val alNameW = (textRenderer.getWidth(alName) * 0.7f).toInt()
        lbl(context, alName, cx - alNameW / 2, geY + 14, 0.7f, 0xFFD700)

        // Border warning
        val (lcBorder, geBorder) = AlignmentCoordinates(alignLawChaos, alignGoodEvil).isOnBorder()
        if (lcBorder || geBorder) {
            val warnY = geY + 26
            lbl(context, "§6⚠ ${tr("bbf.gm.identity.wavering")}", x, warnY, 0.55f, 0xFFAA00)
        }

        // History button
        val histBtnY = h - 12 + y
        btn(context, mouseX, mouseY, x, histBtnY, w, 10, "§7${tr("bbf.gm.identity.history")}") {
            // TODO: open alignment history screen
        }
    }

    // ── Ideals section ────────────────────────────────────────────────────────

    private fun renderIdealsSection(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.ideals"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 12, y - 1, 12, 9, "§a+") {
            inputMode = InputMode.ADD_IDEAL
            inputBuffer = ""
            pendingAxis = IdealAlignment.ANY
        }

        val listY = y + 12
        val rowH = 11
        val visible = ideals.drop(idealScroll).take((h - 12) / rowH)
        visible.forEachIndexed { i, ideal ->
            val fy = listY + i * rowH
            val compatible = isIdealCompatible(ideal)
            val axisColor = axisColor(ideal.alignmentAxis)
            val textColor = if (compatible) 0xCCCCCC else 0xFF5555

            // Axis tag
            val axisShort = ideal.alignmentAxis.name.take(3)
            lbl(context, "§7[$axisShort]", x, fy, 0.55f, axisColor)
            // Ideal text (truncated)
            val maxTextW = w - 40
            val truncated = truncateText(ideal.text, maxTextW, 0.6f)
            lbl(context, truncated, x + 22, fy, 0.6f, textColor)
            // Remove button
            btn(context, mouseX, mouseY, x + w - 12, fy, 10, 9, "§cX") {
                sendRemoveIdeal(ideal.id)
                ideals.removeIf { it.id == ideal.id }
            }
        }

        if (idealScroll > 0) btn(context, mouseX, mouseY, x + w - 10, listY, 10, 9, "§7▲") { idealScroll-- }
        val maxScroll = (ideals.size - (h - 12) / rowH).coerceAtLeast(0)
        if (idealScroll < maxScroll) btn(context, mouseX, mouseY, x + w - 10, listY + h - 22, 10, 9, "§7▼") { idealScroll++ }
    }

    // ── Flaws section ─────────────────────────────────────────────────────────

    private fun renderFlawsSection(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        lbl(context, tr("bbf.gm.identity.flaws"), x, y, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, x + w - 12, y - 1, 12, 9, "§a+") {
            inputMode = InputMode.ADD_FLAW
            inputBuffer = ""
        }

        val listY = y + 12
        val rowH = 11
        val visible = flaws.drop(flawScroll).take((h - 12) / rowH)
        visible.forEachIndexed { i, flaw ->
            val fy = listY + i * rowH
            val truncated = truncateText(flaw.text, w - 20, 0.6f)
            lbl(context, truncated, x, fy, 0.6f, 0xCCCCCC)
            btn(context, mouseX, mouseY, x + w - 12, fy, 10, 9, "§cX") {
                sendRemoveFlaw(flaw.id)
                flaws.removeIf { it.id == flaw.id }
            }
        }

        if (flawScroll > 0) btn(context, mouseX, mouseY, x + w - 10, listY, 10, 9, "§7▲") { flawScroll-- }
        val maxScroll = (flaws.size - (h - 12) / rowH).coerceAtLeast(0)
        if (flawScroll < maxScroll) btn(context, mouseX, mouseY, x + w - 10, listY + h - 22, 10, 9, "§7▼") { flawScroll++ }
    }

    // ── Input overlay ─────────────────────────────────────────────────────────

    private fun renderInputOverlay(context: DrawContext, mouseX: Int, mouseY: Int) {
        val W = width; val H = height
        val overlayW = (W * 0.6f).toInt().coerceAtMost(320)
        val overlayH = when (inputMode) {
            InputMode.ADD_IDEAL -> 80
            InputMode.ADD_GOAL -> 100
            else -> 60
        }
        val ox = (W - overlayW) / 2
        val oy = (H - overlayH) / 2

        context.fill(0, 0, W, H, 0x88000000.toInt())
        box(context, ox, oy, overlayW, overlayH, 0xEE1a1a1a.toInt(), 0xFF8a6a3a.toInt())

        val title = when (inputMode) {
            InputMode.ADD_IDEAL -> tr("bbf.gm.identity.add_ideal")
            InputMode.ADD_FLAW -> tr("bbf.gm.identity.add_flaw")
            InputMode.ADD_MOTIVATION -> tr("bbf.gm.identity.add_motivation")
            InputMode.ADD_GOAL -> tr("bbf.gm.identity.add_goal")
            else -> ""
        }
        lbl(context, title, ox + 6, oy + 5, 0.7f, 0xD4AF37)

        // First text input field
        val inputY = oy + 18
        box(context, ox + 4, inputY, overlayW - 8, 12, 0xCC111111.toInt(), 0xFF6b5a3e.toInt())
        val label1 = if (inputMode == InputMode.ADD_GOAL) tr("bbf.gm.identity.goal.title") else ""
        if (label1.isNotEmpty()) lbl(context, "§7$label1", ox + 4, inputY - 8, 0.55f, 0x888888)
        lbl(context, "${inputBuffer}_", ox + 6, inputY + 2, 0.7f, 0xFFFFFF)

        // Second field for goals (description)
        if (inputMode == InputMode.ADD_GOAL) {
            val input2Y = inputY + 22
            lbl(context, "§7${tr("bbf.gm.identity.goal.description")}", ox + 4, input2Y - 8, 0.55f, 0x888888)
            box(context, ox + 4, input2Y, overlayW - 8, 12, 0xCC111111.toInt(), 0xFF6b5a3e.toInt())
            lbl(context, "${inputBuffer2}_", ox + 6, input2Y + 2, 0.7f, 0xCCCCCC)
        }

        // Axis selector (only for ideals)
        if (inputMode == InputMode.ADD_IDEAL) {
            val axisY = inputY + 16
            lbl(context, "§7${tr("bbf.gm.identity.axis")}:", ox + 4, axisY + 1, 0.6f, 0x888888)
            val axes = IdealAlignment.values()
            val btnW = (overlayW - 8) / axes.size
            axes.forEachIndexed { i, axis ->
                val bx = ox + 4 + i * btnW
                val isSelected = axis == pendingAxis
                val bg = if (isSelected) 0xCC3a2a1a.toInt() else 0xCC1a1a1a.toInt()
                val bd = if (isSelected) 0xFFFFD700.toInt() else 0xFF6b5a3e.toInt()
                box(context, bx, axisY, btnW - 1, 10, bg, bd)
                val m = context.matrices; m.push()
                m.translate((bx + (btnW - 1) / 2).toFloat(), (axisY + 2).toFloat(), 0f)
                m.scale(0.55f, 0.55f, 1f)
                val label = axis.name.take(4)
                val tw = textRenderer.getWidth(label)
                context.drawTextWithShadow(textRenderer, label, -(tw / 2), 0, axisColor(axis))
                m.pop()
                btns.add(Btn(bx, axisY, btnW - 1, 10, label) { pendingAxis = axis })
            }
        }

        // Confirm / Cancel buttons
        val btnY = oy + overlayH - 14
        btn(context, mouseX, mouseY, ox + 4, btnY, 50, 10, "§a${tr("bbf.gm.button.apply")}") { confirmInput() }
        btn(context, mouseX, mouseY, ox + overlayW - 54, btnY, 50, 10, "§c${tr("bbf.gm.button.cancel")}") {
            inputMode = InputMode.NONE
            inputBuffer = ""
            inputBuffer2 = ""
        }
    }

    private fun confirmInput() {
        val text = inputBuffer.trim()
        if (text.isEmpty()) { inputMode = InputMode.NONE; return }
        when (inputMode) {
            InputMode.ADD_IDEAL -> {
                sendAddIdeal(text, pendingAxis)
                ideals.add(omc.boundbyfate.client.state.ClientIdeal(
                    id = "pending_${System.currentTimeMillis()}",
                    text = text, alignmentAxis = pendingAxis,
                    isCompatible = pendingAxis.isCompatibleWith(currentAlignment())
                ))
            }
            InputMode.ADD_FLAW -> {
                sendAddFlaw(text)
                flaws.add(omc.boundbyfate.client.state.ClientFlaw("pending_${System.currentTimeMillis()}", text))
            }
            InputMode.ADD_MOTIVATION -> {
                sendAddMotivation(text)
                motivations.add(omc.boundbyfate.client.state.ClientMotivation(
                    "pending_${System.currentTimeMillis()}", text, addedByGm = true, isActive = true
                ))
            }
            InputMode.ADD_GOAL -> {
                val desc = inputBuffer2.trim()
                sendAddGoal(text, desc, pendingMotivationId)
                goals.add(omc.boundbyfate.client.state.ClientGoal(
                    "pending_${System.currentTimeMillis()}", text, desc, pendingMotivationId,
                    "ACTIVE", 0, emptyList()
                ))
            }
            else -> {}
        }
        inputMode = InputMode.NONE
        inputBuffer = ""
        inputBuffer2 = ""
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private fun applyAll() {
        // Send alignment
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString("set")
        buf.writeInt(alignLawChaos)
        buf.writeInt(alignGoodEvil)
        buf.writeString("GM edit")
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_ALIGNMENT, buf)
        statusMsg = "§a${tr("bbf.gm.status.applied")}"; statusTimer = 1f
    }

    private fun sendAddIdeal(text: String, axis: IdealAlignment) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString("add")
        buf.writeString("")
        buf.writeString(text)
        buf.writeString(axis.name)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDEAL, buf)
    }

    private fun sendRemoveIdeal(id: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString("remove")
        buf.writeString(id)
        buf.writeString("")
        buf.writeString("")
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDEAL, buf)
    }

    private fun sendAddFlaw(text: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString("add")
        buf.writeString("")
        buf.writeString(text)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_FLAW, buf)
    }

    private fun sendRemoveFlaw(id: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString("remove")
        buf.writeString(id)
        buf.writeString("")
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_FLAW, buf)
    }

    private fun sendAddMotivation(text: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString("add")
        buf.writeString("")
        buf.writeString(text)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_MOTIVATION, buf)
    }

    private fun sendRemoveMotivation(id: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString("remove")
        buf.writeString(id)
        buf.writeString("")
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_MOTIVATION, buf)
    }

    private fun sendHandleProposal(proposalId: String, action: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString(action)
        buf.writeString(proposalId)
        ClientPlayNetworking.send(BbfPackets.GM_HANDLE_PROPOSAL, buf)
    }

    private fun sendAddGoal(title: String, description: String, motivationId: String?) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString("add")
        buf.writeString("")
        buf.writeString(title)
        buf.writeString(description)
        buf.writeString(motivationId ?: "")
        buf.writeString("")
        buf.writeInt(0)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
    }

    private fun sendGoalAction(goalId: String, action: String, description: String, taskStatus: String) {
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeString(action)
        buf.writeString(goalId)
        buf.writeString("")
        buf.writeString(description)
        buf.writeString("")
        buf.writeString(taskStatus)
        buf.writeInt(0)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_GOAL, buf)
    }

    // ── Input handling ────────────────────────────────────────────────────────

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
                259 -> { // Backspace — affects active field (inputBuffer2 if ADD_GOAL and Tab pressed)
                    if (inputBuffer.isNotEmpty()) inputBuffer = inputBuffer.dropLast(1)
                    return true
                }
            }
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (inputMode != InputMode.NONE) {
            // For ADD_GOAL: Tab switches between fields
            if (inputMode == InputMode.ADD_GOAL) {
                if (inputBuffer.length < 60) inputBuffer += chr
            } else {
                if (inputBuffer.length < 120) inputBuffer += chr
            }
            return true
        }
        return super.charTyped(chr, modifiers)
    }

    override fun shouldPause() = false

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun axisColor(axis: IdealAlignment): Int = when (axis) {
        IdealAlignment.GOOD -> 0x55FF55
        IdealAlignment.EVIL -> 0xFF5555
        IdealAlignment.LAWFUL -> 0x5555FF
        IdealAlignment.CHAOTIC -> 0xFFAA00
        IdealAlignment.NEUTRAL_GE -> 0xAAAAAA
        IdealAlignment.NEUTRAL_LC -> 0xCCCCCC
        IdealAlignment.TRUE_NEUTRAL -> 0x888888
        IdealAlignment.ANY -> 0x666666
    }

    private fun truncateText(text: String, maxPixelW: Int, scale: Float): String {
        val maxW = (maxPixelW / scale).toInt()
        if (textRenderer.getWidth(text) <= maxW) return text
        var truncated = text
        while (truncated.isNotEmpty() && textRenderer.getWidth("$truncated…") > maxW) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }

    private fun btn(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, label: String, action: () -> Unit) {
        btns.add(Btn(x, y, w, h, label, action))
        val hov = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = if (hov) 0xCC4a3a2a.toInt() else 0xCC2b2321.toInt()
        val bd = if (hov) 0xFFd4a96a.toInt() else 0xFF6b5a3e.toInt()
        context.fill(x, y, x + w, y + h, bg)
        context.fill(x, y, x + w, y + 1, bd); context.fill(x, y + h - 1, x + w, y + h, bd)
        context.fill(x, y, x + 1, y + h, bd); context.fill(x + w - 1, y, x + w, y + h, bd)
        val m = context.matrices; m.push()
        m.translate((x + w / 2).toFloat(), (y + h / 2 - 3).toFloat(), 0f)
        m.scale(0.75f, 0.75f, 1f)
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
