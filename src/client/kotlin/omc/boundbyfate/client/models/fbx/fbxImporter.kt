package omc.boundbyfate.client.models.fbx

import net.minecraft.resources.ResourceLocation
import omc.boundbyfate.client.models.internal.AnimatedModel
import omc.boundbyfate.client.models.internal.manager.ModelLoader
import omc.boundbyfate.client.models.internal.manager.ModelSide
import omc.boundbyfate.client.models.util.startsWith
import omc.boundbyfate.client.util.stream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FbxModelLoader: ModelLoader {
    override val supportedFormats = setOf("fbx")

    override suspend fun load(location: ResourceLocation, side: ModelSide): AnimatedModel {
        return AnimatedModel(import(location).convert(location))
    }

    fun import(location: ResourceLocation): Document {
        val bytes = ByteBuffer.wrap(location.stream.readBytes()).order(ByteOrder.nativeOrder())

        val tokens = ArrayList<Token>()
        buffer = bytes

        var isBinary = false
        if (bytes.startsWith("Kaydara FBX Binary")) {
            isBinary = true
            tokenizeBinary(tokens, bytes)
        } else tokenize(tokens, bytes)

        val parser = Parser(tokens, isBinary)

        return Document(parser)
    }
}

lateinit var buffer: ByteBuffer



