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
    private var lastExpTextX = 0; private var lastExpTextY = 0; private var lastExpTextW = 0
    // Skills box bounds for scroll detection
    private var skillsBoxX = 0; private var skillsBoxY = 0; private var skillsBoxW = 0; private var skillsBoxH2 = 0

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
        lbl(context, snapshot.playerName, pad + 3, headerY + 9, 1.1f, 0xFFD700)
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
        // XP progress bar — narrower, thinner, buttons on sides of bar
        val barMargin = 14  // space for buttons
        val barX = pad + barMargin; val barY = lvY + 13
        val barW = leftHeaderW - barMargin * 2; val barH = 3
        val xpPrev = xpForNextLevel(level - 1)  // XP needed to reach current level
        // Fix: progress within current level only
        val xpInLevel = (experience - xpPrev).coerceAtLeast(0)
        val xpForLevel = (xpNeeded - xpPrev).coerceAtLeast(1)
        val xpFrac = (xpInLevel.toFloat() / xpForLevel).coerceIn(0f, 1f)
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF333333.toInt())
        context.fill(barX, barY, barX + (barW * xpFrac).toInt(), barY + barH, 0xFF4488FF.toInt())
        // Buttons on sides of bar — square 8x8
        btn(context, mouseX, mouseY, pad + 3, barY - 3, 8, 8, "§c-") { experience = (experience - 100).coerceAtLeast(0) }
        btn(context, mouseX, mouseY, pad + barMargin + barW + 1, barY - 3, 8, 8, "§a+") { experience += 100 }
        // EXP text centered below bar — clickable
        val expText = "$experience / $xpNeeded"
        val expScaledW = (textRenderer.getWidth(expText) * 0.45f).toInt()
        val expBtnGap = 3
        val expMinusX = lvCx - expScaledW / 2 - expBtnGap - 8
        val expPlusX = lvCx + expScaledW / 2 + expBtnGap
        val displayExp = if (editingExp) "${expInputBuffer}_" else expText
        val displayColor = if (editingExp) 0xFFFF55 else if (expClickHovered(mouseX, mouseY, lvCx, lvY, expScaledW)) 0xFFFF55 else 0x888888
        lbl(context, displayExp, lvCx - expScaledW / 2, lvY + 19, 0.45f, displayColor)

        // Info box — smaller height (2 rows only), nav buttons INSIDE at bottom
        // ── PROF BONUS BOX (between name/level and info box) ─────────────────
        val profBoxW = 40
        val profBoxX = pad + leftHeaderW + 4
        box(context, profBoxX, headerY, profBoxW, headerH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "§7PROF", profBoxX + 4, headerY + 2, 0.5f, 0x888888)
        lbl(context, "+$profBonus", profBoxX + 4, headerY + 10, 0.85f, 0xFFD700)
        btn(context, mouseX, mouseY, profBoxX + 3, headerY + 22, 8, 9, "§c-") { profBonus = (profBonus - 1).coerceAtLeast(1) }
        btn(context, mouseX, mouseY, profBoxX + 13, headerY + 22, 8, 9, "§a+") { profBonus = (profBonus + 1).coerceAtMost(9) }

        val infoBoxH = 38  // 2 rows of dropdowns + nav buttons row
        val infoX = profBoxX + profBoxW + 4; val infoW = W - infoX - pad
        // Info box is shorter than name+level combined — align to top
        box(context, infoX, headerY, infoW, infoBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderInfoBox(context, mouseX, mouseY, infoX + 3, headerY + 2, infoW - 6, infoBoxH - 4)

        val bodyY = headerY + headerH + 4

        // ── STAT COLUMN — shifted right for equal margins ──────────────────────
        val statOrder = listOf(BbfStats.STRENGTH, BbfStats.DEXTERITY, BbfStats.CONSTITUTION,
                               BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA)
        val statMarginL = pad + 14  // equal margin left
        statOrder.forEachIndexed { i, stat ->
            val slotH = (H - bodyY - pad) / 6
            val sqSize2 = slotH - 1
            val sy = bodyY + i * slotH
            renderStatBox(context, mouseX, mouseY, statMarginL, sy, sqSize2 + 4, sqSize2, stat)
        }

        // ── BODY LAYOUT ───────────────────────────────────────────────────────
        val sqSize = (H - bodyY - pad) / 6 - 1
        val statColEnd = statMarginL + sqSize + 4 + 12  // stat box right edge + btn + gap

        // Saves + Skills — narrower
        val midW = (W - statColEnd - pad) * 38 / 100  // narrower
        val midX = statColEnd + 4
        val savesH = 80
        box(context, midX, bodyY, midW, savesH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "SAVING THROWS", midX + 4, bodyY + 3, 0.65f, 0xD4AF37)
        renderSaves(context, mouseX, mouseY, midX + 4, bodyY + 13, midW - 8)

        val skillsY = bodyY + savesH + 4; val skillsH = H - skillsY - pad
        // Store skills box bounds for scroll detection
        skillsBoxX = midX; skillsBoxY = skillsY; skillsBoxW = midW; skillsBoxH2 = skillsH
        box(context, midX, skillsY, midW, skillsH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "SKILLS", midX + 4, skillsY + 3, 0.65f, 0xD4AF37)
        renderSkills(context, mouseX, mouseY, midX + 4, skillsY + 13, midW - 8, skillsH - 16)

        // Center column: 3 param boxes stacked
        val paramBoxW = 60; val paramBoxH = 50
        val centerX = midX + midW + 4
        val dsY = bodyY; val hpY = dsY + paramBoxH + 4; val spY = hpY + paramBoxH + 4

        box(context, centerX, dsY, paramBoxW, paramBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderDeathSaves(context, mouseX, mouseY, centerX, dsY, paramBoxW, paramBoxH)
        box(context, centerX, hpY, paramBoxW, paramBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderHpBox(context, mouseX, mouseY, centerX, hpY, paramBoxW, paramBoxH)
        box(context, centerX, spY, paramBoxW, paramBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        renderSpeedBox(context, mouseX, mouseY, centerX, spY, paramBoxW, paramBoxH)

        // Right: player model above Features
        val rightX = centerX + paramBoxW + 4; val rightW = W - rightX - pad
        val featH = (H - bodyY - pad) / 3
        val featY = H - pad - featH
        val modelAreaH = featY - bodyY - 4

        // Player model
        val mc = MinecraftClient.getInstance()
        val player = mc.world?.players?.find { it.name.string == snapshot.playerName }
        if (player != null && modelAreaH > 20) {
            val modelCx = rightX + rightW / 2
            val modelY = bodyY + modelAreaH - 10
            InventoryScreen.drawEntity(context, modelCx, modelY, 30, modelCx - mouseX.toFloat(), modelY - mouseY.toFloat(), player)
        }
        btn(context, mouseX, mouseY, rightX + rightW / 2 - 20, featY - 12, 40, 10, "§7Change") { /* TODO */ }

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
        // Nav buttons inside info box (bottom row)
        val navY = y + 24; val navBtnW = (w - 8) / 3
        btn(context, mouseX, mouseY, x, navY, navBtnW, 9, "§eЛичность") { /* TODO */ }
        btn(context, mouseX, mouseY, x + navBtnW + 4, navY, navBtnW, 9, "§eСпособности") { /* TODO */ }
        btn(context, mouseX, mouseY, x + (navBtnW + 4) * 2, navY, navBtnW, 9, "§eМагия") { /* TODO */ }
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
        // thresholds[i] = XP needed to REACH level i+1
        // thresholds[0]=0 (start), thresholds[1]=300 (reach lv2), thresholds[2]=900 (reach lv3)...
        val thresholds = intArrayOf(0,300,900,2700,6500,14000,23000,34000,48000,64000,85000,100000,120000,140000,165000,195000,225000,265000,305000,355000)
        return if (lv <= 0) 0 else if (lv >= 20) 355000 else thresholds.getOrElse(lv) { 355000 }
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
        // Bullets only centered (✓/✗ to the left of centered bullets)
        val bulletsW = 30; val bulletsStartX = cx - bulletsW / 2
        val iconY1 = by + 16; val iconY2 = by + 28
        lbl(context, "§a✓", bulletsStartX - 10, iconY1 + 1, 0.65f, 0x55FF55)
        for (i in 0..2) {
            val icon = if (i < deathSuccesses) "§a●" else "§7○"
            btn(context, mouseX, mouseY, bulletsStartX + i * 10, iconY1, 8, 8, icon) { deathSuccesses = if (deathSuccesses > i) i else i + 1 }
        }
        lbl(context, "§c✗", bulletsStartX - 10, iconY2 + 1, 0.65f, 0xFF5555)
        for (i in 0..2) {
            val icon = if (i < deathFailures) "§c●" else "§7○"
            btn(context, mouseX, mouseY, bulletsStartX + i * 10, iconY2, 8, 8, icon) { deathFailures = if (deathFailures > i) i else i + 1 }
        }
    }

    private fun renderHpBox(context: DrawContext, mouseX: Int, mouseY: Int, bx: Int, by: Int, bw: Int, bh: Int) {
        val cx = bx + bw / 2
        lbl(context, "HIT POINTS", cx - 18, by + 3, 0.55f, 0xD4AF37)
        // "Cur" label above current HP
        lbl(context, "§7Cur", cx - 4, by + 13, 0.5f, 0x888888)
        val row1Y = by + 20
        btn(context, mouseX, mouseY, cx - 14, row1Y, 8, 9, "§c-") { currentHp = (currentHp - 1).coerceAtLeast(0f) }
        val curW = (textRenderer.getWidth("${currentHp.toInt()}") * 0.8f).toInt()
        lbl(context, "${currentHp.toInt()}", cx - curW / 2, row1Y + 1, 0.8f, 0xFF5555)
        btn(context, mouseX, mouseY, cx + 8, row1Y, 8, 9, "§a+") { currentHp = (currentHp + 1).coerceAtMost(maxHp) }
        // Max HP
        val row2Y = by + 32
        btn(context, mouseX, mouseY, cx - 14, row2Y, 8, 9, "§c-") { maxHp = (maxHp - 1).coerceAtLeast(1f) }
        val maxW = (textRenderer.getWidth("${maxHp.toInt()}") * 0.8f).toInt()
        lbl(context, "${maxHp.toInt()}", cx - maxW / 2, row2Y + 1, 0.8f, 0xFFFFFF)
        btn(context, mouseX, mouseY, cx + 8, row2Y, 8, 9, "§a+") { maxHp += 1f }
        // "Max" label below max HP
        lbl(context, "§7Max", cx - 4, by + 43, 0.5f, 0x888888)
    }

    private fun renderSpeedBox(context: DrawContext, mouseX: Int, mouseY: Int, bx: Int, by: Int, bw: Int, bh: Int) {
        val cx = bx + bw / 2
        lbl(context, "MOVEMENT", cx - 16, by + 3, 0.55f, 0xD4AF37)
        // "Spd" label above speed
        lbl(context, "§7Spd", cx - 4, by + 13, 0.5f, 0x888888)
        val row1Y = by + 20
        btn(context, mouseX, mouseY, cx - 18, row1Y, 8, 9, "§c-") { speedFt = (speedFt - 1).coerceAtLeast(0) }
        lbl(context, "${speedFt}ft", cx - 8, row1Y + 1, 0.75f, 0xFFFFFF)
        btn(context, mouseX, mouseY, cx + 12, row1Y, 8, 9, "§a+") { speedFt += 1 }
        // Size
        val row2Y = by + 32
        btn(context, mouseX, mouseY, cx - 18, row2Y, 8, 9, "§c-") { sizeFactor = (sizeFactor - 0.05f).coerceAtLeast(0.1f) }
        lbl(context, "%.2f".format(sizeFactor), cx - 8, row2Y + 1, 0.75f, 0xCCCCCC)
        btn(context, mouseX, mouseY, cx + 12, row2Y, 8, 9, "§a+") { sizeFactor += 0.05f }
        // "Size" label below size
        lbl(context, "§7Size", cx - 5, by + 43, 0.5f, 0x888888)
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
        m.push(); m.translate(0f, 0f, 300f)

        fun drawDropdown(items: List<Pair<String, () -> Unit>>, x: Int, y: Int, w: Int) {
            val itemH = 10
            val totalH = items.size * itemH + 2
            // Background
            context.fill(x, y, x + w, y + totalH, 0xFF1a1a1a.toInt())
            context.fill(x, y, x + w, y + 1, 0xFFd4a96a.toInt())
            context.fill(x, y + totalH - 1, x + w, y + totalH, 0xFFd4a96a.toInt())
            context.fill(x, y, x + 1, y + totalH, 0xFFd4a96a.toInt())
            context.fill(x + w - 1, y, x + w, y + totalH, 0xFFd4a96a.toInt())
            items.forEachIndexed { i, (label, action) ->
                val iy = y + 1 + i * itemH
                val hov = mouseX in x..(x + w) && mouseY in iy..(iy + itemH)
                if (hov) context.fill(x + 1, iy, x + w - 1, iy + itemH, 0xFF3a2a1a.toInt())
                btn(context, mouseX, mouseY, x + 1, iy, w - 2, itemH, label, action)
            }
        }

        val col1X = infoX; val col2X = infoX + (width - 5 - infoX) / 2 + 4
        val dropY = infoY + 10  // just below the Class/Race row

        if (classDropOpen) {
            val items = ClientGmRegistry.classes.map { cls ->
                val label = if (cls.id == classId) "§a${cls.displayName}" else "§f${cls.displayName}"
                label to { classId = cls.id; subclassId = null; classDropOpen = false }
            }
            drawDropdown(items, col1X + 24, dropY, (width - 5 - infoX) / 2 - 28)
        }
        if (subDropOpen) {
            val subs = classId?.let { cid -> ClientGmRegistry.classes.find { it.id == cid }?.subclasses } ?: emptyList()
            val items = subs.map { sub ->
                val label = if (sub.id == subclassId) "§a${sub.displayName}" else "§f${sub.displayName}"
                label to { subclassId = sub.id; subDropOpen = false }
            }
            if (items.isNotEmpty()) drawDropdown(items, col1X + 20, dropY + 12, (width - 5 - infoX) / 2 - 24)
        }
        if (raceDropOpen) {
            val items = ClientGmRegistry.races.map { race ->
                val label = if (race.id == raceId) "§a${race.displayName}" else "§f${race.displayName}"
                label to { raceId = race.id; raceDropOpen = false }
            }
            drawDropdown(items, col2X + 22, dropY, (width - 5 - infoX) / 2 - 26)
        }
        if (subraceDropOpen) {
            // TODO: populate subraces from registry
            subraceDropOpen = false
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
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        if (mx in skillsBoxX..(skillsBoxX + skillsBoxW) && my in skillsBoxY..(skillsBoxY + skillsBoxH2)) {
            skillScroll = (skillScroll - amount.toInt()).coerceAtLeast(0)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun shouldPause() = false
}
