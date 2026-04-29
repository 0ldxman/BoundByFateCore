package omc.boundbyfate.client.models.internal.controller

import kotlinx.coroutines.*
import omc.boundbyfate.client.models.internal.v2.ModelAttachment

class AnimationSystem(val model: ModelAttachment) {

    val dispatcher = AnimationDispatcher("Animation System")
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val transitionJobs = HashMap<String, Job>()

    fun update(dt: Float) {
        dispatcher.update(dt)
    }

    fun onUpdate(action: suspend () -> Unit) {
        scope.launch {
            while (isActive) {
                action()
                dispatcher.awaitNextFrame()
            }
        }
    }

    /**
     * Transitions from one animation to another over [duration] seconds.
     * Uses a simple linear blend.
     */
    suspend fun transition(
        from: String? = null,
        to: String? = null,
        duration: Float = 0.33f,
        wrapMode: WrapMode = WrapMode.Loop,
    ) {
        val original = from?.let { model.animations[it] }
        val target = to?.let { model.animations[it] }

        val key = "${from ?: ""}->${to ?: ""}"
        transitionJobs.remove(key)?.cancel()

        target?.time = 0f
        target?.wrapMode = wrapMode

        val job = scope.launch {
            val steps = (duration * 60f).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                target?.weight = t
                original?.weight = 1f - t
                dispatcher.awaitNextFrame()
            }
            target?.weight = 1f
            original?.weight = 0f
        }
        transitionJobs[key] = job
    }
}
