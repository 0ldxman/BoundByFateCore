package omc.boundbyfate.client.models.internal

import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.render.VertexConsumerProvider
// HollowCore removed
import omc.boundbyfate.client.models.internal.animations.Animation
import omc.boundbyfate.client.models.internal.controller.Controller
import omc.boundbyfate.client.models.internal.rendering.RenderContext
import omc.boundbyfate.client.util.molang.MolangContext


class AnimatedModel(val model: Model) {
    val animations: Map<String, Animation> = model.animations.associateBy { it.name }
    val nodes = model.walkNodes().toList()

    fun update(controller: Controller, query: MolangContext, time: Float) {
        try {
            nodes.forEach {
                it.transform.set(it.baseTransform)
                controller.update(it, query, time)
            }
        } catch (e: Exception) {
            org.apache.logging.log4j.LogManager.getLogger().error("Error while updating animations!", e)
            controller.layers.clear()
        }
    }

    fun render(
        stack: MatrixStack,
        source: VertexConsumerProvider,
        light: Int,
        overlay: Int,
    ) {
        model.pipeline.render(RenderContext(stack, source, light, overlay))
    }

    fun destroy() {
        model.walkNodes().mapNotNull { it.mesh }.flatMap { it.primitives }.forEach(Primitive::destroy)
    }

    companion object {
        val EMPTY = AnimatedModel(Model(0, listOf(), setOf(), listOf()))
    }
}


