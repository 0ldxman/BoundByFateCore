package omc.boundbyfate.client.models.internal

import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableMat4f
import omc.boundbyfate.client.models.internal.v2.RuntimeNode
import org.slf4j.LoggerFactory

class Skin(
    val jointsIds: List<Int>,
    val inverseBindMatrices: Array<Mat4f>,
) {
    private val cache: Array<Mat4f> = Array(jointsIds.size) { MutableMat4f() }
    private var debugCount = 0
    private val logger = LoggerFactory.getLogger(Skin::class.java)

    fun compute(globalRoot: Mat4f, jointGetter: Map<Int, RuntimeNode>): Array<Mat4f> {
        for ((i, id) in jointsIds.withIndex()) {
            val jointGlobalMatrix = MutableMat4f(jointGetter[id]!!.globalMatrix)
            val bindMatrix = MutableMat4f(inverseBindMatrices[i])
            // GLTF skinning formula: skinMatrix = globalJoint * inverseBindMatrix
            val skinMatrix = MutableMat4f(jointGlobalMatrix).mul(bindMatrix)
            cache[i] = skinMatrix.transpose()
        }

        if (debugCount < 3) {
            val legIdx = jointsIds.indexOf(19)
            if (legIdx >= 0) {
                val jNode = jointGetter[19]
                val gm = jNode?.globalMatrix
                
                // Вычисляем skinMatrix до транспозиции для правильного лога
                val jointGlobalMatrix = MutableMat4f(jointGetter[19]!!.globalMatrix)
                val bindMatrix = MutableMat4f(inverseBindMatrices[legIdx])
                val skinMatrix = MutableMat4f(jointGlobalMatrix).mul(bindMatrix)
                
                // translation в kool Mat4f хранится в m03, m13, m23 (column-major)
                logger.info("[Skin] frame=$debugCount joint=19(LeftLeg)")
                logger.info("[Skin]   globalMatrix[19]: t=(${gm?.m03},${gm?.m13},${gm?.m23})")
                logger.info("[Skin]   inverseBindMatrix[19]: t=(${inverseBindMatrices[legIdx].m03},${inverseBindMatrices[legIdx].m13},${inverseBindMatrices[legIdx].m23})")
                logger.info("[Skin]   skinMatrix (joint*bind): t=(${skinMatrix.m03},${skinMatrix.m13},${skinMatrix.m23})")
            }
            debugCount++
        }

        return cache
    }
}
