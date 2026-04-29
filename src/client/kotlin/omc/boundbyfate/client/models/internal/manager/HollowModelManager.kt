package omc.boundbyfate.client.models.internal.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.minecraft.client.MinecraftClient
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import net.minecraft.util.profiler.Profiler
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import omc.boundbyfate.client.models.bedrock.BedrockModelLoader
import omc.boundbyfate.client.models.fbx.FbxModelLoader
import omc.boundbyfate.client.models.gltf.GltfModelLoader
import omc.boundbyfate.client.models.internal.AnimatedModel
import omc.boundbyfate.client.models.obj.ObjModelLoader
import omc.boundbyfate.client.render.textures.GlTexture
import omc.boundbyfate.client.util.stream
import omc.boundbyfate.client.util.scopeAsync
import omc.boundbyfate.client.util.rl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

object HollowModelManager : IdentifiableResourceReloadListener {

    private val models = ConcurrentHashMap<Identifier, MutableStateFlow<AnimatedModel>>()
    private val indexedModels = ConcurrentHashMap.newKeySet<Identifier>()
    var glProgramSkinning = -1
    var glProgramMorphing = -1

    /** The lightmap texture used for entity rendering. */
    val lightTexture: net.minecraft.client.texture.AbstractTexture
        get() = MinecraftClient.getInstance().textureManager.getTexture(
            net.minecraft.util.Identifier("minecraft", "dynamic/light_map_1")
        )

    private val loaders: List<ModelLoader> = listOf(
        GltfModelLoader,
        ObjModelLoader,
        FbxModelLoader,
        BedrockModelLoader
    )

    override fun getFabricId(): Identifier = Identifier("boundbyfate-core", "model_manager")

    override fun reload(
        synchronizer: net.minecraft.resource.ResourceReloader.Synchronizer,
        manager: ResourceManager,
        prepareProfiler: Profiler,
        applyProfiler: Profiler,
        executor: Executor,
        syncExecutor: Executor
    ): CompletableFuture<Void> {
        return CompletableFuture.supplyAsync({
            val indexed = discoverIndexedModels(manager)
            indexedModels.clear()
            indexedModels.addAll(indexed)
            val targets = ModelReloadCoordinator.reloadTargets(models.keys, indexed)

            runBlocking(Dispatchers.IO) {
                targets.map { location ->
                    async { location to prepareModelUpdate(manager, location) }
                }.awaitAll().toMap()
            }
        }, executor).thenCompose { prepared ->
            synchronizer.whenPrepared<Map<Identifier, PreparedModelUpdate<AnimatedModel>>>(prepared)
        }.thenAcceptAsync({ prepared ->
            prepared.forEach { (location, update) ->
                publish(location, models.computeIfAbsent(location) { MutableStateFlow(AnimatedModel.EMPTY) }, update)
            }
        }, syncExecutor)
    }

    private fun loadIntoFlow(location: Identifier, flow: MutableStateFlow<AnimatedModel>) {
        scopeAsync {
            try {
                val loaded = loadModel(location)
                publish(location, flow, PreparedModelUpdate(exists = true, loaded = Result.success(loaded)))
            } catch (e: Exception) {
                org.apache.logging.log4j.LogManager.getLogger().error("Can't reload model $location", e)
            }
        }
    }

    fun getOrCreate(location: Identifier): StateFlow<AnimatedModel> {
        return models.computeIfAbsent(location) {
            val flow = MutableStateFlow(AnimatedModel.EMPTY)
            loadIntoFlow(location, flow)
            flow
        }
    }

    suspend fun loadModel(location: Identifier): AnimatedModel {
        val extension = location.path.substringAfter('.', "")
        val loader = loaders.find { extension in it.supportedFormats }
            ?: error("No suitable model loader found for format .$extension")
        return loader.load(location)
    }

    private fun publish(
        location: Identifier,
        flow: MutableStateFlow<AnimatedModel>,
        update: PreparedModelUpdate<AnimatedModel>,
    ) {
        val swap = ModelReloadCoordinator.resolveSwap(flow.value, update, AnimatedModel.EMPTY)
        flow.value = swap.next
        swap.retired?.let(::destroyLater)

        if (!update.exists && location !in indexedModels) {
            models.remove(location, flow)
        }
    }

    private suspend fun prepareModelUpdate(
        manager: ResourceManager,
        location: Identifier,
    ): PreparedModelUpdate<AnimatedModel> {
        if (!manager.getResource(location).isPresent) {
            return PreparedModelUpdate(exists = false)
        }
        return PreparedModelUpdate(
            exists = true,
            loaded = runCatching { loadModel(location) }.onFailure {
                org.apache.logging.log4j.LogManager.getLogger().error("Can't reload model $location", it)
            }
        )
    }

    private fun discoverIndexedModels(manager: ResourceManager): Set<Identifier> {
        val supportedFormats = loaders.flatMap { it.supportedFormats }.toSet()
        return manager.findResources("models") { path ->
            path.path.substringAfter('.') in supportedFormats
        }.keys.filter { manager.getResource(it.withPath(it.path + ".hemeta")).isPresent }.toSet()
    }

    private fun destroyLater(model: AnimatedModel) {
        if (RenderSystem.isOnRenderThread()) {
            model.destroy()
        } else {
            RenderSystem.recordRenderCall(model::destroy)
        }
    }

    private fun createSkinningProgramGL33() {
        var glShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER)
        GL20.glShaderSource(
            glShader,
            "hollowengine:shaders/core/gltf_skinning.vsh".rl.stream.readBytes().decodeToString()
        )
        GL20.glCompileShader(glShader)

        glProgramSkinning = GL20.glCreateProgram()
        GL20.glAttachShader(glProgramSkinning, glShader)
        GL20.glDeleteShader(glShader)
        GL30.glTransformFeedbackVaryings(
            glProgramSkinning, arrayOf<CharSequence>("outPosition", "outNormal", "outTangent"), GL30.GL_SEPARATE_ATTRIBS
        )
        GL20.glLinkProgram(glProgramSkinning)

        glShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER)
        GL20.glShaderSource(
            glShader,
            "hollowengine:shaders/core/gltf_morphing.vsh".rl.stream.readBytes().decodeToString()
        )
        GL20.glCompileShader(glShader)

        glProgramMorphing = GL20.glCreateProgram()
        GL20.glAttachShader(glProgramMorphing, glShader)
        GL20.glDeleteShader(glShader)
        GL30.glTransformFeedbackVaryings(
            glProgramMorphing, arrayOf<CharSequence>("outPosition", "outNormal", "outTangent"), GL30.GL_SEPARATE_ATTRIBS
        )
        GL20.glLinkProgram(glProgramMorphing)
    }

    fun initialize() {
        val textureManager = MinecraftClient.getInstance().textureManager

        val currentTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

        val defaultColorMap = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, defaultColorMap)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
            create(byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1)))
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)

        val defaultNormalMap = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, defaultNormalMap)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
            create(byteArrayOf(-128, -128, -1, -1, -128, -128, -1, -1, -128, -128, -1, -1, -128, -128, -1, -1)))
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)

        val defaultSpecularMap = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, defaultSpecularMap)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
            create(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)

        textureManager.registerTexture("boundbyfate-core:default_color_map".rl, GlTexture(defaultColorMap))
        textureManager.registerTexture("boundbyfate-core:default_normal_map".rl, GlTexture(defaultNormalMap))
        textureManager.registerTexture("boundbyfate-core:default_specular_map".rl, GlTexture(defaultSpecularMap))

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture)

        createSkinningProgramGL33()
    }

    fun supports(location: Identifier): Boolean {
        val extension = location.path.substringAfter('.', "")
        return loaders.any { extension in it.supportedFormats }
    }

    val allModels get() = indexedModels + models.keys
}

interface ModelLoader {
    val supportedFormats: Set<String>
    suspend fun load(location: Identifier, side: ModelSide = ModelSide.CLIENT): AnimatedModel
}

enum class ModelSide { CLIENT, SERVER }

fun create(data: ByteArray) = create(data, 0, data.size)

fun create(data: ByteArray?, offset: Int, length: Int): ByteBuffer {
    val byteBuffer = ByteBuffer.allocateDirect(length)
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.put(data, offset, length)
    byteBuffer.position(0)
    return byteBuffer
}
