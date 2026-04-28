package omc.boundbyfate.component.components

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.SyncMode

/**
 * Данные способностей и ресурсов entity.
 *
 * ## Ресурсы
 *
 * `resources.current` — текущие значения (Ki: 3, spell_slot_1: 2).
 * `resources.maximums` — максимумы, вычисляются из levelUpHistory при загрузке.
 *
 * ## Известные способности
 *
 * `knownAbilities` — список ID способностей которые персонаж знает.
 * Заполняется при загрузке из CharacterData.stats.abilities.knownAbilities.
 *
 * ## Кулдауны способностей
 *
 * `abilitiesCooldown` — Map<abilityId, AbilityCooldown>.
 * Хранит информацию о восстановлении каждой способности.
 *
 * AbilityCooldown:
 * - type = ON_EVENT, value = "boundbyfate-core:rest/short" — до короткого отдыха
 * - type = ON_EVENT, value = "boundbyfate-core:rest/long" — до длинного отдыха
 * - type = TICKS, value = "1234567" — восстановится в тик 1234567
 *
 * ## Концентрация
 *
 * `currentConcentration` — ID заклинания/способности на которой концентрируется.
 * `concentrationDC` — текущий DC спасброска концентрации (max(10, damage/2)).
 */
class EntityAbilitiesData : BbfComponent() {

    // ── Ресурсы ───────────────────────────────────────────────────────────

    /**
     * Текущие значения ресурсов.
     * Ключ — ID ресурса (например "boundbyfate-core:ki_points").
     */
    val resourcesCurrent by syncedMap(Identifier.CODEC, Codec.INT)

    /**
     * Максимальные значения ресурсов.
     * Вычисляются из levelUpHistory при загрузке персонажа.
     */
    val resourcesMaximums by syncedMap(Identifier.CODEC, Codec.INT)

    // ── Известные способности ─────────────────────────────────────────────

    /**
     * Список ID известных способностей.
     * Заполняется при загрузке из CharacterData.
     */
    val knownAbilities by syncedList(Identifier.CODEC)

    // ── Кулдауны ──────────────────────────────────────────────────────────

    /**
     * Кулдауны способностей.
     * Ключ — ID способности.
     * Значение — информация о восстановлении.
     */
    val abilitiesCooldown by syncedMap(Identifier.CODEC, AbilityCooldown.CODEC)

    // ── Концентрация ──────────────────────────────────────────────────────

    /** ID заклинания/способности на которой концентрируется. null если нет. */
    var currentConcentration by synced<Identifier?>(null)

    /** DC спасброска концентрации. Обновляется при получении урона. */
    var concentrationDC by synced(0)

    // ── Удобные методы ────────────────────────────────────────────────────

    /** Проверяет наличие ресурса в нужном количестве. */
    fun hasResource(resourceId: Identifier, amount: Int = 1): Boolean =
        (resourcesCurrent[resourceId] ?: 0) >= amount

    /** Тратит ресурс. Возвращает true если успешно. */
    fun consumeResource(resourceId: Identifier, amount: Int = 1): Boolean {
        val current = resourcesCurrent[resourceId] ?: 0
        if (current < amount) return false
        resourcesCurrent[resourceId] = current - amount
        return true
    }

    /** Восстанавливает ресурс до максимума. */
    fun restoreResource(resourceId: Identifier) {
        resourcesCurrent[resourceId] = resourcesMaximums[resourceId] ?: 0
    }

    /** Проверяет находится ли способность на кулдауне. */
    fun isOnCooldown(abilityId: Identifier): Boolean = abilityId in abilitiesCooldown

    /** Снимает кулдаун способности. */
    fun clearCooldown(abilityId: Identifier) {
        abilitiesCooldown.remove(abilityId)
    }

    /** Снимает все кулдауны с типом ON_EVENT для данного события. */
    fun clearCooldownsByEvent(eventId: Identifier) {
        val toRemove = abilitiesCooldown.entries
            .filter { (_, cd) -> cd.type == CooldownType.ON_EVENT && cd.value == eventId.toString() }
            .map { it.key }
        toRemove.forEach { abilitiesCooldown.remove(it) }
    }

    /** Проверяет есть ли активная концентрация. */
    val isConcentrating: Boolean get() = currentConcentration != null

    companion object {
        val TYPE = BbfComponents.register(
            id = "boundbyfate-core:abilities",
            syncMode = SyncMode.ON_CHANGE,
            factory = ::EntityAbilitiesData
        )
    }
}

/**
 * Тип кулдауна способности.
 */
enum class CooldownType {
    /** До наступления события (SHORT_REST, LONG_REST и т.д.). */
    ON_EVENT,
    /** До конкретного тика. */
    TICKS
}

/**
 * Кулдаун способности.
 *
 * @property type тип кулдауна
 * @property value для ON_EVENT — ID события, для TICKS — тик восстановления
 */
data class AbilityCooldown(
    val type: CooldownType,
    val value: String
) {
    companion object {
        val CODEC: Codec<AbilityCooldown> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.xmap(
                    { CooldownType.valueOf(it.uppercase()) },
                    { it.name.lowercase() }
                ).fieldOf("type").forGetter { it.type },
                Codec.STRING.fieldOf("value").forGetter { it.value }
            ).apply(instance, ::AbilityCooldown)
        }

        /** Кулдаун до события (например короткого отдыха). */
        fun untilEvent(eventId: Identifier) = AbilityCooldown(
            type = CooldownType.ON_EVENT,
            value = eventId.toString()
        )

        /** Кулдаун до конкретного тика. */
        fun untilTick(tick: Long) = AbilityCooldown(
            type = CooldownType.TICKS,
            value = tick.toString()
        )
    }

    /** Возвращает ID события если тип ON_EVENT. */
    fun asEventId(): Identifier? = if (type == CooldownType.ON_EVENT)
        Identifier(value.substringBefore(':'), value.substringAfter(':'))
    else null

    /** Возвращает тик восстановления если тип TICKS. */
    fun asTick(): Long? = if (type == CooldownType.TICKS) value.toLongOrNull() else null
}

