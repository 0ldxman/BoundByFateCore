package omc.boundbyfate.client.models.internal.v2

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.util.Time
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.minecraft.client.MinecraftClient
import omc.boundbyfate.client.models.internal.AnimatedModel
import omc.boundbyfate.client.models.internal.Material
import omc.boundbyfate.client.models.internal.Primitive
import omc.boundbyfate.client.models.internal.controller.AnimationInstance
import omc.boundbyfate.client.models.internal.manager.HollowModelManager
import omc.boundbyfate.client.models.internal.rendering.ListRenderPipeline
import omc.boundbyfate.client.models.internal.rendering.RenderPipeline
import omc.boundbyfate.client.util.ShadowRenderingDetector
import omc.boundbyfate.client.util.coroutineScope
import omc.boundbyfate.client.util.rl

fun ModelAttachment(model: String) = ModelAttachment(HollowModelManager.getOrCreate(model.rl), null)
class ModelAttachment(val flow: StateFlow<AnimatedModel>, parent: Attachment?) : Attachment(parent) {
    private val rebuildLock = Any()
    private var modelState: AnimatedModel = flow.value
    private var runtimeNodes: List<RuntimeNode> = emptyList()
    private var runtimeAnimations: Animations = Animations(emptyMap())
    private var runtimeMaterials: Set<Material> = emptySet()
    private var nodeIdToNode: Map<Int, RuntimeNode> = emptyMap()
    private var nodeIdToTransform = emptyMap<Int, de.fabmax.kool.scene.TrsTransformF>()
    @Volatile
    private var compiledFor: AnimatedModel? = null
    @Volatile
    private var renderPipeline: ListRenderPipeline = ListRenderPipeline()
    @Volatile
    private var cachedBounds: Pair<Vec3f, Vec3f>? = null
    @Volatile
    private var cachedBoundsFrame = Int.MIN_VALUE

    val model get() = modelState.model
    val nodes get() = runtimeNodes
    val animations get() = runtimeAnimations
    val materials get() = runtimeMaterials
    val pipeline: RenderPipeline
        get() {
            ensureCompiled(flow.value)
            return renderPipeline
        }

    init {
        ensureCompiled(flow.value)
        flow.onEach { ensureCompiled(it) }.launchIn(MinecraftClient.getInstance().coroutineScope)
    }

    val triangles get() =
        model.walkNodes().sumOf {
            it.mesh?.primitives?.sumOf { it.positionsCount / 3 } ?: 0
        }

    val shapekeys get() =
        model.walkNodes().sumOf {
            it.mesh?.primitives?.sumOf { it.morphTargets.size } ?: 0
        }

    private val onUpdates = mutableListOf<ModelAttachment.() -> Unit>()
    private val onPostUpdates = mutableListOf<ModelAttachment.() -> Unit>()

    fun onUpdate(action: ModelAttachment.() -> Unit) {
        onUpdates.add(action)
    }

    fun onPostUpdate(action: ModelAttachment.() -> Unit) {
        onPostUpdates.add(action)
    }

    private fun ensureCompiled(animated: AnimatedModel) {
        if (compiledFor === animated) return

        synchronized(rebuildLock) {
            if (compiledFor === animated) return

            if (animated.model.isBlockBench) {
                transform.rotation.set(180f.deg, Vec3f.Y_AXIS)
            }

            modelState = animated
            configurePrimitiveRenderPaths(animated)
            runtimeNodes = model.scenes.getOrNull(model.scene)?.nodes?.map { RuntimeNode(it, this) } ?: emptyList()
            runtimeAnimations = Animations(model.animations.associate { it.name to AnimationInstance(it) })
            runtimeMaterials = model.materials
            nodeIdToNode = runtimeNodes.flatMap { it.walk() }.associateBy { it.definition.index }
            nodeIdToTransform = nodeIdToNode.mapValues { it.value.transform }
            renderPipeline = ListRenderPipeline().apply(this@ModelAttachment::collectCommands)
            cachedBounds = null
            cachedBoundsFrame = Int.MIN_VALUE
            compiledFor = animated
        }
    }

    private fun configurePrimitiveRenderPaths(animated: AnimatedModel) {
        val allPrimitives = animated.model.walkNodes().mapNotNull { it.mesh }.flatMap { it.primitives }.toList()
        val staticPrimitives = allPrimitives.filter { !it.hasSkinning && it.morphTargets.isEmpty() }

        val primitiveCount = staticPrimitives.size
        if (primitiveCount == 0) return

        val totalCubeCount = staticPrimitives.sumOf(Primitive::estimatedCubeCount)
        val averageCubesPerPrimitive = totalCubeCount.toFloat() / primitiveCount.toFloat()
        val preferBatching = primitiveCount >= MODEL_BATCHING_PRIMITIVE_THRESHOLD ||
            (primitiveCount >= MODEL_BATCHING_DENSE_PRIMITIVE_THRESHOLD && averageCubesPerPrimitive <= MODEL_BATCHING_DENSE_AVERAGE_CUBES) ||
            (primitiveCount >= MODEL_BATCHING_MIXED_PRIMITIVE_THRESHOLD &&
                totalCubeCount >= MODEL_BATCHING_TOTAL_CUBE_THRESHOLD &&
                averageCubesPerPrimitive <= MODEL_BATCHING_MIXED_AVERAGE_CUBES)

        val renderPath = if (preferBatching) Primitive.StaticRenderPath.BATCHING else Primitive.StaticRenderPath.PIPELINE
        staticPrimitives.forEach { it.setStaticRenderPath(renderPath) }
    }

    private fun update(dt: Float) {
        val transforms = nodeIdToTransform
        val indexedNodes = nodeIdToNode
        val currentAnimations = runtimeAnimations

        transforms.forEach { (key, value) ->
            val base = indexedNodes[key]?.definition?.baseTransform ?: return@forEach
            value.set(base)
        }

        onUpdates.forEach { it() }

        for (animation in currentAnimations) {
            animation.update(transforms, dt)
        }

        onPostUpdates.forEach { it() }
    }

    override fun collectCommands(pipeline: RenderPipeline) {
        super.collectCommands(pipeline)
        pipeline.onUpdate { update(if (ShadowRenderingDetector.isShadowRendering()) 0f else Time.deltaT) }
        runtimeNodes.forEach { it.collectCommands(pipeline) }
    }

    fun child(name: String): RuntimeNode {
        ensureCompiled(flow.value)
        return runtimeNodes.single { it.name == name }
    }

    fun findNode(name: String): RuntimeNode? {
        ensureCompiled(flow.value)
        return runtimeNodes.asSequence().flatMap { it.walk().asSequence() }.firstOrNull { it.name == name }
    }

    /**
     * Suspend-функция: ждёт пока модель загрузится и в ней появятся анимации.
     *
     * Используется в [omc.boundbyfate.client.models.internal.controller.AnimationSystem]
     * для безопасного запуска анимаций сразу после создания attachment —
     * когда модель ещё может быть [AnimatedModel.EMPTY].
     *
     * Поллинг с шагом 50мс — модели обычно грузятся за 1-3 кадра.
     * Таймаут 10 секунд — защита от бесконечного ожидания если модель не найдена.
     */
    suspend fun awaitAnimations(timeoutMs: Long = 10_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (runtimeAnimations.isEmpty() && System.currentTimeMillis() < deadline) {
            delay(50)
        }
    }

    fun calculateBoundsCached(frame: Int = Time.frameCount): Pair<Vec3f, Vec3f>? {
        if (cachedBoundsFrame == frame) return cachedBounds

        synchronized(rebuildLock) {
            if (cachedBoundsFrame == frame) return cachedBounds
            val bounds = calculateBoundsInternal()
            cachedBounds = bounds
            cachedBoundsFrame = frame
            return bounds
        }
    }

    private fun calculateBoundsInternal(): Pair<Vec3f, Vec3f>? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var hasBounds = false
        val source = MutableVec3f()
        val transformed = MutableVec3f()

        nodes.forEach { node ->
            node.walk().forEach { runtimeNode ->
                val matrix = runtimeNode.globalMatrix
                runtimeNode.definition.mesh?.primitives?.forEach { primitive ->
                    val localBounds = primitive.localBounds ?: return@forEach
                    val min = localBounds.first
                    val max = localBounds.second

                    fun update(x: Float, y: Float, z: Float) {
                        source.set(x, y, z)
                        matrix.transform(source, 1f, transformed)
                        minX = kotlin.math.min(minX, transformed.x)
                        minY = kotlin.math.min(minY, transformed.y)
                        minZ = kotlin.math.min(minZ, transformed.z)
                        maxX = kotlin.math.max(maxX, transformed.x)
                        maxY = kotlin.math.max(maxY, transformed.y)
                        maxZ = kotlin.math.max(maxZ, transformed.z)
                    }

                    update(min.x, min.y, min.z)
                    update(min.x, min.y, max.z)
                    update(min.x, max.y, min.z)
                    update(min.x, max.y, max.z)
                    update(max.x, min.y, min.z)
                    update(max.x, min.y, max.z)
                    update(max.x, max.y, min.z)
                    update(max.x, max.y, max.z)
                    hasBounds = true
                }
            }
        }

        if (!hasBounds) return null
        return Vec3f(minX, minY, minZ) to Vec3f(maxX, maxY, maxZ)
    }
}

class Animations(private val map: Map<String, AnimationInstance>) : Collection<AnimationInstance> {

    /**
     * Возвращает анимацию по точному имени.
     * @throws IllegalStateException если анимация не найдена
     */
    operator fun get(name: String): AnimationInstance =
        map[name] ?: error("Animation '$name' not found. Available: ${map.keys}")

    /**
     * Возвращает анимацию по точному имени или null.
     */
    fun getOrNull(name: String): AnimationInstance? = map[name]

    /**
     * Ищет анимацию по имени: сначала точное совпадение, потом case-insensitive.
     * Возвращает точное имя анимации (как оно хранится в модели) или null.
     */
    fun findName(name: String): String? {
        if (map.containsKey(name)) return name
        return map.keys.firstOrNull { it.equals(name, ignoreCase = true) }
    }

    /**
     * Возвращает имя первой анимации или null если анимаций нет.
     */
    fun firstName(): String? = map.keys.firstOrNull()

    /**
     * Возвращает список имён всех анимаций (для логирования).
     */
    fun names(): List<String> = map.keys.toList()

    override val size: Int = map.size
    override fun isEmpty(): Boolean = map.isEmpty()
    override fun contains(element: AnimationInstance) = element in map.values
    override fun iterator(): Iterator<AnimationInstance> = map.values.iterator()
    override fun containsAll(elements: Collection<AnimationInstance>) = map.values.containsAll(elements)
}

fun ModelAttachment.calculateBounds(): Pair<Vec3f, Vec3f>? = calculateBoundsCached()

private const val MODEL_BATCHING_PRIMITIVE_THRESHOLD = 48
private const val MODEL_BATCHING_DENSE_PRIMITIVE_THRESHOLD = 24
private const val MODEL_BATCHING_TOTAL_CUBE_THRESHOLD = 128
private const val MODEL_BATCHING_MIXED_PRIMITIVE_THRESHOLD = 16
private const val MODEL_BATCHING_DENSE_AVERAGE_CUBES = 4f
private const val MODEL_BATCHING_MIXED_AVERAGE_CUBES = 8f



