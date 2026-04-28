package omc.boundbyfate.event.content

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.event.core.BaseCancellableEvent
import omc.boundbyfate.event.core.EventBus
import omc.boundbyfate.event.core.eventBus
import java.util.*

/**
 * События персонажа.
 *
 * Покрывают жизненный цикл персонажа: создание, переключение,
 * изменение характеристик, повышение уровня.
 */
object CharacterEvents {

    /** Вызывается перед созданием персонажа. Можно отменить. */
    val BEFORE_CREATE: EventBus<BeforeCharacterCreate> =
        eventBus("character.before_create")

    /** Вызывается после создания персонажа. */
    val AFTER_CREATE: EventBus<AfterCharacterCreate> =
        eventBus("character.after_create")

    /** Вызывается перед переключением персонажа. Можно отменить. */
    val BEFORE_SWITCH: EventBus<BeforeCharacterSwitch> =
        eventBus("character.before_switch")

    /** Вызывается после переключения персонажа. */
    val AFTER_SWITCH: EventBus<AfterCharacterSwitch> =
        eventBus("character.after_switch")

    /** Вызывается при изменении характеристики. */
    val STAT_CHANGED: EventBus<StatChanged> =
        eventBus("character.stat_changed")

    /** Вызывается при повышении уровня. */
    val LEVEL_UP: EventBus<LevelUp> =
        eventBus("character.level_up")
}

// ── Callbacks ─────────────────────────────────────────────────────────────

fun interface BeforeCharacterCreate {
    fun onBeforeCreate(event: CharacterCreateEvent)
}

fun interface AfterCharacterCreate {
    fun onAfterCreate(player: ServerPlayerEntity, characterId: UUID)
}

fun interface BeforeCharacterSwitch {
    fun onBeforeSwitch(event: CharacterSwitchEvent)
}

fun interface AfterCharacterSwitch {
    fun onAfterSwitch(player: ServerPlayerEntity, oldCharacterId: UUID?, newCharacterId: UUID)
}

fun interface StatChanged {
    fun onStatChanged(entity: LivingEntity, statId: Identifier, oldValue: Int, newValue: Int)
}

fun interface LevelUp {
    fun onLevelUp(entity: LivingEntity, oldLevel: Int, newLevel: Int)
}

// ── Event data ────────────────────────────────────────────────────────────

data class CharacterCreateEvent(
    val player: ServerPlayerEntity,
    val characterName: String
) : BaseCancellableEvent()

data class CharacterSwitchEvent(
    val player: ServerPlayerEntity,
    val oldCharacterId: UUID?,
    val newCharacterId: UUID
) : BaseCancellableEvent()
