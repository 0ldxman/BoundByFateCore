package omc.boundbyfate.client.models.internal.rendering

import org.joml.Matrix3f
import org.joml.Matrix4f
import omc.boundbyfate.client.util.InstancingEntityInfo
import omc.boundbyfate.client.util.instancingBackend

data class SubmittedInstance(
    val modelView: Matrix4f,
    val normal: Matrix3f,
    val overlay: Int,
    val light: Int,
    val sortKey: Float,
    val entityInfo: InstancingEntityInfo = InstancingEntityInfo(),
)

object InstanceBatchManager {
    private val batches = LinkedHashMap<PipelineRenderer, MutableList<SubmittedInstance>>()

    fun canBatch(): Boolean = instancingBackend.canBatch()

    fun submit(renderer: PipelineRenderer, instance: SubmittedInstance) {
        batches.getOrPut(renderer) { ArrayList() }.add(instance)
    }

    fun flush() {
        if (batches.isEmpty()) return
        try {
            instancingBackend.flush(batches)
        } finally {
            clear()
        }
    }

    fun clear() {
        batches.clear()
    }
}



