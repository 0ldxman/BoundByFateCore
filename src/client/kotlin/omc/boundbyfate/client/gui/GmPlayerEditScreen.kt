package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.client.state.ClientGmRegistry
import omc.boundbyfate.client.state.GmPlayerSnapshot
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.registry.BbfStats

class GmPlayerEditScreen(private val snapshot: GmPlayerSnapshot) :
    Screen(Text.literal("GM: ${snapshot.playerName}")) {

    // ── Editable state ────────────────────────────────────────────────────────
    private val stats = mutableMapOf<Identifier, Int>().also { m ->
        listOf(BbfStats.STRENGTH, BbfStats.DEXTERITY, BbfStats.CONSTITUTION,
               BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA).forEach { s ->
            m[s.id] = snapshot.statsData?.getStatValue(s.id)?.total ?: 10
        }
    }
    private var classId: Identifier? = snapshot.classData?.classId
    private var subclassId: Identifier? = snapshot.classData?.subclassId
    private var level: Int = snapshot.level
    private var experience: Int = snapshot.experience
    private var raceId: Identifier? = snapshot.raceData?.raceId
    private var gender: String = snapshot.gender ?: "male"
    private var alignment: String = snapshot.alignment
    private var profBonus: Int = when { level >= 17 -> 6; level >= 13 -> 5; level >= 9 -> 4; level >= 5 -> 3; else -> 2 }
    private var currentHp: Float = snapshot.currentHp
    private var maxHp: Float = snapshot.maxHp
    private val skills = mutableMapOf<Identifier, Int>().also { m ->
        snapshot.skillData?.proficiencies?.forEach { (id, lv) -> m[id] = lv }
    }
    private val features = mutableListOf<Identifier>().also { it.addAll(snapshot.grantedFeatures) }

    // ── UI state ──────────────────────────────────────────────────────────────
    private var statusMsg = ""; private var statusTimer = 0f
    private var classDropOpen = false; private var subDropOpen = false
    private var raceDropOpen = false; private var alignDropOpen = false
    private var featDropOpen = false; private var skillScroll = 0
    private var selectedFeat: Identifier? = null
    private var subraceId: Identifier? = snapshot.raceData?.subraceId
    private var subraceDropOpen = false
    private var editingExp = false
    private var expInputBuffer = ""
    // Stored for click detection outside render
    private var lastExpTextX = 0; private var lastExpTextY = 0; private var lastExpTextW = 0

    private data class Btn(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, val action: () -> Unit)
    private val btns = mutableListOf<Btn>()

    private val ALIGNMENTS = listOf("Lawful Good","Neutral Good","Chaotic Good",
        "Lawful Neutral","True Neutral","Chaotic Neutral",
        "Lawful Evil","Neutral Evil","Chaotic Evil")

    // ── speed (editable, stored as ft) ───────────────────────────────────────
    private var speedFt: Int = (snapshot.speed * 200).toInt().let { if (it == 0) 30 else it }
    private var sizeFactor: Float = 1.0f
    // ── death saves ───────────────────────────────────────────────────────────
    private var deathSuccesses: Int = 0
    private var deathFailures: Int = 0

    override fun init() { /* layout is dynamic */ }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        btns.clear()
        if (statusTimer > 0f) statusTimer -= delta * 0.05f

        val W = width; val H = height; val pad = 5

        // ── HEADER ────────────────────────────────────────────────────────────
        btn(context, mouseX, mouseY, pad, pad, 38, 11, "§7← Back") { MinecraftClient.getInstance().setScreen(GmScreen()) }
        btn(context, mouseX, mouseY, W - 46, pad, 40, 11, "§aApply") { applyAll() }

        val headerY = pad + 13
        // Name+Level boxes width = same as each other, ending where infoBox starts
        val leftHeaderW = W / 5   // total width for name+level blocks
        val nameBoxH = 26; val lvBoxH = 24
        val headerH = nameBoxH + lvBoxH + 2

        // Block 1: Name + gender (top-left)
        box(context, pad, headerY, leftHeaderW, nameBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "§7Name", pad + 3, headerY + 2, 0.5f, 0x888888)
        lbl(context, snapshot.playerName, pad + 3, headerY + 10, 0.8f, 0xFFD700)
        val gIcon = when (gender) { "male" -> "♂"; "female" -> "♀"; else -> "⚧" }
        btn(context, mouseX, mouseY, pad + leftHeaderW - 16, headerY + 7, 14, 12, gIcon) {
            gender = when (gender) { "male" -> "female"; "female" -> "other"; else -> "male" }
        }

        // Block 2: Level + EXP (below name)
        val lvY = headerY + nameBoxH + 2
        val xpNeeded = xpForNextLevel(level)
        box(context, pad, lvY, leftHeaderW, lvBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        val lvCx = pad + leftHeaderW / 2
        // Level centered with wider spacing between buttons
        btn(context, mouseX, mouseY, lvCx - 24, lvY + 1, 8, 9, "§c-") { level = (level - 1).coerceAtLeast(1); recalcProfBonus() }
        lbl(context, "Lv $level", lvCx - 8, lvY + 2, 0.65f, 0xFFFFFF)
        btn(context, mouseX, mouseY, lvCx + 16, lvY + 1, 8, 9, "§a+") { level = (level + 1).coerceAtMost(20); recalcProfBonus() }
        // XP progress bar
        val barX = pad + 3; val barY = lvY + 13; val barW = leftHeaderW - 6; val barH = 4
        val xpPrev = xpForNextLevel(level - 1)
        val xpFrac = if (xpNeeded > xpPrev) ((experience - xpPrev).toFloat() / (xpNeeded - xpPrev)).coerceIn(0f, 1f) else 1f
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF333333.toInt())
        context.fill(barX, barY, barX + (barW * xpFrac).toInt(), barY + barH, 0xFF4488FF.toInt())
        context.fill(barX, barY, barX + barW, barY + 1, 0xFF555555.toInt())
        // EXP: [-] clickable_text [+] — dynamic spacing based on text width
        val expText = "$experience / $xpNeeded"
        val expScaledW = (textRenderer.getWidth(expText) * 0.45f).toInt()
        val expBtnGap = 3
        val expMinusX = lvCx - expScaledW / 2 - expBtnGap - 8
        val expPlusX = lvCx + expScaledW / 2 + expBtnGap
        btn(context, mouseX, mouseY, expMinusX, lvY + 19, 8, 7, "§c-") { experience = (experience - 100).coerceAtLeast(0) }
        // Clickable EXP text — shows input buffer when editing
        val displayExp = if (editingExp) "${expInputBuffer}_" else expText
        val displayColor = if (editingExp) 0xFFFF55 else if (expClickHovered(mouseX, mouseY, lvCx, lvY, expScaledW)) 0xFFFF55 else 0x888888
        lbl(context, displayExp, lvCx - expScaledW / 2, lvY + 19, 0.45f, displayColor)
        btn(context, mouseX, mouseY, expPlusX, lvY + 19, 8, 7, "§a+") { experience += 100 }

        // Info box — from end of name/level blocks to right edge
        val infoX = pad + leftHeaderW + 4; val infoW = W - infoX - pad
        box(context, infoX, headerY, infoW, headerH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderInfoBox(context, mouseX, mouseY, infoX + 3, headerY + 2, infoW - 6, headerH - 4)

        val bodyY = headerY + headerH + 4

        // ── STAT COLUMN (left, individual boxes) ──────────────────────────────
        val statOrder = listOf(BbfStats.STRENGTH, BbfStats.DEXTERITY, BbfStats.CONSTITUTION,
                               BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA)
        statOrder.forEachIndexed { i, stat ->
            val sy = bodyY + i * ((H - bodyY - pad) / 6)
            val sqSize2 = (H - bodyY - pad) / 6 - 1
            renderStatBox(context, mouseX, mouseY, pad + 12, sy, sqSize2, sqSize2, stat)
        }

        // ── BODY LAYOUT ───────────────────────────────────────────────────────
        // Stat column width = square size + buttons
        val sqSize = (H - bodyY - pad) / 6 - 1
        val statColEnd = pad + 12 + sqSize + 12 + 11  // left edge + margin + sq + margin + btn

        // Center column: 3 param boxes stacked, width = ~120px
        val paramBoxW = 90; val paramBoxH = 50
        val centerX = (W - paramBoxW) * 3 / 5  // смещён правее центра
        val dsY = bodyY; val hpY = dsY + paramBoxH + 4; val spY = hpY + paramBoxH + 4

        box(context, centerX, dsY, paramBoxW, paramBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderDeathSaves(context, mouseX, mouseY, centerX, dsY, paramBoxW, paramBoxH)
        box(context, centerX, hpY, paramBoxW, paramBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderHpBox(context, mouseX, mouseY, centerX, hpY, paramBoxW, paramBoxH)
        box(context, centerX, spY, paramBoxW, paramBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderSpeedBox(context, mouseX, mouseY, centerX, spY, paramBoxW, paramBoxH)

        // Middle-left: Saves + Skills (between stat col and center col)
        val midX = statColEnd + 4; val midW = centerX - midX - 4
        val savesH = 80
        box(context, midX, bodyY, midW, savesH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "SAVING THROWS", midX + 4, bodyY + 3, 0.65f, 0xD4AF37)
        renderSaves(context, mouseX, mouseY, midX + 4, bodyY + 13, midW - 8)

        val skillsY = bodyY + savesH + 4; val skillsH = H - skillsY - pad
        box(context, midX, skillsY, midW, skillsH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "SKILLS", midX + 4, skillsY + 3, 0.65f, 0xD4AF37)
        renderSkills(context, mouseX, mouseY, midX + 4, skillsY + 13, midW - 8, skillsH - 16)

        // Right: Features — 1/3 of available height, anchored to BOTTOM
        val rightX = centerX + paramBoxW + 4; val rightW = W - rightX - pad
        val featH = (H - bodyY - pad) / 3
        val featY = H - pad - featH
        box(context, rightX, featY, rightW, featH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "FEATURES & TRAITS", rightX + 4, featY + 3, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, rightX + rightW - 14, featY + 2, 12, 9, "§a+") { featDropOpen = !featDropOpen }
        renderFeatures(context, mouseX, mouseY, rightX + 4, featY + 14, rightW - 8, featH - 18)

        // ── DROPDOWNS (on top, high Z) ────────────────────────────────────────
        renderDropdowns(context, mouseX, mouseY, infoX + 3, headerY + 2)

        if (statusTimer > 0f) {
            val a = (statusTimer * 255).toInt().coerceIn(0, 255)
            context.drawCenteredTextWithShadow(textRenderer, statusMsg, W / 2, H - 10, (a shl 24) or 0x55FF55)
        }
        super.render(context, mouseX, mouseY, delta)
    }

    private fun renderInfoBox(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val col1 = x; val col2 = x + w / 2 + 4
        // Col 1: Class + Subclass
        lbl(context, "Class:", col1, y, 0.6f, 0x888888)
        val clsName = classId?.let { id -> ClientGmRegistry.classes.find { it.id == id }?.displayName ?: id.path } ?: "—"
        btn(context, mouseX, mouseY, col1 + 24, y - 1, w / 2 - 28, 9, "§f$clsName §e▼") { classDropOpen = !classDropOpen; subDropOpen = false; raceDropOpen = false; alignDropOpen = false }
        lbl(context, "Sub:", col1, y + 12, 0.6f, 0x888888)
        val subName = subclassId?.let { id -> classId?.let { cid -> ClientGmRegistry.classes.find { it.id == cid }?.subclasses?.find { it.id == id }?.displayName } ?: id.path } ?: "—"
        btn(context, mouseX, mouseY, col1 + 20, y + 11, w / 2 - 24, 9, "§f$subName §e▼") { subDropOpen = !subDropOpen; classDropOpen = false; raceDropOpen = false; alignDropOpen = false }
        // Col 2: Race + Subrace
        lbl(context, "Race:", col2, y, 0.6f, 0x888888)
        val raceName = raceId?.let { id -> ClientGmRegistry.races.find { it.id == id }?.displayName ?: id.path } ?: "—"
        btn(context, mouseX, mouseY, col2 + 22, y - 1, w / 2 - 26, 9, "§f$raceName §e▼") { raceDropOpen = !raceDropOpen; classDropOpen = false; subDropOpen = false; alignDropOpen = false; subraceDropOpen = false }
        lbl(context, "Sub:", col2, y + 12, 0.6f, 0x888888)
        val subraceName = subraceId?.path?.replace("_", " ")?.split(" ")?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } } ?: "—"
        btn(context, mouseX, mouseY, col2 + 20, y + 11, w / 2 - 24, 9, "§f$subraceName §e▼") { subraceDropOpen = !subraceDropOpen; classDropOpen = false; subDropOpen = false; raceDropOpen = false; alignDropOpen = false }
        // Bottom row: navigation buttons
        val btnY = y + 24; val btnW = (w - 8) / 3
        btn(context, mouseX, mouseY, x, btnY, btnW, 9, "§eЛичность") { /* TODO */ }
        btn(context, mouseX, mouseY, x + btnW + 4, btnY, btnW, 9, "§eСпособности") { /* TODO */ }
        btn(context, mouseX, mouseY, x + (btnW + 4) * 2, btnY, btnW, 9, "§eМагия") { /* TODO */ }
    }

    private fun renderStatBox(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, stat: omc.boundbyfate.api.stat.StatDefinition) {
        val v = stats[stat.id] ?: 10
        val mod = (v - 10) / 2
        val modStr = if (mod >= 0) "+$mod" else "$mod"
        val modColor = if (mod > 0) 0x55FF55 else if (mod < 0) 0xFF5555 else 0x888888
        val shortKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.short"
        val shortName = Text.translatable(shortKey).string
        val changed = v != (snapshot.statsData?.getStatValue(stat.id)?.total ?: 10)

        box(context, x, y, w, h, 0xCC1a1a1a.toInt(), if (changed) 0xFFFFAA44.toInt() else 0xFF8a6a3a.toInt())
        // Name top center
        val m = context.matrices; m.push()
        m.translate((x + w / 2).toFloat(), (y + 3).toFloat(), 0f); m.scale(0.6f, 0.6f, 1f)
        val nw = textRenderer.getWidth(shortName)
        context.drawTextWithShadow(textRenderer, shortName, -(nw / 2), 0, 0xCCCCCC); m.pop()
        // Value center big
        m.push(); m.translate((x + w / 2).toFloat(), (y + h / 2 - 4).toFloat(), 0f); m.scale(1.1f, 1.1f, 1f)
        val vw = textRenderer.getWidth("$v")
        context.drawTextWithShadow(textRenderer, "$v", -(vw / 2), 0, 0xFFFFFF); m.pop()
        // Mod bottom center
        m.push(); m.translate((x + w / 2).toFloat(), (y + h - 9).toFloat(), 0f); m.scale(0.7f, 0.7f, 1f)
        val mw = textRenderer.getWidth(modStr)
        context.drawTextWithShadow(textRenderer, modStr, -(mw / 2), 0, modColor); m.pop()
        // Buttons outside box — full height of box
        btn(context, mouseX, mouseY, x - 12, y, 11, h, "§c-") { stats[stat.id] = (v - 1).coerceAtLeast(1) }
        btn(context, mouseX, mouseY, x + w + 1, y, 11, h, "§a+") { stats[stat.id] = (v + 1).coerceAtMost(30) }
    }

    private fun expClickHovered(mouseX: Int, mouseY: Int, cx: Int, lvY: Int, w: Int): Boolean {
        return mouseX in (cx - w / 2)..(cx + w / 2) && mouseY in (lvY + 18)..(lvY + 27)
    }

    private fun recalcProfBonus() {
        profBonus = when { level >= 17 -> 6; level >= 13 -> 5; level >= 9 -> 4; level >= 5 -> 3; else -> 2 }
    }

    private fun setExpManually() {
        // Simple: add 1000 as placeholder — in real impl would open text input
        // For now cycle through common XP values
        val thresholds = intArrayOf(0,300,900,2700,6500,14000,23000,34000,48000,64000,85000)
        val next = thresholds.firstOrNull { it > experience } ?: (experience + 1000)
        experience = next
    }

    private fun xpForNextLevel(lv: Int): Int {
        val thresholds = intArrayOf(0,300,900,2700,6500,14000,23000,34000,48000,64000,85000,100000,120000,140000,165000,195000,225000,265000,305000,355000)
        return if (lv >= 20) 355000 else thresholds.getOrElse(lv) { 355000 }
    }

    private fun renderSaves(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int) {
        val saves = ClientGmRegistry.skills.filter { it.isSavingThrow }
        saves.forEachIndexed { i, save ->
            val lv = skills[save.id] ?: 0
            val icon = when (lv) { 0 -> "§7○"; 1 -> "§a●"; else -> "§b◆" }
            btn(context, mouseX, mouseY, x, y + i * 10, 8, 8, icon) { skills[save.id] = (lv + 1) % 3 }
            val statMod = stats[save.linkedStat]?.let { (it - 10) / 2 } ?: 0
            val bonus = statMod + lv * profBonus
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"
            lbl(context, "§7$bonusStr", x + 10, y + i * 10 + 1, 0.65f, if (lv > 0) 0x55FF55 else 0xAAAAAA)
            lbl(context, save.displayName, x + 26, y + i * 10 + 1, 0.65f, if (lv > 0) 0x55FF55 else 0xCCCCCC)
        }
    }

    private fun renderSkills(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val skillList = ClientGmRegistry.skills.filter { !it.isSavingThrow }
        val visible = skillList.drop(skillScroll).take(h / 9)
        visible.forEachIndexed { i, skill ->
            val lv = skills[skill.id] ?: 0
            val icon = when (lv) { 0 -> "§7○"; 1 -> "§a●"; else -> "§b◆" }
            btn(context, mouseX, mouseY, x, y + i * 9, 8, 8, icon) { skills[skill.id] = (lv + 1) % 3 }
            val statMod = stats[skill.linkedStat]?.let { (it - 10) / 2 } ?: 0
            val bonus = statMod + lv * profBonus
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"
            lbl(context, "§7$bonusStr", x + 10, y + i * 9 + 1, 0.6f, if (lv > 0) 0x55FF55 else 0xAAAAAA)
            lbl(context, skill.displayName, x + 26, y + i * 9 + 1, 0.6f, if (lv > 0) 0x55FF55 else 0xCCCCCC)
        }
        if (skillScroll > 0) btn(context, mouseX, mouseY, x + w - 10, y, 10, 9, "§7▲") { skillScroll-- }
        if (skillScroll + h / 9 < skillList.size) btn(context, mouseX, mouseY, x + w - 10, y + h - 10, 10, 9, "§7▼") { skillScroll++ }
    }

    private fun renderDeathSaves(context: DrawContext, mouseX: Int, mouseY: Int, bx: Int, by: Int, bw: Int, bh: Int) {
        val cx = bx + bw / 2
        lbl(context, "DEATH SAVES", cx - 22, by + 3, 0.55f, 0xD4AF37)
        // Successes / Failures centered
        val iconY1 = by + 16; val iconY2 = by + 30
        lbl(context, "§a✓", cx - 18, iconY1, 0.65f, 0x55FF55)
        for (i in 0..2) {
            val icon = if (i < deathSuccesses) "§a●" else "§7○"
            btn(context, mouseX, mouseY, cx - 10 + i * 10, iconY1 - 1, 8, 8, icon) { deathSuccesses = if (deathSuccesses > i) i else i + 1 }
        }
        lbl(context, "§c✗", cx - 18, iconY2, 0.65f, 0xFF5555)
        for (i in 0..2) {
            val icon = if (i < deathFailures) "§c●" else "§7○"
            btn(context, mouseX, mouseY, cx - 10 + i * 10, iconY2 - 1, 8, 8, icon) { deathFailures = if (deathFailures > i) i else i + 1 }
        }
    }

    private fun renderHpBox(context: DrawContext, mouseX: Int, mouseY: Int, bx: Int, by: Int, bw: Int, bh: Int) {
        val cx = bx + bw / 2
        lbl(context, "HIT POINTS", cx - 18, by + 3, 0.55f, 0xD4AF37)
        // Cur HP: label left, [-] value [+] centered
        val row1Y = by + 16
        lbl(context, "§7Cur", bx + 3, row1Y + 1, 0.55f, 0x888888)
        btn(context, mouseX, mouseY, cx - 14, row1Y, 8, 9, "§c-") { currentHp = (currentHp - 1).coerceAtLeast(0f) }
        val curW = (textRenderer.getWidth("${currentHp.toInt()}") * 0.8f).toInt()
        lbl(context, "${currentHp.toInt()}", cx - curW / 2, row1Y + 1, 0.8f, 0xFF5555)
        btn(context, mouseX, mouseY, cx + 8, row1Y, 8, 9, "§a+") { currentHp = (currentHp + 1).coerceAtMost(maxHp) }
        // Max HP: label left, [-] value [+] centered
        val row2Y = by + 30
        lbl(context, "§7Max", bx + 3, row2Y + 1, 0.55f, 0x888888)
        btn(context, mouseX, mouseY, cx - 14, row2Y, 8, 9, "§c-") { maxHp = (maxHp - 1).coerceAtLeast(1f) }
        val maxW = (textRenderer.getWidth("${maxHp.toInt()}") * 0.8f).toInt()
        lbl(context, "${maxHp.toInt()}", cx - maxW / 2, row2Y + 1, 0.8f, 0xFFFFFF)
        btn(context, mouseX, mouseY, cx + 8, row2Y, 8, 9, "§a+") { maxHp += 1f }
    }

    private fun renderSpeedBox(context: DrawContext, mouseX: Int, mouseY: Int, bx: Int, by: Int, bw: Int, bh: Int) {
        val cx = bx + bw / 2
        lbl(context, "MOVEMENT", cx - 16, by + 3, 0.55f, 0xD4AF37)
        // Speed: label left, [-] value [+] centered
        val row1Y = by + 16
        lbl(context, "§7Spd", bx + 3, row1Y + 1, 0.55f, 0x888888)
        btn(context, mouseX, mouseY, cx - 16, row1Y, 8, 9, "§c-") { speedFt = (speedFt - 5).coerceAtLeast(0) }
        lbl(context, "${speedFt}ft", cx - 8, row1Y + 1, 0.75f, 0xFFFFFF)
        btn(context, mouseX, mouseY, cx + 8, row1Y, 8, 9, "§a+") { speedFt += 5 }
        // Size: label left, [-] value [+] centered — independent from speed
        val row2Y = by + 30
        lbl(context, "§7Size", bx + 3, row2Y + 1, 0.55f, 0x888888)
        btn(context, mouseX, mouseY, cx - 16, row2Y, 8, 9, "§c-") { sizeFactor = (sizeFactor - 0.05f).coerceAtLeast(0.1f) }
        lbl(context, "%.2f".format(sizeFactor), cx - 8, row2Y + 1, 0.75f, 0xCCCCCC)
        btn(context, mouseX, mouseY, cx + 8, row2Y, 8, 9, "§a+") { sizeFactor += 0.05f }
    }

    private fun renderFeatures(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        features.forEachIndexed { i, featId ->
            val featName = ClientGmRegistry.features.find { it.id == featId }?.displayName ?: featId.path
            val fy = y + i * 11
            if (fy + 10 > y + h) return@forEachIndexed
            val hovered = mouseX in x..(x + w - 14) && mouseY in fy..(fy + 10)
            lbl(context, featName, x, fy + 1, 0.65f, if (hovered) 0xFFD700 else 0xCCCCCC)
            btn(context, mouseX, mouseY, x + w - 12, fy, 10, 9, "§cX") { features.remove(featId) }
        }
        // Feature add dropdown
        if (featDropOpen) {
            val dropX = x; var dropY = y + features.size * 11
            ClientGmRegistry.features.take(10).forEach { feat ->
                btn(context, mouseX, mouseY, dropX, dropY, w - 4, 9, "§7${feat.displayName}") {
                    if (!features.contains(feat.id)) features.add(feat.id)
                    featDropOpen = false
                }
                dropY += 10
            }
        }
    }

    private fun renderDropdowns(context: DrawContext, mouseX: Int, mouseY: Int, infoX: Int, infoY: Int) {
        val m = context.matrices
        // Render dropdowns at high Z so they appear on top
        m.push(); m.translate(0f, 0f, 200f)
        if (classDropOpen) {
            var dy = infoY + 8
            ClientGmRegistry.classes.forEach { cls ->
                btn(context, mouseX, mouseY, infoX + 24, dy, 70, 9,
                    if (cls.id == classId) "§a${cls.displayName}" else "§7${cls.displayName}") {
                    classId = cls.id; subclassId = null; classDropOpen = false
                }
                dy += 10
            }
        }
        if (subDropOpen) {
            val subs = classId?.let { cid -> ClientGmRegistry.classes.find { it.id == cid }?.subclasses } ?: emptyList()
            var dy = infoY + 18
            subs.forEach { sub ->
                btn(context, mouseX, mouseY, infoX + 20, dy, 74, 9,
                    if (sub.id == subclassId) "§a${sub.displayName}" else "§7${sub.displayName}") {
                    subclassId = sub.id; subDropOpen = false
                }
                dy += 10
            }
        }
        if (raceDropOpen) {
            var dy = infoY + 28
            ClientGmRegistry.races.forEach { race ->
                btn(context, mouseX, mouseY, infoX + 22, dy, 72, 9,
                    if (race.id == raceId) "§a${race.displayName}" else "§7${race.displayName}") {
                    raceId = race.id; raceDropOpen = false
                }
                dy += 10
            }
        }
        if (alignDropOpen) {
            val col2 = infoX + (width - 5 - infoX) / 2
            var dy = infoY + 28
            ALIGNMENTS.forEach { al ->
                btn(context, mouseX, mouseY, col2 + 22, dy, 80, 9,
                    if (al == alignment) "§a$al" else "§7$al") {
                    alignment = al; alignDropOpen = false
                }
                dy += 10
            }
        }
        m.pop()
    }

    private fun applyAll() {
        val statBuf = PacketByteBufs.create()
        statBuf.writeString(snapshot.playerName)
        statBuf.writeInt(stats.size)
        stats.forEach { (id, v) -> statBuf.writeIdentifier(id); statBuf.writeInt(v) }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_STATS, statBuf)

        val idBuf = PacketByteBufs.create()
        idBuf.writeString(snapshot.playerName)
        idBuf.writeBoolean(classId != null); if (classId != null) idBuf.writeIdentifier(classId!!)
        idBuf.writeBoolean(subclassId != null); if (subclassId != null) idBuf.writeIdentifier(subclassId!!)
        idBuf.writeInt(level)
        idBuf.writeBoolean(raceId != null); if (raceId != null) idBuf.writeIdentifier(raceId!!)
        idBuf.writeBoolean(true); idBuf.writeString(gender)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDENTITY, idBuf)

        val skillBuf = PacketByteBufs.create()
        skillBuf.writeString(snapshot.playerName)
        skillBuf.writeInt(skills.size)
        skills.forEach { (id, lv) -> skillBuf.writeIdentifier(id); skillBuf.writeInt(lv) }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_SKILLS, skillBuf)

        statusMsg = "§aApplied!"; statusTimer = 1f
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
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

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        // Check EXP text click — stored bounds
        val pad = 5; val headerY = pad + 13; val lvY = headerY + 26 + 2
        val lvCx = pad + (width / 5) / 2
        val expText = "$experience / ${xpForNextLevel(level)}"
        val expScaledW = (textRenderer.getWidth(expText) * 0.45f).toInt()
        if (mx in (lvCx - expScaledW / 2)..(lvCx + expScaledW / 2) && my in (lvY + 18)..(lvY + 27)) {
            editingExp = !editingExp
            if (editingExp) expInputBuffer = "$experience"
            return true
        }
        if (editingExp) { editingExp = false; return true }
        for (b in btns.reversed()) {
            if (mx in b.x..(b.x + b.w) && my in b.y..(b.y + b.h)) { b.action(); return true }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (editingExp) {
            when (keyCode) {
                256 -> { editingExp = false; return true } // ESC
                257, 335 -> { // Enter
                    experience = expInputBuffer.toIntOrNull()?.coerceAtLeast(0) ?: experience
                    editingExp = false; return true
                }
                259 -> { // Backspace
                    if (expInputBuffer.isNotEmpty()) expInputBuffer = expInputBuffer.dropLast(1)
                    return true
                }
            }
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (editingExp && chr.isDigit()) {
            expInputBuffer += chr
            return true
        }
        return super.charTyped(chr, modifiers)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        // Only scroll skills if mouse is in the skills area (right side of screen, below saves)
        val pad = 5; val statBoxW = 52
        val midX = pad + 10 + statBoxW + 14
        if (mouseX.toInt() > midX) {
            skillScroll = (skillScroll - amount.toInt()).coerceAtLeast(0)
        }
        return true
    }

    override fun shouldPause() = false
}
