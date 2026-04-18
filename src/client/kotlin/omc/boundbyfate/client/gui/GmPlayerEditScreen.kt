package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.api.stat.StatDefinition
import omc.boundbyfate.client.state.ClientGmRegistry
import omc.boundbyfate.client.state.GmPlayerSnapshot
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.registry.BbfStats

class GmPlayerEditScreen(private val snapshot: GmPlayerSnapshot) :
    Screen(Text.literal("GM: ${snapshot.playerName}")) {

    val editingPlayerName: String get() = snapshot.playerName

    // Helper function for translations
    private fun tr(key: String, vararg args: Any): String {
        return net.minecraft.client.resource.language.I18n.translate(key, *args)
    }

    // ── Editable state ────────────────────────────────────────────────────────
    private val stats = mutableMapOf<Identifier, Int>().also { m ->
        listOf(BbfStats.STRENGTH, BbfStats.DEXTERITY, BbfStats.CONSTITUTION,
               BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA).forEach { s ->
            // Use base value only (without race/class bonuses)
            m[s.id] = snapshot.statsData?.baseStats?.get(s.id) ?: 10
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

    // ── Vitality state ────────────────────────────────────────────────────────
    private var vitality: Int = snapshot.vitality
    private var scarCount: Int = snapshot.scarCount

    // ── Extended state ────────────────────────────────────────────────────────
    private var tempHp: Int = 0   // temporary HP (absorption)
    private var pendingSkinName: String? = null  // null = no change

    // ── UI state ──────────────────────────────────────────────────────────────
    private var statusMsg = ""; private var statusTimer = 0f
    private var skillScroll = 0
    private var featScroll = 0
    private var selectedFeat: Identifier? = null
    private var subraceId: Identifier? = snapshot.raceData?.subraceId
    private var editingExp = false
    private var expInputBuffer = ""
    private var lastExpTextX = 0; private var lastExpTextY = 0; private var lastExpTextW = 0
    // Skills box bounds for scroll detection
    private var skillsBoxX = 0; private var skillsBoxY = 0; private var skillsBoxW = 0; private var skillsBoxH2 = 0
    // Features box bounds for scroll detection
    private var featBoxX = 0; private var featBoxY = 0; private var featBoxW = 0; private var featBoxH = 0

    // Tooltip state
    private var pendingTooltipLines: List<String> = emptyList()
    private var tooltipX = 0; private var tooltipY = 0

    private data class Btn(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, val action: () -> Unit)
    private val btns = mutableListOf<Btn>()

    private val ALIGNMENTS = listOf("Lawful Good","Neutral Good","Chaotic Good",
        "Lawful Neutral","True Neutral","Chaotic Neutral",
        "Lawful Evil","Neutral Evil","Chaotic Evil")

    // ── speed and scale (editable, speed in ft) ──────────────────────────────
    // snapshot.speed is now in ft directly (server sends ft, not attribute value)
    private var speedFt: Int = snapshot.speed.toInt().let { if (it < 5) 30 else it }
    private var sizeFactor: Float = snapshot.scale.let { if (it <= 0f) 1.0f else it }

    override fun init() { /* layout is dynamic */ }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        btns.clear()
        if (statusTimer > 0f) statusTimer -= delta * 0.05f

        val W = width; val H = height; val pad = 5

        // ── HEADER ────────────────────────────────────────────────────────────
        btn(context, mouseX, mouseY, pad, pad, 38, 11, "§7${tr("bbf.gm.button.back")}") { MinecraftClient.getInstance().setScreen(GmScreen()) }
        btn(context, mouseX, mouseY, W - 46, pad, 40, 11, "§a${tr("bbf.gm.button.apply")}") { applyAll() }

        val headerY = pad + 13
        // Name+Level boxes width = same as each other, ending where infoBox starts
        val leftHeaderW = W / 5   // total width for name+level blocks
        val nameBoxH = 26; val lvBoxH = 24
        val headerH = nameBoxH + lvBoxH + 2

        // Block 1: Name + gender (top-left)
        box(context, pad, headerY, leftHeaderW, nameBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, "§7Name", pad + 3, headerY + 2, 0.5f, 0x888888)
        // Name: scale 0.85, clip/scroll if too wide
        val nameScale = 0.85f
        val maxNameW = leftHeaderW - 22  // leave room for gender button
        val namePixelW = (textRenderer.getWidth(snapshot.playerName) * nameScale).toInt()
        val m0 = context.matrices; m0.push()
        if (namePixelW > maxNameW) {
            // Scroll: offset so end of name is visible
            val overflow = namePixelW - maxNameW
            val scrollX = -(overflow * ((System.currentTimeMillis() / 30) % (overflow * 2 + 40)).let {
                if (it < overflow + 20) it.toFloat() else (overflow * 2 + 40 - it).toFloat()
            }.coerceIn(0f, overflow.toFloat()) / overflow)
            m0.translate((pad + 3 + scrollX).toFloat(), (headerY + 9).toFloat(), 0f)
        } else {
            m0.translate((pad + 3).toFloat(), (headerY + 9).toFloat(), 0f)
        }
        m0.scale(nameScale, nameScale, 1f)
        context.drawTextWithShadow(textRenderer, snapshot.playerName, 0, 0, 0xFFD700)
        m0.pop()
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
        lbl(context, tr("bbf.gm.level", level), lvCx - 8, lvY + 2, 0.65f, 0xFFFFFF)
        btn(context, mouseX, mouseY, lvCx + 16, lvY + 1, 8, 9, "§a+") { level = (level + 1).coerceAtMost(20); recalcProfBonus() }
        // XP progress bar — narrower, thinner, buttons on sides of bar
        val barMargin = 14  // space for buttons
        val barX = pad + barMargin; val barY = lvY + 13
        val barW = leftHeaderW - barMargin * 2; val barH = 3
        // experience is per-level XP (resets to 0 on level up), xpNeeded is XP required to advance from current level
        val xpFrac = if (xpNeeded > 0) (experience.toFloat() / xpNeeded).coerceIn(0f, 1f) else 0f
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

        // ── PROF BONUS BOX — centered content, same height as info box ──────
        val profBoxW = 40
        val profBoxX = pad + leftHeaderW + 4
        val infoBoxH = headerH  // same height as name+level combined
        box(context, profBoxX, headerY, profBoxW, infoBoxH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        val profCx = profBoxX + profBoxW / 2
        // Vertically center: label(6) + gap(3) + value(9) + gap(3) + buttons(9) = 30px total
        val profBlockH = 30
        val profBlockY = headerY + (infoBoxH - profBlockH) / 2
        lbl(context, "§7PROF", profCx - 8, profBlockY, 0.5f, 0x888888)
        val profStr = "+$profBonus"
        val profStrW = (textRenderer.getWidth(profStr) * 0.85f).toInt()
        lbl(context, profStr, profCx - profStrW / 2, profBlockY + 9, 0.85f, 0xFFD700)
        btn(context, mouseX, mouseY, profCx - 10, profBlockY + 21, 8, 9, "§c-") { profBonus = (profBonus - 1).coerceAtLeast(1) }
        btn(context, mouseX, mouseY, profCx + 2, profBlockY + 21, 8, 9, "§a+") { profBonus = (profBonus + 1).coerceAtMost(9) }

        val infoX = profBoxX + profBoxW + 4; val infoW = W - infoX - pad
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
        lbl(context, tr("bbf.gm.saving_throws"), midX + 4, bodyY + 3, 0.65f, 0xD4AF37)
        renderSaves(context, mouseX, mouseY, midX + 4, bodyY + 13, midW - 8)

        val skillsY = bodyY + savesH + 4; val skillsH = H - skillsY - pad
        // Store skills box bounds for scroll detection
        skillsBoxX = midX; skillsBoxY = skillsY; skillsBoxW = midW; skillsBoxH2 = skillsH
        box(context, midX, skillsY, midW, skillsH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, tr("bbf.gm.skills"), midX + 4, skillsY + 3, 0.65f, 0xD4AF37)
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
        val changeBtnH = 11; val changeBtnW = 54
        val changeBtnY = featY - changeBtnH - 2
        if (player != null && modelAreaH > 20) {
            val modelCx = rightX + rightW / 2
            val modelY = changeBtnY - 6
            InventoryScreen.drawEntity(context, modelCx, modelY, 45, modelCx - mouseX.toFloat(), modelY - mouseY.toFloat(), player)
        }
        val btnLabel = if (pendingSkinName != null) "§e${pendingSkinName}" else "§7${tr("bbf.gm.button.change_skin")}"
        btn(context, mouseX, mouseY, rightX + rightW / 2 - changeBtnW / 2, changeBtnY, changeBtnW, changeBtnH, btnLabel) { openSkinPicker() }

        box(context, rightX, featY, rightW, featH, 0xCC1a1a1a.toInt(), 0xFF8a6a3a.toInt())
        lbl(context, tr("bbf.gm.features_traits"), rightX + 4, featY + 3, 0.65f, 0xD4AF37)
        btn(context, mouseX, mouseY, rightX + rightW - 14, featY + 2, 12, 9, "§a+") { openFeaturePicker() }
        // Store features box bounds for scroll detection
        featBoxX = rightX + 4; featBoxY = featY + 14; featBoxW = rightW - 8; featBoxH = featH - 18
        renderFeatures(context, mouseX, mouseY, featBoxX, featBoxY, featBoxW, featBoxH)

        // ── DROPDOWNS (on top, high Z) ────────────────────────────────────────
        // Dropdowns replaced by GmPickerScreen modal overlay

        if (statusTimer > 0f) {
            val a = (statusTimer * 255).toInt().coerceIn(0, 255)
            context.drawCenteredTextWithShadow(textRenderer, statusMsg, W / 2, H - 10, (a shl 24) or 0x55FF55)
        }
        super.render(context, mouseX, mouseY, delta)
        // Draw tooltip on top of everything
        if (pendingTooltipLines.isNotEmpty()) {
            drawGmTooltip(context, pendingTooltipLines, tooltipX, tooltipY)
        }
        pendingTooltipLines = emptyList()
    }

    private fun renderInfoBox(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val col1 = x; val col2 = x + w / 2 + 4
        val contentH = 36
        val startY = y + (h - contentH) / 2
        val row0 = startY; val row1 = startY + 13; val row2 = startY + 26

        // Col 1: Class + Subclass
        lbl(context, tr("bbf.gm.class"), col1, row0 + 1, 0.6f, 0x888888)
        val clsName = classId?.let { id -> ClientGmRegistry.classes.find { it.id == id }?.displayName ?: id.path } ?: tr("bbf.gm.none")
        btn(context, mouseX, mouseY, col1 + 24, row0, w / 2 - 28, 9, "§f$clsName §e▼") {
            openClassPicker()
        }
        lbl(context, tr("bbf.gm.subclass"), col1, row1 + 1, 0.6f, 0x888888)
        val subName = subclassId?.let { id ->
            classId?.let { cid -> ClientGmRegistry.classes.find { it.id == cid }?.subclasses?.find { it.id == id }?.displayName }
            ?: id.path
        } ?: tr("bbf.gm.none")
        btn(context, mouseX, mouseY, col1 + 20, row1, w / 2 - 24, 9, "§f$subName §e▼") {
            openSubclassPicker()
        }

        // Col 2: Race + Subrace
        lbl(context, tr("bbf.gm.race"), col2, row0 + 1, 0.6f, 0x888888)
        val raceName = raceId?.let { id -> ClientGmRegistry.races.find { it.id == id }?.displayName ?: id.path } ?: tr("bbf.gm.none")
        btn(context, mouseX, mouseY, col2 + 22, row0, w / 2 - 26, 9, "§f$raceName §e▼") {
            openRacePicker()
        }
        lbl(context, tr("bbf.gm.subrace"), col2, row1 + 1, 0.6f, 0x888888)
        val subraceName = subraceId?.path?.replace("_", " ")
            ?.split(" ")?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } } ?: tr("bbf.gm.none")
        btn(context, mouseX, mouseY, col2 + 20, row1, w / 2 - 24, 9, "§f$subraceName §e▼") {
            openSubracePicker()
        }

        // Nav buttons (bottom row)
        val navBtnW = (w - 8) / 3
        btn(context, mouseX, mouseY, x, row2, navBtnW, 9, "§e${tr("bbf.gm.button.identity")}") {
            client?.setScreen(GmIdentityScreen(snapshot))
        }
        btn(context, mouseX, mouseY, x + navBtnW + 4, row2, navBtnW, 9, "§e${tr("bbf.gm.button.abilities")}") { /* TODO */ }
        btn(context, mouseX, mouseY, x + (navBtnW + 4) * 2, row2, navBtnW, 9, "§e${tr("bbf.gm.button.magic")}") { /* TODO */ }
    }

    // ── Picker helpers ────────────────────────────────────────────────────────

    private fun openClassPicker() {
        val items = ClientGmRegistry.classes.map { it.id to it.displayName }
        client?.setScreen(GmPickerScreen(tr("bbf.gm.picker.class"), items, classId, this) { picked ->
            classId = picked
            subclassId = null
        })
    }

    private fun openSubclassPicker() {
        val cls = classId ?: return
        val clsInfo = ClientGmRegistry.classes.find { it.id == cls } ?: return
        if (clsInfo.subclasses.isEmpty()) return
        if (level < clsInfo.subclassLevel) {
            statusMsg = "§c${tr("bbf.gm.status.subclass_level", clsInfo.subclassLevel)}"
            statusTimer = 2f
            return
        }
        val items = clsInfo.subclasses.map { it.id to it.displayName }
        client?.setScreen(GmPickerScreen(tr("bbf.gm.picker.subclass"), items, subclassId, this) { picked ->
            subclassId = picked
        })
    }

    private fun openRacePicker() {
        val items = ClientGmRegistry.races.map { it.id to it.displayName }
        client?.setScreen(GmPickerScreen(tr("bbf.gm.picker.race"), items, raceId, this) { picked ->
            raceId = picked
            subraceId = null
        })
    }

    private fun openSubracePicker() {
        val selectedRace = raceId?.let { id -> ClientGmRegistry.races.find { it.id == id } }
        val items = selectedRace?.subraces?.map { it.id to it.displayName } ?: emptyList()
        client?.setScreen(GmPickerScreen(tr("bbf.gm.picker.subrace"), items, subraceId, this) { picked ->
            subraceId = picked
        })
    }

    private fun openSkinPicker() {
        client?.setScreen(GmSkinPickerScreen(pendingSkinName, this) { picked ->
            pendingSkinName = picked
        })
    }

    private fun openFeaturePicker() {
        val alreadyHas = features.toSet()
        val items = ClientGmRegistry.features
            .filter { it.id !in alreadyHas }
            .map { it.id to it.displayName }
        client?.setScreen(GmPickerScreen(tr("bbf.gm.picker.feature"), items, null, this) { picked ->
            if (!features.contains(picked)) features.add(picked)
        })
    }

    /** Sends a chat command to the server as the current player. */
    private fun sendCommand(command: String) {
        client?.player?.networkHandler?.sendChatCommand(command.removePrefix("/"))
    }

    private fun drawGmTooltip(context: DrawContext, lines: List<String>, mouseX: Int, mouseY: Int) {
        if (lines.isEmpty()) return
        val pad = 4
        val lineH = (textRenderer.fontHeight * 0.8f).toInt() + 1  // Уменьшенная высота строки
        val maxW = lines.maxOf { (textRenderer.getWidth(it) * 0.8f).toInt() }  // Уменьшенная ширина
        val totalH = lines.size * lineH + pad * 2
        val totalW = maxW + pad * 2

        var tx = mouseX + 8
        var ty = mouseY - 4
        // Keep on screen
        if (tx + totalW > width) tx = mouseX - totalW - 4
        if (ty + totalH > height) ty = height - totalH - 2

        val bgColor = 0xF02b2321.toInt()
        val brdColor = 0xFFb08a66.toInt()

        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)
        context.fill(tx, ty, tx + totalW, ty + totalH, bgColor)
        context.fill(tx, ty, tx + totalW, ty + 1, brdColor)
        context.fill(tx, ty + totalH - 1, tx + totalW, ty + totalH, brdColor)
        context.fill(tx, ty, tx + 1, ty + totalH, brdColor)
        context.fill(tx + totalW - 1, ty, tx + totalW, ty + totalH, brdColor)
        
        context.matrices.push()
        context.matrices.scale(0.8f, 0.8f, 1f)  // Уменьшаем текст
        lines.forEachIndexed { i, line ->
            val color = when {
                i == 0 -> 0xFFFFAA  // header — yellow
                line.startsWith("  ") -> 0xAAAAAA  // indented — gray
                else -> 0xCCCCCC
            }
            val scaledX = ((tx + pad) / 0.8f).toInt()
            val scaledY = ((ty + pad + i * lineH) / 0.8f).toInt()
            context.drawTextWithShadow(textRenderer, line, scaledX, scaledY, color)
        }
        context.matrices.pop()
        context.matrices.pop()
    }

    private fun setTooltip(lines: List<String>, mouseX: Int, mouseY: Int) {
        pendingTooltipLines = lines
        tooltipX = mouseX
        tooltipY = mouseY
    }

    private fun renderStatBox(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, stat: StatDefinition) {
        val v = stats[stat.id] ?: 10
        val bonus = snapshot.statBonuses[stat.id] ?: 0
        val total = v + bonus
        val mod = (total - 10) / 2
        val modStr = if (mod >= 0) "+$mod" else "$mod"
        val modColor = if (mod > 0) 0x55FF55 else if (mod < 0) 0xFF5555 else 0x888888
        val shortKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.short"
        val shortName = Text.translatable(shortKey).string
        val changed = v != (snapshot.statsData?.baseStats?.get(stat.id) ?: 10)

        box(context, x, y, w, h, 0xCC1a1a1a.toInt(), if (changed) 0xFFFFAA44.toInt() else 0xFF8a6a3a.toInt())
        // Name top center
        val m = context.matrices; m.push()
        m.translate((x + w / 2).toFloat(), (y + 3).toFloat(), 0f); m.scale(0.6f, 0.6f, 1f)
        val nw = textRenderer.getWidth(shortName)
        context.drawTextWithShadow(textRenderer, shortName, -(nw / 2), 0, 0xCCCCCC); m.pop()
        // Value center: show "13+2" if there's a race/class bonus (без пробела)
        m.push(); m.translate((x + w / 2).toFloat(), (y + h / 2 - 4).toFloat(), 0f); m.scale(0.95f, 0.95f, 1f)  // Уменьшен размер
        if (bonus != 0) {
            val bonusStr = if (bonus > 0) "+$bonus" else "$bonus"
            val combined = "$v§a$bonusStr"  // Убран пробел
            val vw = textRenderer.getWidth("$v$bonusStr")
            context.drawTextWithShadow(textRenderer, combined, -(vw / 2), 0, 0xFFFFFF)
        } else {
            val vw = textRenderer.getWidth("$v")
            context.drawTextWithShadow(textRenderer, "$v", -(vw / 2), 0, 0xFFFFFF)
        }
        m.pop()
        // Mod bottom center (based on total)
        m.push(); m.translate((x + w / 2).toFloat(), (y + h - 9).toFloat(), 0f); m.scale(0.7f, 0.7f, 1f)
        val mw = textRenderer.getWidth(modStr)
        context.drawTextWithShadow(textRenderer, modStr, -(mw / 2), 0, modColor); m.pop()
        // Buttons outside box — full height of box
        btn(context, mouseX, mouseY, x - 12, y, 11, h, "§c-") { stats[stat.id] = (v - 1).coerceAtLeast(1) }
        btn(context, mouseX, mouseY, x + w + 1, y, 11, h, "§a+") { stats[stat.id] = (v + 1).coerceAtMost(30) }

        // Tooltip on hover
        if (mouseX in x..(x + w) && mouseY in y..(y + h)) {
            val lines = mutableListOf<String>()
            val shortKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.short"
            val shortName = Text.translatable(shortKey).string
            lines.add("$shortName: $total")
            lines.add("  ${tr("bbf.gm.tooltip.base", v)}")
            val breakdown = snapshot.statBonusBreakdown[stat.id]
            if (!breakdown.isNullOrEmpty()) {
                for (entry in breakdown) {
                    val parts = entry.split("|")
                    if (parts.size == 2) {
                        val src = parts[0]
                        val bVal = parts[1].toIntOrNull() ?: 0
                        val sign = if (bVal >= 0) "+" else ""
                        lines.add("  $sign$bVal ${tr("bbf.gm.tooltip.from", src)}")
                    }
                }
            }
            setTooltip(lines, mouseX, mouseY)
        }
    }

    private fun expClickHovered(mouseX: Int, mouseY: Int, cx: Int, lvY: Int, w: Int): Boolean {
        return mouseX in (cx - w / 2)..(cx + w / 2) && mouseY in (lvY + 18)..(lvY + 27)
    }

    private fun recalcProfBonus() {
        profBonus = when { level >= 17 -> 6; level >= 13 -> 5; level >= 9 -> 4; level >= 5 -> 3; else -> 2 }
    }

    private fun setExpManually() {
        // Placeholder — manual XP editing is handled via the clickable EXP text input
    }

    private fun xpForNextLevel(lv: Int): Int {
        // Per-level XP required to advance FROM the given level (XP resets to 0 on level up)
        return when (lv) {
            1 -> 300; 2 -> 900; 3 -> 2700; 4 -> 6500; 5 -> 14000
            6 -> 23000; 7 -> 34000; 8 -> 48000; 9 -> 64000; 10 -> 85000
            11 -> 100000; 12 -> 120000; 13 -> 140000; 14 -> 165000; 15 -> 195000
            16 -> 225000; 17 -> 265000; 18 -> 305000; 19 -> 355000
            else -> Int.MAX_VALUE // level 20 is max
        }
    }

    private fun renderSaves(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int) {
        val saves = ClientGmRegistry.skills.filter { it.isSavingThrow }
        saves.forEachIndexed { i, save ->
            val lv = skills[save.id] ?: 0
            val isLocked = save.id in snapshot.lockedSkills
            val icon = when (lv) { 0 -> "§7○"; 1 -> "§a●"; else -> "§b◆" }
            btn(context, mouseX, mouseY, x, y + i * 10, 8, 8, icon) {
                val next = (lv + 1) % 3
                // Locked skills cannot go below proficient (1)
                skills[save.id] = if (isLocked && next == 0) 1 else next
            }
            val statMod = stats[save.linkedStat]?.let { base ->
                val bonus = snapshot.statBonuses[save.linkedStat] ?: 0
                ((base + bonus) - 10) / 2
            } ?: 0
            val bonus = statMod + lv * profBonus
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"
            val nameColor = if (lv > 0) 0x55FF55 else 0xCCCCCC
            lbl(context, "§7$bonusStr", x + 10, y + i * 10 + 1, 0.65f, if (lv > 0) 0x55FF55 else 0xAAAAAA)
            lbl(context, save.displayName, x + 26, y + i * 10 + 1, 0.65f, nameColor)
            // Show lock icon for class-granted saves
            if (isLocked) lbl(context, "§8🔒", x + w - 8, y + i * 10 + 1, 0.55f, 0x555555)

            // Tooltip on hover
            val rowY = y + i * 10
            if (mouseX in x..(x + w) && mouseY in rowY..(rowY + 10)) {
                val lines = mutableListOf<String>()
                lines.add(save.displayName)
                // Убрана строка "Бонус: ..."
                val sources = snapshot.skillSources[save.id]
                if (!sources.isNullOrEmpty()) {
                    lines.add("  ${tr("bbf.gm.tooltip.proficiency_from")}")
                    sources.forEach { lines.add("    $it") }
                }
                setTooltip(lines, mouseX, mouseY)
            }
        }
    }

    private fun renderSkills(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val skillList = ClientGmRegistry.skills.filter { !it.isSavingThrow }
        val visible = skillList.drop(skillScroll).take(h / 9)
        visible.forEachIndexed { i, skill ->
            val lv = skills[skill.id] ?: 0
            val isLocked = skill.id in snapshot.lockedSkills
            val icon = when (lv) { 0 -> "§7○"; 1 -> "§a●"; else -> "§b◆" }
            btn(context, mouseX, mouseY, x, y + i * 9, 8, 8, icon) {
                val next = (lv + 1) % 3
                skills[skill.id] = if (isLocked && next == 0) 1 else next
            }
            val statMod = stats[skill.linkedStat]?.let { base ->
                val bonus = snapshot.statBonuses[skill.linkedStat] ?: 0
                ((base + bonus) - 10) / 2
            } ?: 0
            val bonus = statMod + lv * profBonus
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"
            lbl(context, "§7$bonusStr", x + 10, y + i * 9 + 1, 0.6f, if (lv > 0) 0x55FF55 else 0xAAAAAA)
            lbl(context, skill.displayName, x + 26, y + i * 9 + 1, 0.6f, if (lv > 0) 0x55FF55 else 0xCCCCCC)

            // Tooltip on hover
            val rowY = y + i * 9
            if (mouseX in x..(x + w) && mouseY in rowY..(rowY + 9)) {
                val lines = mutableListOf<String>()
                lines.add(skill.displayName)
                // Убрана строка "Бонус: ..."
                val sources = snapshot.skillSources[skill.id]
                if (!sources.isNullOrEmpty()) {
                    lines.add("  ${tr("bbf.gm.tooltip.proficiency_from")}")
                    sources.forEach { lines.add("    $it") }
                }
                setTooltip(lines, mouseX, mouseY)
            }
        }
        if (skillScroll > 0) btn(context, mouseX, mouseY, x + w - 10, y, 10, 9, "§7▲") { skillScroll-- }
        if (skillScroll + h / 9 < skillList.size) btn(context, mouseX, mouseY, x + w - 10, y + h - 10, 10, 9, "§7▼") { skillScroll++ }
    }

    private fun renderDeathSaves(context: DrawContext, mouseX: Int, mouseY: Int, bx: Int, by: Int, bw: Int, bh: Int) {
        val cx = bx + bw / 2
        lbl(context, tr("bbf.gm.vitality"), cx - 16, by + 3, 0.55f, 0xD4AF37)

        // 5 pip slots — filled = vitality remaining, empty = lost
        val maxVit = 5
        val pipSize = 8; val pipGap = 3
        val totalPipsW = maxVit * pipSize + (maxVit - 1) * pipGap
        val pipsStartX = cx - totalPipsW / 2
        val pipsY = by + 14

        for (i in 0 until maxVit) {
            val px = pipsStartX + i * (pipSize + pipGap)
            val filled = i < vitality
            val pipColor = when {
                filled && vitality >= 4 -> 0xFF55FF55.toInt()  // green — safe
                filled && vitality >= 2 -> 0xFFFFAA00.toInt()  // orange — warning
                filled -> 0xFFFF5555.toInt()                    // red — critical
                else -> 0xFF333333.toInt()                      // dark — lost
            }
            val borderColor = if (filled) 0xFF888888.toInt() else 0xFF444444.toInt()
            // Draw pip (filled square with border)
            context.fill(px, pipsY, px + pipSize, pipsY + pipSize, borderColor)
            context.fill(px + 1, pipsY + 1, px + pipSize - 1, pipsY + pipSize - 1, pipColor)
            // Click to toggle (GM can set vitality by clicking pips)
            val finalI = i
            btn(context, mouseX, mouseY, px, pipsY, pipSize, pipSize, "") {
                vitality = if (vitality == finalI + 1) finalI else finalI + 1
            }
        }

        // Vitality number label
        val vitStr = "$vitality/$maxVit"
        val vitStrW = (textRenderer.getWidth(vitStr) * 0.6f).toInt()
        lbl(context, vitStr, cx - vitStrW / 2, pipsY + pipSize + 3, 0.6f, 0xCCCCCC)

        // Scars button (functionality TBD)
        val scarY = by + 36
        val scarBtnW = bw - 8
        val scarBtnX = bx + 4
        val scarLabel = if (scarCount > 0) "§c${tr("bbf.gm.scars_count", scarCount)}" else "§7${tr("bbf.gm.scars")}"
        btn(context, mouseX, mouseY, scarBtnX, scarY, scarBtnW, 10, scarLabel) { /* TODO: open scars screen */ }
    }

    private fun renderHpBox(context: DrawContext, mouseX: Int, mouseY: Int, bx: Int, by: Int, bw: Int, bh: Int) {
        val cx = bx + bw / 2
        lbl(context, tr("bbf.gm.hit_points"), cx - 18, by + 3, 0.55f, 0xD4AF37)
        lbl(context, "§7${tr("bbf.gm.current")}", cx - 4, by + 13, 0.5f, 0x888888)
        val row1Y = by + 20
        val hasTempHp = currentHp > maxHp
        val curColor = if (hasTempHp) 0xFFFF55 else 0xFF5555  // yellow if temp HP, red otherwise
        btn(context, mouseX, mouseY, cx - 14, row1Y, 8, 9, "§c-") { currentHp = (currentHp - 1).coerceAtLeast(0f) }
        val curW = (textRenderer.getWidth("${currentHp.toInt()}") * 0.8f).toInt()
        lbl(context, "${currentHp.toInt()}", cx - curW / 2, row1Y + 1, 0.8f, curColor)
        btn(context, mouseX, mouseY, cx + 8, row1Y, 8, 9, "§a+") { currentHp += 1f }
        // Max HP
        val row2Y = by + 32
        btn(context, mouseX, mouseY, cx - 14, row2Y, 8, 9, "§c-") { maxHp = (maxHp - 1).coerceAtLeast(1f) }
        val maxW = (textRenderer.getWidth("${maxHp.toInt()}") * 0.8f).toInt()
        lbl(context, "${maxHp.toInt()}", cx - maxW / 2, row2Y + 1, 0.8f, 0xFFFFFF)
        btn(context, mouseX, mouseY, cx + 8, row2Y, 8, 9, "§a+") { maxHp += 1f }
        lbl(context, "§7${tr("bbf.gm.max")}", cx - 4, by + 43, 0.5f, 0x888888)
        // Calculate tempHp for apply
        tempHp = if (hasTempHp) (currentHp - maxHp).toInt() else 0
    }

    private fun renderSpeedBox(context: DrawContext, mouseX: Int, mouseY: Int, bx: Int, by: Int, bw: Int, bh: Int) {
        val cx = bx + bw / 2
        lbl(context, tr("bbf.gm.movement"), cx - 16, by + 3, 0.55f, 0xD4AF37)
        
        // Speed row
        lbl(context, "§7${tr("bbf.gm.speed")}", cx - 4, by + 13, 0.5f, 0x888888)
        val row1Y = by + 20
        
        // Calculate speed text
        val baseSpeed = snapshot.baseSpeed
        val modifier = speedFt - baseSpeed
        val speedText = if (modifier != 0) {
            val sign = if (modifier > 0) "+" else ""
            val modColor = if (modifier > 0) "§a" else "§c"
            "§7${baseSpeed}${modColor}${sign}${modifier}§fft"
        } else {
            "${speedFt}ft"
        }
        
        // Measure actual text width using textRenderer
        val scale = 0.75f
        val textWidth = (textRenderer.getWidth(speedText) * scale).toInt()
        
        // Position buttons with proper spacing
        val btnWidth = 7
        val spacing = 3
        val leftBtnX = cx - textWidth / 2 - btnWidth - spacing
        val rightBtnX = cx + textWidth / 2 + spacing
        val textX = cx - textWidth / 2
        
        btn(context, mouseX, mouseY, leftBtnX, row1Y, btnWidth, 9, "§c-") { 
            speedFt = (speedFt - 1).coerceAtLeast(0) 
        }
        lbl(context, speedText, textX, row1Y + 1, scale, 0xFFFFFF)
        btn(context, mouseX, mouseY, rightBtnX, row1Y, btnWidth, 9, "§a+") { 
            speedFt += 1 
        }
        
        // Size row
        val row2Y = by + 32
        
        // Calculate scale text
        val baseScale = snapshot.baseScale
        val scaleModifier = sizeFactor - baseScale
        val scaleText = if (scaleModifier.let { kotlin.math.abs(it) } > 0.01f) {
            val sign = if (scaleModifier > 0) "+" else ""
            val modColor = if (scaleModifier > 0) "§a" else "§c"
            "§7%.2f${modColor}${sign}%.2f".format(baseScale, scaleModifier)
        } else {
            "%.2f".format(sizeFactor)
        }
        
        // Measure actual text width
        val scaleTextWidth = (textRenderer.getWidth(scaleText) * scale).toInt()
        val scaleLeftBtnX = cx - scaleTextWidth / 2 - btnWidth - spacing
        val scaleRightBtnX = cx + scaleTextWidth / 2 + spacing
        val scaleTextX = cx - scaleTextWidth / 2
        
        btn(context, mouseX, mouseY, scaleLeftBtnX, row2Y, btnWidth, 9, "§c-") { 
            sizeFactor = (sizeFactor - 0.05f).coerceAtLeast(0.1f) 
        }
        lbl(context, scaleText, scaleTextX, row2Y + 1, scale, 0xCCCCCC)
        btn(context, mouseX, mouseY, scaleRightBtnX, row2Y, btnWidth, 9, "§a+") { 
            sizeFactor += 0.05f 
        }
        
        lbl(context, "§7${tr("bbf.gm.size")}", cx - 5, by + 43, 0.5f, 0x888888)
    }

    private fun renderFeatures(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val rowH = 11
        val visible = features.drop(featScroll).take(h / rowH)
        visible.forEachIndexed { i, featId ->
            val featName = ClientGmRegistry.features.find { it.id == featId }?.displayName ?: featId.path
            val fy = y + i * rowH
            val isLocked = featId in snapshot.lockedFeatures
            val hovered = !isLocked && mouseX in x..(x + w - 20) && mouseY in fy..(fy + 10)
            val nameColor = if (isLocked) 0x888888 else if (hovered) 0xFFD700 else 0xCCCCCC
            lbl(context, featName, x, fy + 1, 0.65f, nameColor)
            if (isLocked) {
                // Move lock icon further left to avoid scroll buttons
                lbl(context, "§7🔒", x + w - 20, fy + 1, 0.65f, 0x666666)
            } else {
                btn(context, mouseX, mouseY, x + w - 12, fy, 10, 9, "§cX") { features.remove(featId) }
            }

            // Tooltip on hover
            if (mouseX in x..(x + w) && mouseY in fy..(fy + 10)) {
                val lines = mutableListOf<String>()
                lines.add(featName)
                val source = snapshot.featureSources[featId]
                if (source != null) lines.add("  ${tr("bbf.gm.tooltip.added_by", source)}")
                setTooltip(lines, mouseX, mouseY)
            }
        }
        if (featScroll > 0) btn(context, mouseX, mouseY, x + w - 10, y, 10, rowH - 2, "§7▲") { featScroll-- }
        if (featScroll + h / rowH < features.size) btn(context, mouseX, mouseY, x + w - 10, y + h - rowH + 2, 10, rowH - 2, "§7▼") { featScroll++ }
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
        idBuf.writeBoolean(subraceId != null); if (subraceId != null) idBuf.writeIdentifier(subraceId!!)
        idBuf.writeBoolean(true); idBuf.writeString(gender)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDENTITY, idBuf)

        val skillBuf = PacketByteBufs.create()
        skillBuf.writeString(snapshot.playerName)
        skillBuf.writeInt(skills.size)
        skills.forEach { (id, lv) -> skillBuf.writeIdentifier(id); skillBuf.writeInt(lv) }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_SKILLS, skillBuf)

        val vitalityBuf = PacketByteBufs.create()
        vitalityBuf.writeString(snapshot.playerName)
        vitalityBuf.writeInt(vitality)
        vitalityBuf.writeInt(scarCount)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_VITALITY, vitalityBuf)

        // Features diff: send added and removed features
        val originalFeatures = snapshot.grantedFeatures.toSet()
        val currentFeatures = features.toSet()
        val added = currentFeatures - originalFeatures
        val removed = originalFeatures - currentFeatures
        for (featId in added) {
            val featBuf = PacketByteBufs.create()
            featBuf.writeString(snapshot.playerName)
            featBuf.writeIdentifier(featId)
            featBuf.writeBoolean(true)
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_FEATURE, featBuf)
        }
        for (featId in removed) {
            val featBuf = PacketByteBufs.create()
            featBuf.writeString(snapshot.playerName)
            featBuf.writeIdentifier(featId)
            featBuf.writeBoolean(false)
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_FEATURE, featBuf)
        }

        // HP (current, max, temp)
        val hpBuf = PacketByteBufs.create()
        hpBuf.writeString(snapshot.playerName)
        hpBuf.writeFloat(currentHp)
        hpBuf.writeFloat(maxHp)
        hpBuf.writeInt(tempHp)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_HP, hpBuf)

        // Speed and scale
        val speedBuf = PacketByteBufs.create()
        speedBuf.writeString(snapshot.playerName)
        speedBuf.writeInt(speedFt)
        speedBuf.writeFloat(sizeFactor)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_SPEED_SCALE, speedBuf)

        // Skin (only if changed)
        if (pendingSkinName != null) {
            val skinBuf = PacketByteBufs.create()
            skinBuf.writeString(snapshot.playerName)
            skinBuf.writeString(pendingSkinName!!)
            skinBuf.writeString("default")  // model — could be made configurable
            ClientPlayNetworking.send(BbfPackets.GM_SET_PLAYER_SKIN, skinBuf)
        }

        statusMsg = "§a${tr("bbf.gm.status.applied")}"; statusTimer = 1f
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
            val skillList = ClientGmRegistry.skills.filter { !it.isSavingThrow }
            val visibleCount = (skillsBoxH2 - 16) / 9
            val maxScroll = (skillList.size - visibleCount).coerceAtLeast(0)
            skillScroll = (skillScroll - amount.toInt()).coerceIn(0, maxScroll)
            return true
        }
        if (mx in featBoxX..(featBoxX + featBoxW) && my in featBoxY..(featBoxY + featBoxH)) {
            val visibleCount = featBoxH / 11
            val maxScroll = (features.size - visibleCount).coerceAtLeast(0)
            featScroll = (featScroll - amount.toInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun shouldPause() = false
}
