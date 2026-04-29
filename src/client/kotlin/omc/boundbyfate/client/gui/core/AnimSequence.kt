package omc.boundbyfate.client.gui.core

/**
 * Последовательность анимационных шагов.
 *
 * Описываешь что происходит, не как. Шаги выполняются один за другим.
 *
 * ## Использование
 *
 * ```kotlin
 * private val openAnim = AnimSequence()
 *     .then(0.3f) { p -> titleAlpha.target = p }
 *     .delay(0.1f)
 *     .then(0.4f) { p -> cardsProgress = p }
 *     .then(0.2f) { p -> btnAlpha.target = p }
 *
 * // В render():
 * openAnim.tick(delta)
 * ```
 *
 * @param easing функция easing применяемая к прогрессу каждого шага
 */
class AnimSequence(
    private val easing: (Float) -> Float = Easing.EASE_OUT
) {
    private data class Step(val duration: Float, val block: (progress: Float) -> Unit)

    private val steps = mutableListOf<Step>()
    private var currentStep = 0
    private var stepTime = 0f
    private var onFinishedCallbacks = mutableListOf<() -> Unit>()

    /** Добавляет шаг с заданной длительностью. */
    fun then(duration: Float, block: (progress: Float) -> Unit): AnimSequence {
        steps += Step(duration, block)
        return this
    }

    /** Добавляет паузу без действия. */
    fun delay(duration: Float): AnimSequence = then(duration) {}

    /** Регистрирует колбек на завершение всей последовательности. */
    fun onFinished(block: () -> Unit): AnimSequence {
        onFinishedCallbacks += block
        return this
    }

    /** Тикает последовательность. Вызывай каждый кадр. */
    fun tick(delta: Float) {
        if (isFinished) return

        val step = steps[currentStep]
        stepTime += delta

        val rawProgress = (stepTime / step.duration).coerceIn(0f, 1f)
        step.block(easing(rawProgress))

        if (stepTime >= step.duration) {
            // Гарантируем что шаг завершился с progress = 1
            step.block(1f)
            currentStep++
            stepTime = 0f

            if (isFinished) {
                onFinishedCallbacks.forEach { it() }
            }
        }
    }

    /** Сбрасывает последовательность в начало. */
    fun reset() {
        currentStep = 0
        stepTime = 0f
    }

    val isFinished get() = currentStep >= steps.size
    val progress get() = if (steps.isEmpty()) 1f else currentStep.toFloat() / steps.size
}

// ── Easing функции ────────────────────────────────────────────────────────

object Easing {
    val LINEAR: (Float) -> Float = { t -> t }

    val EASE_IN: (Float) -> Float = { t -> t * t }

    val EASE_OUT: (Float) -> Float = { t -> 1f - (1f - t) * (1f - t) }

    val EASE_IN_OUT: (Float) -> Float = { t ->
        if (t < 0.5f) 2f * t * t
        else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f
    }

    /** Перелетает за цель и возвращается — пружина. */
    val EASE_OUT_BACK: (Float) -> Float = { t ->
        val c1 = 1.70158f
        val c3 = c1 + 1f
        1f + c3 * (t - 1f) * (t - 1f) * (t - 1f) + c1 * (t - 1f) * (t - 1f)
    }

    /** Упругий отскок. */
    val EASE_OUT_ELASTIC: (Float) -> Float = { t ->
        val c4 = (2f * Math.PI / 3f).toFloat()
        when {
            t == 0f -> 0f
            t == 1f -> 1f
            else -> Math.pow(2.0, (-10 * t).toDouble()).toFloat() *
                    Math.sin(((t * 10f - 0.75f) * c4).toDouble()).toFloat() + 1f
        }
    }

    /** Отскок от пола. */
    val BOUNCE_OUT: (Float) -> Float = { t -> bounceOut(t) }

    /** Предвосхищение — чуть назад перед броском. */
    val ANTICIPATE: (Float) -> Float = { t ->
        val c1 = 1.70158f
        (c1 + 1f) * t * t * t - c1 * t * t
    }

    private fun bounceOut(t: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> { val t2 = t - 1.5f / d1; n1 * t2 * t2 + 0.75f }
            t < 2.5f / d1 -> { val t2 = t - 2.25f / d1; n1 * t2 * t2 + 0.9375f }
            else -> { val t2 = t - 2.625f / d1; n1 * t2 * t2 + 0.984375f }
        }
    }
}
