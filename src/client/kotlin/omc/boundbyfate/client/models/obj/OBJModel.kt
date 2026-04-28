package omc.boundbyfate.client.models.obj

import net.minecraft.resources.ResourceLocation
import omc.boundbyfate.client.models.internal.AnimatedModel
import omc.boundbyfate.client.models.internal.manager.ModelLoader
import omc.boundbyfate.client.models.internal.manager.ModelSide

object ObjModelLoader: ModelLoader {
    override val supportedFormats: Set<String>
        get() = setOf("obj")

    override suspend fun load(location: ResourceLocation, side: ModelSide): AnimatedModel {
        return AnimatedModel(OBJModel(location, null).toInternalModel())
    }

}


