package omc.boundbyfate.client.hud

import net.minecraft.client.gui.DrawContext
import omc.boundbyfate.client.gui.core.AnimOwner
import omc.boundbyfate.client.gui.core.Easing
import omc.boundbyfate.client.gui.core.animFloat
import omc.boundbyfate.client.gui.core.transform
import kotlin.reflect.KClass

/**
 * Базовый класс HUD элемента.
 *
 * ## Создание нового элемента
 *
 * ```kotlin
 * class HealthBar : HudElement() {
 *
 *     val displayHp   = animFloat(20f, speed = 0.08f)
 *     val damageFlash = animFloat(0f,  speed = 0.05f)
 *
 *     override fun HudContext.setup() {
 *         watch({ ClientPlayerData.health }) { newHp ->
 *             displayHp.target = newHp
 *         }
 *         on<HudEvent.DamageTaken> {
 *             damageFlash.snap(1f)
 *             damageFlash.target = 0f
 *         }
 *         visibleWhen { ClientPlayerData.hasCharacter }
 *     }
 *
 *     override fun tick(delta: Float) { tickAll(delta) }
 *
 *     override fun render(ctx: DrawContext, x: Int, y: Int) {
 *         // рисуем
 *     }
 * }
 * ```
 */
abstract class HudElement : AnimOwner() {

    // ── Видимость ─────────────────────────────────────────────────────────

    /** Прогресс видимости (0 = скрыт, 1 = полностью виден). */
    val visibility = animFloat(0f, speed = 0.1f)

    /** true если элемент хоть немного виден. */
    val isVisible get() = visibility.current > 0.005f

    /** Условие видимости. Проверяется каждый тик автоматически. */
    var visibleCondition: (() -> Boolean)? = null

    /** Устанавливает условие видимости. */
    fun visibleWhen(condition: () -> Boolean) {
        visibleCondition = condition
    }

    // ── Контекст ──────────────────────────────────────────────────────────

    internal val hudContext = HudContext(this)

    init {
        // setup вызывается сразу — элемент самодостаточен
        hudContext.setup()
    }

    /** Объявляет зависимости элемента — биндинги и подписки на события. */
    protected abstract fun HudContext.setup()

    // ── Тик ───────────────────────────────────────────────────────────────

    /**
     * Тикает элемент. По умолчанию тикает всё через [tickAll].
     * Переопредели если нужна дополнительная логика.
     */
    open fun tick(delta: Float) {
        tickAll(delta)
    }

    /**
     * Тикает все AnimState + биндинги + обновляет видимость.
     * Вызывай в конце своего [tick].
     */
    override fun tickAll(delta: Float) {
        // Обновляем видимость
        val shouldBeVisible = visibleCondition?.invoke() ?: true
        visibility.target = if (shouldBeVisible) 1f else 0f

        // Тикаем биндинги
        hudContext.tickBindings()

        // Тикаем все AnimState (включая visibility)
        super.tickAll(delta)
    }

    // ── Рендер ────────────────────────────────────────────────────────────

    /**
     * Рисует элемент. x, y — вычислены системой на основе якоря.
     * Вызывается только если [isVisible] = true.
     */
    abstract fun render(ctx: DrawContext, x: Int, y: Int)

    /**
     * Внутренний рендер — применяет alpha видимости автоматически.
     * Вызывается из [HudSystem].
     */
    internal fun renderWithVisibility(ctx: DrawContext, x: Int, y: Int) {
        if (!isVisible) return
        if (visibility.current >= 0.999f) {
            render(ctx, x, y)
        } else {
            ctx.transform(alpha = visibility.current) {
                render(this, x, y)
            }
        }
    }

    // ── Управление ────────────────────────────────────────────────────────

    /** Принудительно показать элемент (переопределяет visibleWhen). */
    fun forceShow() { visibility.target = 1f }

    /** Принудительно скрыть элемент. */
    fun forceHide() { visibility.target = 0f }

    /** Мгновенно показать без анимации. */
    fun snapShow() { visibility.snap(1f) }

    /** Мгновенно скрыть без анимации. */
    fun snapHide() { visibility.snap(0f) }

    /** Внутренний метод для HudContext — создаёт AnimState зарегистрированный в этом элементе. */
    internal fun createAnimFloat(initial: Float, speed: Float): omc.boundbyfate.client.gui.core.AnimState<Float> =
        animFloat(initial, speed)
}

// ── HudContext ────────────────────────────────────────────────────────────

/**
 * Контекст HUD элемента — предоставляет API для объявления зависимостей.
 *
 * Используется только внутри [HudElement.setup].
 */
class HudContext(private val element: HudElement) {

    private val bindings = mutableListOf<omc.boundbyfate.client.gui.core.Binding<*>>()

    // ── Биндинги ──────────────────────────────────────────────────────────

    /**
     * Следит за значением каждый тик.
     * Колбек вызывается при каждом изменении.
     */
    fun <T> watch(source: () -> T, onChange: (T) -> Unit): omc.boundbyfate.client.gui.core.Binding<T> {
        val binding = omc.boundbyfate.client.gui.core.Binding(source).onChange(onChange)
        bindings += binding
        return binding
    }

    /**
     * Следит за Float значением и возвращает AnimState который
     * автоматически анимируется к новому значению.
     *
     * ```kotlin
     * val displayHp = watchAnimated({ ClientPlayerData.health }, speed = 0.08f)
     * // displayHp.current — текущее анимированное значение
     * ```
     */
    fun watchAnimated(
        source: () -> Float,
        speed: Float = 0.1f
    ): omc.boundbyfate.client.gui.core.AnimState<Float> {
        val state = animFloat(source(), speed)
        watch(source) { state.target = it }
        return state
    }

    // ── События ───────────────────────────────────────────────────────────

    /**
     * Подписывается на конкретный тип HudEvent.
     * Колбек вызывается когда [HudSystem.notify] получает это событие.
     *
     * ```kotlin
     * on<HudEvent.DamageTaken> { event ->
     *     damageFlash.snap(1f)
     *     damageFlash.target = 0f
     * }
     * ```
     */
    inline fun <reified E : HudEvent> on(noinline handler: (E) -> Unit) {
        HudSystem.subscribe(E::class, element) { event ->
            if (event is E) handler(event)
        }
    }

    // ── Видимость ─────────────────────────────────────────────────────────

    /**
     * Устанавливает условие видимости элемента.
     * Проверяется каждый тик — при изменении запускается fade in/out.
     */
    fun visibleWhen(condition: () -> Boolean) {
        element.visibleWhen(condition)
    }

    // ── Внутреннее ────────────────────────────────────────────────────────

    internal fun tickBindings() = bindings.forEach { it.check() }

    // Делегируем animFloat к AnimOwner элемента
    fun animFloat(initial: Float, speed: Float = 0.15f): omc.boundbyfate.client.gui.core.AnimState<Float> =
        element.createAnimFloat(initial, speed)
}
