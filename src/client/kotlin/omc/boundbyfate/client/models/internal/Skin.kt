package omc.boundbyfate.client.models.internal

import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableMat4f
import omc.boundbyfate.client.models.internal.v2.RuntimeNode

class Skin(
    val jointsIds: List<Int>,
    val inverseBindMatrices: Array<Mat4f>,
) {
    private val cache: Array<Mat4f> = Array(jointsIds.size) { MutableMat4f() }

    private var skinLogCount = 0

    fun compute(globalRoot: Mat4f, jointGetter: Map<Int, RuntimeNode>): Array<Mat4f> {
        // Получаем и инвертируем глобальную матрицу узла модели
        val inverseRoot = MutableMat4f(globalRoot)
        inverseRoot.invert()

        // Проходим по всем суставам
        for ((i, id) in jointsIds.withIndex()) {
            val jointGlobalMatrix = MutableMat4f(jointGetter[id]!!.globalMatrix)
            val bindMatrix = MutableMat4f(inverseBindMatrices[i])
            val skinMatrix = MutableMat4f(jointGlobalMatrix).mul(bindMatrix)
            cache[i] = MutableMat4f(inverseRoot).mul(skinMatrix).transpose()
        }

        if (skinLogCount++ < 2) {
            val m = cache[0] as? MutableMat4f
            org.apache.logging.log4j.LogManager.getLogger().info(
                "[Skin] compute: joints=${jointsIds.size}, cache[0] t=(${m?.m30?.let { Math.round(it*1000f)/1000f }},${m?.m31?.let { Math.round(it*1000f)/1000f }},${m?.m32?.let { Math.round(it*1000f)/1000f }})"
            )
        }

        return cache
    }
}




