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

        entity.yaw     = rotationY
        entity.bodyYaw = rotationY
        entity.headYaw = rotationY + headYaw
        entity.pitch   = headPitch
        entity.prevYaw = rotationY

        // Настраиваем освещение
        lighting.apply()

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        if (alpha < 1f) RenderSystem.setShaderColor(1f, 1f, 1f, alpha)

        val matrices = ctx.matrices
        matrices.push()
        matrices.translate(x.toFloat(), y.toFloat(), 50f)
        matrices.scale(scale, scale, -scale)  // -scale по Z чтобы модель смотрела на нас

        // Наклон по X
        if (rotationX != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationX))
        }

        val immediate = client.bufferBuilders.entityVertexConsumers

        dispatcher.setRenderShadows(false)
        dispatcher.render(
            entity,
            0.0, 0.0, 0.0,
            0f,  // yaw передаём через entity.yaw выше
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
        entity.yaw     = prevYaw
        entity.bodyYaw = prevBodyYaw
        entity.headYaw = prevHeadYaw
        entity.pitch   = prevPitch
        entity.prevYaw = prevPrevYaw
    }

    /**
     * Рисует [NpcEntity] с GLTF моделью в GUI.
     *
     * Использует Kool pipeline напрямую — обходит стандартный EntityRenderDispatcher.
     * Если у entity нет загруженной модели — ничего не рисует.
     *
     * @param ctx контекст рисования
     * @param entity NPC entity с NpcModelComponent
     * @param x центр по X
     * @param y нижняя точка по Y
     * @param scale масштаб
     * @param rotationY поворот по Y (градусы)
     * @param rotationX наклон по X (градусы)
     * @param alpha прозрачность
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

        // Получаем AnimationSystem — она же содержит ссылку на ModelAttachment через pipeline
        val animSystem = NpcModelRenderer.getAnimationSystem(entity) ?: return

        // Обновляем анимацию
        animSystem.update(MinecraftClient.getInstance().tickDelta.toDouble())

        val client = MinecraftClient.getInstance()
        val immediate = client.bufferBuilders.entityVertexConsumers

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        if (alpha < 1f) RenderSystem.setShaderColor(1f, 1f, 1f, alpha)

        DiffuseLighting.enableGuiDepthLighting()

        val matrices = ctx.matrices
        matrices.push()
        matrices.translate(x.toFloat(), y.toFloat(), 50f)
        matrices.scale(scale, scale, -scale)

        if (rotationX != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationX))
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationY))

        // Вызываем Kool pipeline напрямую с GUI MatrixStack
        val modelComponent = entity.getAttached(
            omc.boundbyfate.component.components.NpcModelComponent.TYPE
        )

        if (modelComponent != null) {
            // Биндим скин если есть
            if (modelComponent.skinId.isNotEmpty()) {
                omc.boundbyfate.client.skin.ClientSkinManager.ensureLoaded(modelComponent.skinId)
                val skinTex = omc.boundbyfate.client.skin.ClientSkinManager.getTexture(modelComponent.skinId)
                if (skinTex != null) {
                    RenderSystem.setShaderTexture(0,
                        client.textureManager.getTexture(skinTex).glId
                    )
                }
            }

            // Получаем pipeline через NpcModelRenderer (он хранит CachedModel внутри)
            // Используем reflection-free подход — вызываем onRenderPre с GUI MatrixStack
            NpcModelRenderer.onRenderPre(
                entity = entity,
                entityYaw = rotationY,
                partialTick = client.tickDelta,
                poseStack = matrices,
                buffer = immediate,
                packedLight = 0xF000F0
            )
        }

        immediate.draw()
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
