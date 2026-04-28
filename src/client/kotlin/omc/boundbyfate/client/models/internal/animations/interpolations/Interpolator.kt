package omc.boundbyfate.client.models.internal.animations.interpolations

import omc.boundbyfate.client.util.molang.MolangContext

interface Interpolator<T> {
    val duration: Float

    fun compute(time: Float, context: MolangContext = MolangContext.EMPTY): T
}

abstract class StaticInterpolator<T>(val keys: FloatArray, val values: Array<T>) : Interpolator<T> {
    override val duration = keys.lastOrNull() ?: 0f

    override fun compute(time: Float, context: MolangContext): T = compute(time)

    protected abstract fun compute(time: Float): T

    protected val Float.animIndex: Int
        get() {
            val index = java.util.Arrays.binarySearch(keys, this)
            return if (index >= 0) index else (-index - 2).coerceAtLeast(0)
        }
}


