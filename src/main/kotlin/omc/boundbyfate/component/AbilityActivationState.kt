package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.ability.component.ActivationComponent
import java.util.UUID

/**
 * Состояние активации способности.
 * 
 * Хранится как attachment на игроке во время активации способности.
 * Используется для отслеживания прогресса каста, зарядки, канала.
 */
data class AbilityActivationState(
    /** ID активируемой способности */
    val abilityId: Identifier,
    
    /** UUID кастера */
    val caster: UUID,
    
    /** UUID цели (если есть) */
    val target: UUID? = null,
    
    /** Позиция цели (для AoE) */
    val targetPos: Vec3d? = null,
    
    /** Уровень апкаста (для заклинаний) */
    val upcastLevel: Int? = null,
    
    /** Тик начала активации */
    val startTick: Long,
    
    /** Время подготовки в тиках */
    val preparationTime: Int,
    
    /** Тип активации */
    val activationType: ActivationType
) {
    /**
     * Вычисляет прогресс активации (0.0 - 1.0).
     */
    fun getProgress(currentTick: Long): Float {
        if (preparationTime == 0) return 1.0f
        val elapsed = (currentTick - startTick).toInt()
        return (elapsed.toFloat() / preparationTime).coerceIn(0f, 1f)
    }
    
    /**
     * Проверяет, завершена ли активация.
     */
    fun isComplete(currentTick: Long): Boolean {
        val elapsed = (currentTick - startTick).toInt()
        return elapsed >= preparationTime
    }
    
    /**
     * Вычисляет уровень зарядки для Charged активации (0.0 - 1.0).
     */
    fun getChargeLevel(currentTick: Long, minCharge: Int, maxCharge: Int): Float {
        val elapsed = (currentTick - startTick).toInt() - preparationTime
        if (elapsed < minCharge) return 0f
        if (elapsed >= maxCharge) return 1f
        return (elapsed - minCharge).toFloat() / (maxCharge - minCharge)
    }
    
    companion object {
        val CODEC: Codec<AbilityActivationState> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("abilityId").forGetter { it.abilityId },
                Codec.STRING.xmap({ UUID.fromString(it) }, { it.toString() })
                    .fieldOf("caster").forGetter { it.caster },
                Codec.STRING.xmap({ UUID.fromString(it) }, { it.toString() })
                    .optionalFieldOf("target").forGetter { java.util.Optional.ofNullable(it.target) },
                Vec3d.CODEC.optionalFieldOf("targetPos").forGetter { java.util.Optional.ofNullable(it.targetPos) },
                Codec.INT.optionalFieldOf("upcastLevel").forGetter { java.util.Optional.ofNullable(it.upcastLevel) },
                Codec.LONG.fieldOf("startTick").forGetter { it.startTick },
                Codec.INT.fieldOf("preparationTime").forGetter { it.preparationTime },
                Codec.STRING.xmap({ ActivationType.valueOf(it) }, { it.name })
                    .fieldOf("activationType").forGetter { it.activationType }
            ).apply(instance) { abilityId, caster, target, targetPos, upcastLevel, startTick, preparationTime, activationType ->
                AbilityActivationState(
                    abilityId, caster, target.orElse(null), targetPos.orElse(null),
                    upcastLevel.orElse(null), startTick, preparationTime, activationType
                )
            }
        }
    }
}

/**
 * Тип активации (упрощённая версия для сериализации).
 */
enum class ActivationType {
    INSTANT,
    CHANNELED,
    CHARGED,
    RITUAL
}
