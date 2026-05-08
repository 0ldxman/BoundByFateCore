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

    private fun r(f: Float?) = f?.let { Math.round(it * 1000f) / 1000f } ?: 0f

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

        skinLogCount++
        if (skinLogCount in 50..55) {
            val m0 = cache[0] as? MutableMat4f
            val m1 = cache[1] as? MutableMat4f
            // Log diagonal elements to detect rotation changes
            org.apache.logging.log4j.LogManager.getLogger().info(
                "[Skin] compute #$skinLogCount: cache[0] m00=${r(m0?.m00)} m11=${r(m0?.m11)} m22=${r(m0?.m22)} t=(${r(m0?.m30)},${r(m0?.m31)},${r(m0?.m32)})"
            )
        }

        return cache
    }
}




