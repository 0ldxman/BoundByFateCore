package omc.boundbyfate.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Modal picker overlay shown on top of GmPlayerEditScreen.
 * Displays a searchable, alphabetically sorted list of items.
 * The parent screen is dimmed but not closed.
 *
 * @param title Title shown at the top of the picker
 * @param items Full list of (id, displayName) pairs
 * @param currentId Currently selected id (highlighted)
 * @param onPick Callback invoked when an item is selected
 */
class GmPickerScreen(
    private val title: String,
    private val items: List<Pair<Identifier, String>>,
    private val currentId: Identifier?,
    private val onPick: (Identifier) -> Unit
) : Screen(Text.literal(title)) {

    private var searchQuery = ""
    private var scroll = 0
    private var editingSearch = true  // search field is focused by default

    // Sorted alphabetically by display name
    private val sortedItems: List<Pair<Identifier, String>> get() =
        items.sortedBy { it.second.lowercase() }

    private val filteredItems: List<Pair<Identifier, String>> get() {
        val q = searchQuery.lowercase().trim()
        return if (q.isEmpty()) sortedItems
        else sortedItems.filter { it.second.lowercase().contains(q) }
    }

    // Layout constants
    private val PANEL_W = 200
    private val PANEL_H = 180
    private val ITEM_H = 12
    private val HEADER_H = 32  // title + search field
    private val FOOTER_H = 0

    private val panelX get() = (width - PANEL_W) / 2
    private val panelY get() = (height - PANEL_H) / 2

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Dim the background (parent screen is still rendered underneath by Minecraft)
        context.fill(0, 0, width, height, 0xAA000000.toInt())

        val px = panelX; val py = panelY
        val pw = PANEL_W; val ph = PANEL_H

        // Panel background + border
        context.fill(px, py, px + pw, py + ph, 0xFF1a1a1a.toInt())
        context.fill(px, py, px + pw, py + 1, 0xFFd4a96a.toInt())
        context.fill(px, py + ph - 1, px + pw, py + ph, 0xFFd4a96a.toInt())
        context.fill(px, py, px + 1, py + ph, 0xFFd4a96a.toInt())
        context.fill(px + pw - 1, py, px + pw, py + ph, 0xFFd4a96a.toInt())

        // Title
        val m = context.matrices
        m.push()
        m.translate((px + pw / 2).toFloat(), (py + 5).toFloat(), 0f)
        m.scale(0.75f, 0.75f, 1f)
        val tw = textRenderer.getWidth(title)
        context.drawTextWithShadow(textRenderer, title, -(tw / 2), 0, 0xFFD700)
        m.pop()

        // Close button (top-right)
        val closeBtnX = px + pw - 12; val closeBtnY = py + 2
        val closeHov = mouseX in closeBtnX..(closeBtnX + 10) && mouseY in closeBtnY..(closeBtnY + 10)
        context.fill(closeBtnX, closeBtnY, closeBtnX + 10, closeBtnY + 10,
            if (closeHov) 0xCC4a3a2a.toInt() else 0xCC2b2321.toInt())
        m.push()
        m.translate((closeBtnX + 5).toFloat(), (closeBtnY + 2).toFloat(), 0f)
        m.scale(0.75f, 0.75f, 1f)
        context.drawTextWithShadow(textRenderer, "§cX", -3, 0, 0xFFFFFF)
        m.pop()

        // Search field
        val sfX = px + 4; val sfY = py + 16; val sfW = pw - 8; val sfH = 10
        context.fill(sfX, sfY, sfX + sfW, sfY + sfH, 0xFF111111.toInt())
        context.fill(sfX, sfY, sfX + sfW, sfY + 1,
            if (editingSearch) 0xFF88aaff.toInt() else 0xFF555555.toInt())
        context.fill(sfX, sfY + sfH - 1, sfX + sfW, sfY + sfH,
            if (editingSearch) 0xFF88aaff.toInt() else 0xFF555555.toInt())
        context.fill(sfX, sfY, sfX + 1, sfY + sfH,
            if (editingSearch) 0xFF88aaff.toInt() else 0xFF555555.toInt())
        context.fill(sfX + sfW - 1, sfY, sfX + sfW, sfY + sfH,
            if (editingSearch) 0xFF88aaff.toInt() else 0xFF555555.toInt())
        val displaySearch = if (searchQuery.isEmpty() && !editingSearch) "§7Поиск..."
                            else if (editingSearch) "${searchQuery}_" else searchQuery
        m.push()
        m.translate((sfX + 3).toFloat(), (sfY + 2).toFloat(), 0f)
        m.scale(0.7f, 0.7f, 1f)
        context.drawTextWithShadow(textRenderer, displaySearch, 0, 0, 0xFFFFFF)
        m.pop()

        // Item list
        val listX = px + 2; val listY = py + HEADER_H
        val listH = ph - HEADER_H - FOOTER_H - 2
        val visibleCount = listH / ITEM_H
        val filtered = filteredItems
        val clampedScroll = scroll.coerceIn(0, (filtered.size - visibleCount).coerceAtLeast(0))
        if (scroll != clampedScroll) scroll = clampedScroll

        // Clip region (simple — just don't draw outside)
        filtered.drop(scroll).take(visibleCount).forEachIndexed { i, (id, name) ->
            val iy = listY + i * ITEM_H
            val hov = mouseX in listX..(listX + pw - 4) && mouseY in iy..(iy + ITEM_H)
            val selected = id == currentId
            val bg = when {
                selected -> 0xFF2a3a2a.toInt()
                hov -> 0xFF3a2a1a.toInt()
                else -> 0x00000000
            }
            if (bg != 0) context.fill(listX, iy, listX + pw - 4, iy + ITEM_H, bg)
            // Selection indicator
            if (selected) context.fill(listX, iy, listX + 2, iy + ITEM_H, 0xFF55FF55.toInt())
            m.push()
            m.translate((listX + 5).toFloat(), (iy + 2).toFloat(), 0f)
            m.scale(0.7f, 0.7f, 1f)
            val color = if (selected) 0x55FF55 else if (hov) 0xFFD700 else 0xCCCCCC
            context.drawTextWithShadow(textRenderer, name, 0, 0, color)
            m.pop()
        }

        // Scrollbar (if needed)
        if (filtered.size > visibleCount) {
            val sbX = px + pw - 4; val sbY = listY; val sbH = listH
            context.fill(sbX, sbY, sbX + 3, sbY + sbH, 0xFF222222.toInt())
            val thumbH = (sbH * visibleCount / filtered.size).coerceAtLeast(8)
            val thumbY = sbY + (sbH - thumbH) * scroll / (filtered.size - visibleCount).coerceAtLeast(1)
            context.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF888888.toInt())
        }

        // Separator line between header and list
        context.fill(px + 2, py + HEADER_H - 1, px + pw - 2, py + HEADER_H, 0xFF444444.toInt())

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        val px = panelX; val py = panelY; val pw = PANEL_W; val ph = PANEL_H

        // Click outside panel → close
        if (mx !in px..(px + pw) || my !in py..(py + ph)) {
            close(); return true
        }

        // Close button
        val closeBtnX = px + pw - 12; val closeBtnY = py + 2
        if (mx in closeBtnX..(closeBtnX + 10) && my in closeBtnY..(closeBtnY + 10)) {
            close(); return true
        }

        // Search field click
        val sfX = px + 4; val sfY = py + 16; val sfW = PANEL_W - 8; val sfH = 10
        if (mx in sfX..(sfX + sfW) && my in sfY..(sfY + sfH)) {
            editingSearch = true; return true
        }

        // Item list click
        val listX = px + 2; val listY = py + HEADER_H
        val listH = ph - HEADER_H - FOOTER_H - 2
        val visibleCount = listH / ITEM_H
        val filtered = filteredItems
        if (mx in listX..(listX + PANEL_W - 4) && my in listY..(listY + listH)) {
            val idx = (my - listY) / ITEM_H + scroll
            if (idx in filtered.indices) {
                onPick(filtered[idx].first)
                close()
                return true
            }
        }

        editingSearch = false
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val filtered = filteredItems
        val listH = PANEL_H - HEADER_H - FOOTER_H - 2
        val visibleCount = listH / ITEM_H
        scroll = (scroll - amount.toInt()).coerceIn(0, (filtered.size - visibleCount).coerceAtLeast(0))
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (editingSearch) {
            when (keyCode) {
                256 -> { close(); return true }  // ESC
                259 -> {  // Backspace
                    if (searchQuery.isNotEmpty()) {
                        searchQuery = searchQuery.dropLast(1)
                        scroll = 0
                    }
                    return true
                }
            }
            return true
        }
        if (keyCode == 256) { close(); return true }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (editingSearch) {
            searchQuery += chr
            scroll = 0
            return true
        }
        return super.charTyped(chr, modifiers)
    }

    override fun shouldPause() = false

    override fun close() {
        client?.setScreen(null)
    }
}
