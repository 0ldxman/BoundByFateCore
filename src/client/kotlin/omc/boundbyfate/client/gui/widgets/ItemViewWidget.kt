package omc.boundbyfate.client.gui.widgets

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.DiffuseLighting
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.item.ItemStack
import net.minecraft.util.math.RotationAxis
import omc.boundbyfate.client.gui.core.*

/**
 * Виджет отображения 3D модели предмета в GUI.
 *
 * Рендерит [ItemStack] через стандартный ItemRenderer Minecraft.
 * Работает с любыми предметами включая генеративные модели (item/generated, item/handheld).
 *
 * ## Использование
 * ```kotlin
 * val sword = ItemViewWidget(ItemStack(Items.DIAMOND_SWORD), scale = 2f)
 * add(sword, width = 32, height = 32)
 *
 * // С поворотом
 * val item = ItemViewWidget(
 *     stack = myCustomItem,
 *     scale = 2f,
 *     rotationY = 30f,
 *     mode = ModelTransformationMode.GROUND
 * )
 * ```
 */
class ItemViewWidget(
    var stack: ItemStack = ItemStack.EMPTY,
    scale: Float = 1f,
    rotationY: Float = 0f,
    rotationX: Float = 0f,
    rotationZ: Float = 0f,
    /** Режим трансформации модели. GUI — стандартный вид иконки. */
    var mode: ModelTransformationMode = ModelTransformationMode.GUI,
    draggable: Boolean = false
) : View3DWidget(
    initialScale     = scale,
    initialRotationY = rotationY,
    rotationX        = rotationX,
    rotationZ        = rotationZ,
    draggable        = draggable
) {

    override fun render3D(
        ctx: RenderContext,
        effectiveScale: Float,
        effectiveRotY: Float,
        effectiveAlpha: Float
    ) {
        if (stack.isEmpty) return

        val client = MinecraftClient.getInstance()
        val itemRenderer = client.itemRenderer
        val matrices = ctx.drawContext.matrices
        val immediate = client.bufferBuilders.entityVertexConsumers

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        if (effectiveAlpha < 1f) RenderSystem.setShaderColor(1f, 1f, 1f, effectiveAlpha)

        DiffuseLighting.enableGuiDepthLighting()

        matrices.push()
        matrices.translate(ctx.cx.toFloat(), ctx.cy.toFloat(), 150f)
        // 16f — нормализуем к стандартному размеру предмета (1 unit = 16px)
        matrices.scale(effectiveScale * 16f, -effectiveScale * 16f, effectiveScale * 16f)

        if (rotationX != 0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationX))
        if (effectiveRotY != 0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(effectiveRotY))
        if (rotationZ != 0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationZ))

        itemRenderer.renderItem(
            stack,
            mode,
            false,
            matrices,
            immediate,
            0xF000F0,  // полное освещение
            0,         // нет overlay
            itemRenderer.getModel(stack, null, null, 0)
        )

        immediate.draw()
        matrices.pop()

        if (effectiveAlpha < 1f) RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
        DiffuseLighting.enableGuiDepthLighting()
    }
}
