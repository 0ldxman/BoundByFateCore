package omc.boundbyfate.client.models.internal.v2

import de.fabmax.kool.math.MutableMat3f
import net.minecraft.client.MinecraftClient
import net.minecraft.util.MathHelper
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ModelTransformationMode
import org.joml.Quaternionf
import omc.boundbyfate.client.models.internal.rendering.RenderPipeline
import omc.boundbyfate.client.util.asMatrix3f
import omc.boundbyfate.client.util.asMatrix4f
import omc.boundbyfate.client.util.*

class ItemNode(val entity: LivingEntity, val slot: EquipmentSlot, parent: Attachment?): Attachment(parent) {
    override fun collectCommands(pipeline: RenderPipeline) {
        super.collectCommands(pipeline)
        pipeline.addBatchedRenderable {
            stack.push()

            stack.multiplyPositionMatrix(globalMatrix.asMatrix4f())
            stack.peek().normalMatrix.mul(globalMatrix.getUpperLeft(MutableMat3f()).asMatrix3f())

            stack.multiply(Quaternionf().rotateX(-90 * MathHelper.RADIANS_PER_DEGREE))

            MinecraftClient.getInstance().itemRenderer.renderItem(
                entity.getEquippedStack(slot),
                when (slot) {
                    EquipmentSlot.MAINHAND -> ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
                    EquipmentSlot.OFFHAND -> ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                    EquipmentSlot.HEAD -> ModelTransformationMode.HEAD
                    else -> ModelTransformationMode.FIXED
                },
                slot == EquipmentSlot.OFFHAND,
                stack,
                source,
                light, overlay
            )

            stack.pop()
        }
    }
}
