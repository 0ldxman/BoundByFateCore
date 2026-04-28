package omc.boundbyfate.api.ability

import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import omc.boundbyfate.api.ability.event.AbilityExecuteOnTargetEvent
import omc.boundbyfate.api.ability.event.AbilityEvents
import omc.boundbyfate.api.action.ActionSlotType
import omc.boundbyfate.api.dice.AdvantageType
import omc.boundbyfate.component.components.EntityAbilitiesData
import omc.boundbyfate.component.components.EntityCharacterData
import omc.boundbyfate.component.components.EntityCombatData
import omc.boundbyfate.component.components.EntityStatsData
import omc.boundbyfate.component.components.AbilityCooldown
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.util.math.DndMath
import omc.boundbyfate.util.time.Duration

/**
 * Extension-функции на [AbilityContext].
 *
 * Предоставляют удобный DSL для написания логики способностей.
 * Все функции автоматически:
 * - Пишут в [AbilityContext.results] для dry-run и AFTER_EXECUTE
 * - Пропускают реальные изменения при [AbilityContext.isDryRun]
 * - Публикуют [AbilityEvents.ON_EXECUTE] при контакте с целью
 */

// ── Броски кубиков ────────────────────────────────────────────────────────

/**
 * Бросает кубики по выражению и возвращает итог.
 */
fun AbilityContext.roll(dice: DiceExpression): Int =
    dice.rollTotal()

/**
 * Бросает кубики с преимуществом/помехой.
 */
fun AbilityContext.rollD20(
    advantage: AdvantageType = AdvantageType.NONE,
    modifier: Int = 0
): Int = omc.boundbyfate.api.dice.DiceRoller.rollD20(advantage, modifier).total

/**
 * Спасбросок цели против DC.
 *
 * @param target цель
 * @param statId характеристика для спасброска
 * @param dc сложность
 * @return true если спасбросок успешен
 */
fun AbilityContext.rollSavingThrow(
    target: LivingEntity,
    statId: Identifier,
    dc: Int
): Boolean {
    val stats = target.getOrCreate(EntityStatsData.TYPE)
    val statModifier = stats.getStatModifier(statId)

    // Получаем уровень персонажа через characterId
    val characterId = caster.getOrCreate(EntityCharacterData.TYPE).characterId
    val level = if (caster is net.minecraft.server.network.ServerPlayerEntity && characterId != null) {
        omc.boundbyfate.data.world.BbfWorldData.get(caster.server)
            .getSection(omc.boundbyfate.data.world.sections.CharacterSection.TYPE)
            .characters[characterId]?.progression?.level ?: 1
    } else 1

    val profBonus = if (stats.savingThrowProficiencies.containsKey(statId)) {
        DndMath.calculateProficiencyBonus(level)
    } else 0

    val roll = omc.boundbyfate.api.dice.DiceRoller.rollD20().total
    return (roll + statModifier + profBonus) >= dc
}

// ── Модификаторы значений ─────────────────────────────────────────────────

/**
 * Вычисляет Int значение с применением всех активных модификаторов.
 *
 * ```kotlin
 * val damage = ctx.modify("damage") { roll(ctx.data.requireDiceExpression("damage_dice")) }
 * ```
 *
 * @param key ключ значения
 * @param compute функция вычисления базового значения
 * @return модифицированное значение
 */
fun AbilityContext.modify(key: String, compute: AbilityContext.() -> Int): Int {
    val base = this.compute()
    return AbilityModifiers.applyInt(key, base, this)
}

/**
 * Вычисляет Float значение с применением всех активных модификаторов.
 */
fun AbilityContext.modifyFloat(key: String, compute: AbilityContext.() -> Float): Float {
    val base = this.compute()
    return AbilityModifiers.applyFloat(key, base, this)
}

/**
 * Проверяет есть ли активные модификаторы для ключа.
 */
fun AbilityContext.hasModifier(key: String): Boolean =
    AbilityModifiers.hasModifiers(key)

// ── Урон ──────────────────────────────────────────────────────────────────

/**
 * Наносит урон цели.
 *
 * Публикует [AbilityEvents.ON_EXECUTE] — слушатели могут отменить
 * применение к конкретной цели (Shield, Evasion).
 *
 * Пишет [AbilityExecutionResult.DamageDealt] в [results].
 *
 * @param target цель
 * @param amount количество урона
 * @param damageTypeId тип урона
 * @param wasCritical был ли критический удар
 */
fun AbilityContext.dealDamage(
    target: LivingEntity,
    amount: Int,
    damageTypeId: Identifier,
    wasCritical: Boolean = false
) {
    val event = AbilityExecuteOnTargetEvent(this, target)
    val prevTarget = currentTarget
    currentTarget = target

    AbilityEvents.ON_EXECUTE.invokeCancellable(event) { it.onExecute(event) }

    currentTarget = prevTarget

    if (event.isCancelled) return

    results.add(AbilityExecutionResult.DamageDealt(target, amount, damageTypeId, wasCritical))

    if (isDryRun) return

    // Применяем сопротивления цели
    val resistance = target.getOrCreate(EntityCombatData.TYPE).getResistance(damageTypeId)
    val finalAmount = (amount * resistance.multiplier).toInt()
    if (finalAmount > 0) {
        target.damage(target.damageSources.magic(), finalAmount.toFloat())
    }
}

// ── Исцеление ─────────────────────────────────────────────────────────────

/**
 * Исцеляет цель.
 *
 * Пишет [AbilityExecutionResult.HealingApplied] в [results].
 *
 * @param target цель
 * @param amount количество HP
 */
fun AbilityContext.heal(target: LivingEntity, amount: Int) {
    results.add(AbilityExecutionResult.HealingApplied(target, amount))

    if (isDryRun) return

    target.heal(amount.toFloat())
}

// ── Статусы ───────────────────────────────────────────────────────────────

/**
 * Применяет статус к цели.
 *
 * Пишет [AbilityExecutionResult.StatusApplied] в [results].
 *
 * @param target цель
 * @param statusId идентификатор статуса
 * @param duration длительность
 */
fun AbilityContext.applyStatus(
    target: LivingEntity,
    statusId: Identifier,
    duration: Duration
) {
    results.add(AbilityExecutionResult.StatusApplied(target, statusId))
    if (isDryRun) return
    omc.boundbyfate.system.status.StatusSystem.apply(
        target, statusId, duration,
        omc.boundbyfate.util.source.SourceReference.ability(definition.id)
    )
}

fun AbilityContext.removeStatus(target: LivingEntity, statusId: Identifier) {
    results.add(AbilityExecutionResult.StatusRemoved(target, statusId))
    if (isDryRun) return
    omc.boundbyfate.system.status.StatusSystem.remove(target, statusId)
}

// ── Действия и ресурсы ────────────────────────────────────────────────────

fun AbilityContext.consumeAction(type: ActionSlotType = definition.actionCost) {
    if (isDryRun) return
    val combat = caster.getOrCreate(EntityCombatData.TYPE)
    val slot = combat.actionSlots[type] ?: omc.boundbyfate.api.action.ActionSlot.free(type)
    combat.actionSlots[type] = slot.consume(currentTick)
}

fun AbilityContext.hasAction(type: ActionSlotType = definition.actionCost): Boolean =
    caster.getOrCreate(EntityCombatData.TYPE).isActionAvailable(type, currentTick)

fun AbilityContext.consumeResource(resourceId: Identifier, amount: Int = 1): Boolean {
    results.add(AbilityExecutionResult.ResourceConsumed(resourceId, amount))
    if (isDryRun) return true
    return caster.getOrCreate(EntityAbilitiesData.TYPE).consumeResource(resourceId, amount)
}

fun AbilityContext.hasResource(resourceId: Identifier, amount: Int = 1): Boolean =
    caster.getOrCreate(EntityAbilitiesData.TYPE).hasResource(resourceId, amount)

fun AbilityContext.startRecovery() {
    if (isDryRun) return
    val recovery = definition.recovery ?: return
    val cooldown = when (recovery) {
        is omc.boundbyfate.api.resource.ResourceRecovery.OnEvent ->
            AbilityCooldown.untilEvent(recovery.eventId)
        is omc.boundbyfate.api.resource.ResourceRecovery.Manual ->
            return // Manual — не ставим кулдаун, управляется вручную
    }
    caster.getOrCreate(EntityAbilitiesData.TYPE).abilitiesCooldown[definition.id] = cooldown
}

fun AbilityContext.isOnCooldown(): Boolean =
    caster.getOrCreate(EntityAbilitiesData.TYPE).isOnCooldown(definition.id)

// ── Таргетинг ─────────────────────────────────────────────────────────────

/**
 * Находит все живые сущности в радиусе от позиции.
 *
 * @param center центр поиска
 * @param radius радиус в блоках
 * @param filter фильтр целей
 */
fun AbilityContext.findTargetsInRadius(
    center: Vec3d,
    radius: Float,
    filter: TargetFilter = TargetFilter.ENEMIES
): List<LivingEntity> {
    val box = Box(
        center.x - radius, center.y - radius, center.z - radius,
        center.x + radius, center.y + radius, center.z + radius
    )
    val radiusSq = radius * radius

    return world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
        if (entity.isDead) return@getEntitiesByClass false
        if (entity.squaredDistanceTo(center) > radiusSq) return@getEntitiesByClass false
        when (filter) {
            TargetFilter.ENEMIES -> entity != caster && !entity.isTeammate(caster)
            TargetFilter.ALLIES -> entity != caster && entity.isTeammate(caster)
            TargetFilter.ALL_LIVING -> entity != caster
            TargetFilter.ALL_INCLUDING_SELF -> true
        }
    }
}

/**
 * Находит первую сущность в прицеле кастера.
 *
 * @param range дальность в блоках
 * @param filter фильтр целей
 */
fun AbilityContext.findTargetInSight(
    range: Float,
    filter: TargetFilter = TargetFilter.ENEMIES
): LivingEntity? {
    val eyePos = caster.eyePos
    val lookVec = caster.rotationVector
    val endPos = eyePos.add(lookVec.multiply(range.toDouble()))

    val box = Box(eyePos, endPos).expand(1.0)

    return world.getEntitiesByClass(LivingEntity::class.java, box) { entity ->
        if (entity == caster || entity.isDead) return@getEntitiesByClass false
        when (filter) {
            TargetFilter.ENEMIES -> !entity.isTeammate(caster)
            TargetFilter.ALLIES -> entity.isTeammate(caster)
            TargetFilter.ALL_LIVING, TargetFilter.ALL_INCLUDING_SELF -> true
        }
    }.minByOrNull { it.squaredDistanceTo(eyePos) }
}

// ── Кастомные результаты ──────────────────────────────────────────────────

/**
 * Добавляет кастомный результат в [results].
 * Используй для нестандартных эффектов которые хочешь отслеживать.
 */
fun AbilityContext.addCustomResult(key: String, data: Map<String, Any> = emptyMap()) {
    results.add(AbilityExecutionResult.Custom(key, data))
}

// ── Фильтр целей ──────────────────────────────────────────────────────────

/**
 * Фильтр для поиска целей.
 */
enum class TargetFilter {
    /** Только враги (не союзники, не сам кастер). */
    ENEMIES,
    /** Только союзники (не сам кастер). */
    ALLIES,
    /** Все живые кроме кастера. */
    ALL_LIVING,
    /** Все живые включая кастера. */
    ALL_INCLUDING_SELF
}
