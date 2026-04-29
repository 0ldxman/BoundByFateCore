package omc.boundbyfate.client.gui.core

import net.minecraft.util.math.MathHelper
import kotlin.reflect.KProperty

/**
 * Анимируемое значение с автоматической интерполяцией.
 *
 * Хранит текущее значение и целевое. Каждый тик плавно
 * приближает текущее к целевому через lerp.
 *
 * Поддерживает делегирование через `by`:
 * ```kotlin
 * var scale by animFloat(1f)
 * scale = 1.25f  // устанавливает target
 * // scale читает current
 * ```
 */
class AnimState<T>(
    initial: T,
    val speed: Float,
    private val lerp: (from: T, to: T, t: Float) -> T
) {
    var current: T = initial
        private set

    var target: T = initial

    /**
     * Тикает анимацию на один кадр.
     * Вызывается автоматически через [AnimOwner.tickAll].
     */
    fun tick(delta: Float) {
        if (Accessibility.reduceMotion) {
            current = target
            return
        }
        val t = (speed * delta).coerceIn(0f, 1f)
        current = lerp(current, target, t)
    }

    /**
     * Мгновенно устанавливает значение без анимации.
     */
    fun snap(value: T) {
        current = value
        target = value
    }

    /**
     * Сбрасывает к начальному значению.
     */
    fun reset(value: T) = snap(value)

    // Делегирование: чтение возвращает current, запись устанавливает target
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = current
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) { target = value }
}

// ── Фабричные функции ─────────────────────────────────────────────────────

fun animFloat(initial: Float, speed: Float = 0.15f) =
    AnimState(initial, speed) { from, to, t -> MathHelper.lerp(t, from, to) }

fun animInt(initial: Int, speed: Float = 0.15f) =
    AnimState(initial, speed) { from, to, t -> MathHelper.lerp(t, from.toFloat(), to.toFloat()).toInt() }

fun animColor(initial: Int, speed: Float = 0.15f) =
    AnimState(initial, speed) { from, to, t -> lerpColor(from, to, t) }

fun animVec2(initialX: Float, initialY: Float, speed: Float = 0.15f) =
    AnimState(initialX to initialY, speed) { from, to, t ->
        MathHelper.lerp(t, from.first, to.first) to MathHelper.lerp(t, from.second, to.second)
    }

/**
 * Линейная интерполяция цвета по каналам ARGB.
 */
fun lerpColor(from: Int, to: Int, t: Float): Int {
    val fa = (from ushr 24) and 0xFF
    val fr = (from ushr 16) and 0xFF
    val fg = (from ushr 8)  and 0xFF
    val fb =  from           and 0xFF
    val ta = (to ushr 24) and 0xFF
    val tr = (to ushr 16) and 0xFF
    val tg = (to ushr 8)  and 0xFF
    val tb =  to           and 0xFF
    val a = MathHelper.lerp(t, fa.toFloat(), ta.toFloat()).toInt()
    val r = MathHelper.lerp(t, fr.toFloat(), tr.toFloat()).toInt()
    val g = MathHelper.lerp(t, fg.toFloat(), tg.toFloat()).toInt()
    val b = MathHelper.lerp(t, fb.toFloat(), tb.toFloat()).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Настройки доступности.
 */
object Accessibility {
    /** Отключить анимации — мгновенные переходы. */
    var reduceMotion = false
    /** Высокий контраст. */
    var highContrast = false
    /** Масштаб UI. */
    var uiScale = 1f
}
