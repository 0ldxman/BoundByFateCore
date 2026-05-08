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
        val inverseRoot = MutableMat4f(globalRoot)
        inverseRoot.invert()

        for ((i, id) in jointsIds.withIndex()) {
            val jointGlobalMatrix = MutableMat4f(jointGetter[id]!!.globalMatrix)
            val bindMatrix = MutableMat4f(inverseBindMatrices[i])
            val skinMatrix = MutableMat4f(jointGlobalMatrix).mul(bindMatrix)
            cache[i] = MutableMat4f(inverseRoot).mul(skinMatrix).transpose()
        }

        if (debugCount < 3) {
            val legIdx = jointsIds.indexOf(19)
            if (legIdx >= 0) {
                val m = cache[legIdx]
                val jNode = jointGetter[19]
                val gm = jNode?.globalMatrix
                val parentNode = jNode?.parent
                val parentGm = (parentNode as? omc.boundbyfate.client.models.internal.v2.RuntimeNode)?.globalMatrix
                // translation в kool Mat4f хранится в m03, m13, m23 (column-major)
                logger.info("[Skin] frame=$debugCount joint=19(LeftLeg) skinMatrix: t=(${m.m03},${m.m13},${m.m23}) r00=${m.m00} r11=${m.m11}")
                logger.info("[Skin]   globalMatrix[19]: t=(${gm?.m03},${gm?.m13},${gm?.m23})")
                logger.info("[Skin]   globalMatrix[parent]: t=(${parentGm?.m03},${parentGm?.m13},${parentGm?.m23})")
                logger.info("[Skin]   transform[19].translation: ${jNode?.transform?.translation}")
                logger.info("[Skin]   transform[19].rotation: ${jNode?.transform?.rotation}")
                logger.info("[Skin]   globalRoot: t=(${globalRoot.m03},${globalRoot.m13},${globalRoot.m23})")
            }
            debugCount++
        }

        return cache
    }
}
