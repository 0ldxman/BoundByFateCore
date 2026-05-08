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
            val legIdx = jointsIds.indexOf(19)
            if (legIdx >= 0) {
                val jNode = jointGetter[19]
                val gm = jNode?.globalMatrix
                
                // Вычисляем skinMatrix до транспозиции для правильного лога
                val jointGlobalMatrix = MutableMat4f(jointGetter[19]!!.globalMatrix)
                val bindMatrix = MutableMat4f(inverseBindMatrices[legIdx])
                val jointInMeshSpace = MutableMat4f(inverseRoot).mul(jointGlobalMatrix)
                val skinMatrix = MutableMat4f(jointInMeshSpace).mul(bindMatrix)
                
                // translation в kool Mat4f хранится в m03, m13, m23 (column-major)
                logger.info("[Skin] totalFrame=$totalFrames debugCount=$debugCount joint=19(LeftLeg)")
                logger.info("[Skin]   globalMatrix[19]: t=(${gm?.m03},${gm?.m13},${gm?.m23})")
                logger.info("[Skin]   jointInMeshSpace: t=(${jointInMeshSpace.m03},${jointInMeshSpace.m13},${jointInMeshSpace.m23})")
                logger.info("[Skin]   skinMatrix (final): t=(${skinMatrix.m03},${skinMatrix.m13},${skinMatrix.m23})")
            }
            debugCount++
        }

        return cache
    }
}
