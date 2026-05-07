package omc.boundbyfate.client.gui.render

import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.DiffuseLighting
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.RotationAxis
import omc.boundbyfate.client.models.internal.rendering.RenderContext as ModelRenderContext
import omc.boundbyfate.client.render.NpcModelRenderer
import omc.boundbyfate.client.util.rl
import omc.boundbyfate.entity.NpcEntity
import org.joml.Quaternionf

/**
 * Рендерер 3D entity в GUI контексте.
 *
 * Позволяет рисовать любую [LivingEntity] внутри GUI экрана с полным контролем
 * над масштабом, поворотом и освещением. Переиспользуется везде где нужна
 * 3D модель в интерфейсе — экран внешности, карточки персонажей, диалоги НПС и т.д.
 *
 * ## Использование
 * ```kotlin
 * // Простой рендер по центру контекста
 * EntityGuiRenderer.render(ctx.drawContext, entity, ctx.cx, ctx.cy + 40, scale = 40f)
 *
 * // С поворотом и слежением за мышью
 * EntityGuiRenderer.render(
 *     ctx.drawContext, entity,
 *     x = ctx.cx, y = ctx.cy + 40,
 *     scale = 40f,
 *     rotationY = 180f,
 *     mouseX = ctx.mouseX.toFloat(),
 *     mouseY = ctx.mouseY.toFloat(),
 *     followMouse = true
 * )
 * ```
 */
@Environment(EnvType.CLIENT)
object EntityGuiRenderer {

    /**
     * Рисует [entity] в GUI.
     *
     * @param ctx контекст рисования
     * @param entity entity для рендера
     * @param x центр по X в GUI-координатах
     * @param y нижняя точка модели по Y (ноги)
     * @param scale масштаб (больше = крупнее)
     * @param rotationY поворот вокруг вертикальной оси (градусы, 0 = лицом к нам)
     * @param rotationX наклон вокруг горизонтальной оси (градусы, положительный = смотрим сверху)
     * @param followMouse если true — голова поворачивается к курсору
     * @param mouseX позиция курсора X (используется если followMouse = true)
     * @param mouseY позиция курсора Y (используется если followMouse = true)
     * @param lighting тип освещения
     * @param alpha прозрачность (0..1)
     */
    fun render(
        ctx: DrawContext,
        entity: LivingEntity,
        x: Int,
        y: Int,
        scale: Float,
        rotationY: Float = 180f,
        rotationX: Float = 0f,
        followMouse: Boolean = false,
        mouseX: Float = 0f,
        mouseY: Float = 0f,
        lighting: GuiEntityLighting = GuiEntityLighting.FRONT,
        alpha: Float = 1f
    ) {
        if (alpha < 0.005f) return

        val client = MinecraftClient.getInstance()
        val dispatcher = client.entityRenderDispatcher

        // Вычисляем углы поворота головы для слежения за мышью
        val headYaw: Float
        val headPitch: Float
        if (followMouse) {
            val dx = mouseX - x
            val dy = mouseY - (y - entity.height * scale * 0.5f)
            headYaw   = -(Math.toDegrees(Math.atan2(dx.toDouble(), 100.0))).toFloat()
            headPitch =  (Math.toDegrees(Math.atan2(dy.toDouble(), 100.0))).toFloat()
        } else {
            headYaw   = 0f
            headPitch = 0f
        }

        // Сохраняем состояние entity и временно подменяем углы
        val prevYaw       = entity.yaw
        val prevBodyYaw   = entity.bodyYaw
        val prevHeadYaw   = entity.headYaw
        val prevPitch     = entity.pitch
        val prevPrevYaw   = entity.prevYaw
        val prevPrevBodyYaw = entity.prevBodyYaw

        entity.yaw         = rotationY
        entity.bodyYaw     = rotationY
        entity.prevBodyYaw = rotationY
        entity.headYaw     = rotationY + headYaw
        entity.pitch       = headPitch
        entity.prevYaw     = rotationY

        // Настраиваем освещение
        lighting.apply()

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        if (alpha < 1f) RenderSystem.setShaderColor(1f, 1f, 1f, alpha)

        val matrices = ctx.matrices
        matrices.push()
        matrices.translate(x.toFloat(), y.toFloat(), 50f)
        matrices.scale(scale, scale, scale)
        // Инвертируем Z чтобы модель смотрела на нас (стандартный MC GUI подход)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f))

        // Наклон по X
        if (rotationX != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationX))
        }

        val immediate = client.bufferBuilders.entityVertexConsumers

        dispatcher.setRenderShadows(false)
        dispatcher.render(
            entity,
            0.0, 0.0, 0.0,
            rotationY,
            client.tickDelta,
            matrices,
            immediate,
            0xF000F0  // полное освещение (sky=15, block=15)
        )
        immediate.draw()
        dispatcher.setRenderShadows(true)

        matrices.pop()

        if (alpha < 1f) RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()

        // Восстанавливаем освещение по умолчанию
        DiffuseLighting.enableGuiDepthLighting()

        // Восстанавливаем состояние entity
        entity.yaw         = prevYaw
        entity.bodyYaw     = prevBodyYaw
        entity.prevBodyYaw = prevPrevBodyYaw
        entity.headYaw     = prevHeadYaw
        entity.pitch       = prevPitch
        entity.prevYaw     = prevPrevYaw
    }

    // ── Рендер NPC через NpcModelRenderer ────────────────────────────────

    /**
     * Рисует [NpcEntity] с GLTF моделью в GUI.
     *
     * Использует [NpcModelRenderer.onRenderPre] напрямую — тот же путь что и для entity в мире.
     * Матрица настраивается для GUI контекста (translate + scale + rotate).
     */
    fun renderNpc(
        ctx: DrawContext,
        entity: NpcEntity,
        x: Int,
        y: Int,
        scale: Float,
        rotationY: Float = 180f,
        rotationX: Float = 0f,
        alpha: Float = 1f
    ) {
        if (alpha < 0.005f) return

        val client = MinecraftClient.getInstance()

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        if (alpha < 1f) RenderSystem.setShaderColor(1f, 1f, 1f, alpha)

        DiffuseLighting.enableGuiDepthLighting()

        val matrices = ctx.matrices
        matrices.push()
        matrices.translate(x.toFloat(), y.toFloat(), 50f)
        matrices.scale(scale, scale, scale)
        // Поворот Z на 180° — стандартный GUI трюк (модель "стоит" на ногах)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f))
        if (rotationX != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationX))
        }

        // Устанавливаем yaw чтобы NpcModelRenderer применил правильный поворот
        entity.bodyYaw     = rotationY
        entity.prevBodyYaw = rotationY

        // renderForGui — минует frustum culling, который отсекает entity не в мире
        val rendered = NpcModelRenderer.renderForGui(
            entity      = entity,
            poseStack   = matrices,
            buffer      = client.bufferBuilders.entityVertexConsumers,
            packedLight = 0xF000F0,
            entityYaw   = rotationY,
            partialTick = client.tickDelta
        )
        org.slf4j.LoggerFactory.getLogger("BbfGui")
            .info("[renderNpc] renderForGui=$rendered, entity=${entity.uuid}")

        client.bufferBuilders.entityVertexConsumers.draw()
        matrices.pop()

        if (alpha < 1f) RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
        DiffuseLighting.enableGuiDepthLighting()
    }
}

// ── Типы освещения ────────────────────────────────────────────────────────

/**
 * Тип освещения для рендера entity в GUI.
 */
enum class GuiEntityLighting {
    /** Стандартное фронтальное освещение (как в инвентаре Minecraft). */
    FRONT {
        override fun apply() = DiffuseLighting.enableGuiDepthLighting()
    },
    /** Освещение сверху — более драматичный эффект. */
    TOP {
        override fun apply() {
            DiffuseLighting.disableGuiDepthLighting()
            DiffuseLighting.enableForLevel(MatrixStack().also {
                it.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30f))
            }.peek().positionMatrix)
        }
    },
    /** Без дополнительного освещения — только ambient. */
    NONE {
        override fun apply() = DiffuseLighting.disableGuiDepthLighting()
    };

    abstract fun apply()
}
