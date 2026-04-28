package omc.boundbyfate.event.content

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.event.core.BaseCancellableEvent
import omc.boundbyfate.event.core.EventBus
import omc.boundbyfate.event.core.eventBus

/**
 * События отдыха.
 *
 * Отдых — это **событие**, а не таймер. Что именно его вызывает —
 * решается отдельно (костёр, кровать, медитация, команда GM).
 *
 * ## Идентификаторы для Duration
 *
 * Используй [SHORT] и [LONG] в [omc.boundbyfate.util.time.Duration.UntilEvent]:
 * ```kotlin
 * Duration.UntilEvent(RestEvents.SHORT)  // до короткого отдыха
 * Duration.UntilEvent(RestEvents.LONG)   // до длинного отдыха
 * ```
 *
 * ## Короткий отдых
 * В D&D 5e — 1 час без сна. В нашей реализации:
 * - Посиделка у костра
 * - Медитация монаха
 * - Любое действие, публикующее [BEFORE_SHORT] / [AFTER_SHORT]
 *
 * ## Длинный отдых
 * В D&D 5e — 8 часов, включая сон. В нашей реализации:
 * - Сон (кровать, палатка)
 * - Любое действие, публикующее [BEFORE_LONG] / [AFTER_LONG]
 *
 * ## Как публиковать событие отдыха
 * ```kotlin
 * // Когда игрок садится у костра:
 * val event = ShortRestEvent(player)
 * RestEvents.BEFORE_SHORT.invoker().onBeforeShortRest(event)
 * if (!event.isCancelled()) {
 *     // ... логика отдыха ...
 *     RestEvents.AFTER_SHORT.invoker().onAfterShortRest(player)
 * }
 * ```
 */
object RestEvents {

    /**
     * Идентификатор события короткого отдыха.
     * Используется в [omc.boundbyfate.util.time.Duration.UntilEvent].
     */
    val SHORT: Identifier = Identifier("boundbyfate-core", "rest/short")

    /**
     * Идентификатор события длинного отдыха (сна).
     * Используется в [omc.boundbyfate.util.time.Duration.UntilEvent].
     */
    val LONG: Identifier = Identifier("boundbyfate-core", "rest/long")

    /**
     * Вызывается перед началом короткого отдыха.
     * Можно отменить (например, если рядом враги).
     */
    val BEFORE_SHORT: EventBus<BeforeShortRest> =
        eventBus("rest.before_short")

    /**
     * Вызывается после завершения короткого отдыха.
     * Здесь системы восстанавливают ресурсы и снимают состояния.
     */
    val AFTER_SHORT: EventBus<AfterShortRest> =
        eventBus("rest.after_short")

    /**
     * Вызывается перед началом длинного отдыха (сна).
     * Можно отменить.
     */
    val BEFORE_LONG: EventBus<BeforeLongRest> =
        eventBus("rest.before_long")

    /**
     * Вызывается после завершения длинного отдыха.
     * Здесь системы восстанавливают ресурсы, снимают состояния, снижают истощение.
     */
    val AFTER_LONG: EventBus<AfterLongRest> =
        eventBus("rest.after_long")
}

// ── Callbacks ─────────────────────────────────────────────────────────────

fun interface BeforeShortRest {
    fun onBeforeShortRest(event: ShortRestEvent)
}

fun interface AfterShortRest {
    fun onAfterShortRest(player: ServerPlayerEntity)
}

fun interface BeforeLongRest {
    fun onBeforeLongRest(event: LongRestEvent)
}

fun interface AfterLongRest {
    fun onAfterLongRest(player: ServerPlayerEntity)
}

// ── Event data ────────────────────────────────────────────────────────────

class ShortRestEvent(
    val player: ServerPlayerEntity
) : BaseCancellableEvent()

class LongRestEvent(
    val player: ServerPlayerEntity
) : BaseCancellableEvent()

