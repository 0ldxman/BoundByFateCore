package omc.boundbyfate.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.render.item.ItemRenderer
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.client.state.ClientFeatureState
import omc.boundbyfate.network.BbfPackets
import omc.boundbyfate.registry.FeatureRegistry

/**
 * Feature management screen.
 *
 * Layout:
 * - Top: 10 hotbar slots (assignable via drag & drop)
 * - Bottom: scrollable grid of all available features
 *
 * Background texture: 352x222 px
 * Slot size: 18x18 px
 */
class FeatureScreen : Screen(Text.translatable("screen.boundbyfate.features")) {

    companion object {
        val BACKGROUND = Identifier("boundbyfate-core", "textures/gui/feature_screen.png")
        const val BG_WIDTH = 352
        const val BG_HEIGHT = 222
        const val SLOT_SIZE = 18
        const val HOTBAR_SLOTS = 10
        const val GRID_COLS = 9
    }

    private var bgX = 0
    private var bgY = 0

    // Drag state
    private var draggingFeatureId: Identifier? = null
    private var dragMouseX = 0
    private var dragMouseY = 0

    override fun init() {
        bgX = (width - BG_WIDTH) / 2
        bgY = (height - BG_HEIGHT) / 2
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)

        // Draw background (fallback: dark rectangle)
        context.fill(bgX, bgY, bgX + BG_WIDTH, bgY + BG_HEIGHT, 0xCC1A1A2E.toInt())
        context.fill(bgX, bgY, bgX + BG_WIDTH, bgY + 1, 0xFF6B4C9A.toInt())
        context.fill(bgX, bgY + BG_HEIGHT - 1, bgX + BG_WIDTH, bgY + BG_HEIGHT, 0xFF6B4C9A.toInt())
        context.fill(bgX, bgY, bgX + 1, bgY + BG_HEIGHT, 0xFF6B4C9A.toInt())
        context.fill(bgX + BG_WIDTH - 1, bgY, bgX + BG_WIDTH, bgY + BG_HEIGHT, 0xFF6B4C9A.toInt())

        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, bgX + BG_WIDTH / 2, bgY + 6, 0xFFFFFF)

        // Hotbar label
        context.drawTextWithShadow(textRenderer, Text.literal("Активные особенности:"), bgX + 8, bgY + 20, 0xAAAAAA)

        // Draw hotbar slots
        drawHotbarSlots(context, mouseX, mouseY)

        // Divider
        context.fill(bgX + 8, bgY + 50, bgX + BG_WIDTH - 8, bgY + 51, 0xFF6B4C9A.toInt())

        // Available features label
        context.drawTextWithShadow(textRenderer, Text.literal("Доступные особенности:"), bgX + 8, bgY + 56, 0xAAAAAA)

        // Draw feature grid
        drawFeatureGrid(context, mouseX, mouseY)

        // Draw dragged feature
        if (draggingFeatureId != null) {
            drawFeatureIcon(context, draggingFeatureId!!, dragMouseX - SLOT_SIZE / 2, dragMouseY - SLOT_SIZE / 2)
        }

        super.render(context, mouseX, mouseY, delta)

        // Tooltip
        drawTooltipIfNeeded(context, mouseX, mouseY)
    }

    private fun drawHotbarSlots(context: DrawContext, mouseX: Int, mouseY: Int) {
        val startX = bgX + (BG_WIDTH - HOTBAR_SLOTS * SLOT_SIZE) / 2
        val y = bgY + 30

        for (i in 0 until HOTBAR_SLOTS) {
            val x = startX + i * SLOT_SIZE
            val featureId = ClientFeatureState.getHotbarSlot(i)
            val hovered = mouseX in x..(x + SLOT_SIZE) && mouseY in y..(y + SLOT_SIZE)

            // Slot background
            val slotColor = if (hovered) 0xFF3D3D5C.toInt() else 0xFF2A2A3E.toInt()
            context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, slotColor)
            context.fill(x, y, x + SLOT_SIZE, y + 1, 0xFF555577.toInt())
            context.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF555577.toInt())
            context.fill(x, y, x + 1, y + SLOT_SIZE, 0xFF555577.toInt())
            context.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF555577.toInt())

            // Slot number
            context.drawTextWithShadow(textRenderer, Text.literal("${(i + 1) % 10}"), x + 2, y + 2, 0x888888)

            // Feature icon
            if (featureId != null) {
                drawFeatureIcon(context, featureId, x + 1, y + 1)
            }
        }
    }

    private fun drawFeatureGrid(context: DrawContext, mouseX: Int, mouseY: Int) {
        val features = ClientFeatureState.grantedFeatures.toList()
        val startX = bgX + 8
        val startY = bgY + 66

        features.forEachIndexed { index, featureId ->
            val col = index % GRID_COLS
            val row = index / GRID_COLS
            val x = startX + col * SLOT_SIZE
            val y = startY + row * SLOT_SIZE

            if (y + SLOT_SIZE > bgY + BG_HEIGHT - 8) return // Out of bounds

            val hovered = mouseX in x..(x + SLOT_SIZE) && mouseY in y..(y + SLOT_SIZE)
            val slotColor = if (hovered) 0xFF3D3D5C.toInt() else 0xFF2A2A3E.toInt()

            context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, slotColor)
            context.fill(x, y, x + SLOT_SIZE, y + 1, 0xFF555577.toInt())
            context.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF555577.toInt())
            context.fill(x, y, x + 1, y + SLOT_SIZE, 0xFF555577.toInt())
            context.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF555577.toInt())

            drawFeatureIcon(context, featureId, x + 1, y + 1)
        }
    }

    private fun drawFeatureIcon(context: DrawContext, featureId: Identifier, x: Int, y: Int) {
        val feature = FeatureRegistry.getFeature(featureId) ?: return
        val icon = feature.icon

        if (icon.startsWith("item:")) {
            // Use item texture as icon
            val itemId = Identifier(icon.removePrefix("item:"))
            val item = Registries.ITEM.get(itemId)
            val stack = ItemStack(item)
            context.drawItem(stack, x, y)
        }
        // Custom textures will be added later
    }

    private fun drawTooltipIfNeeded(context: DrawContext, mouseX: Int, mouseY: Int) {
        // Check hotbar slots
        val hotbarStartX = bgX + (BG_WIDTH - HOTBAR_SLOTS * SLOT_SIZE) / 2
        val hotbarY = bgY + 30
        for (i in 0 until HOTBAR_SLOTS) {
            val x = hotbarStartX + i * SLOT_SIZE
            if (mouseX in x..(x + SLOT_SIZE) && mouseY in hotbarY..(hotbarY + SLOT_SIZE)) {
                val featureId = ClientFeatureState.getHotbarSlot(i) ?: return
                showFeatureTooltip(context, featureId, mouseX, mouseY)
                return
            }
        }

        // Check feature grid
        val features = ClientFeatureState.grantedFeatures.toList()
        val startX = bgX + 8
        val startY = bgY + 66
        features.forEachIndexed { index, featureId ->
            val col = index % GRID_COLS
            val row = index / GRID_COLS
            val x = startX + col * SLOT_SIZE
            val y = startY + row * SLOT_SIZE
            if (mouseX in x..(x + SLOT_SIZE) && mouseY in y..(y + SLOT_SIZE)) {
                showFeatureTooltip(context, featureId, mouseX, mouseY)
                return
            }
        }
    }

    private fun showFeatureTooltip(context: DrawContext, featureId: Identifier, mouseX: Int, mouseY: Int) {
        val feature = FeatureRegistry.getFeature(featureId) ?: return
        val lines = mutableListOf<Text>()
        lines.add(Text.literal(feature.displayName).styled { it.withColor(0xFFD700.toInt()) })
        if (feature.description.isNotBlank()) {
            lines.add(Text.literal(feature.description).styled { it.withColor(0xAAAAAA.toInt()) })
        }
        val typeLabel = feature.trigger?.let { "Triggered: ${it.event}" } ?: "Passive"
        lines.add(Text.literal("§8$typeLabel"))
        context.drawTooltip(textRenderer, lines, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        // Start dragging from feature grid
        val features = ClientFeatureState.grantedFeatures.toList()
        val startX = bgX + 8
        val startY = bgY + 66
        features.forEachIndexed { index, featureId ->
            val col = index % GRID_COLS
            val row = index / GRID_COLS
            val x = startX + col * SLOT_SIZE
            val y = startY + row * SLOT_SIZE
            if (mx in x..(x + SLOT_SIZE) && my in y..(y + SLOT_SIZE)) {
                draggingFeatureId = featureId
                dragMouseX = mx
                dragMouseY = my
                return true
            }
        }

        // Start dragging from hotbar
        val hotbarStartX = bgX + (BG_WIDTH - HOTBAR_SLOTS * SLOT_SIZE) / 2
        val hotbarY = bgY + 30
        for (i in 0 until HOTBAR_SLOTS) {
            val x = hotbarStartX + i * SLOT_SIZE
            if (mx in x..(x + SLOT_SIZE) && my in hotbarY..(hotbarY + SLOT_SIZE)) {
                val featureId = ClientFeatureState.getHotbarSlot(i)
                if (featureId != null) {
                    draggingFeatureId = featureId
                    dragMouseX = mx
                    dragMouseY = my
                    // Clear the slot
                    updateSlot(i, null)
                }
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (draggingFeatureId != null) {
            dragMouseX = mouseX.toInt()
            dragMouseY = mouseY.toInt()
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val featureId = draggingFeatureId ?: return super.mouseReleased(mouseX, mouseY, button)
        draggingFeatureId = null

        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        // Drop on hotbar slot
        val hotbarStartX = bgX + (BG_WIDTH - HOTBAR_SLOTS * SLOT_SIZE) / 2
        val hotbarY = bgY + 30
        for (i in 0 until HOTBAR_SLOTS) {
            val x = hotbarStartX + i * SLOT_SIZE
            if (mx in x..(x + SLOT_SIZE) && my in hotbarY..(hotbarY + SLOT_SIZE)) {
                updateSlot(i, featureId)
                return true
            }
        }

        return true
    }

    private fun updateSlot(slot: Int, featureId: Identifier?) {
        ClientFeatureState.setHotbarSlot(slot, featureId)

        // Send to server
        val buf = PacketByteBufs.create()
        buf.writeInt(slot)
        buf.writeBoolean(featureId != null)
        if (featureId != null) buf.writeIdentifier(featureId)
        ClientPlayNetworking.send(BbfPackets.UPDATE_FEATURE_SLOT, buf)
    }

    override fun shouldPause() = false
}
