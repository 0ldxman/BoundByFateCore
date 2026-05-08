package omc.boundbyfate.client.models.internal.controller

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.TrsTransformF
import omc.boundbyfate.client.models.internal.animations.Animation

class AnimationInstance(private val animation: Animation) {
    val name = animation.name
    private var reversed = false

    var wrapMode = WrapMode.Once
    var overrides = Overrides(translation = false, rotation = false, scale = false)
    var priority = 0
    var speed = 1f
    var duration: Float
        set(value) {
            animation.duration = value
        }
        get() = animation.duration

    var time = 0f
    var weight = 0f

    fun update(model: Map<Int, TrsTransformF>, dt: Float) {
        if (weight == 0f) return
        time += speed * dt
        updatePlaying(model)
    }

    private fun updatePlaying(model: Map<Int, TrsTransformF>) {
        applyWrapMode()

        val time = if (reversed) duration - time else time

        animation.nodes.forEach { (node, channels) ->
            val transform = model[node] ?: return@forEach

            // Читаем текущие значения (уже сброшены в baseTransform)
            val curT = transform.translation
            val curR = transform.rotation
            val curS = transform.scale

            val newT = channels.translation?.let { Vec3f.ZERO.mix(it.compute(time), weight) }
            val newR = channels.rotation?.let { QuatF.IDENTITY.mix(it.compute(time), weight) }
            val newS = channels.scale?.let { Vec3f.ONES.mix(it.compute(time), weight) }

            // Пересобираем transform полностью чтобы гарантированно инвалидировать кеш matrixF
            transform.setIdentity()
            transform.translate(if (newT != null) curT + newT else curT)
            transform.rotate(if (newR != null) curR.mul(newR, de.fabmax.kool.math.MutableQuatF()) else curR)
            transform.scale(if (newS != null) curS * newS else curS)
        }
    }

    private fun applyWrapMode() {
        when (wrapMode) {
            WrapMode.Once -> {
                if (time >= animation.duration) {
                    weight = 0f
                }
            }
            WrapMode.Loop -> {
                if (time >= animation.duration) time -= animation.duration
                else if (time < 0) time += animation.duration
            }
            WrapMode.ClampForever -> if (time >= animation.duration) time = animation.duration
            WrapMode.PingPong -> {
                if (time >= animation.duration) {
                    reversed = !reversed
                    time -= animation.duration
                }
            }
        }
        time = time.coerceIn(0f, animation.duration)
    }

    data class Overrides(var translation: Boolean, var rotation: Boolean, var scale: Boolean)
}
