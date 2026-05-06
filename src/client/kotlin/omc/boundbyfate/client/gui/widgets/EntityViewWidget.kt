package omc.boundbyfate.client.gui.widgets

import net.minecraft.entity.LivingEntity
import omc.boundbyfate.client.gui.core.*
import omc.boundbyfate.client.gui.render.EntityGuiRenderer
import omc.boundbyfate.client.gui.render.GuiEntityLighting
import omc.boundbyfate.entity.NpcEntity

/**
 * Виджет отображения 3D модели entity в GUI.
 *
 * Поддерживает:
 * - [LivingEntity] — рендер через EntityRenderDispatcher (игроки, мобы, CharacterDummy)
 * - [NpcEntity]    — рендер через Kool GLTF pipeline
 * - Слежение головы за курсором
 * - Drag-вращение (унаследовано от [View3DWidget])
 *
 * ## Использование
 * ```kotlin
 * val view = EntityViewWidget(entity = characterDummy, scale = 60f)
 * add(view, width = 120, height = 160)
 *
 * // Проброс drag из экрана:
 * override fun mouseClicked(...) {
 *     if (view.isHovered) view.startDrag(mouseX.toFloat())
 * }
 * override fun mouseDragged(...) { view.updateDrag(mouseX.toFloat()) }
 * override fun mouseReleased(...) { view.endDrag() }
 * ```
 */
class EntityViewWidget(
    var entity: LivingEntity? = null,
    scale: Float = 60f,
    rotationY: Float = 180f,
    rotationX: Float = 0f,
    var followMouse: Boolean = true,
    var lighting: GuiEntityLighting = GuiEntityLighting.FRONT,
    draggable: Boolean = true
) : View3DWidget(
    initialScale    = scale,
    initialRotationY = rotationY,
    rotationX       = rotationX,
    draggable       = draggable
) {

    override fun render3D(
        ctx: RenderContext,
        effectiveScale: Float,
        effectiveRotY: Float,
        effectiveAlpha: Float
    ) {
        val e = entity ?: return

        val footY = ctx.cy + (effectiveScale * 0.6f).toInt()

        when (e) {
            is NpcEntity -> EntityGuiRenderer.renderNpc(
                ctx      = ctx.drawContext,
                entity   = e,
                x        = ctx.cx,
                y        = footY,
                scale    = effectiveScale,
                rotationY = effectiveRotY,
                rotationX = rotationX,
                alpha    = effectiveAlpha
            )
            else -> EntityGuiRenderer.render(
                ctx         = ctx.drawContext,
                entity      = e,
                x           = ctx.cx,
                y           = footY,
                scale       = effectiveScale,
                rotationY   = effectiveRotY,
                rotationX   = rotationX,
                followMouse = followMouse && isHovered,
                mouseX      = ctx.mouseX.toFloat(),
                mouseY      = ctx.mouseY.toFloat(),
                lighting    = lighting,
                alpha       = effectiveAlpha
            )
        }
    }
}
