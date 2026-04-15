package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.api.skill.ProficiencyLevel
import omc.boundbyfate.client.state.ClientGmRegistry
import omc.boundbyfate.client.state.GmPlayerSnapshot
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.registry.BbfSkills
import omc.boundbyfate.registry.BbfStats

/**
 * GM edit screen — tabbed interface for editing a player's character.
 * Tabs: Stats | Identity | Skills | Features
 */
class GmPlayerEditScreen(
    private val snapshot: GmPlayerSnapshot
) : Screen(Text.literal("GM: ${snapshot.playerName}")) {

    private var cx = 0
    private var cy = 0

    // ═══ TABS ═══
    private enum class Tab { STATS, IDENTITY, SKILLS, FEATURES }
    private var activeTab = Tab.STATS

    // ═══ EDITABLE STATE ═══
    // Stats
    private val editedStats = mutableMapOf<Identifier, Int>().also { map ->
        listOf(BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY,
               BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA).forEach { stat ->
            map[stat.id] = snapshot.statsData?.getStatValue(stat.id)?.total ?: 10
        }
    }

    // Identity
    private var editedClassId: Identifier? = snapshot.classData?.classId
    private var editedSubclassId: Identifier? = snapshot.classData?.subclassId
    private var editedLevel: Int = snapshot.level
    private var editedRaceId: Identifier? = snapshot.raceData?.raceId
    private var editedGender: String = snapshot.gender ?: "male"

    // Skills — map of skillId -> proficiency level (0=none, 1=proficient, 2=expertise)
    private val editedSkills = mutableMapOf<Identifier, Int>().also { map ->
        snapshot.skillData?.proficiencies?.forEach { (id, level) -> map[id] = level }
    }

    // Features
    private val editedFeatures = mutableSetOf<Identifier>()

    // UI state
    private var hasUnsavedChanges = false
    private var statusMessage = ""
    private var statusTimer = 0f

    // Scroll for long lists
    private var skillScrollOffset = 0
    private var featureScrollOffset = 0
    private val visibleRows = 12

    // Dropdown state
    private var classDropdownOpen = false
    private var subclassDropdownOpen = false
    private var raceDropdownOpen = false

    private data class UiButton(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, val action: () -> Unit)
    private val buttons = mutableListOf<UiButton>()

    override fun init() {
        cx = width / 2
        cy = height / 2
        buttons.clear()
        buildCommonButtons()
    }

    private fun buildCommonButtons() {
        // Back
        buttons.add(UiButton(8, 8, 45, 12, "§7← Back") {
            MinecraftClient.getInstance().setScreen(GmScreen())
        })
        // Apply
        buttons.add(UiButton(cx - 25, height - 18, 50, 12, "§aApply") { applyAll() })
        // Tabs
        val tabW = 55
        val tabY = 22
        Tab.values().forEachIndexed { i, tab ->
            val tx = 8 + i * (tabW + 2)
            buttons.add(UiButton(tx, tabY, tabW, 12, tabLabel(tab)) {
                activeTab = tab
                classDropdownOpen = false; subclassDropdownOpen = false; raceDropdownOpen = false
            })
        }
    }

    private fun tabLabel(tab: Tab) = when (tab) {
        Tab.STATS -> "§eStats"
        Tab.IDENTITY -> "§bIdentity"
        Tab.SKILLS -> "§aSkills"
        Tab.FEATURES -> "§dFeatures"
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        if (statusTimer > 0f) statusTimer -= delta * 0.05f

        // Header
        context.drawCenteredTextWithShadow(textRenderer, "§6${snapshot.playerName} §7— GM Edit", cx, 8, 0xFFFFFF)

        // Tab content
        val contentY = 38
        when (activeTab) {
            Tab.STATS -> renderStats(context, mouseX, mouseY, contentY)
            Tab.IDENTITY -> renderIdentity(context, mouseX, mouseY, contentY)
            Tab.SKILLS -> renderSkills(context, mouseX, mouseY, contentY)
            Tab.FEATURES -> renderFeatures(context, mouseX, mouseY, contentY)
        }

        // Common buttons
        buttons.forEach { btn ->
            val isTab = Tab.values().any { tabLabel(it) == btn.label }
            val isActive = isTab && Tab.values().any { activeTab == it && tabLabel(it) == btn.label }
            drawButton(context, btn, mouseX, mouseY, isActive)
        }

        // Unsaved indicator
        if (hasUnsavedChanges) {
            context.drawCenteredTextWithShadow(textRenderer, "§e● Unsaved", cx, height - 28, 0xFFFF55)
        }
        if (statusTimer > 0f) {
            val alpha = (statusTimer * 255).toInt().coerceIn(0, 255)
            context.drawCenteredTextWithShadow(textRenderer, statusMessage, cx, height - 38, (alpha shl 24) or 0x55FF55)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    // ═══ STATS TAB ═══
    private fun renderStats(context: DrawContext, mouseX: Int, mouseY: Int, startY: Int) {
        val stats = listOf(BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY,
                           BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA)
        val colW = (width - 20) / 3
        stats.forEachIndexed { i, stat ->
            val col = i % 3
            val row = i / 2
            val x = 10 + col * colW
            val y = startY + row * 30
            val value = editedStats[stat.id] ?: 10
            val mod = (value - 10) / 2
            val modStr = if (mod >= 0) "+$mod" else "$mod"
            val shortKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.short"
            val shortName = Text.translatable(shortKey).string
            val changed = value != (snapshot.statsData?.getStatValue(stat.id)?.total ?: 10)

            // Label
            drawSmall(context, "$shortName: §f$value §7($modStr)", x + 14, y + 3, if (changed) 0xFFAA44 else 0xD4AF37)

            // Minus
            val minusBtn = UiButton(x, y, 12, 10, "§c-") {
                editedStats[stat.id] = ((editedStats[stat.id] ?: 10) - 1).coerceAtLeast(1)
                hasUnsavedChanges = true
            }
            drawButton(context, minusBtn, mouseX, mouseY)

            // Plus
            val plusBtn = UiButton(x + colW - 14, y, 12, 10, "§a+") {
                editedStats[stat.id] = ((editedStats[stat.id] ?: 10) + 1).coerceAtMost(30)
                hasUnsavedChanges = true
            }
            drawButton(context, plusBtn, mouseX, mouseY)
        }

        // Player model
        val mc = MinecraftClient.getInstance()
        val player = mc.world?.players?.find { it.name.string == snapshot.playerName }
        if (player != null) {
            InventoryScreen.drawEntity(context, cx, cy + 60, 40, cx - mouseX.toFloat(), cy - mouseY.toFloat(), player)
        }
    }

    // ═══ IDENTITY TAB ═══
    private fun renderIdentity(context: DrawContext, mouseX: Int, mouseY: Int, startY: Int) {
        var y = startY

        // Level
        drawSmall(context, "Level: §f$editedLevel", 10, y + 3, 0xAAAAAA)
        drawInlineButton(context, mouseX, mouseY, 80, y, "§c-") { editedLevel = (editedLevel - 1).coerceAtLeast(1); hasUnsavedChanges = true }
        drawInlineButton(context, mouseX, mouseY, 94, y, "§a+") { editedLevel = (editedLevel + 1).coerceAtMost(20); hasUnsavedChanges = true }
        y += 18

        // Gender
        drawSmall(context, "Gender: §f$editedGender", 10, y + 3, 0xAAAAAA)
        drawInlineButton(context, mouseX, mouseY, 80, y, "§e↔") {
            editedGender = when (editedGender) { "male" -> "female"; "female" -> "other"; else -> "male" }
            hasUnsavedChanges = true
        }
        y += 18

        // Class dropdown
        val className = editedClassId?.let { id ->
            ClientGmRegistry.classes.find { it.id == id }?.displayName ?: id.path
        } ?: "None"
        drawSmall(context, "Class: §f$className", 10, y + 3, 0xAAAAAA)
        drawInlineButton(context, mouseX, mouseY, 10 + textRenderer.getWidth("Class: $className") * 6 / 10 + 4, y, "§e▼") {
            classDropdownOpen = !classDropdownOpen; subclassDropdownOpen = false
        }
        y += 14

        if (classDropdownOpen) {
            ClientGmRegistry.classes.forEachIndexed { i, cls ->
                val btnY = y + i * 11
                val selected = cls.id == editedClassId
                val btn = UiButton(10, btnY, 120, 10, if (selected) "§a${cls.displayName}" else "§7${cls.displayName}") {
                    editedClassId = cls.id; editedSubclassId = null
                    classDropdownOpen = false; hasUnsavedChanges = true
                }
                drawButton(context, btn, mouseX, mouseY)
            }
            y += ClientGmRegistry.classes.size * 11 + 2
        }

        // Subclass dropdown
        val subclasses = editedClassId?.let { id -> ClientGmRegistry.classes.find { it.id == id }?.subclasses } ?: emptyList()
        if (subclasses.isNotEmpty()) {
            val subName = editedSubclassId?.let { id -> subclasses.find { it.id == id }?.displayName ?: id.path } ?: "None"
            drawSmall(context, "Subclass: §f$subName", 10, y + 3, 0xAAAAAA)
            drawInlineButton(context, mouseX, mouseY, 10 + textRenderer.getWidth("Subclass: $subName") * 6 / 10 + 4, y, "§e▼") {
                subclassDropdownOpen = !subclassDropdownOpen; classDropdownOpen = false
            }
            y += 14

            if (subclassDropdownOpen) {
                subclasses.forEachIndexed { i, sub ->
                    val btnY = y + i * 11
                    val selected = sub.id == editedSubclassId
                    val btn = UiButton(10, btnY, 120, 10, if (selected) "§a${sub.displayName}" else "§7${sub.displayName}") {
                        editedSubclassId = sub.id; subclassDropdownOpen = false; hasUnsavedChanges = true
                    }
                    drawButton(context, btn, mouseX, mouseY)
                }
                y += subclasses.size * 11 + 2
            }
        }

        // Race dropdown
        val raceName = editedRaceId?.let { id -> ClientGmRegistry.races.find { it.id == id }?.displayName ?: id.path } ?: "None"
        drawSmall(context, "Race: §f$raceName", 10, y + 3, 0xAAAAAA)
        drawInlineButton(context, mouseX, mouseY, 10 + textRenderer.getWidth("Race: $raceName") * 6 / 10 + 4, y, "§e▼") {
            raceDropdownOpen = !raceDropdownOpen; classDropdownOpen = false; subclassDropdownOpen = false
        }
        y += 14

        if (raceDropdownOpen) {
            ClientGmRegistry.races.forEachIndexed { i, race ->
                val btnY = y + i * 11
                val selected = race.id == editedRaceId
                val btn = UiButton(10, btnY, 120, 10, if (selected) "§a${race.displayName}" else "§7${race.displayName}") {
                    editedRaceId = race.id; raceDropdownOpen = false; hasUnsavedChanges = true
                }
                drawButton(context, btn, mouseX, mouseY)
            }
        }
    }

    // ═══ SKILLS TAB ═══
    private fun renderSkills(context: DrawContext, mouseX: Int, mouseY: Int, startY: Int) {
        val allSkills = ClientGmRegistry.skills
        val saves = allSkills.filter { it.isSavingThrow }
        val skills = allSkills.filter { !it.isSavingThrow }

        var y = startY
        drawSmall(context, "§7Saving Throws:", 10, y, 0x888888)
        y += 10

        saves.forEach { skill ->
            val level = editedSkills[skill.id] ?: 0
            val label = when (level) { 0 -> "§7○"; 1 -> "§a●"; else -> "§b◆" }
            val btn = UiButton(10, y, 8, 8, label) {
                editedSkills[skill.id] = (level + 1) % 3; hasUnsavedChanges = true
            }
            drawButton(context, btn, mouseX, mouseY)
            drawSmall(context, skill.displayName, 22, y + 1, if (level > 0) 0x55FF55 else 0xAAAAAA)
            y += 10
        }

        y += 4
        drawSmall(context, "§7Skills:", 10, y, 0x888888)
        y += 10

        val visibleSkills = skills.drop(skillScrollOffset).take(visibleRows)
        visibleSkills.forEach { skill ->
            val level = editedSkills[skill.id] ?: 0
            val label = when (level) { 0 -> "§7○"; 1 -> "§a●"; else -> "§b◆" }
            val btn = UiButton(10, y, 8, 8, label) {
                editedSkills[skill.id] = (level + 1) % 3; hasUnsavedChanges = true
            }
            drawButton(context, btn, mouseX, mouseY)
            drawSmall(context, skill.displayName, 22, y + 1, if (level > 0) 0x55FF55 else 0xAAAAAA)
            y += 10
        }

        // Scroll buttons
        if (skillScrollOffset > 0) {
            drawInlineButton(context, mouseX, mouseY, width - 20, startY + 20, "§7▲") { skillScrollOffset-- }
        }
        if (skillScrollOffset + visibleRows < skills.size) {
            drawInlineButton(context, mouseX, mouseY, width - 20, startY + 30, "§7▼") { skillScrollOffset++ }
        }
    }

    // ═══ FEATURES TAB ═══
    private fun renderFeatures(context: DrawContext, mouseX: Int, mouseY: Int, startY: Int) {
        val allFeatures = ClientGmRegistry.features
        var y = startY

        drawSmall(context, "§7Click to toggle. §a● = granted", 10, y, 0x888888)
        y += 12

        val visible = allFeatures.drop(featureScrollOffset).take(visibleRows)
        visible.forEach { feat ->
            val has = editedFeatures.contains(feat.id)
            val label = if (has) "§a●" else "§7○"
            val btn = UiButton(10, y, 8, 8, label) {
                if (has) editedFeatures.remove(feat.id) else editedFeatures.add(feat.id)
                hasUnsavedChanges = true
            }
            drawButton(context, btn, mouseX, mouseY)
            drawSmall(context, feat.displayName, 22, y + 1, if (has) 0x55FF55 else 0xAAAAAA)
            y += 10
        }

        if (featureScrollOffset > 0) {
            drawInlineButton(context, mouseX, mouseY, width - 20, startY + 12, "§7▲") { featureScrollOffset-- }
        }
        if (featureScrollOffset + visibleRows < allFeatures.size) {
            drawInlineButton(context, mouseX, mouseY, width - 20, startY + 22, "§7▼") { featureScrollOffset++ }
        }
    }

    // ═══ APPLY ═══
    private fun applyAll() {
        // Stats
        val statBuf = PacketByteBufs.create()
        statBuf.writeString(snapshot.playerName)
        statBuf.writeInt(editedStats.size)
        editedStats.forEach { (id, v) -> statBuf.writeIdentifier(id); statBuf.writeInt(v) }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_STATS, statBuf)

        // Identity
        val idBuf = PacketByteBufs.create()
        idBuf.writeString(snapshot.playerName)
        idBuf.writeBoolean(editedClassId != null)
        if (editedClassId != null) idBuf.writeIdentifier(editedClassId!!)
        idBuf.writeBoolean(editedSubclassId != null)
        if (editedSubclassId != null) idBuf.writeIdentifier(editedSubclassId!!)
        idBuf.writeInt(editedLevel)
        idBuf.writeBoolean(editedRaceId != null)
        if (editedRaceId != null) idBuf.writeIdentifier(editedRaceId!!)
        idBuf.writeBoolean(true)
        idBuf.writeString(editedGender)
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_IDENTITY, idBuf)

        // Skills
        val skillBuf = PacketByteBufs.create()
        skillBuf.writeString(snapshot.playerName)
        skillBuf.writeInt(editedSkills.size)
        editedSkills.forEach { (id, level) -> skillBuf.writeIdentifier(id); skillBuf.writeInt(level) }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_SKILLS, skillBuf)

        // Features — send each change
        editedFeatures.forEach { featId ->
            val buf = PacketByteBufs.create()
            buf.writeString(snapshot.playerName)
            buf.writeIdentifier(featId)
            buf.writeBoolean(true)
            ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_FEATURE, buf)
        }

        hasUnsavedChanges = false
        statusMessage = "§aApplied!"
        statusTimer = 1f
    }

    // ═══ UI HELPERS ═══
    private fun drawButton(context: DrawContext, btn: UiButton, mouseX: Int, mouseY: Int, active: Boolean = false) {
        val hovered = mouseX in btn.x..(btn.x + btn.w) && mouseY in btn.y..(btn.y + btn.h)
        val bg = when { active -> 0xCC5a4a2a.toInt(); hovered -> 0xCC4a3a2a.toInt(); else -> 0xCC2b2321.toInt() }
        val border = if (hovered || active) 0xFFd4a96a.toInt() else 0xFF6b5a3e.toInt()
        context.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, bg)
        context.fill(btn.x, btn.y, btn.x + btn.w, btn.y + 1, border)
        context.fill(btn.x, btn.y + btn.h - 1, btn.x + btn.w, btn.y + btn.h, border)
        context.fill(btn.x, btn.y, btn.x + 1, btn.y + btn.h, border)
        context.fill(btn.x + btn.w - 1, btn.y, btn.x + btn.w, btn.y + btn.h, border)
        val m = context.matrices
        m.push()
        m.translate((btn.x + btn.w / 2).toFloat(), (btn.y + btn.h / 2 - 3).toFloat(), 0f)
        m.scale(0.75f, 0.75f, 1f)
        val tw = textRenderer.getWidth(btn.label)
        context.drawTextWithShadow(textRenderer, btn.label, -(tw / 2), 0, 0xFFFFFF)
        m.pop()
    }

    private fun drawInlineButton(context: DrawContext, mouseX: Int, mouseY: Int, x: Int, y: Int, label: String, action: () -> Unit) {
        val btn = UiButton(x, y, 10, 10, label, action)
        drawButton(context, btn, mouseX, mouseY)
    }

    private fun drawSmall(context: DrawContext, text: String, x: Int, y: Int, color: Int) {
        val m = context.matrices
        m.push()
        m.translate(x.toFloat(), y.toFloat(), 0f)
        m.scale(0.75f, 0.75f, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        m.pop()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        buttons.forEach { btn ->
            if (mx in btn.x..(btn.x + btn.w) && my in btn.y..(btn.y + btn.h)) {
                btn.action(); return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        when (activeTab) {
            Tab.SKILLS -> skillScrollOffset = (skillScrollOffset - amount.toInt()).coerceAtLeast(0)
            Tab.FEATURES -> featureScrollOffset = (featureScrollOffset - amount.toInt()).coerceAtLeast(0)
            else -> {}
        }
        return true
    }

    override fun shouldPause() = false
}
