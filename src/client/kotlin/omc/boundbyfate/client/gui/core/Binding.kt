package omc.boundbyfate.client.gui.core

/**
 * Реактивный биндинг данных.
 *
 * Отслеживает изменения значения и вызывает колбеки.
 * Убирает ручные проверки `if (current != last)` в `tick`.
 *
 * ## Использование
 *
 * ```kotlin
 * class HealthBar : AnimOwner() {
 *     private val hpBinding = Binding { MinecraftClient.getInstance().player?.health ?: 0f }
 *         .onChange { newHp ->
 *             if (newHp < (lastHp ?: newHp)) {
 *                 damageFlash.snap(1f)
 *                 damageFlash.target = 0f
 *             }
 *             displayHp.target = newHp
 *         }
 *
 *     fun tick(delta: Float) {
 *         hpBinding.check()  // одна строка вместо ручной проверки
 *         tickAll(delta)
 *     }
 * }
 * ```
 */
class Binding<T>(
    private val source: () -> T
) {
    private var lastValue: T? = null
    private val listeners = mutableListOf<(T) -> Unit>()

    /** Регистрирует колбек на изменение значения. */
    fun onChange(block: (T) -> Unit): Binding<T> {
        listeners += block
        return this
    }

    /**
     * Проверяет изменилось ли значение.
     * Вызывай в `tick` виджета.
     */
    fun check() {
        val current = source()
        if (current != lastValue) {
            lastValue = current
            listeners.forEach { it(current) }
        }
    }

    /** Текущее значение без проверки изменений. */
    val value: T get() = source()

    /** Принудительно сбрасывает кэш — следующий [check] всегда вызовет колбеки. */
    fun invalidate() { lastValue = null }
}

/**
 * Биндинг с дополнительным условием — колбек вызывается только если условие выполнено.
 */
fun <T> Binding<T>.onChangeIf(condition: (T) -> Boolean, block: (T) -> Unit): Binding<T> =
    onChange { if (condition(it)) block(it) }

/**
 * Биндинг который отслеживает переход значения через порог.
 */
fun Binding<Float>.onCrossThreshold(threshold: Float, onBelow: () -> Unit, onAbove: () -> Unit): Binding<Float> {
    var wasAbove: Boolean? = null
    return onChange { value ->
        val isAbove = value >= threshold
        if (wasAbove != null && wasAbove != isAbove) {
            if (isAbove) onAbove() else onBelow()
        }
        wasAbove = isAbove
    }
}
