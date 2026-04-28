package omc.boundbyfate.client.models.internal.rendering

import omc.boundbyfate.client.models.internal.MatrixGetter
import omc.boundbyfate.client.models.internal.SkinGetter
import omc.boundbyfate.client.models.internal.VisibilityGetter

interface MeshRenderer {
    fun init()
    fun setupPipeline(
        pipeline: RenderPipeline,
        skinGetter: SkinGetter,
        matrixGetter: MatrixGetter,
        visibilityGetter: VisibilityGetter
    )
    fun destroy()
}


