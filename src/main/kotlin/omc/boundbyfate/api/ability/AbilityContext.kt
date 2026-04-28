package omc.boundbyfate.api.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d

/**
 * Контекст конкретного использования способности.
 *
 * Передаётся во все методы [AbilityHandler] и extension-функции.
 * Содержит всё необходимое для выполнения логики способности.
 *
 * ## Жизненный цикл
 *
 * Создаётся [omc.boundbyfate.system.ability.AbilityExecutor] при попытке
 * использовать способность. Живёт на протяжении всего выполнения.
 *
 * ## Передача данных между хуками
 *
 * Используй [stash] для передачи данных между [AbilityHandler.onPreparationStart],
 * [AbilityHandler.onPreparationTick] и [AbilityHandler.execute]:
 *
 * ```kotlin
 * override fun onPreparationTick(ctx: AbilityContext, ticksElapsed: Int) {
 *     ctx.stash["charge_level"] = ticksElapsed.toFloat() / 60f
 * }
 *
 * override fun execute(ctx: AbilityContext) {
 *     val charge = ctx.stash["charge_level"] as? Float ?: 1f
 *     val damage = (ctx.data.getInt("base_damage") * charge).toInt()
 * }
 * ```
 *
 * ## Dry-run
 *
 * При [isDryRun] = true extension-функции не применяют реальных изменений,
 * но пишут в [results]. Используется для UI превью.
 *
 * @property caster кастер способности
 * @property definition данные способности из JSON
 * @property world серверный мир
 * @property currentTick текущий игровой тик
 * @property targets список целей (заполняется в execute через extension-функции)
 * @property targetPos позиция цели для AoE (опционально)
 * @property isDryRun режим превью — без реальных изменений
 * @property stash хранилище данных между хуками
 * @property results накопленные результаты выполнения
 */
data class AbilityContext(
    val caster: LivingEntity,
    val definition: AbilityDefinition,
    val world: ServerWorld,
    val currentTick: Long,
    val targets: MutableList<LivingEntity> = mutableListOf(),
    val targetPos: Vec3d? = null,
    val isDryRun: Boolean = false,
    val stash: MutableMap<String, Any> = mutableMapOf(),
    val results: MutableList<AbilityExecutionResult> = mutableListOf()
) {
    // ── Удобные геттеры ───────────────────────────────────────────────────

    /**
     * Данные способности из JSON блока `data`.
     */
    val data: AbilityData get() = definition.abilityData

    /**
     * Данные масштабирования из JSON блока `scaling`.
     */
    val scaling: AbilityData get() = definition.abilityScaling

    /**
     * Является ли способность заклинанием.
     */
    val isSpell: Boolean get() = definition.isSpell

    /**
     * Первая цель из списка или null.
     */
    val primaryTarget: LivingEntity? get() = targets.firstOrNull()

    /**
     * Текущая обрабатываемая цель.
     * Устанавливается в extension-функциях при итерации по целям.
     * Используется модификаторами для проверки конкретной цели.
     */
    var currentTarget: LivingEntity? = null
        internal set

    // ── Stash helpers ─────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    fun <T> getStash(key: String): T? = stash[key] as? T

    fun <T : Any> putStash(key: String, value: T) { stash[key] = value }

    // ── Уровень кастера ───────────────────────────────────────────────────

    /**
     * Уровень персонажа кастера.
     * TODO: получать из компонента персонажа
     */
    val casterLevel: Int get() = 1 // заглушка до реализации компонента уровня
}
