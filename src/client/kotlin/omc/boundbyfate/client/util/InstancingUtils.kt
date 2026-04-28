package omc.boundbyfate.client.util

import omc.boundbyfate.client.models.internal.rendering.ModelInstancingBackend
import omc.boundbyfate.client.models.internal.rendering.PipelineRenderer
import omc.boundbyfate.client.models.internal.rendering.SubmittedInstance
import omc.boundbyfate.client.models.internal.rendering.VanillaInstancingBackend

/**
 * Entity info for instanced rendering (Iris/Oculus compatibility).
 */
data class InstancingEntityInfo(
    val entity: Int = 0,
    val blockEntity: Int = 0,
    val item: Int = 0,
)

/**
 * The active instancing backend. Defaults to vanilla.
 */
var instancingBackend: ModelInstancingBackend = VanillaInstancingBackend

/**
 * Per-render-call entity info for instancing.
 */
val RenderContext.instancingEntityInfo: InstancingEntityInfo
    get() = InstancingEntityInfo()

// Re-export RenderContext for convenience
typealias RenderContext = omc.boundbyfate.client.models.internal.rendering.RenderContext
