package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.text.Text
import omc.boundbyfate.client.state.ClientGmData
import omc.boundbyfate.client.state.GmPlayerSnapshot
import omc.boundbyfate.network.BbfPackets

/**
 * GM Screen — main view showing all online players as cards.
 * Click a card to open the player's character sheet in edit mode.
 */
class GmScreen : Screen(Text.translatable("screen.boundbyfate.gm")) {

    private var cx = 0
    private var cy = 0

    // Card layout
    private val cardW = 60
    private val cardH = 90
    private val cardPad = 12
    private val cardsPerRow = 6

    // Hover state
    private var hoveredCard = -1

    // Card hover scales for animation
    private val cardScales = FloatArray(20) { 1f }

    override fun init() {
        cx = width / 2
        cy = height / 2

        // Request fresh data from server
        ClientPlayNetworking.send(BbfPackets.GM_REQUEST_REFRESH, PacketByteBufs.empty())
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)

        val players = ClientGmData.players

        // Title
        val title = "§6GM Panel §7— ${players.size} players online"
        context.drawCenteredTextWithShadow(textRenderer, title, cx, 10, 0xFFFFFF)

        if (players.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "§7No players online", cx, cy, 0x888888)
            super.render(context, mouseX, mouseY, delta)
            return
        }

        // Calculate grid start position
        val totalCols = minOf(players.size, cardsPerRow)
        val totalW = totalCols * cardW + (totalCols - 1) * cardPad
        val startX = cx - totalW / 2
        val startY = 35

        hoveredCard = -1

        players.forEachIndexed { idx, snapshot ->
            val col = idx % cardsPerRow
            val row = idx / cardsPerRow
            val cardX = startX + col * (cardW + cardPad)
            val cardY = startY + row * (cardH + cardPad)

            val hovered = mouseX in cardX..(cardX + cardW) && mouseY in cardY..(cardY + cardH)
            if (hovered) hoveredCard = idx

            // Animate scale
            cardScales[idx] = lerp(cardScales[idx], if (hovered) 1.08f else 1f, 0.15f)

            drawPlayerCard(context, cardX, cardY, snapshot, cardScales[idx], hovered)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawPlayerCard(
        context: DrawContext,
        x: Int, y: Int,
        snapshot: GmPlayerSnapshot,
        scale: Float,
        hovered: Boolean
    ) {
        val matrices = context.matrices
        matrices.push()

        // Scale from center of card
        val cardCx = (x + cardW / 2).toFloat()
        val cardCy = (y + cardH / 2).toFloat()
        matrices.translate(cardCx, cardCy, 0f)
        matrices.scale(scale, scale, 1f)
        matrices.translate(-cardCx, -cardCy, 0f)

        // Card background
        val bgColor = if (hovered) 0xCC3a2e1e.toInt() else 0xCC2b2321.toInt()
        val borderColor = if (hovered) 0xFFd4a96a.toInt() else 0xFF6b5a3e.toInt()
        context.fill(x, y, x + cardW, y + cardH, bgColor)
        // Border
        context.fill(x, y, x + cardW, y + 1, borderColor)
        context.fill(x, y + cardH - 1, x + cardW, y + cardH, borderColor)
        context.fill(x, y, x + 1, y + cardH, borderColor)
        context.fill(x + cardW - 1, y, x + cardW, y + cardH, borderColor)

        // Player model (застывшая поза — mouseX/Y далеко чтобы не следил)
        val modelX = x + cardW / 2
        val modelY = y + cardH - 15
        val mc = MinecraftClient.getInstance()
        val player = mc.world?.players?.find { it.name.string == snapshot.playerName }
        if (player != null) {
            InventoryScreen.drawEntity(
                context,
                modelX, modelY,
                18,
                (modelX - 1000).toFloat(),
                (modelY - 1000).toFloat(),
                player
            )
        } else {
            // Offline — показываем заглушку
            context.drawCenteredTextWithShadow(textRenderer, "§7?", modelX, y + 30, 0x888888)
        }

        // Player name
        val nameScale = 0.6f
        val nameM = context.matrices
        nameM.push()
        nameM.translate((x + cardW / 2).toFloat(), (y + cardH - 18).toFloat(), 0f)
        nameM.scale(nameScale, nameScale, 1f)
        val nameW = textRenderer.getWidth(snapshot.playerName)
        context.drawTextWithShadow(textRenderer, snapshot.playerName, -(nameW / 2), 0, 0xFFD700)
        nameM.pop()

        // HP bar
        val hpBarW = cardW - 8
        val hpBarH = 3
        val hpBarX = x + 4
        val hpBarY = y + cardH - 8
        val hpFrac = if (snapshot.maxHp > 0) (snapshot.currentHp / snapshot.maxHp).coerceIn(0f, 1f) else 0f
        context.fill(hpBarX, hpBarY, hpBarX + hpBarW, hpBarY + hpBarH, 0xFF333333.toInt())
        val hpColor = when {
            hpFrac > 0.5f -> 0xFF44AA44.toInt()
            hpFrac > 0.25f -> 0xFFAAAA22.toInt()
            else -> 0xFFAA2222.toInt()
        }
        context.fill(hpBarX, hpBarY, hpBarX + (hpBarW * hpFrac).toInt(), hpBarY + hpBarH, hpColor)

        // Class/level badge
        val classKey = snapshot.classData?.classId?.let { "bbf.class.${it.namespace}.${it.path}" }
        val classStr = if (classKey != null) Text.translatable(classKey).string else "?"
        val badgeScale = 0.5f
        val badgeM = context.matrices
        badgeM.push()
        badgeM.translate((x + cardW / 2).toFloat(), (y + 5).toFloat(), 0f)
        badgeM.scale(badgeScale, badgeScale, 1f)
        val badgeText = "$classStr ${snapshot.level}"
        val badgeW = textRenderer.getWidth(badgeText)
        context.drawTextWithShadow(textRenderer, badgeText, -(badgeW / 2), 0, 0xD4AF37)
        badgeM.pop()

        matrices.pop()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (hoveredCard >= 0 && hoveredCard < ClientGmData.players.size) {
            val snapshot = ClientGmData.players[hoveredCard]
            MinecraftClient.getInstance().setScreen(GmPlayerEditScreen(snapshot))
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun shouldPause() = false
}
