package omc.boundbyfate.api.status

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.util.codec.CodecUtil
import omc.boundbyfate.util.source.SourceReference
import omc.boundbyfate.util.time.Duration
import omc.boundbyfate.util.time.TimeUtil

/**
 * Активное состояние на сущности — runtime факт того, что состояние действует.
 *
 * ## Разделение ответственности
 *
 * - [StatusDefinition] — *что* делает состояние (в Registry, из JSON)
 * - [ActiveStatus] — *факт* что состояние активно (в EntityStatusData, runtime)
 *
 * ## Жизненный цикл
 *
 * ```
 * StatusSystem.apply(entity, statusId, duration, source)
 *   → создаёт ActiveStatus
 *   → применяет эффекты из StatusDefinition
 *   → сохраняет в EntityStatusData
 *
 * StatusSystem.tick(entity, currentTick)
 *   → проверяет Duration.Ticks — истёк ли таймер
 *   → тикует длящиеся эффекты через EffectApplier.tick (с stash из ActiveStatus)
 *
 * StatusSystem.removeByEvent(entity, eventId)
 *   → снимает состояния с Duration.UntilEvent(eventId)
 *
 * StatusSystem.remove(entity, statusId)
 *   → снимает эффекты
 *   → пересчитывает includes (снимает те что больше не нужны)
 *   → удаляет из EntityStatusData
 * ```
 *
 * @property statusId ссылка на [StatusDefinition] в Registry
 * @property source кто наложил состояние (для трассировки и снятия)
 * @property duration как долго действует
 * @property appliedAtTick тик когда было наложено (для Duration.Ticks)
 * @property stash хранилище данных для тикующих эффектов (сохраняется между тиками)
 */
data class ActiveStatus(
    val statusId: Identifier,
    val source: SourceReference,
    val duration: Duration,
    val appliedAtTick: Long,
    val stash: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * Проверяет, истекло ли состояние по тикам.
     *
     * Возвращает true только для [Duration.Ticks].
     * Для остальных типов длительности — всегда false
     * (они снимаются через события или явно).
     *
     * @param currentTick текущий игровой тик
     */
    fun isExpired(currentTick: Long): Boolean {
        return when (val d = duration) {
            is Duration.Ticks -> TimeUtil.isExpired(appliedAtTick, d.ticks, currentTick)
            else -> false
        }
    }

    /**
     * Возвращает оставшееся время в тиках.
     * Только для [Duration.Ticks], иначе -1.
     */
    fun remainingTicks(currentTick: Long): Int {
        return when (val d = duration) {
            is Duration.Ticks -> TimeUtil.remainingTicks(appliedAtTick, d.ticks, currentTick)
            else -> -1
        }
    }

    /**
     * Проверяет, снимается ли это состояние при наступлении события.
     *
     * @param eventId идентификатор события (например, RestEvents.SHORT)
     */
    fun isRemovedByEvent(eventId: Identifier): Boolean {
        return duration is Duration.UntilEvent && duration.eventId == eventId
    }

    /**
     * Проверяет, требует ли это состояние периодического спасброска.
     */
    fun requiresSavingThrow(): Boolean = duration is Duration.UntilSave

    companion object {
        val CODEC: Codec<ActiveStatus> = RecordCodecBuilder.create { instance ->
            instance.group(
                CodecUtil.IDENTIFIER.fieldOf("status_id").forGetter { it.statusId },
                SourceReference.CODEC.fieldOf("source").forGetter { it.source },
                Duration.CODEC.fieldOf("duration").forGetter { it.duration },
                Codec.LONG.fieldOf("applied_at_tick").forGetter { it.appliedAtTick }
            ).apply(instance, ::ActiveStatus)
        }
    }
}
