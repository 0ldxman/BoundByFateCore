package omc.boundbyfate.client.models.internal.rendering

import net.minecraft.client.render.VertexConsumer
import de.fabmax.kool.math.MutableMat3f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.Color
import org.joml.Matrix3f
import org.joml.Matrix4f
import omc.boundbyfate.client.models.internal.*
import omc.boundbyfate.client.models.internal.rendering.RenderContext
import omc.boundbyfate.client.util.*

class BatchingRenderer(
    private val primitive: Primitive
) : MeshRenderer {

    override fun init() {
        // No GL initialization needed for batching
    }

    override fun setupPipeline(
        pipeline: RenderPipeline,
        skinGetter: SkinGetter,
        matrixGetter: MatrixGetter,
        visibilityGetter: VisibilityGetter
    ) {
        val indices = primitive.indices
        val positions = primitive.positions
        val normals = primitive.normals
        val texCoords = primitive.texCoords
        val iterator = indices?.asIterable() ?: (0 until primitive.positionsCount / 3)

        pipeline.addBatchedRenderable {
            if (!visibilityGetter()) return@addBatchedRenderable
            val posArray = positions ?: return@addBatchedRenderable
            val normArray = normals ?: return@addBatchedRenderable
            val texArray = texCoords ?: return@addBatchedRenderable
            if (posArray.isEmpty() || normArray.isEmpty() || texArray.isEmpty()) return@addBatchedRenderable
            if (indices != null && indices.isEmpty()) return@addBatchedRenderable

            val renderType = batchingRenderType.apply(primitive.material)
            openedBatchedRenderTypes?.add(renderType)
            val vertexConsumer = source.getBuffer(renderType)
            val pose = stack.peek().positionMatrix
            val normal = stack.peek().normalMatrix
            val color = primitive.material.color

            // Disable backface culling for model geometry — GLTF winding order may differ
            com.mojang.blaze3d.systems.RenderSystem.disableCull()

            for (i in iterator) {
                putVertex(matrixGetter, i, vertexConsumer, pose, normal, color, overlay, light, posArray, normArray, texArray)
            }

            com.mojang.blaze3d.systems.RenderSystem.enableCull()
        }    }

    private fun putVertex(
        getter: MatrixGetter,
        index: Int,
        consumer: VertexConsumer,
        pose: Matrix4f,
        normalMat: Matrix3f,
        color: Color,
        overlayCoords: Int,
        packedLight: Int,
        posArray: Array<Vec3f>,
        normArray: Array<Vec3f>,
        texArray: Array<Vec2f>,
    ) {
        if (index !in posArray.indices || index !in normArray.indices || index !in texArray.indices) return

        val global = getter()
        val pos = global.transform(posArray[index], 1f, MutableVec3f())
        val normal = global.getUpperLeft(MutableMat3f()).transform(normArray[index], MutableVec3f())

        consumer
            .vertex(pose, pos.x, pos.y, pos.z)
            .color(color.r, color.g, color.b, color.a)
            .texture(1f - texArray[index].x, texArray[index].y)
            .overlay(overlayCoords)
            .light(packedLight)
            .normal(normalMat, normal.x, normal.y, normal.z)
            .next()
    }

    override fun destroy() {
        // Nothing to destroy
    }
}



