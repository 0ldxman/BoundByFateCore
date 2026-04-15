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
import omc.boundbyfate.registry.BbfSkills
import omc.boundbyfate.registry.BbfStats

/**
 * GM character edit screen — single-page D&D charsheet layout with editing controls.
 *
 * Layout (scaled to screen):
 * ┌─────────────────────────────────────────────────────────────┐
 * │  [← Back]                              [Apply Changes]      │
 * │  Name: Pisechnitsa  Class: [Fighter▼] Lv:[3-+] Race:[▼] ♂  │
 * │  Subclass: [Battle Master▼]                                  │
 * ├──────────────┬──────────────────────────┬───────────────────┤
 * │ ABILITY      │   [Player Model]         │ SAVING THROWS     │
 * │ STR 15 [-][+]│                          │ ○ STR  +5         │
 * │ CON 14 [-][+]│                          │ ● DEX  +3         │
 * │ DEX 13 [-][+]│                          │ ...               │
 * │ INT 10 [-][+]│                          ├───────────────────┤
 * │ WIS 11 [-][+]│                          │ SKILLS            │
 * │ CHA  8 [-][+]│                          │ ○ Athletics  +4   │
 * │              │                          │ ● Acrobatics +2   │
 * │              │                          │ ...               │
 * └──────────────┴──────────────────────────┴───────────────────┘
 */
class GmPlayerEditScreen(
    private val snapshot: GmPlayerSnapshot
) : Screen(Text.literal("GM: ${snapshot.playerName}")) {

    private var cx = 0
    private var cy = 0

    // ═══ EDITABLE STATE ═══
    private val editedStats = mutableMapOf<Identifier, Int>().also { map ->
        listOf(BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY,
               BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA).forEach { stat ->
            map[stat.id] = snapshot.statsData?.getStatValue(stat.id)?.total ?: 10
        }
    }
    private var editedClassId: Identifier? = snapshot.classData?.classId
    private var editedSubclassId: Identifier? = snapshot.classData?.subclassId
    private var editedLevel: Int = snapshot.level
    private var editedRaceId: Identifier? = snapshot.raceData?.raceId
    private var editedGender: String = snapshot.gender ?: "male"
    private val editedSkills = mutableMapOf<Identifier, Int>().also { map ->
        snapshot.skillData?.proficiencies?.forEach { (id, level) -> map[id] = level }
    }

    // ═══ UI STATE ═══
    private var statusMessage = ""
    private var statusTimer = 0f
    private var classDropdownOpen = false
    private var subclassDropdownOpen = false
    private var raceDropdownOpen = false
    private var skillScrollOffset = 0

    // All clickable buttons registered each frame
    private data class Btn(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, val action: () -> Unit)
    private val frameButtons = mutableListOf<Btn>()

    override fun init() {
        cx = width / 2
        cy = height / 2
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        frameButtons.clear()

        if (statusTimer > 0f) statusTimer -= delta * 0.05f

        val pad = 8
        val colLeft = pad
        val colRight = width - pad
        val statColW = 90
        val skillColW = 130
        val modelColX = colLeft + statColW + pad
        val modelColW = colRight - statColW - skillColW - pad * 3
        val skillColX = colRight - skillColW

        // ═══ TOP BAR ═══
        var topY = pad

        // Back button
        addBtn(context, mouseX, mouseY, pad, topY, 40, 11, "§7← Back") {
            MinecraftClient.getInstance().setScreen(GmScreen())
        }

        // Apply button
        addBtn(context, mouseX, mouseY, width - 55, topY, 50, 11, "§aApply") { applyAll() }

        topY += 14

        // Name
        drawLabel(context, "§6${snapshot.playerName}", pad, topY, 1.0f, 0xFFD700)
        topY += 12

        // Class row
        val classLabel = editedClassId?.let { id -> ClientGmRegistry.classes.find { it.id == id }?.displayName ?: id.path } ?: "None"
        drawLabel(context, "Class:", pad, topY + 2, 0.75f, 0x888888)
        addBtn(context, mouseX, mouseY, pad + 28, topY, 70, 11, "§f$classLabel §e▼") {
            classDropdownOpen = !classDropdownOpen; subclassDropdownOpen = false; raceDropdownOpen = false
        }

        // Level
        drawLabel(context, "Lv:", pad + 105, topY + 2, 0.75f, 0x888888)
        addBtn(context, mouseX, mouseY, pad + 120, topY, 10, 11, "§c-") { editedLevel = (editedLevel - 1).coerceAtLeast(1) }
        drawLabel(context, "$editedLevel", pad + 132, topY + 2, 0.75f, 0xFFFFFF)
        addBtn(context, mouseX, mouseY, pad + 140, topY, 10, 11, "§a+") { editedLevel = (editedLevel + 1).coerceAtMost(20) }

        // Race
        val raceLabel = editedRaceId?.let { id -> ClientGmRegistry.races.find { it.id == id }?.displayName ?: id.path } ?: "None"
        drawLabel(context, "Race:", pad + 158, topY + 2, 0.75f, 0x888888)
        addBtn(context, mouseX, mouseY, pad + 183, topY, 65, 11, "§f$raceLabel §e▼") {
            raceDropdownOpen = !raceDropdownOpen; classDropdownOpen = false; subclassDropdownOpen = false
        }

        // Gender toggle
        val genderIcon = when (editedGender) { "male" -> "♂"; "female" -> "♀"; else -> "⚧" }
        addBtn(context, mouseX, mouseY, pad + 253, topY, 14, 11, genderIcon) {
            editedGender = when (editedGender) { "male" -> "female"; "female" -> "other"; else -> "male" }
        }

        topY += 14

        // Subclass row
        val subclasses = editedClassId?.let { id -> ClientGmRegistry.classes.find { it.id == id }?.subclasses } ?: emptyList()
        if (subclasses.isNotEmpty()) {
            val subLabel = editedSubclassId?.let { id -> subclasses.find { it.id == id }?.displayName ?: id.path } ?: "None"
            drawLabel(context, "Subclass:", pad, topY + 2, 0.75f, 0x888888)
            addBtn(context, mouseX, mouseY, pad + 42, topY, 90, 11, "§f$subLabel §e▼") {
                subclassDropdownOpen = !subclassDropdownOpen; classDropdownOpen = false; raceDropdownOpen = false
            }
            topY += 14
        }

        val contentY = topY + 4
        drawHLine(context, pad, width - pad, contentY - 2, 0xFF6b5a3e.toInt())

        // ═══ LEFT COLUMN — ABILITY SCORES ═══
        drawLabel(context, "ABILITY SCORES", colLeft, contentY, 0.75f, 0xD4AF37)
        var statY = contentY + 10

        val stats = listOf(BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY,
                           BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA)
        stats.forEach { stat ->
            val value = editedStats[stat.id] ?: 10
            val mod = (value - 10) / 2
            val modStr = if (mod >= 0) "+$mod" else "$mod"
            val shortKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.short"
            val shortName = Text.translatable(shortKey).string
            val changed = value != (snapshot.statsData?.getStatValue(stat.id)?.total ?: 10)
            val nameColor = if (changed) 0xFFAA44 else 0xCCCCCC

            addBtn(context, mouseX, mouseY, colLeft, statY, 10, 10, "§c-") {
                editedStats[stat.id] = (value - 1).coerceAtLeast(1)
            }
            drawLabel(context, "$shortName", colLeft + 12, statY + 1, 0.75f, nameColor)
            drawLabel(context, "§f$value", colLeft + 38, statY + 1, 0.75f, 0xFFFFFF)
            drawLabel(context, "§7($modStr)", colLeft + 52, statY + 1, 0.75f, if (mod >= 0) 0x55FF55 else 0xFF5555)
            addBtn(context, mouseX, mouseY, colLeft + 72, statY, 10, 10, "§a+") {
                editedStats[stat.id] = (value + 1).coerceAtMost(30)
            }
            statY += 13
        }

        // ═══ CENTER — PLAYER MODEL ═══
        val mc = MinecraftClient.getInstance()
        val player = mc.world?.players?.find { it.name.string == snapshot.playerName }
        if (player != null) {
            val modelX = modelColX + modelColW / 2
            val modelY = contentY + 80
            InventoryScreen.drawEntity(context, modelX, modelY, 45, modelX - mouseX.toFloat(), modelY - mouseY.toFloat(), player)
        }

        // ═══ RIGHT COLUMN — SAVES + SKILLS ═══
        var rightY = contentY
        drawLabel(context, "SAVING THROWS", skillColX, rightY, 0.75f, 0xD4AF37)
        rightY += 10

        val saves = listOf(BbfSkills.SAVE_STRENGTH, BbfSkills.SAVE_CONSTITUTION, BbfSkills.SAVE_DEXTERITY,
                           BbfSkills.SAVE_INTELLIGENCE, BbfSkills.SAVE_WISDOM, BbfSkills.SAVE_CHARISMA)
        saves.forEach { save ->
            val level = editedSkills[save.id] ?: 0
            val icon = if (level > 0) "§a●" else "§7○"
            addBtn(context, mouseX, mouseY, skillColX, rightY, 8, 8, icon) {
                editedSkills[save.id] = if (level == 0) 1 else 0
            }
            val statMod = editedStats[save.linkedStat]?.let { (it - 10) / 2 } ?: 0
            val bonus = statMod + if (level > 0) 2 else 0
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"
            val shortSaveName = save.displayName.replace("Спасбросок ", "").replace("Saving Throw", "").trim().take(3).uppercase()
            drawLabel(context, "$shortSaveName §7$bonusStr", skillColX + 10, rightY + 1, 0.7f, if (level > 0) 0x55FF55 else 0xAAAAAA)
            rightY += 10
        }

        rightY += 4
        drawHLine(context, skillColX, colRight, rightY, 0xFF6b5a3e.toInt())
        rightY += 4

        drawLabel(context, "SKILLS", skillColX, rightY, 0.75f, 0xD4AF37)
        rightY += 10

        val allSkills = ClientGmRegistry.skills.filter { !it.isSavingThrow }
        val visibleSkills = allSkills.drop(skillScrollOffset).take(14)
        visibleSkills.forEach { skill ->
            val level = editedSkills[skill.id] ?: 0
            val icon = when (level) { 0 -> "§7○"; 1 -> "§a●"; else -> "§b◆" }
            addBtn(context, mouseX, mouseY, skillColX, rightY, 8, 8, icon) {
                editedSkills[skill.id] = (level + 1) % 3
            }
            val statMod = editedStats[skill.linkedStat]?.let { (it - 10) / 2 } ?: 0
            val bonus = statMod + level * 2
            val bonusStr = if (bonus >= 0) "+$bonus" else "$bonus"
            val skillName = skill.displayName.take(14)
            drawLabel(context, "$skillName §7$bonusStr", skillColX + 10, rightY + 1, 0.65f, if (level > 0) 0x55FF55 else 0xAAAAAA)
            rightY += 9
        }

        // Scroll
        if (skillScrollOffset > 0) {
            addBtn(context, mouseX, mouseY, colRight - 12, contentY + 80, 10, 10, "§7▲") { skillScrollOffset-- }
        }
        if (skillScrollOffset + 14 < allSkills.size) {
            addBtn(context, mouseX, mouseY, colRight - 12, contentY + 92, 10, 10, "§7▼") { skillScrollOffset++ }
        }

        // ═══ DROPDOWNS (rendered on top) ═══
        if (classDropdownOpen) {
            var dy = topY - 14 + 11
            ClientGmRegistry.classes.forEach { cls ->
                val selected = cls.id == editedClassId
                addBtn(context, mouseX, mouseY, pad + 28, dy, 70, 10,
                    if (selected) "§a${cls.displayName}" else "§7${cls.displayName}") {
                    editedClassId = cls.id; editedSubclassId = null; classDropdownOpen = false
                }
                dy += 11
            }
        }
        if (subclassDropdownOpen) {
            val subs = editedClassId?.let { id -> ClientGmRegistry.classes.find { it.id == id }?.subclasses } ?: emptyList()
            var dy = topY + 11
            subs.forEach { sub ->
                val selected = sub.id == editedSubclassId
                addBtn(context, mouseX, mouseY, pad + 42, dy, 90, 10,
                    if (selected) "§a${sub.displayName}" else "§7${sub.displayName}") {
                    editedSubclassId = sub.id; subclassDropdownOpen = false
                }
                dy += 11
            }
        }
        if (raceDropdownOpen) {
            var dy = topY - 14 + 11
            ClientGmRegistry.races.forEach { race ->
                val selected = race.id == editedRaceId
                addBtn(context, mouseX, mouseY, pad + 183, dy, 65, 10,
                    if (selected) "§a${race.displayName}" else "§7${race.displayName}") {
                    editedRaceId = race.id; raceDropdownOpen = false
                }
                dy += 11
            }
        }

        // Status
        if (statusTimer > 0f) {
            val alpha = (statusTimer * 255).toInt().coerceIn(0, 255)
            context.drawCenteredTextWithShadow(textRenderer, statusMessage, cx, height - 12, (alpha shl 24) or 0x55FF55)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    // ═══ APPLY ═══
    private fun applyAll() {
        val statBuf = PacketByteBufs.create()
        statBuf.writeString(snapshot.playerName)
        statBuf.writeInt(editedStats.size)
        editedStats.forEach { (id, v) -> statBuf.writeIdentifier(id); statBuf.writeInt(v) }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_STATS, statBuf)

        val idBuf = PacketByteBufs.create()
        idBuf.writeString(snapshot.playerName)
        idBuf.writeBoolean(editedClassId != null)
        if (editedClassId != null) idBuf.writeIdentifier(editedClassId!!)
        idBuf.writeBoolean(editedSubclassId != null)
        if (editedSubclassId != null) idBuf.writeIdentifier(editedSubclassId!!)
        idBuf.writeInt(editedLevel)
        idBuf.writeBoolean(editedRaceId != null)
        if (editedRaceId != null) idBuf.writeIdentifier(editedRaceId!!)
        idBuf.writeBoolean(true); idBuf.writeString(editedGender)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDENTITY, idBuf)

        val skillBuf = PacketByteBufs.create()
        skillBuf.writeString(snapshot.playerName)
        skillBuf.writeInt(editedSkills.size)
        editedSkills.forEach { (id, level) -> skillBuf.writeIdentifier(id); skillBuf.writeInt(level) }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_SKILLS, skillBuf)

        statusMessage = "§aApplied!"
        statusTimer = 1f
    }

    // ═══ UI HELPERS ═══

    /** Registers and draws a button, returns true if hovered */
    private fun addBtn(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, label: String, action: () -> Unit) {
        frameButtons.add(Btn(x, y, w, h, label, action))
        val hovered = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = if (hovered) 0xCC4a3a2a.toInt() else 0xCC2b2321.toInt()
        val border = if (hovered) 0xFFd4a96a.toInt() else 0xFF6b5a3e.toInt()
        context.fill(x, y, x + w, y + h, bg)
        context.fill(x, y, x + w, y + 1, border)
        context.fill(x, y + h - 1, x + w, y + h, border)
        context.fill(x, y, x + 1, y + h, border)
        context.fill(x + w - 1, y, x + w, y + h, border)
        val m = context.matrices
        m.push()
        m.translate((x + w / 2).toFloat(), (y + h / 2 - 3).toFloat(), 0f)
        m.scale(0.75f, 0.75f, 1f)
        val tw = textRenderer.getWidth(label)
        context.drawTextWithShadow(textRenderer, label, -(tw / 2), 0, 0xFFFFFF)
        m.pop()
    }

    private fun drawLabel(context: DrawContext, text: String, x: Int, y: Int, scale: Float = 0.75f, color: Int = 0xCCCCCC) {
        val m = context.matrices
        m.push()
        m.translate(x.toFloat(), y.toFloat(), 0f)
        m.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        m.pop()
    }

    private fun drawHLine(context: DrawContext, x1: Int, x2: Int, y: Int, color: Int) {
        context.fill(x1, y, x2, y + 1, color)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        // Iterate in reverse so topmost (last drawn) buttons get priority
        for (btn in frameButtons.reversed()) {
            if (mx in btn.x..(btn.x + btn.w) && my in btn.y..(btn.y + btn.h)) {
                btn.action()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        skillScrollOffset = (skillScrollOffset - amount.toInt()).coerceAtLeast(0)
        return true
    }

    override fun shouldPause() = false
}
