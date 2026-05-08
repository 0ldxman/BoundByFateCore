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

    private var debugLogCount = 0

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
            // Copy matrices to avoid issues with MatrixStack reusing Entry objects
            val pose = Matrix4f(stack.peek().positionMatrix)
            val normal = Matrix3f(stack.peek().normalMatrix)
            val color = primitive.material.color

            // Capture globalMatrix ONCE per draw call with explicit copy
            val globalSnapshot = de.fabmax.kool.math.MutableMat4f().also { it.set(matrixGetter()) }

            // One-time debug: log first primitive's vertices as sent to GPU
            val doLog = debugLogCount++ == 0 && posArray.size == 24
            if (doLog) {
                val log = org.apache.logging.log4j.LogManager.getLogger()
                log.info("[BatchingRenderer] === 24-vertex primitive (head?) debug ===")
                log.info("[BatchingRenderer] pose translation: (${r(pose.m30())}, ${r(pose.m31())}, ${r(pose.m32())})")
                log.info("[BatchingRenderer] global: t=(${r(globalSnapshot.m30)},${r(globalSnapshot.m31)},${r(globalSnapshot.m32)}) scale=(${r(globalSnapshot.m00)},${r(globalSnapshot.m11)},${r(globalSnapshot.m22)})")
                for (vi in posArray.indices) {
                    val p = posArray[vi]
                    val transformed = globalSnapshot.transform(de.fabmax.kool.math.MutableVec3f(p.x, p.y, p.z), 1f, de.fabmax.kool.math.MutableVec3f())
                    val uv = if (vi < texArray.size) texArray[vi] else null
                    log.info("[BatchingRenderer] v$vi raw=(${r(p.x)},${r(p.y)},${r(p.z)}) -> (${r(transformed.x)},${r(transformed.y)},${r(transformed.z)}) uv=${if(uv!=null) "(${r(uv.x)},${r(uv.y)})" else "N/A"}")
                }
                if (indices != null) log.info("[BatchingRenderer] indices=${indices.toList()}")
            }

            for (i in iterator) {
                putVertex(globalSnapshot, i, vertexConsumer, pose, normal, color, overlay, light, posArray, normArray, texArray)
            }
        }    }

    private fun putVertex(
        global: de.fabmax.kool.math.MutableMat4f,
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

        val pos = global.transform(posArray[index], 1f, MutableVec3f())
        val normal = global.getUpperLeft(MutableMat3f()).transform(normArray[index], MutableVec3f())

        consumer
            .vertex(pose, pos.x, pos.y, pos.z)
            .color(color.r, color.g, color.b, color.a)
            .texture(texArray[index].x, texArray[index].y)
            .overlay(overlayCoords)
            .light(packedLight)
            .normal(normalMat, normal.x, normal.y, normal.z)
            .next()
    }

    override fun destroy() {
        // Nothing to destroy
    }

    private fun r(f: Float) = Math.round(f * 1000f) / 1000f
}



