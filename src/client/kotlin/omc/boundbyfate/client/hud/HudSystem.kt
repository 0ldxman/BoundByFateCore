package omc.boundbyfate.client.hud

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import omc.boundbyfate.client.keybind.BbfKeybinds
import kotlin.reflect.KClass

/**
 * Центральная система HUD.
 *
 * Управляет регистрацией элементов, тиком, рендером и маршрутизацией событий.
 *
 * ## Регистрация элемента
 *
 * ```kotlin
 * HudSystem.register(
 *     element = HealthBar(),
 *     anchor  = HudAnchor.BOTTOM_LEFT,
 *     offsetX = 10,
 *     offsetY = -40,
 *     layer   = HudLayer.HUD_MAIN
 * )
 * ```
 *
 * ## Отправка события
 *
 * ```kotlin
 * // Из обработчика пакета:
 * HudSystem.notify(HudEvent.DamageTaken(amount = 15f))
 * ```
 */
object HudSystem {

    private val elements = mutableListOf<RegisteredElement>()
    private val subscriptions = mutableMapOf<KClass<*>, MutableList<Pair<HudElement, (HudEvent) -> Unit>>>()

    // ── Регистрация ───────────────────────────────────────────────────────

    /**
     * Регистрирует HUD элемент.
     *
     * @param element элемент
     * @param anchor якорь экрана
     * @param offsetX смещение по X от якоря
     * @param offsetY смещение по Y от якоря
     * @param layer слой рендера
     */
    fun register(
        element: HudElement,
        anchor: HudAnchor = HudAnchor.BOTTOM_LEFT,
        offsetX: Int = 0,
        offsetY: Int = 0,
        layer: HudLayer = HudLayer.HUD_MAIN
    ) {
        elements += RegisteredElement(element, anchor, offsetX, offsetY, layer)
    }

    /**
     * Удаляет элемент и все его подписки.
     */
    fun unregister(element: HudElement) {
        elements.removeIf { it.element === element }
        subscriptions.values.forEach { list ->
            list.removeIf { (el, _) -> el === element }
        }
    }

    /**
     * Удаляет все элементы.
     */
    fun clear() {
        elements.clear()
        subscriptions.clear()
    }

    // ── События ───────────────────────────────────────────────────────────

    /**
     * Постит событие всем подписанным элементам.
     * Вызывай из обработчиков пакетов на клиенте.
     */
    fun notify(event: HudEvent) {
        subscriptions[event::class]?.forEach { (_, handler) ->
            try { handler(event) } catch (e: Exception) {
                org.slf4j.LoggerFactory.getLogger("BbfHud")
                    .error("Error handling HudEvent ${event::class.simpleName}", e)
            }
        }
    }

    fun subscribe(type: KClass<*>, element: HudElement, handler: (HudEvent) -> Unit) {
        subscriptions.getOrPut(type) { mutableListOf() } += element to handler
    }

    // ── Тик и рендер ─────────────────────────────────────────────────────

    private fun tick(delta: Float) {
        val mc = MinecraftClient.getInstance()
        val screenW = mc.window.scaledWidth
        val screenH = mc.window.scaledHeight

        elements.forEach { reg ->
            reg.element.tick(delta)
            reg.updatePosition(screenW, screenH)
        }
    }

    private fun render(ctx: DrawContext, tickDelta: Float) {
        val mc = MinecraftClient.getInstance()
        val screenW = mc.window.scaledWidth
        val screenH = mc.window.scaledHeight

        // Рендерим по слоям в правильном порядке
        HudLayer.entries.forEach { layer ->
            elements
                .filter { it.layer == layer && it.element.isVisible }
                .forEach { reg ->
                    reg.element.renderWithVisibility(ctx, reg.currentX, reg.currentY)
                }
        }
    }

    // ── Инициализация ─────────────────────────────────────────────────────

    /**
     * Регистрирует тиковый и рендер колбеки.
     * Вызывается из [omc.boundbyfate.client.BoundByFateCoreClient].
     */
    fun register() {
        // Тик — каждый клиентский тик
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.world != null && !client.isPaused) {
                tick(client.tickDelta)
            }

            // Обработка toggle кейбиндингов — постим HudEvent
            while (BbfKeybinds.TOGGLE_DETAILED_HUD.wasPressed()) {
                notify(HudEvent.KeyPressed(BbfKeybinds.TOGGLE_DETAILED_HUD))
            }
            // Сюда добавляй обработку новых кейбиндингов по мере необходимости
        }

        // Рендер — каждый кадр поверх игры
        HudRenderCallback.EVENT.register { ctx, tickDelta ->
            render(ctx, tickDelta)
        }
    }
}

// ── Зарегистрированный элемент ────────────────────────────────────────────

internal class RegisteredElement(
    val element: HudElement,
    val anchor: HudAnchor,
    val offsetX: Int,
    val offsetY: Int,
    val layer: HudLayer
) {
    var currentX = 0
        private set
    var currentY = 0
        private set

    fun updatePosition(screenW: Int, screenH: Int) {
        val (baseX, baseY) = anchor.resolve(screenW, screenH)
        currentX = baseX + offsetX
        currentY = baseY + offsetY
    }
}

// ── Якоря ─────────────────────────────────────────────────────────────────

/**
 * Якорь — именованная точка экрана.
 * Адаптируется к любому разрешению и GUI Scale.
 */
enum class HudAnchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

    fun resolve(screenW: Int, screenH: Int): Pair<Int, Int> = when (this) {
        TOP_LEFT      -> 0 to 0
        TOP_CENTER    -> screenW / 2 to 0
        TOP_RIGHT     -> screenW to 0
        MIDDLE_LEFT   -> 0 to screenH / 2
        CENTER        -> screenW / 2 to screenH / 2
        MIDDLE_RIGHT  -> screenW to screenH / 2
        BOTTOM_LEFT   -> 0 to screenH
        BOTTOM_CENTER -> screenW / 2 to screenH
        BOTTOM_RIGHT  -> screenW to screenH
    }
}

// ── Слои рендера ──────────────────────────────────────────────────────────

/**
 * Слои рендера HUD — определяют порядок отрисовки.
 */
enum class HudLayer {
    WORLD_OVERLAY,   // поверх мира, под HUD (виньетка, затемнение)
    HUD_BACKGROUND,  // фоновые элементы
    HUD_MAIN,        // основные элементы (HP, действия, ресурсы)
    HUD_EFFECTS,     // эффекты поверх основных (вспышки, пульсации)
    HUD_OVERLAY,     // временные оверлеи (диалог, уведомления)
    HUD_TOP          // всегда поверх всего (затемнение при смене персонажа)
}
