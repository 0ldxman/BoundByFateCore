package omc.boundbyfate.api.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.component.EntityStatData
import java.util.UUID

/**
 * Контекст выполнения способности.
 * 
 * Содержит всю информацию, необходимую для выполнения эффектов способности.
 * Передаётся между эффектами и фазами выполнения.
 */
data class AbilityContext(
    /** Кастер способности */
    val caster: LivingEntity,
    
    /** Список целей способности */
    val targets: List<LivingEntity>,
    
    /** Позиция цели (для AoE и зональных способностей) */
    val targetPos: Vec3d?,
    
    /** Мир, в котором выполняется способность */
    val world: ServerWorld,
    
    /** ID способности */
    val abilityId: Identifier,
    
    /** Текущая фаза выполнения */
    val phase: AbilityPhase,
    
    /** Уровень апкаста (для заклинаний) */
    val upcastLevel: Int? = null,
    
    /** Уровень зарядки (0.0-1.0 для charged abilities) */
    val chargeLevel: Float = 1.0f,
    
    /** Уровень персонажа кастера */
    val casterLevel: Int,
    
    /** Статы кастера */
    val casterStats: EntityStatData?,
    
    /** Дополнительные данные для передачи между эффектами */
    val data: MutableMap<String, Any> = mutableMapOf(),
    
    /** Результаты спасбросков (UUID цели -> успех/провал) */
    val savingThrowResults: Map<UUID, Boolean> = emptyMap()
) {
    /**
     * Получает первую цель из списка.
     */
    fun getTarget(): LivingEntity? = targets.firstOrNull()
    
    /**
     * Проверяет, есть ли цели.
     */
    fun hasTargets(): Boolean = targets.isNotEmpty()
    
    /**
     * Проверяет, прошла ли цель спасбросок.
     */
    fun didTargetSave(target: LivingEntity): Boolean = 
        savingThrowResults[target.uuid] ?: false
    
    /**
     * Проверяет, провалила ли цель спасбросок.
     */
    fun didTargetFail(target: LivingEntity): Boolean = 
        !didTargetSave(target)
}
