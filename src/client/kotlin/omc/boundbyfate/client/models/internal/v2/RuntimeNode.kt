package omc.boundbyfate.client.models.internal.v2

import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.scene.TrsTransformF
import omc.boundbyfate.client.models.internal.NodeDefinition
import omc.boundbyfate.client.models.internal.rendering.RenderPipeline

abstract class Attachment(val parent: Attachment? = null) {
    val transform = TrsTransformF()
    val globalMatrix = MutableMat4f()

    open fun updateGlobalMatrix() {
        val localM = transform.matrixF
        if (parent != null) {
            globalMatrix.set(parent.globalMatrix)
            globalMatrix.mul(localM)
        } else {
            globalMatrix.set(localM)
        }
    }

    open fun collectCommands(pipeline: RenderPipeline) {
        pipeline.onUpdate { updateGlobalMatrix() }
    }
}

open class RuntimeNode(
    val definition: NodeDefinition,
    parent: Attachment?,
) : Attachment(parent) {
    val name: String = definition.name ?: parent?.let { "Node_${definition.index}" } ?: "Root"
    var isVisible = true
        set(value) {
            field = value
            children.forEach { it.isVisible = value }
        }

    init {
        transform.set(definition.baseTransform)
    }

    val attachments = arrayListOf<Attachment>()

    val children = definition.children.map {
        RuntimeNode(it, this)
    }

    val jointGetter by lazy {
        definition.skin!!.jointsIds.associateWith { id -> root.walk().first { it.definition.index == id } }
    }

    override fun collectCommands(pipeline: RenderPipeline) {
        super.collectCommands(pipeline)
        definition.mesh?.primitives?.forEachIndexed { primIdx, primitive ->
            // Debug: log golova mesh data once
            if (name == "golova" && primIdx == 0) {
                val log = org.apache.logging.log4j.LogManager.getLogger()
                val pos = primitive.positions
                val tex = primitive.texCoords
                val idx = primitive.indices
                log.info("[RuntimeNode] === golova primitive debug ===")
                log.info("[RuntimeNode] positions=${pos?.size}, texCoords=${tex?.size}, indices=${idx?.size}")
                pos?.forEachIndexed { i, v -> log.info("[RuntimeNode] pos[$i]=(${r(v.x)},${r(v.y)},${r(v.z)})") }
                tex?.forEachIndexed { i, v -> log.info("[RuntimeNode] uv[$i]=(${r(v.x)},${r(v.y)})") }
                idx?.let { log.info("[RuntimeNode] indices=${it.toList()}") }
            }
            primitive.setupPipeline(pipeline, { definition.skin!!.compute(globalMatrix, jointGetter) }, ::globalMatrix, ::isVisible)
        }
        attachments.forEach { it.collectCommands(pipeline) }
        children.forEach { it.collectCommands(pipeline) }
    }

    private fun r(f: Float) = Math.round(f * 1000f) / 1000f

    fun child(name: String): RuntimeNode = children.single { it.name == name }
}

fun RuntimeNode.walk(): List<RuntimeNode> = buildList {
    add(this@walk)
    children.forEach {
        addAll(it.walk())
    }
}

val RuntimeNode.root: RuntimeNode
    get() {
        var current: RuntimeNode = this
        while (current.parent is RuntimeNode) {
            current = current.parent
        }
        return current
    }


