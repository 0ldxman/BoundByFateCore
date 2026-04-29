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
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Активные задачи переходов. Ключ: "from->to". */
    private val transitionJobs = HashMap<String, Job>()

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
    }
}
