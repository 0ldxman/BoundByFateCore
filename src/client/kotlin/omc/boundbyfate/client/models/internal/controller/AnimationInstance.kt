package omc.boundbyfate.client.models.internal.controller

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.TrsTransformF
import omc.boundbyfate.client.models.internal.animations.Animation

class AnimationInstance(private val animation: Animation) {
    val name = animation.name
    private var reversed = false
    private var logCount = 0

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
        if (logCount++ < 3) {
            org.apache.logging.log4j.LogManager.getLogger().info(
                "[AnimationInstance] update: name=$name, weight=$weight, time=$time, dt=$dt, nodes=${animation.nodes.size}"
            )
        }
        updatePlaying(model)
    }

    private fun updatePlaying(model: Map<Int, TrsTransformF>) {
        applyWrapMode()

        val time = if (reversed) duration - time else time

        var appliedCount = 0
        animation.nodes.forEach { (node, channels) ->
            val transform = model[node] ?: return@forEach
            appliedCount++

            channels.translation?.let {
                val translation = Vec3f.Companion.ZERO.mix(it.compute(time), weight)
                if (logCount <= 3 && appliedCount == 1) {
                    org.apache.logging.log4j.LogManager.getLogger().info(
                        "[AnimationInstance] node=$node translation delta=(${r(translation.x)},${r(translation.y)},${r(translation.z)}) at time=$time"
                    )
                }
                if (overrides.translation) transform.translation.set(translation)
                else transform.translation.set(transform.translation + translation)
            }
            channels.rotation?.let {
                val rotation = QuatF.Companion.IDENTITY.mix(it.compute(time), weight)
                if (logCount <= 3) {
                    org.apache.logging.log4j.LogManager.getLogger().info(
                        "[AnimationInstance] node=$node rotation=(${r(rotation.x)},${r(rotation.y)},${r(rotation.z)},${r(rotation.w)}) at time=$time"
                    )
                }
                if (overrides.rotation) transform.rotation.set(rotation)
                else transform.rotation.set(transform.rotation.mul(rotation, de.fabmax.kool.math.MutableQuatF()))
            }
            channels.scale?.let {
                val scale = Vec3f.Companion.ONES.mix(it.compute(time), weight)
                if (overrides.scale) transform.scale.set(scale)
                else transform.scale.set(transform.scale * scale)
            }
        }
    }

    private fun r(f: Float) = Math.round(f * 1000f) / 1000f

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


