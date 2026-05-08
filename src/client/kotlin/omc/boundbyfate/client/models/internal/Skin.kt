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
            val armIdx = jointsIds.indexOf(15)
            if (legIdx >= 0) {
                val m = cache[legIdx]
                val jNode = jointGetter[19]
                val gm = jNode?.globalMatrix
                logger.info("[Skin] frame=$debugCount joint=19(LeftLeg) idx=$legIdx skinMatrix: t=(${m.m30},${m.m31},${m.m32}) r00=${m.m00} r11=${m.m11}")
                logger.info("[Skin]   globalMatrix[19]: t=(${gm?.m30},${gm?.m31},${gm?.m32}) r00=${gm?.m00} r11=${gm?.m11}")
                logger.info("[Skin]   transform[19].translation: ${jNode?.transform?.translation}")
                logger.info("[Skin]   transform[19].rotation: ${jNode?.transform?.rotation}")
            }
            if (armIdx >= 0) {
                val m = cache[armIdx]
                logger.info("[Skin] frame=$debugCount joint=15(LeftArm) idx=$armIdx skinMatrix: t=(${m.m30},${m.m31},${m.m32}) r00=${m.m00} r11=${m.m11}")
            }
            debugCount++
        }

        return cache
    }
}
