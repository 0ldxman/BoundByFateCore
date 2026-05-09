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
        val shouldLog = totalFrames == 1 || totalFrames == 60
        
        // Получаем и инвертируем глобальную матрицу узла модели
        val inverseRoot = MutableMat4f(globalRoot)
        inverseRoot.invert()

        // Проходим по всем суставам
        for ((i, id) in jointsIds.withIndex()) {
            val jointGlobalMatrix = MutableMat4f(jointGetter[id]!!.globalMatrix)
            val bindMatrix = MutableMat4f(inverseBindMatrices[i])
            val skinMatrix = MutableMat4f(jointGlobalMatrix).mul(bindMatrix)
            cache[i] = MutableMat4f(inverseRoot).mul(skinMatrix).transpose() // Транспозируем для передачи в шейдер
            
            if (shouldLog && (id == 19 || id == 20)) {
                logger.info("[Skin] joint=$id frame=$totalFrames translation=(${jointGlobalMatrix.m03},${jointGlobalMatrix.m13},${jointGlobalMatrix.m23})")
            }
        }

        return cache
    }
}
