package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.client.state.GmPlayerSnapshot
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.registry.BbfStats

/**
 * GM edit screen for a single player's character.
 * Shows the character sheet with +/- buttons for each stat.
 */
class GmPlayerEditScreen(
    private val snapshot: GmPlayerSnapshot
) : Screen(Text.literal("GM: ${snapshot.playerName}")) {

    private var cx = 0
    private var cy = 0

    // Editable stat values (copy from snapshot)
    private val editedStats = mutableMapOf<net.minecraft.util.Identifier, Int>().also { map ->
        val stats = listOf(
            BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY,
            BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA
        )
        stats.forEach { stat ->
            map[stat.id] = snapshot.statsData?.getStatValue(stat.id)?.total ?: 10
        }
    }

    private var hasUnsavedChanges = false
    private var statusMessage = ""
    private var statusTimer = 0f

    // Button hit areas: list of (x, y, w, h, action)
    private data class Button(val x: Int, val y: Int, val w: Int, val h: Int, val label: String, val action: () -> Unit)
    private val buttons = mutableListOf<Button>()

    override fun init() {
        cx = width / 2
        cy = height / 2
        buttons.clear()
        buildButtons()
    }

    private fun buildButtons() {
        // Back button
        buttons.add(Button(10, 10, 40, 14, "§7← Back") {
            MinecraftClient.getInstance().setScreen(GmScreen())
        })

        // Apply button
        buttons.add(Button(cx - 30, height - 25, 60, 14, "§aApply") {
            applyChanges()
        })

        // Stat +/- buttons
        val stats = listOf(
            BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY,
            BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA
        )
        val leftStats = stats.take(3)
        val rightStats = stats.drop(3)

        val shieldW = 28
        val shieldH = 43
        val shieldDiagStep = 12
        val leftBaseX = cx - 75
        val rightBaseX = cx + 75 - shieldW
        val shieldsTopY = cy - 55
        val shieldStep = shieldH + 6

        leftStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = leftBaseX - diagOffset
            val sy = shieldsTopY + i * shieldStep
            // Minus button (left of shield)
            buttons.add(Button(sx - 14, sy + shieldH / 2 - 5, 12, 10, "§c-") {
                editedStats[stat.id] = (editedStats[stat.id] ?: 10) - 1
                hasUnsavedChanges = true
            })
            // Plus button (right of shield)
            buttons.add(Button(sx + shieldW + 2, sy + shieldH / 2 - 5, 12, 10, "§a+") {
                editedStats[stat.id] = (editedStats[stat.id] ?: 10) + 1
                hasUnsavedChanges = true
            })
        }

        rightStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = rightBaseX + diagOffset
            val sy = shieldsTopY + i * shieldStep
            buttons.add(Button(sx - 14, sy + shieldH / 2 - 5, 12, 10, "§c-") {
                editedStats[stat.id] = (editedStats[stat.id] ?: 10) - 1
                hasUnsavedChanges = true
            })
            buttons.add(Button(sx + shieldW + 2, sy + shieldH / 2 - 5, 12, 10, "§a+") {
                editedStats[stat.id] = (editedStats[stat.id] ?: 10) + 1
                hasUnsavedChanges = true
            })
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)

        // Update status timer
        if (statusTimer > 0f) statusTimer -= delta * 0.05f

        // ═══ PLAYER MODEL ═══
        val mc = MinecraftClient.getInstance()
        val player = mc.world?.players?.find { it.name.string == snapshot.playerName }
        if (player != null) {
            InventoryScreen.drawEntity(context, cx, cy + 85, 70, cx - mouseX.toFloat(), cy - mouseY.toFloat(), player)
        }

        // ═══ STAT SHIELDS ═══
        val stats = listOf(
            BbfStats.STRENGTH, BbfStats.CONSTITUTION, BbfStats.DEXTERITY,
            BbfStats.INTELLIGENCE, BbfStats.WISDOM, BbfStats.CHARISMA
        )
        val leftStats = stats.take(3)
        val rightStats = stats.drop(3)

        val shieldW = 28
        val shieldH = 43
        val shieldDiagStep = 12
        val leftBaseX = cx - 75
        val rightBaseX = cx + 75 - shieldW
        val shieldsTopY = cy - 55
        val shieldStep = shieldH + 6

        leftStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = leftBaseX - diagOffset
            val sy = shieldsTopY + i * shieldStep
            drawEditableShield(context, sx, sy, stat, shieldW, shieldH, mouseX, mouseY)
        }

        rightStats.forEachIndexed { i, stat ->
            val diagOffset = (2 - i) * shieldDiagStep
            val sx = rightBaseX + diagOffset
            val sy = shieldsTopY + i * shieldStep
            drawEditableShield(context, sx, sy, stat, shieldW, shieldH, mouseX, mouseY)
        }

        // ═══ HEADER ═══
        val headerText = "§6${snapshot.playerName} §7— GM Edit"
        context.drawCenteredTextWithShadow(textRenderer, headerText, cx, 12, 0xFFFFFF)

        val classKey = snapshot.classData?.classId?.let { "bbf.class.${it.namespace}.${it.path}" }
        val classStr = if (classKey != null) Text.translatable(classKey).string else "Commoner"
        val raceKey = snapshot.raceData?.raceId?.let { "bbf.race.${it.namespace}.${it.path}" }
        val raceStr = if (raceKey != null) Text.translatable(raceKey).string else "Human"
        context.drawCenteredTextWithShadow(
            textRenderer,
            "§7$classStr ${snapshot.level} · $raceStr",
            cx, 24, 0xAAAAAA
        )

        // ═══ BUTTONS ═══
        buttons.forEach { btn ->
            val hovered = mouseX in btn.x..(btn.x + btn.w) && mouseY in btn.y..(btn.y + btn.h)
            val bgColor = if (hovered) 0xCC4a3a2a.toInt() else 0xCC2b2321.toInt()
            val borderColor = if (hovered) 0xFFd4a96a.toInt() else 0xFF6b5a3e.toInt()
            context.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, bgColor)
            context.fill(btn.x, btn.y, btn.x + btn.w, btn.y + 1, borderColor)
            context.fill(btn.x, btn.y + btn.h - 1, btn.x + btn.w, btn.y + btn.h, borderColor)
            context.fill(btn.x, btn.y, btn.x + 1, btn.y + btn.h, borderColor)
            context.fill(btn.x + btn.w - 1, btn.y, btn.x + btn.w, btn.y + btn.h, borderColor)
            val m = context.matrices
            m.push()
            m.translate((btn.x + btn.w / 2).toFloat(), (btn.y + btn.h / 2 - 3).toFloat(), 0f)
            m.scale(0.75f, 0.75f, 1f)
            val tw = textRenderer.getWidth(btn.label)
            context.drawTextWithShadow(textRenderer, btn.label, -(tw / 2), 0, 0xFFFFFF)
            m.pop()
        }

        // Unsaved changes indicator
        if (hasUnsavedChanges) {
            context.drawCenteredTextWithShadow(textRenderer, "§e● Unsaved changes", cx, height - 40, 0xFFFF55)
        }

        // Status message
        if (statusTimer > 0f) {
            val alpha = (statusTimer * 255).toInt().coerceIn(0, 255)
            val color = (alpha shl 24) or 0x55FF55
            context.drawCenteredTextWithShadow(textRenderer, statusMessage, cx, height - 50, color)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawEditableShield(
        context: DrawContext,
        x: Int, y: Int,
        stat: omc.boundbyfate.api.stat.StatDefinition,
        shieldW: Int, shieldH: Int,
        mouseX: Int, mouseY: Int
    ) {
        GuiAtlas.ICON_STAT_BG.draw(context, x, y, shieldW, shieldH)

        val value = editedStats[stat.id] ?: 10
        val mod = (value - 10) / 2
        val modStr = if (mod >= 0) "+$mod" else "$mod"
        val midX = x + shieldW / 2

        val shortKey = "bbf.stat.${stat.id.namespace}.${stat.id.path}.short"
        val shortName = Text.translatable(shortKey).string

        // Highlight if value changed from original
        val originalValue = snapshot.statsData?.getStatValue(stat.id)?.total ?: 10
        val nameColor = if (value != originalValue) 0xFFAA44 else 0xD4AF37

        drawScaledCenteredText(context, shortName, midX, y + 9, nameColor, 0.6f)
        drawScaledCenteredText(context, "$value", midX, y + 17, 0xFFFFFF, 1.0f)
        drawScaledCenteredText(context, modStr, midX, y + 29, if (mod >= 0) 0x2ECC71 else 0xE74C3C, 0.6f)
    }

    private fun applyChanges() {
        // Send stat changes to server
        val buf = PacketByteBufs.create()
        buf.writeString(snapshot.playerName)
        buf.writeInt(editedStats.size)
        editedStats.forEach { (id, value) ->
            buf.writeIdentifier(id)
            buf.writeInt(value)
        }
        ClientPlayNetworking.send(BbfPackets.GM_EDIT_PLAYER_STATS, buf)
        hasUnsavedChanges = false
        statusMessage = "§aChanges applied!"
        statusTimer = 1f
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        buttons.forEach { btn ->
            if (mouseX.toInt() in btn.x..(btn.x + btn.w) && mouseY.toInt() in btn.y..(btn.y + btn.h)) {
                btn.action()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun drawScaledCenteredText(context: DrawContext, text: String, cx: Int, y: Int, color: Int, scale: Float) {
        val m = context.matrices
        m.push()
        m.translate(cx.toFloat(), y.toFloat(), 0f)
        m.scale(scale, scale, 1f)
        val w = textRenderer.getWidth(text)
        context.drawTextWithShadow(textRenderer, text, -(w / 2), 0, color)
        m.pop()
    }

    override fun shouldPause() = false
}
