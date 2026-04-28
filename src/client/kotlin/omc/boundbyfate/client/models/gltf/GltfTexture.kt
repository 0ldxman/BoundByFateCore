package omc.boundbyfate.client.models.gltf

import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import omc.boundbyfate.client.util.stream
import omc.boundbyfate.client.util.rl
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*

@Serializable
data class GltfTexture(
    val sampler: Int = -1,
    val source: Int = 0,
    val name: String? = null,
) {
    @Transient
    lateinit var imageRef: GltfImage

    @Transient
    var samplerRef: GltfSampler? = null

    @Transient
    private lateinit var createdTex: NativeImageBackedTexture
    @Transient
    private var isRegistered = false

    fun makeTexture(location: Identifier): Identifier {
        val uri = imageRef.uri
        val texName = if (uri != null && !uri.startsWith("data:", true)) {
            uri
        } else {
            val folderPath = location.path.substringBefore(".")
            "${location.namespace}:$folderPath/unnamed_texture_$source"
        }

        if (!this::createdTex.isInitialized) {
            if (uri != null && imageRef.bufferViewRef == null) {
                fun retrieveFile(path: String): InputStream {
                    if (path.startsWith("data:application/octet-stream;base64,")) {
                        return Base64.getDecoder().wrap(path.substring(37).byteInputStream())
                    }
                    if (path.startsWith("data:image/png;base64,")) {
                        return Base64.getDecoder().wrap(path.substring(22).byteInputStream())
                    }
                    return path.rl.stream
                }
                createdTex = NativeImageBackedTexture(NativeImage.read(retrieveFile(uri)))
            } else {
                createdTex = NativeImageBackedTexture(
                    NativeImage.read(ByteArrayInputStream(imageRef.bufferViewRef!!.getData().toArray()))
                )
            }
        }

        val textureId = texName.lowercase().rl
        if (!isRegistered) {
            isRegistered = true
            if (RenderSystem.isOnRenderThread()) {
                MinecraftClient.getInstance().textureManager.registerTexture(textureId, createdTex)
            } else {
                RenderSystem.recordRenderCall {
                    MinecraftClient.getInstance().textureManager.registerTexture(textureId, createdTex)
                }
            }
        }

        return textureId
    }

    @Serializable
    data class Info(
        val index: Int,
        val strength: Float = 1f,
        val texCoord: Int = 0,
        val scale: Float = 1f,
    ) {
        fun getTexture(gltfFile: GltfFile, location: Identifier): Identifier {
            return gltfFile.textures[index].makeTexture(location)
        }
    }
}
