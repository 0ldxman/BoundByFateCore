package omc.boundbyfate.client.models.internal.controller

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.TrsTransformF
import omc.boundbyfate.client.models.internal.animations.Animation
import org.slf4j.LoggerFactory
import kotlin.math.absoluteValue

class AnimationInstance(private val animation: Animation) {
    val name = animation.name
    private var reversed = false

    var wrapMode = WrapMode.Once
    var overrides = Overrides(translation = false, rotation = false, scale = false)
    var priority = 0
    var speed = 1f
    var duration: Float
        set(value) { animation.duration = value }
        get() = animation.duration

    var time = 0f
    var weight = 0f

    private var debugFrameCount = 0

    fun update(model: Map<Int, TrsTransformF>, dt: Float) {
        if (weight == 0f) return
        time += speed * dt
        updatePlaying(model)
    }

    private fun updatePlaying(model: Map<Int, TrsTransformF>) {
        applyWrapMode()

        val time = if (reversed) duration - time else time

        val doDebug = debugFrameCount < 5
        if (doDebug) {
            logger.info("[AnimInst] '${animation.name}' frame=$debugFrameCount time=$time weight=$weight nodes=${animation.nodes.size}")
        }

        animation.nodes.forEach { (node, channels) ->
            val transform = model[node] ?: return@forEach

            channels.translation?.let {
                val delta = it.compute(time)
                
                // Фильтруем аномально большие translation дельты (>0.5 блока по любой оси)
                // Это защита от неправильных keyframes в GLTF для container нод
                val isAnomalous = delta.x.absoluteValue > 0.5f || delta.y.absoluteValue > 0.5f || delta.z.absoluteValue > 0.5f
                
                if (isAnomalous) {
                    if (doDebug) logger.warn("[AnimInst]   node=$node SKIPPED anomalous translation delta=(${delta.x},${delta.y},${delta.z})")
                    return@let
                }
                
                val translation = Vec3f.ZERO.mix(delta, weight)
                if (doDebug) logger.info("[AnimInst]   node=$node translation delta=(${delta.x},${delta.y},${delta.z}) applied=(${translation.x},${translation.y},${translation.z})")
                if (overrides.translation) transform.translation.set(translation)
                else transform.translate(translation)
            }
            channels.rotation?.let {
                val delta = it.compute(time)
                val rotation = QuatF.IDENTITY.mix(delta, weight)
                if (doDebug) logger.info("[AnimInst]   node=$node rotation delta=(${delta.x},${delta.y},${delta.z},${delta.w}) applied=(${rotation.x},${rotation.y},${rotation.z},${rotation.w})")
                if (overrides.rotation) transform.rotation.set(rotation)
                else transform.rotate(rotation)
            }
            channels.scale?.let {
                val delta = it.compute(time)
                val scale = Vec3f.ONES.mix(delta, weight)
                if (doDebug) logger.info("[AnimInst]   node=$node scale delta=(${delta.x},${delta.y},${delta.z})")
                if (overrides.scale) transform.scale.set(scale)
                else transform.scale(scale)
            }
        }

        if (doDebug) debugFrameCount++
    }

    private fun applyWrapMode() {
        when (wrapMode) {
            WrapMode.Once -> { if (time >= animation.duration) weight = 0f }
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

    companion object {
        private val logger = LoggerFactory.getLogger(AnimationInstance::class.java)
    }
}
