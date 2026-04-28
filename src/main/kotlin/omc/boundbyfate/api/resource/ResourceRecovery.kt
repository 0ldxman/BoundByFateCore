package omc.boundbyfate.api.resource

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Правило восстановления ресурса.
 *
 * Описывает *когда* ресурс восстанавливается до максимума.
 * *Сколько* восстанавливается — всегда полностью (до максимума).
 *
 * Частичное восстановление (например, Arcane Recovery Волшебника)
 * реализуется через Feature/Ability класса, которая вызывает
 * `ResourceSystem.restore(entity, resourceId, amount)` при нужном событии.
 * Это не дело самого ресурса.
 *
 * ## Примеры
 *
 * ```kotlin
 * // Ярость — восстанавливается на длинном отдыхе
 * ResourceRecovery.OnEvent(RestEvents.LONG)
 *
 * // Очки ки — восстанавливаются на коротком отдыхе
 * ResourceRecovery.OnEvent(RestEvents.SHORT)
 *
 * // Ячейки заклинаний — восстанавливаются на длинном отдыхе
 * ResourceRecovery.OnEvent(RestEvents.LONG)
 *
 * // Ячейки Колдуна — восстанавливаются на коротком отдыхе
 * ResourceRecovery.OnEvent(RestEvents.SHORT)
 *
 * // Кости превосходства — восстанавливаются на коротком отдыхе
 * ResourceRecovery.OnEvent(RestEvents.SHORT)
 *
 * // Ресурс который восстанавливается только вручную
 * ResourceRecovery.Manual
 * ```
 *
 * ## JSON
 *
 * ```json
 * {"type": "on_event", "event": "boundbyfate-core:rest/long"}
 * {"type": "on_event", "event": "boundbyfate-core:rest/short"}
 * {"type": "manual"}
 * ```
 */
sealed class ResourceRecovery {

    /**
     * Полное восстановление при наступлении события.
     *
     * Событие публикуется через EventBus — ресурс не знает что именно
     * его вызывает. Это позволяет гибко настраивать триггеры.
     *
     * Стандартные события:
     * - `RestEvents.SHORT` (`"boundbyfate-core:rest/short"`) — короткий отдых
     * - `RestEvents.LONG`  (`"boundbyfate-core:rest/long"`)  — длинный отдых
     *
     * Кастомные события (для модов):
     * - `"mymod:meditation_complete"` — завершение медитации
     * - `"mymod:sunrise"` — рассвет
     *
     * @param eventId идентификатор события восстановления
     */
    data class OnEvent(val eventId: Identifier) : ResourceRecovery() {
        companion object {
            val CODEC: Codec<OnEvent> = RecordCodecBuilder.create { i ->
                i.group(CodecUtil.IDENTIFIER.fieldOf("event").forGetter { it.eventId })
                 .apply(i, ::OnEvent)
            }
        }
    }

    /**
     * Только ручное восстановление.
     *
     * Ресурс восстанавливается только через:
     * - Явный вызов `ResourceSystem.restore(entity, resourceId, amount)`
     * - Способности или Features класса
     * - GM-команды
     *
     * Примеры:
     * - Ресурс который восстанавливается только через конкретную способность
     * - Кастомный ресурс мода с уникальной механикой восстановления
     */
    data object Manual : ResourceRecovery() {
        val CODEC: Codec<Manual> = Codec.unit(Manual)
    }

    companion object {
        val CODEC: Codec<ResourceRecovery> = Codec.STRING.dispatch(
            "type",
            { recovery ->
                when (recovery) {
                    is OnEvent -> "on_event"
                    is Manual  -> "manual"
                }
            },
            { type ->
                when (type) {
                    "on_event" -> OnEvent.CODEC
                    "manual"   -> Manual.CODEC
                    else -> throw IllegalArgumentException("Unknown recovery type: $type")
                }
            }
        )

        /** Восстановление на коротком отдыхе. */
        fun onShortRest(): ResourceRecovery =
            OnEvent(Identifier("boundbyfate-core", "rest/short"))

        /** Восстановление на длинном отдыхе. */
        fun onLongRest(): ResourceRecovery =
            OnEvent(Identifier("boundbyfate-core", "rest/long"))
    }
}
