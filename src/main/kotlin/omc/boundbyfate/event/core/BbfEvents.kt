package omc.boundbyfate.event.core

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier

/**
 * Инфраструктурные события BoundByFate.
 *
 * Содержит только события жизненного цикла мода и компонентной системы.
 * Эти события не зависят от игрового контента и нужны для корректной
 * инициализации и работы всех систем.
 *
 * ## Игровые события
 *
 * Все события связанные с игровым контентом находятся в `event/content/`:
 * - [omc.boundbyfate.event.content.CharacterEvents] — персонаж
 * - [omc.boundbyfate.event.content.CombatEvents] — бой
 * - [omc.boundbyfate.event.content.RestEvents] — отдых
 *
 * ## Два подхода к событиям
 *
 * 1. **Fabric Event API** (`Event<T>`) — для lifecycle событий.
 *    Простые, без приоритетов, минимальный overhead.
 *
 * 2. **EventBus** — для gameplay событий в `event/content/`.
 *    Поддерживает приоритеты, отмену, изменение результата.
 */
object BbfEvents {

    // ── Lifecycle (Fabric API) ─────────────────────────────────────────────

    /**
     * События жизненного цикла мода.
     */
    object Lifecycle {

        /** Вызывается после инициализации всех регистров. */
        val REGISTRIES_INITIALIZED: Event<RegistriesInitialized> =
            EventFactory.createArrayBacked(RegistriesInitialized::class.java) { listeners ->
                RegistriesInitialized {
                    for (listener in listeners) listener.onRegistriesInitialized()
                }
            }

        /** Вызывается после инициализации всех компонентов. */
        val COMPONENTS_INITIALIZED: Event<ComponentsInitialized> =
            EventFactory.createArrayBacked(ComponentsInitialized::class.java) { listeners ->
                ComponentsInitialized {
                    for (listener in listeners) listener.onComponentsInitialized()
                }
            }

        /** Вызывается при перезагрузке данных (datapack reload). */
        val DATA_RELOAD: Event<DataReload> =
            EventFactory.createArrayBacked(DataReload::class.java) { listeners ->
                DataReload {
                    for (listener in listeners) listener.onDataReload()
                }
            }
    }

    // ── Component (EventBus) ───────────────────────────────────────────────

    /**
     * События компонентной системы.
     */
    object Component {

        /** Вызывается перед изменением компонента. Можно отменить. */
        val BEFORE_MODIFY: EventBus<BeforeComponentModify> =
            eventBus("component.before_modify")

        /** Вызывается после изменения компонента. */
        val AFTER_MODIFY: EventBus<AfterComponentModify> =
            eventBus("component.after_modify")

        /** Вызывается при синхронизации компонента с клиентом. */
        val SYNC: EventBus<ComponentSync> =
            eventBus("component.sync")
    }
}

// ── Lifecycle callbacks ────────────────────────────────────────────────────

fun interface RegistriesInitialized {
    fun onRegistriesInitialized()
}

fun interface ComponentsInitialized {
    fun onComponentsInitialized()
}

fun interface DataReload {
    fun onDataReload()
}

// ── Component callbacks ────────────────────────────────────────────────────

fun interface BeforeComponentModify {
    fun onBeforeModify(entity: LivingEntity, componentId: Identifier)
}

fun interface AfterComponentModify {
    fun onAfterModify(entity: LivingEntity, componentId: Identifier)
}

fun interface ComponentSync {
    fun onSync(entity: LivingEntity, componentId: Identifier)
}
