package omc.boundbyfate.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.client.state.ClientGmRegistry

/**
 * Skin picker screen for GM panel.
 * Shows skin cards with front+back 2D UV preview decoded from the skin PNG texture.
 *
 * Minecraft skin PNG layout (64×64):
 *   Head front:       u=8,  v=8,  w=8, h=8
 *   Head back:        u=24, v=8,  w=8, h=8
 *   Body front:       u=20, v=20, w=8, h=12
 *   Body back:        u=32, v=20, w=8, h=12
 *   Right arm front:  u=44, v=20, w=4, h=12
 *   Right arm back:   u=52, v=20, w=4, h=12
 *   Left arm front:   u=36, v=52, w=4, h=12  (64×64 new format only)
 *   Left arm back:    u=44, v=52, w=4, h=12
 *   Right leg front:  u=4,  v=20, w=4, h=12
 *   Right leg back:   u=12, v=20, w=4, h=12
 *   Left leg front:   u=20, v=52, w=4, h=12
 *   Left leg back:    u=28, v=52, w=4, h=12
 */
class GmSkinPickerScreen(
    private val currentSkin: String?,
    private val parentScreen: Screen?,
    private val onPick: (String) -> Unit
) : Screen(Text.literal("Выбор скина")) {

    private var scroll = 0
    private var searchQuery = ""
    private var editingSearch = true

    // Card layout
    private val CARD_W = 80
    private val CARD_H = 100
    private val CARD_PAD = 8
    private val COLS = 3
    private val HEADER_H = 38  // title + search
    private val PANEL_MARGIN = 20

    private val filteredSkins: List<String> get() {
        val q = searchQuery.lowercase().trim()
        return if (q.isEmpty()) ClientGmRegistry.availableSkins.sorted()
        else ClientGmRegistry.availableSkins.filter { it.lowercase().contains(q) }.sorted()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Dim background
        context.fill(0, 0, width, height, 0xAA000000.toInt())

        val panelX = PANEL_MARGIN
        val panelY = PANEL_MARGIN
        val panelW = width - PANEL_MARGIN * 2
        val panelH = height - PANEL_MARGIN * 2

        // Panel background + border
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1a1a1a.toInt())
        drawBorder(context, panelX, panelY, panelW, panelH, 0xFFd4a96a.toInt())

        // Title
        val m = context.matrices
        m.push(); m.translate((panelX + panelW / 2).toFloat(), (panelY + 6).toFloat(), 0f); m.scale(0.9f, 0.9f, 1f)
        val tw = textRenderer.getWidth("Выбор скина")
        context.drawTextWithShadow(textRenderer, "Выбор скина", -(tw / 2), 0, 0xFFD700); m.pop()

        // Close button
        val cbX = panelX + panelW - 14; val cbY = panelY + 3
        val cbHov = mouseX in cbX..(cbX + 12) && mouseY in cbY..(cbY + 12)
        context.fill(cbX, cbY, cbX + 12, cbY + 12, if (cbHov) 0xCC4a3a2a.toInt() else 0xCC2b2321.toInt())
        drawBorder(context, cbX, cbY, 12, 12, if (cbHov) 0xFFd4a96a.toInt() else 0xFF555555.toInt())
        m.push(); m.translate((cbX + 6).toFloat(), (cbY + 3).toFloat(), 0f); m.scale(0.75f, 0.75f, 1f)
        context.drawTextWithShadow(textRenderer, "§cX", -3, 0, 0xFFFFFF); m.pop()

        // Search field
        val sfX = panelX + 6; val sfY = panelY + 20; val sfW = panelW - 12; val sfH = 12
        context.fill(sfX, sfY, sfX + sfW, sfY + sfH, 0xFF111111.toInt())
        val sfBorderColor = if (editingSearch) 0xFF88aaff.toInt() else 0xFF555555.toInt()
        drawBorder(context, sfX, sfY, sfW, sfH, sfBorderColor)
        val displaySearch = if (searchQuery.isEmpty() && !editingSearch) "§7Поиск скина..." else if (editingSearch) "${searchQuery}_" else searchQuery
        m.push(); m.translate((sfX + 4).toFloat(), (sfY + 3).toFloat(), 0f); m.scale(0.7f, 0.7f, 1f)
        context.drawTextWithShadow(textRenderer, displaySearch, 0, 0, 0xFFFFFF); m.pop()

        // Skin grid
        val gridX = panelX + CARD_PAD
        val gridY = panelY + HEADER_H
        val gridH = panelH - HEADER_H - CARD_PAD
        val skins = filteredSkins
        val rowH = CARD_H + CARD_PAD
        val visibleRows = gridH / rowH
        val maxScroll = ((skins.size + COLS - 1) / COLS - visibleRows).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)

        val startIdx = scroll * COLS
        val endIdx = ((scroll + visibleRows + 1) * COLS).coerceAtMost(skins.size)

        for (i in startIdx until endIdx) {
            val col = i % COLS
            val row = i / COLS - scroll
            val cx = gridX + col * (CARD_W + CARD_PAD)
            val cy = gridY + row * rowH
            if (cy + CARD_H > panelY + panelH - CARD_PAD) continue  // clip

            val skinName = skins[i]
            val isSelected = skinName == currentSkin
            val isHovered = mouseX in cx..(cx + CARD_W) && mouseY in cy..(cy + CARD_H)

            // Card background
            val cardBg = when {
                isSelected -> 0xFF2a3a2a.toInt()
                isHovered  -> 0xFF2a2a3a.toInt()
                else       -> 0xFF222222.toInt()
            }
            context.fill(cx, cy, cx + CARD_W, cy + CARD_H, cardBg)
            val borderColor = if (isSelected) 0xFF55FF55.toInt() else if (isHovered) 0xFFd4a96a.toInt() else 0xFF444444.toInt()
            drawBorder(context, cx, cy, CARD_W, CARD_H, borderColor)

            // Skin preview (front + back side by side, scaled up 4x)
            val texId = ClientGmRegistry.skinTextures[skinName]
            if (texId != null) {
                val scale = 4
                val previewY = cy + 6
                val frontX = cx + 6
                val backX = cx + CARD_W / 2 + 2

                // Front view
                drawSkinView(context, texId, frontX, previewY, scale, false)
                // Back view
                drawSkinView(context, texId, backX, previewY, scale, true)
            } else {
                // No texture yet — show placeholder
                m.push(); m.translate((cx + CARD_W / 2).toFloat(), (cy + 40).toFloat(), 0f); m.scale(0.6f, 0.6f, 1f)
                val pw = textRenderer.getWidth("?")
                context.drawTextWithShadow(textRenderer, "§7?", -(pw / 2), 0, 0x888888); m.pop()
            }

            // Name label at bottom of card
            m.push(); m.translate((cx + CARD_W / 2).toFloat(), (cy + CARD_H - 12).toFloat(), 0f); m.scale(0.6f, 0.6f, 1f)
            val nw = textRenderer.getWidth(skinName)
            val nameColor = if (isSelected) 0x55FF55 else if (isHovered) 0xFFD700 else 0xCCCCCC
            context.drawTextWithShadow(textRenderer, skinName, -(nw / 2), 0, nameColor); m.pop()
        }

        // Scrollbar
        if (maxScroll > 0) {
            val sbX = panelX + panelW - 6; val sbY = gridY; val sbH = gridH
            context.fill(sbX, sbY, sbX + 4, sbY + sbH, 0xFF222222.toInt())
            val thumbH = (sbH * visibleRows / ((maxScroll + visibleRows))).coerceAtLeast(16)
            val thumbY = sbY + (sbH - thumbH) * scroll / maxScroll.coerceAtLeast(1)
            context.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF888888.toInt())
        }

        super.render(context, mouseX, mouseY, delta)
    }

    /**
     * Draws the front or back 2D skin view from the UV layout.
     * Scale = pixel size multiplier (4 = each skin pixel becomes 4×4 screen pixels).
     */
    private fun drawSkinView(context: DrawContext, texId: Identifier, x: Int, y: Int, scale: Int, back: Boolean) {
        // All parts: (srcU, srcV, srcW, srcH, dstX offset, dstY offset)
        // Texture is 64×64, we draw each region scaled
        val parts: List<IntArray> = if (!back) listOf(
            // front: head, body, rightArm, leftArm, rightLeg, leftLeg
            intArrayOf(8,  8,  8, 8,  0, 0),            // head
            intArrayOf(20, 20, 8, 12, 0, 8 * scale),     // body
            intArrayOf(44, 20, 4, 12, -4 * scale, 8 * scale), // right arm
            intArrayOf(36, 52, 4, 12, 8 * scale, 8 * scale),  // left arm
            intArrayOf(4,  20, 4, 12, 0, 20 * scale),    // right leg
            intArrayOf(20, 52, 4, 12, 4 * scale, 20 * scale)  // left leg
        ) else listOf(
            // back: mirrored horizontally per part
            intArrayOf(24, 8,  8, 8,  0, 0),
            intArrayOf(32, 20, 8, 12, 0, 8 * scale),
            intArrayOf(52, 20, 4, 12, -4 * scale, 8 * scale),
            intArrayOf(44, 52, 4, 12, 8 * scale, 8 * scale),
            intArrayOf(12, 20, 4, 12, 0, 20 * scale),
            intArrayOf(28, 52, 4, 12, 4 * scale, 20 * scale)
        )

        val texSize = 64
        for (p in parts) {
            val (su, sv, sw, sh, ox, oy) = p
            context.drawTexture(
                texId,
                x + ox, y + oy,
                (sw * scale), (sh * scale),
                su.toFloat(), sv.toFloat(),
                sw, sh,
                texSize, texSize
            )
        }
    }

    private operator fun IntArray.component6() = this[5]

    private fun drawBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        context.fill(x, y, x + w, y + 1, color)
        context.fill(x, y + h - 1, x + w, y + h, color)
        context.fill(x, y, x + 1, y + h, color)
        context.fill(x + w - 1, y, x + w, y + h, color)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        val panelX = PANEL_MARGIN; val panelY = PANEL_MARGIN
        val panelW = width - PANEL_MARGIN * 2; val panelH = height - PANEL_MARGIN * 2

        // Outside panel → close
        if (mx !in panelX..(panelX + panelW) || my !in panelY..(panelY + panelH)) {
            close(); return true
        }

        // Close button
        val cbX = panelX + panelW - 14; val cbY = panelY + 3
        if (mx in cbX..(cbX + 12) && my in cbY..(cbY + 12)) { close(); return true }

        // Search field
        val sfX = panelX + 6; val sfY = panelY + 20; val sfW = panelW - 12; val sfH = 12
        if (mx in sfX..(sfX + sfW) && my in sfY..(sfY + sfH)) { editingSearch = true; return true }

        // Grid click
        val gridX = panelX + CARD_PAD; val gridY = panelY + HEADER_H
        val gridH = panelH - HEADER_H - CARD_PAD
        val skins = filteredSkins
        val rowH = CARD_H + CARD_PAD
        val visibleRows = gridH / rowH

        if (mx in gridX..(panelX + panelW - CARD_PAD) && my in gridY..(gridY + gridH)) {
            val col = (mx - gridX) / (CARD_W + CARD_PAD)
            val row = (my - gridY) / rowH
            val idx = (scroll + row) * COLS + col
            if (col in 0 until COLS && idx in skins.indices) {
                onPick(skins[idx])
                close()
                return true
            }
        }

        editingSearch = false
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val skins = filteredSkins
        val panelH = height - PANEL_MARGIN * 2
        val gridH = panelH - HEADER_H - CARD_PAD
        val visibleRows = gridH / (CARD_H + CARD_PAD)
        val maxScroll = ((skins.size + COLS - 1) / COLS - visibleRows).coerceAtLeast(0)
        scroll = (scroll - amount.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (editingSearch) {
            when (keyCode) {
                256 -> { close(); return true }
                259 -> { if (searchQuery.isNotEmpty()) { searchQuery = searchQuery.dropLast(1); scroll = 0 }; return true }
            }
            return true
        }
        if (keyCode == 256) { close(); return true }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (editingSearch) { searchQuery += chr; scroll = 0; return true }
        return super.charTyped(chr, modifiers)
    }

    override fun shouldPause() = false

    override fun close() {
        client?.setScreen(parentScreen)
    }
}
