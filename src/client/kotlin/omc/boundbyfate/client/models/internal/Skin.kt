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
    private var totalFrames = 0
    private val logger = LoggerFactory.getLogger(Skin::class.java)

    fun compute(globalRoot: Mat4f, jointGetter: Map<Int, RuntimeNode>): Array<Mat4f> {
        totalFrames++
        val shouldLog = debugCount < 3 || (totalFrames % 30 == 0 && debugCount < 20)
        
        // GLTF skinning formula: skinMatrix = inverse(globalMesh) * globalJoint * inverseBindMatrix
        // globalRoot = globalMatrix ноды с мешем (нода 22)
        // Нужно преобразовать joint из мирового пространства в пространство меша
        val inverseRoot = MutableMat4f(globalRoot)
        inverseRoot.invert()
        
        for ((i, id) in jointsIds.withIndex()) {
            val jointGlobalMatrix = MutableMat4f(jointGetter[id]!!.globalMatrix)
            val bindMatrix = MutableMat4f(inverseBindMatrices[i])
            // Преобразуем joint из мирового пространства в пространство меша
            val jointInMeshSpace = MutableMat4f(inverseRoot).mul(jointGlobalMatrix)
            // Применяем inverseBindMatrix
            val skinMatrix = MutableMat4f(jointInMeshSpace).mul(bindMatrix)
            cache[i] = skinMatrix.transpose()
        }

        if (shouldLog) {
            logger.info("[Skin] Computing skin matrices, frame=$totalFrames")
            debugCount++
        }

        return cache
    }
}
