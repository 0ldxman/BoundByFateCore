package omc.boundbyfate.client.models.internal.controller

import kotlinx.coroutines.*
import omc.boundbyfate.client.models.internal.v2.ModelAttachment
import org.slf4j.LoggerFactory

/**
 * Система управления анимациями для одной [ModelAttachment].
 *
 * Работает на собственном [AnimationDispatcher] — корутинном диспетчере
 * который прокручивается синхронно на render thread через [update].
 *
 * ## Использование
 *
 * ```kotlin
 * val animSystem = AnimationSystem(attachment)
 *
 * // Запустить idle когда модель загрузится
 * animSystem.playWhenReady("idle", wrapMode = WrapMode.Loop)
 *
 * // Переключить анимацию в любой момент
 * animSystem.play("attack", wrapMode = WrapMode.Once)
 *
 * // Плавный переход
 * animSystem.play("walk", duration = 0.3f)
 *
 * // Обязательно вызвать при удалении сущности
 * animSystem.destroy()
 * ```
 *
 * ## Жизненный цикл
 *
 * Создаётся вместе с [ModelAttachment], уничтожается через [destroy].
 * Не уничтожать — утечка корутин.
 */
class AnimationSystem(val model: ModelAttachment) {

    private val logger = LoggerFactory.getLogger(AnimationSystem::class.java)

    val dispatcher = AnimationDispatcher("AnimationSystem")

    /**
     * Coroutine scope для запуска анимационных задач.
     *
     * `internal` — доступен внутри пакета (в частности [AnimationController]),
     * но не виден снаружи модуля.
     */
    internal val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Активные задачи переходов. Ключ: "from->to". */
    private val transitionJobs = HashMap<String, Job>()

    /** Активные задачи переходов для именованных слоёв. Ключ: layerName. */
    private val layerTransitionJobs = HashMap<String, Job>()

    /** Имя текущей активной анимации (для логики переключений). */
    var currentAnimation: String? = null
        private set

    /** true если [destroy] уже был вызван. */
    private var destroyed = false

    // ── Обновление ────────────────────────────────────────────────────────

    /**
     * Прокручивает очередь корутин диспетчера.
     * Вызывается каждый кадр из рендерера.
     */
    fun update(dt: Float) {
        if (destroyed) return
        dispatcher.update(dt)
    }

    // ── Управление анимациями ─────────────────────────────────────────────

    /**
     * Запускает анимацию немедленно если модель уже загружена,
     * или откладывает до загрузки если ещё нет.
     *
     * Безопасно вызывать сразу после создания [AnimationSystem] —
     * не упадёт если анимаций ещё нет.
     *
     * ```kotlin
     * animSystem.playWhenReady("idle", wrapMode = WrapMode.Loop)
     * ```
     *
     * @param name имя анимации (case-insensitive поиск если точное не найдено)
     * @param duration длительность перехода в секундах (0 = мгновенно)
     * @param wrapMode режим воспроизведения
     */
    fun playWhenReady(
        name: String,
        duration: Float = 0f,
        wrapMode: WrapMode = WrapMode.Loop
    ) {
        if (destroyed) return

        scope.launch {
            // Ждём пока модель загрузится и в ней есть анимации
            model.awaitAnimations()

            // Ищем анимацию: сначала точное совпадение, потом case-insensitive
            val resolvedName = model.animations.findName(name)
            if (resolvedName == null) {
                logger.warn("Animation '$name' not found in model. Available: ${model.animations.names()}")
                return@launch
            }

            transition(to = resolvedName, duration = duration, wrapMode = wrapMode)
        }
    }

    /**
     * Запускает первую доступную анимацию как idle.
     * Ищет анимацию с именем "idle" (case-insensitive), иначе берёт первую.
     *
     * Безопасно вызывать сразу после создания — ждёт загрузки модели.
     *
     * ```kotlin
     * animSystem.playIdleWhenReady()
     * ```
     */
    fun playIdleWhenReady() {
        if (destroyed) return

        scope.launch {
            model.awaitAnimations()

            val idleName = model.animations.findName("idle")
                ?: model.animations.firstName()

            if (idleName == null) {
                logger.debug("No animations found in model, skipping idle")
                return@launch
            }

            transition(to = idleName, duration = 0f, wrapMode = WrapMode.Loop)
        }
    }

    /**
     * Плавно переходит к анимации [to] от [from].
     *
     * Suspend-функция — вызывай из корутины или через [scope].
     * Для вызова извне используй [play].
     *
     * Безопасна: если анимация не найдена — логирует предупреждение и выходит.
     *
     * @param from анимация из которой переходим (null = текущая)
     * @param to анимация в которую переходим (null = остановить)
     * @param duration длительность перехода в секундах
     * @param wrapMode режим воспроизведения целевой анимации
     */
    suspend fun transition(
        from: String? = null,
        to: String? = null,
        duration: Float = 0.33f,
        wrapMode: WrapMode = WrapMode.Loop,
    ) {
        if (destroyed) return

        val fromName = from ?: currentAnimation
        val original = fromName?.let { model.animations.getOrNull(it) }
        val target = to?.let {
            model.animations.getOrNull(it) ?: run {
                logger.warn("transition(): animation '$it' not found. Available: ${model.animations.names()}")
                return
            }
        }

        val key = "${fromName ?: ""}->${to ?: ""}"
        transitionJobs.remove(key)?.cancel()

        target?.time = 0f
        target?.wrapMode = wrapMode

        val job = scope.launch {
            val steps = (duration * 60f).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                target?.weight = t
                original?.weight = 1f - t
                dispatcher.awaitNextFrame()
            }
            target?.weight = 1f
            original?.weight = 0f
        }
        transitionJobs[key] = job

        if (to != null) currentAnimation = to
    }

    /**
     * Запускает анимацию с плавным переходом.
     * Не-suspend версия [transition] для вызова извне корутин.
     *
     * ```kotlin
     * // Из обработчика события атаки:
     * animSystem.play("attack", duration = 0.2f, wrapMode = WrapMode.Once)
     * ```
     */
    fun play(
        name: String,
        duration: Float = 0.2f,
        wrapMode: WrapMode = WrapMode.Loop
    ) {
        if (destroyed) return

        scope.launch {
            val resolvedName = model.animations.findName(name)
            if (resolvedName == null) {
                logger.warn("play(): animation '$name' not found. Available: ${model.animations.names()}")
                return@launch
            }
            transition(to = resolvedName, duration = duration, wrapMode = wrapMode)
        }
    }

    // ── Слоевые анимации ──────────────────────────────────────────────────

    /**
     * Активные слои. Ключ — имя слоя, значение — имя текущей анимации.
     * Используется для diff-сравнения в [NpcModelRenderer].
     */
    private val activeLayers = HashMap<String, String>()

    /**
     * Запускает анимацию на именованном слое с transition.
     *
     * Если на этом слое уже играет другая анимация — плавно убирает её вес
     * пока нарастает вес новой (cross-fade).
     *
     * Если слой уже играет эту же анимацию — ничего не делает.
     *
     * @param layerName  Имя слоя (произвольная строка)
     * @param animation  Имя анимации из модели
     * @param blendIn    Время cross-fade в секундах
     * @param wrapMode   Режим воспроизведения
     */
    fun playLayer(
        layerName: String,
        animation: String,
        blendIn: Float = 0.2f,
        wrapMode: WrapMode = WrapMode.Loop
    ) {
        if (destroyed) return
        if (activeLayers[layerName] == animation) return

        val previousAnimation = activeLayers[layerName]
        activeLayers[layerName] = animation

        scope.launch {
            model.awaitAnimations()

            val resolvedName = model.animations.findName(animation)
            if (resolvedName == null) {
                logger.warn("playLayer(): animation '$animation' not found. Available: ${model.animations.names()}")
                activeLayers.remove(layerName)
                return@launch
            }

            val target = model.animations.getOrNull(resolvedName) ?: return@launch
            val previous = previousAnimation?.let { model.animations.findName(it) }
                ?.let { model.animations.getOrNull(it) }

            // Отменяем предыдущий transition на этом слое если был
            layerTransitionJobs.remove(layerName)?.cancel()

            target.time = 0f
            target.wrapMode = wrapMode

            val job = scope.launch {
                val steps = (blendIn * 60f).toInt().coerceAtLeast(1)
                val prevStartWeight = previous?.weight ?: 0f
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    target.weight = t
                    previous?.weight = prevStartWeight * (1f - t)
                    dispatcher.awaitNextFrame()
                }
                target.weight = 1f
                previous?.weight = 0f
            }
            layerTransitionJobs[layerName] = job
        }
    }

    /**
     * Останавливает анимацию на именованном слое с плавным blendOut.
     *
     * @param layerName  Имя слоя
     * @param blendOut   Время плавного исчезновения в секундах
     */
    fun stopLayer(layerName: String, blendOut: Float = 0.2f) {
        if (destroyed) return
        val animation = activeLayers.remove(layerName) ?: return
        layerTransitionJobs.remove(layerName)?.cancel()

        scope.launch {
            model.awaitAnimations()
            val resolvedName = model.animations.findName(animation) ?: return@launch
            val anim = model.animations.getOrNull(resolvedName) ?: return@launch
            val startWeight = anim.weight
            val steps = (blendOut * 60f).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                anim.weight = startWeight * (1f - i.toFloat() / steps)
                dispatcher.awaitNextFrame()
            }
            anim.weight = 0f
        }
    }

    // ── Жизненный цикл ────────────────────────────────────────────────────

    /**
     * Уничтожает систему анимаций и отменяет все корутины.
     *
     * Обязательно вызывать при удалении НПС из мира.
     * После вызова объект нельзя использовать.
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        scope.cancel()
        transitionJobs.clear()
        layerTransitionJobs.clear()
        activeLayers.clear()
    }
}
