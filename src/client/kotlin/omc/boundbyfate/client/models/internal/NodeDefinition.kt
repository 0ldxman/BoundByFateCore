package omc.boundbyfate.client.models.internal

import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.MutableQuatF
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.TrsTransformF

open class NodeDefinition(
    val index: Int,
    val name: String? = null,
    val children: MutableList<NodeDefinition>,
    val transform: TrsTransformF,
    val mesh: Mesh? = null,
    val skin: Skin? = null,
) {
    var parent: NodeDefinition? = null
    val root: NodeDefinition by lazy { parent?.root ?: this }
    val path: String get() = parent?.let { it.name + "/" + name } ?: name ?: "Unnamed Bone"

    val baseTransform = TrsTransformF().apply {
        translate(transform.translation)
        rotate(transform.rotation)
        scale(transform.scale)
    }

    /**
     * Глобальная T-pose translation (сумма всех translation по цепочке родителей).
     * Используется в AnimationLoader для вычисления дельт от Blockbench анимаций,
     * которые хранят трансформации в глобальных координатах.
     */
    val globalBaseTranslation: Vec3f by lazy {
        val parentT = parent?.globalBaseTranslation ?: Vec3f.ZERO
        parentT + baseTransform.translation
    }

    /**
     * Глобальная T-pose rotation (произведение всех rotation по цепочке родителей).
     */
    val globalBaseRotation: QuatF by lazy {
        val parentR = parent?.globalBaseRotation ?: QuatF.IDENTITY
        parentR.mul(baseTransform.rotation, MutableQuatF())
    }


    val localMatrix get() = transform.matrixF
    val globalMatrix = MutableMat4f()

    val globalRotation: QuatF
        get() {
            var rotation = parent?.globalRotation ?: return transform.rotation
            transform.apply {
                rotation = rotation.mul(this.rotation, MutableQuatF())
            }
            return rotation
        }


    fun allBones(): Set<NodeDefinition> = buildSet {
        add(this@NodeDefinition)
        addAll(children.flatMap { it.allBones() })
    }

    override fun toString(): String {
        return "Node $name [Mesh: $mesh, Skin: $skin]"
    }
}


