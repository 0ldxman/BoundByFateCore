package omc.boundbyfate.client.kool.minecraft

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.Image
import de.fabmax.kool.modules.ui2.ImageScope
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.image
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture2d
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import omc.boundbyfate.client.util.stream

object ImageManager : SimpleSynchronousResourceReloadListener {
    private val IMAGES = Object2ObjectOpenHashMap<Identifier, Texture2d>()

    override fun getFabricId(): Identifier = Identifier("boundbyfate-core", "image_manager")

    fun load(location: Identifier, mode: SamplerMode = SamplerMode.NEAREST): Texture2d =
        IMAGES.getOrPut(location) {
            Texture2d(
                mipMapping = MipMapping.Off,
                samplerSettings = SamplerSettings().let {
                    if (mode == SamplerMode.NEAREST) it.nearest() else it.linear()
                }
            ) {
                Assets.loadImage2d(location.toString()).getOrThrow()
            }
        }

    override fun reload(manager: ResourceManager) {
        IMAGES.forEach { location, image ->
            image.uploadLazy {
                Assets.loadImage2d(location.toString()).getOrThrow()
            }
        }
    }
}

fun UiScope.Image(
    location: Identifier,
    mode: SamplerMode = SamplerMode.NEAREST,
    block: ImageScope.() -> Unit = {}
) = Image {
    modifier.image(ImageManager.load(location, mode))
    block()
}

enum class SamplerMode {
    NEAREST, LINEAR
}
