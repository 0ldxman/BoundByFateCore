package omc.boundbyfate.system.effect

import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.EffectContext
import omc.boundbyfate.api.effect.EffectHandler
import omc.boundbyfate.api.effect.addArmorClassModifier
import omc.boundbyfate.api.effect.addAttackModifier
import omc.boundbyfate.api.effect.addDamageModifier
import omc.boundbyfate.api.effect.addDisadvantage
import omc.boundbyfate.api.effect.addStatModifier
import omc.boundbyfate.api.effect.dealDamageToSelf
import omc.boundbyfate.api.effect.grantAbility
import omc.boundbyfate.api.effect.grantProficiency
import omc.boundbyfate.api.effect.removeArmorClassModifier
import omc.boundbyfate.api.effect.removeAttackModifier
import omc.boundbyfate.api.effect.removeDamageModifier
import omc.boundbyfate.api.effect.removeDisadvantage
import omc.boundbyfate.api.effect.removeStatModifier
import omc.boundbyfate.api.effect.revokeAbility
import omc.boundbyfate.api.effect.revokeProficiency
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.registry.EffectRegistry
import org.slf4j.LoggerFactory

/**
 * Регистрация всех встроенных эффектов BoundByFate Core.
 *
 * ## Добавление нового встроенного эффекта
 *
 * 1. Создай `object MyEffect : EffectHandler()` ниже или в отдельном файле
 * 2. Добавь `EffectRegistry.register(MyEffect)` в [register]
 * 3. Создай JSON файл `resources/data/boundbyfate-core/bbf_effect/my_effect.json`
 *
 * ## Добавление эффекта из другого мода
 *
 * В своём `ModInitializer`:
 * ```kotlin
 * EffectRegistry.register(MyModEffects.MyCustomEffect)
 * ```
 */
object BbfEffects {

    private val logger = LoggerFactory.getLogger(BbfEffects::class.java)

    fun register() {
        logger.info("Registering built-in effects...")

        EffectRegistry.register(Darkvision)
        EffectRegistry.register(StatModifier)
        EffectRegistry.register(SkillModifier)
        EffectRegistry.register(GrantProficiency)
        EffectRegistry.register(DamageResistance)
        EffectRegistry.register(CriticalRange)
        EffectRegistry.register(ExtraAttack)
        EffectRegistry.register(ConditionalDamage)
        EffectRegistry.register(ArmorClassFormula)
        EffectRegistry.register(Advantage)
        EffectRegistry.register(ConditionImmunity)
        EffectRegistry.register(MaxHpModifier)
        EffectRegistry.register(GrantAbility)
        EffectRegistry.register(AttackPenalty)
        EffectRegistry.register(ArmorClassPenalty)
        EffectRegistry.register(StealthDisadvantage)
        EffectRegistry.register(Poison)
        
        // Эффекты для состояний
        EffectRegistry.register(BlockMovement)
        EffectRegistry.register(BlockSpeech)
        EffectRegistry.register(BlockSpeechSlurred)
        EffectRegistry.register(AutoFailSave)
        EffectRegistry.register(AutoFailSightChecks)
        EffectRegistry.register(MeleeCritAuto)

        logger.info("Registered ${EffectRegistry.handlerCount()} effect handlers")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Пассивные эффекты (без тиков)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Тёмное зрение.
     *
     * JSON: `{"id": "boundbyfate-core:darkvision", "data": {"range": 60}}`
     */
    object Darkvision : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "darkvision")

        override fun apply(ctx: EffectContext) {
            // Darkvision хранится как активный эффект в EntityEffectsData.
            // Системы рендера/зрения проверяют hasEffect(id) и читают параметр "range".
            // Ванильный night vision применяется как визуальный индикатор.
            val range = ctx.data.getInt("range", 60)
            ctx.putStash("range", range)
        }

        override fun remove(ctx: EffectContext) {
            // Снятие — факт убирается из EntityEffectsData через EffectApplier
        }
    }

    /**
     * Модификатор характеристики.
     *
     * JSON: `{"id": "boundbyfate-core:stat_modifier", "data": {"stat": "boundbyfate-core:strength", "value": 2}}`
     */
    object StatModifier : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "stat_modifier")

        override fun apply(ctx: EffectContext) {
            val stat = ctx.data.requireId("stat")
            val value = ctx.data.requireInt("value")
            ctx.addStatModifier(stat, value)
        }

        override fun remove(ctx: EffectContext) {
            val stat = ctx.data.requireId("stat")
            ctx.removeStatModifier(stat)
        }
    }

    /**
     * Модификатор навыка.
     *
     * JSON: `{"id": "boundbyfate-core:skill_modifier", "data": {"skill": "boundbyfate-core:stealth", "value": 5}}`
     */
    object SkillModifier : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "skill_modifier")

        // Факт активности в EntityEffectsData — система навыков читает параметры skill/value.
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Даёт владение.
     *
     * JSON: `{"id": "boundbyfate-core:grant_proficiency", "data": {"proficiency": "boundbyfate-core:simple_weapons"}}`
     */
    object GrantProficiency : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "grant_proficiency")

        override fun apply(ctx: EffectContext) {
            val proficiency = ctx.data.requireId("proficiency")
            ctx.grantProficiency(proficiency)
        }

        override fun remove(ctx: EffectContext) {
            val proficiency = ctx.data.requireId("proficiency")
            ctx.revokeProficiency(proficiency)
        }
    }

    /**
     * Сопротивление к урону.
     *
     * JSON:
     * ```json
     * {
     *   "id": "boundbyfate-core:damage_resistance",
     *   "data": {
     *     "damage_type": "boundbyfate-core:poison",
     *     "level": "resistance"
     *   }
     * }
     * ```
     * Уровни: "resistance" (половина), "immunity" (ноль), "vulnerability" (двойной)
     */
    object DamageResistance : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "damage_resistance")

        override fun apply(ctx: EffectContext) {
            val damageType = ctx.data.getId("damage_type") ?: return
            val levelStr = ctx.data.getString("level", "resistance")
            val level = omc.boundbyfate.api.damage.ResistanceLevel.valueOf(levelStr.uppercase())
            ctx.entity.getOrCreate(omc.boundbyfate.component.components.EntityCombatData.TYPE)
                .damageResistances[damageType] = level
        }

        override fun remove(ctx: EffectContext) {
            val damageType = ctx.data.getId("damage_type") ?: return
            ctx.entity.getOrCreate(omc.boundbyfate.component.components.EntityCombatData.TYPE)
                .damageResistances.remove(damageType)
        }
    }

    /**
     * Изменяет диапазон критических попаданий.
     * Факт активности хранится в EntityEffectsData — система атаки проверяет hasEffect().
     */
    object CriticalRange : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "critical_range")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Дополнительные атаки.
     * Факт активности в EntityEffectsData — система атаки читает параметр "attacks".
     */
    object ExtraAttack : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "extra_attack")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Условный дополнительный урон (Sneak Attack, Divine Smite).
     * Факт активности в EntityEffectsData — система атаки читает formula/damage_type/trigger.
     */
    object ConditionalDamage : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "conditional_damage")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Альтернативная формула AC (Unarmored Defense).
     * Факт активности в EntityEffectsData — система AC пересчитывает armorClass.
     */
    object ArmorClassFormula : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "armor_class_formula")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Преимущество на определённые броски.
     * Факт активности в EntityEffectsData — система бросков читает параметр "on".
     */
    object Advantage : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "advantage")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Иммунитет к состоянию.
     *
     * JSON: `{"id": "boundbyfate-core:condition_immunity", "data": {"condition": "dnd:magical_sleep"}}`
     */
    object ConditionImmunity : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "condition_immunity")

        override fun apply(ctx: EffectContext) {
            val condition = ctx.data.requireId("condition")
            ctx.entity.getOrCreate(omc.boundbyfate.component.components.EntityCombatData.TYPE)
                .statusImmunities.add(condition)
        }

        override fun remove(ctx: EffectContext) {
            val condition = ctx.data.requireId("condition")
            ctx.entity.getOrCreate(omc.boundbyfate.component.components.EntityCombatData.TYPE)
                .statusImmunities.remove(condition)
        }
    }

    /**
     * Модификатор максимального HP.
     *
     * JSON: `{"id": "boundbyfate-core:max_hp_modifier", "data": {"formula": "@level * 2"}}`
     */
    object MaxHpModifier : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "max_hp_modifier")

        override fun apply(ctx: EffectContext) {
            val value = ctx.data.getInt("value", 0)
            val params = ctx.entity.getOrCreate(omc.boundbyfate.component.components.EntityParamsData.TYPE)
            params.maxHits += value
            // Синхронизируем с Minecraft через атрибут
            val maxHealthAttr = ctx.entity.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH)
            maxHealthAttr?.baseValue = params.maxHits.toDouble()
        }

        override fun remove(ctx: EffectContext) {
            val value = ctx.data.getInt("value", 0)
            val params = ctx.entity.getOrCreate(omc.boundbyfate.component.components.EntityParamsData.TYPE)
            params.maxHits = maxOf(1f, params.maxHits - value)
            val maxHealthAttr = ctx.entity.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH)
            maxHealthAttr?.baseValue = params.maxHits.toDouble()
            // Ограничиваем текущее HP новым максимумом
            if (ctx.entity.health > params.maxHits) {
                ctx.entity.health = params.maxHits
            }
        }
    }

    /**
     * Даёт активную способность.
     *
     * JSON: `{"id": "boundbyfate-core:grant_ability", "data": {"ability": "dnd:second_wind"}}`
     */
    object GrantAbility : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "grant_ability")

        override fun apply(ctx: EffectContext) {
            val ability = ctx.data.requireId("ability")
            ctx.grantAbility(ability)
        }

        override fun remove(ctx: EffectContext) {
            val ability = ctx.data.requireId("ability")
            ctx.revokeAbility(ability)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Штрафы за отсутствие владения
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Штраф к броскам атаки.
     *
     * JSON: `{"id": "boundbyfate-core:attack_penalty", "data": {"penalty": -4}}`
     */
    object AttackPenalty : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "attack_penalty")

        override fun apply(ctx: EffectContext) {
            val penalty = ctx.data.requireInt("penalty")
            ctx.addAttackModifier(penalty)
        }

        override fun remove(ctx: EffectContext) {
            ctx.removeAttackModifier()
        }
    }

    /**
     * Штраф к AC.
     *
     * JSON: `{"id": "boundbyfate-core:armor_class_penalty", "data": {"penalty": -2}}`
     */
    object ArmorClassPenalty : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "armor_class_penalty")

        override fun apply(ctx: EffectContext) {
            val penalty = ctx.data.requireInt("penalty")
            ctx.addArmorClassModifier(penalty)
        }

        override fun remove(ctx: EffectContext) {
            ctx.removeArmorClassModifier()
        }
    }

    /**
     * Помеха на Stealth (и другие навыки).
     *
     * JSON:
     * ```json
     * {
     *   "id": "boundbyfate-core:stealth_disadvantage",
     *   "data": {
     *     "skills": ["boundbyfate-core:stealth"]
     *   }
     * }
     * ```
     */
    object StealthDisadvantage : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "stealth_disadvantage")

        override fun apply(ctx: EffectContext) {
            val skills = ctx.data.getIds("skills")
            ctx.addDisadvantage(skills)
        }

        override fun remove(ctx: EffectContext) {
            val skills = ctx.data.getIds("skills")
            ctx.removeDisadvantage(skills)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Эффекты для состояний (Status Conditions)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Блокирует передвижение.
     * Факт активности в EntityEffectsData — система движения проверяет hasEffect(id).
     */
    object BlockMovement : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "block_movement")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Блокирует речь.
     * Факт активности в EntityEffectsData.
     */
    object BlockSpeech : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "block_speech")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Блокирует речь (невнятная).
     * Факт активности в EntityEffectsData.
     */
    object BlockSpeechSlurred : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "block_speech_slurred")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Автоматический провал спасброска по характеристике.
     * Факт активности в EntityEffectsData — система спасбросков проверяет hasEffect(id)
     * и читает параметр "stat".
     */
    object AutoFailSave : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "auto_fail_save")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Автоматический провал проверок зрения.
     * Факт активности в EntityEffectsData.
     */
    object AutoFailSightChecks : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "auto_fail_sight_checks")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Ближние атаки против сущности автоматически критические.
     * Факт активности в EntityEffectsData — система атаки проверяет hasEffect(id).
     */
    object MeleeCritAuto : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "melee_crit_auto")
        override fun apply(ctx: EffectContext) {}
        override fun remove(ctx: EffectContext) {}
    }

    /**
     * Яд — наносит урон каждый тик.
     *
     * JSON:
     * ```json
     * {
     *   "id": "boundbyfate-core:poison",
     *   "data": {
     *     "damage_per_tick": 1,
     *     "damage_type": "boundbyfate-core:poison"
     *   }
     * }
     * ```
     */
    object Poison : EffectHandler() {
        override val id = Identifier("boundbyfate-core", "poison")

        // Тикует раз в секунду
        override val tickInterval: Int = 20

        override fun apply(ctx: EffectContext) {
            // Сохраняем параметры в stash для использования в tick
            ctx.putStash("damage", ctx.data.getInt("damage_per_tick", 1))
            ctx.putStash("damage_type", ctx.data.getId("damage_type")
                ?: Identifier("boundbyfate-core", "poison"))
        }

        override fun tick(ctx: EffectContext) {
            val damage = ctx.getStash<Int>("damage") ?: return
            val damageType = ctx.getStash<Identifier>("damage_type") ?: return
            ctx.dealDamageToSelf(damage, damageType)
        }

        override fun remove(ctx: EffectContext) {
            // Яд просто заканчивается — ничего не нужно откатывать
        }
    }
}

