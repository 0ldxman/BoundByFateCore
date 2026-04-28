package omc.boundbyfate.component.components

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.SyncMode
import omc.boundbyfate.util.codec.CodecUtil
import omc.boundbyfate.util.source.SourceReference
import omc.boundbyfate.util.time.Duration

/**
 * Активные эффекты на entity.
 *
 * Хранит список активных эффектов с метаданными.
 * Эффекты применяются через EffectApplier — компонент только хранит факт активности.
 *
 * ## Тикующие эффекты
 *
 * Для тикующих эффектов (яд, горение) `stash` хранит состояние между тиками.
 * `stash` не сериализуется — пересоздаётся при загрузке из snapshot.
 *
 * ## Snapshot
 *
 * При сохранении персонажа в snapshot попадают только эффекты с
 * `duration = UntilEvent` или `Permanent`. Эффекты с `Ticks` — только
 * если не истекли.
 */
class EntityEffectsData : BbfComponent() {

    /** Список активных эффектов. */
    val activeEffects by syncedList(ActiveEffect.CODEC)

    /** Проверяет наличие эффекта по ID. */
    fun hasEffect(effectId: Identifier): Boolean =
        activeEffects.any { it.effectId == effectId }

    /** Возвращает активный эффект по ID или null. */
    fun getEffect(effectId: Identifier): ActiveEffect? =
        activeEffects.find { it.effectId == effectId }

    /** Добавляет эффект. */
    fun addEffect(effect: ActiveEffect) {
        activeEffects.add(effect)
    }

    /** Удаляет эффект по ID. */
    fun removeEffect(effectId: Identifier): Boolean =
        activeEffects.removeIf { it.effectId == effectId }

    /** Удаляет все эффекты от источника. */
    fun removeEffectsFromSource(source: SourceReference): Int {
        val before = activeEffects.size
        activeEffects.removeIf { it.source == source }
        return before - activeEffects.size
    }

    companion object {
        val TYPE = BbfComponents.register(
            id = "boundbyfate-core:effects",
            syncMode = SyncMode.ON_CHANGE,
            factory = ::EntityEffectsData
        )
    }
}

/**
 * Активный эффект на entity.
 *
 * @property effectId ID эффекта из Registry
 * @property source кто применил эффект
 * @property duration длительность (null = мгновенный, уже применён)
 * @property appliedAtTick тик когда был применён
 * @property ticksActive сколько тиков эффект активен (для тикующих)
 * @property stash состояние между тиками (не сериализуется)
 */
data class ActiveEffect(
    val effectId: Identifier,
    val source: SourceReference,
    val duration: Duration?,
    val appliedAtTick: Long,
    val ticksActive: Int = 0,
    @Transient val stash: MutableMap<String, Any> = mutableMapOf()
) {
    companion object {
        val CODEC: Codec<ActiveEffect> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("effectId").forGetter { it.effectId },
                SourceReference.CODEC.fieldOf("source").forGetter { it.source },
                Duration.CODEC.optionalFieldOf("duration").forGetter {
                    java.util.Optional.ofNullable(it.duration)
                },
                Codec.LONG.fieldOf("appliedAtTick").forGetter { it.appliedAtTick },
                Codec.INT.optionalFieldOf("ticksActive", 0).forGetter { it.ticksActive }
            ).apply(instance) { effectId, source, duration, appliedAtTick, ticksActive ->
                ActiveEffect(effectId, source, duration.orElse(null), appliedAtTick, ticksActive)
            }
        }
    }
}
