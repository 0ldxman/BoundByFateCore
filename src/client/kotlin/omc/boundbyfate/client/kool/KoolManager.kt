package omc.boundbyfate.client.kool

import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.util.*
import de.fabmax.kool.scene.Scene
import omc.boundbyfate.client.kool.minecraft.ImageManager
import omc.boundbyfate.client.kool.minecraft.MCAssetLoader
import omc.boundbyfate.client.kool.minecraft.SamplerMode
import net.minecraft.util.Identifier
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object KoolManager {
    val LOGGER: Logger = LogManager.getLogger()

    val context: MCKoolContext

    init {
        Log.printer = LogPrinter { level, tag, message ->
            LOGGER.info("[$level] $tag: $message")
        }
        KoolSystem.initialize(KoolConfigJvm(defaultAssetLoader = MCAssetLoader))

        context = MCKoolContext()
    }

    fun loadTexture(texture: Identifier, mode: SamplerMode = SamplerMode.NEAREST) {
        ImageManager.load(texture, mode)
    }

    fun attachScene(scene: Scene) {
        context.scenes.stageAdd(scene, 0)
    }
}
