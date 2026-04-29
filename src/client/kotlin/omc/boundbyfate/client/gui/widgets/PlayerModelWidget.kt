package omc.boundbyfate.client.gui.widgets

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.entity.LivingEntity
import com.mojang.blaze3d.systems.RenderSystem
import omc.boundbyfate.client.gui.core.AnimOwner
import omc.boundbyfate.client.gui.core.RenderContext

/**
 * Виджет отображения модели персонажа/сущности.
 *
 * Поддерживает alpha и слежение за курсором.
 *
 * ## Использование
 *
 * ```kotlin
 * val model = PlayerModelWidget(scale = 70)
 *
 * fun tick(ctx: RenderContext) {
 *     model.alpha.target = if (hovered) 0.3f else 1f
 *     model.tickAll(ctx.delta)
 * }
 *
 * fun render(ctx: RenderContext) {
 *     model.render(ctx.drawContext, ctx.cx, ctx.cy + 85,
 *         entity = player,
 *         mouseX = ctx.mouseX.toFloat(),
 *         mouseY = ctx.mouseY.toFloat()
 *     )
 * }
 * ```
 */
class PlayerModelWidget(
    val scale: Int = 70,
    /** Следить ли за курсором (голова поворачивается). */
    var followMouse: Boolean = true
) : AnimOwner() {

    val alpha = animFloat(1f, speed = 0.1f)

    fun render(
        ctx: DrawContext,
        x: Int, y: Int,
        entity: LivingEntity,
        mouseX: Float = 0f,
        mouseY: Float = 0f
    ) {
        if (alpha.current < 0.005f) return

        val effectiveMouseX = if (followMouse) mouseX else (x - 1000f)
        val effectiveMouseY = if (followMouse) mouseY else (y - 1000f)

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha.current)

        InventoryScreen.drawEntity(ctx, x, y, scale, effectiveMouseX, effectiveMouseY, entity)

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    fun render(ctx: RenderContext, entity: LivingEntity) =
        render(ctx.drawContext, ctx.cx, ctx.cy + scale,
            entity, ctx.mouseX.toFloat(), ctx.mouseY.toFloat())
}

/**
 * Extension для удобного рисования модели без виджета.
 */
fun DrawContext.playerModel(
    entity: LivingEntity,
    x: Int, y: Int,
    scale: Int = 70,
    mouseX: Float = 0f,
    mouseY: Float = 0f,
    alpha: Float = 1f
) {
    if (alpha < 0.005f) return
    RenderSystem.enableBlend()
    RenderSystem.setShaderColor(1f, 1f, 1f, alpha)
    InventoryScreen.drawEntity(this, x, y, scale, mouseX, mouseY, entity)
    RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    RenderSystem.disableBlend()
}
