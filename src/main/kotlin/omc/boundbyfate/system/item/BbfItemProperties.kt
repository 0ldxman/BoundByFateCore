package omc.boundbyfate.system.item

import net.minecraft.util.Identifier
import omc.boundbyfate.api.item.ItemPropertyContext
import omc.boundbyfate.api.item.ItemPropertyHandler
import omc.boundbyfate.api.item.addArmorClassFormula
import omc.boundbyfate.api.item.addStatModifier
import omc.boundbyfate.api.item.grantAbility
import omc.boundbyfate.api.item.grantProficiency
import omc.boundbyfate.api.item.grantResource
import omc.boundbyfate.api.item.removeArmorClassFormula
import omc.boundbyfate.api.item.removeStatModifier
import omc.boundbyfate.api.item.revokeAbility
import omc.boundbyfate.api.item.revokeProficiency
import omc.boundbyfate.api.item.revokeResource
import omc.boundbyfate.registry.ItemPropertyRegistry
import org.slf4j.LoggerFactory

/**
 * Регистрация всех встроенных свойств предметов BoundByFate Core.
 *
 * ## Добавление нового встроенного свойства
 *
 * 1. Создай `object MyProperty : ItemPropertyHandler()` ниже или в отдельном файле
 * 2. Добавь `ItemPropertyRegistry.register(MyProperty)` в [register]
 * 3. Используй в JSON предмета: `{"id": "boundbyfate-core:my_property", "data": {...}}`
 *
 * ## Добавление свойства из другого мода
 *
 * В своём `ModInitializer`:
 * ```kotlin
 * ItemPropertyRegistry.register(MyModProperties.MyCustomProperty)
 * ```
 */
object BbfItemProperties {

    private val logger = LoggerFactory.getLogger(BbfItemProperties::class.java)

    fun register() {
        logger.info("Registering built-in item properties...")

        // Общие
        ItemPropertyRegistry.register(StatBonus)
        ItemPropertyRegistry.register(GrantAbility)
        ItemPropertyRegistry.register(GrantResource)
        ItemPropertyRegistry.register(GrantProficiency)

        // Оружие
        ItemPropertyRegistry.register(MeleeDamage)
        ItemPropertyRegistry.register(RangedDamage)
        ItemPropertyRegistry.register(Finesse)
        ItemPropertyRegistry.register(Versatile)
        ItemPropertyRegistry.register(Thrown)
        ItemPropertyRegistry.register(TwoHanded)
        ItemPropertyRegistry.register(Light)
        ItemPropertyRegistry.register(Reach)
        ItemPropertyRegistry.register(Heavy)

        // Броня
        ItemPropertyRegistry.register(ArmorClass)
        ItemPropertyRegistry.register(StealthDisadvantage)

        logger.info("Registered ${ItemPropertyRegistry.handlerCount()} item property handlers")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Общие свойства
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Бонус к характеристике.
     *
     * JSON: `{"id": "boundbyfate-core:stat_bonus", "data": {"stat": "boundbyfate-core:charisma", "value": 2}}`
     */
    object StatBonus : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "stat_bonus")

        override fun onEquip(ctx: ItemPropertyContext) {
            val stat = ctx.data.requireId("stat")
            val value = ctx.data.requireInt("value")
            ctx.addStatModifier(stat, value)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            val stat = ctx.data.requireId("stat")
            ctx.removeStatModifier(stat)
        }
    }

    /**
     * Даёт способность пока предмет надет/в руке.
     *
     * JSON:
     * ```json
     * {
     *   "id": "boundbyfate-core:grants_ability",
     *   "data": {
     *     "ability": "boundbyfate-core:fireball",
     *     "uses": 1,
     *     "recovery": "long_rest"
     *   }
     * }
     * ```
     */
    object GrantAbility : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "grants_ability")

        override fun onEquip(ctx: ItemPropertyContext) {
            val ability = ctx.data.requireId("ability")
            ctx.grantAbility(ability)
            // Если есть uses — создаём ресурс с recovery
            val uses = ctx.data.getInt("uses", 0)
            if (uses > 0) {
                val recoveryStr = ctx.data.getString("recovery", "long_rest")
                val resourceId = Identifier(ability.namespace, "${ability.path}_uses")
                ctx.grantResource(resourceId, uses)
            }
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            val ability = ctx.data.requireId("ability")
            ctx.revokeAbility(ability)
        }
    }

    /**
     * Даёт ресурс пока предмет надет.
     *
     * JSON: `{"id": "boundbyfate-core:grants_resource", "data": {"resource": "boundbyfate-core:ki_points", "amount": 2}}`
     */
    object GrantResource : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "grants_resource")

        override fun onEquip(ctx: ItemPropertyContext) {
            val resource = ctx.data.requireId("resource")
            val amount = ctx.data.requireInt("amount")
            ctx.grantResource(resource, amount)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            val resource = ctx.data.requireId("resource")
            ctx.revokeResource(resource)
        }
    }

    /**
     * Даёт владение пока предмет надет.
     *
     * JSON: `{"id": "boundbyfate-core:grants_proficiency", "data": {"proficiency": "boundbyfate-core:simple_weapons"}}`
     */
    object GrantProficiency : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "grants_proficiency")

        override fun onEquip(ctx: ItemPropertyContext) {
            val proficiency = ctx.data.requireId("proficiency")
            ctx.grantProficiency(proficiency)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            val proficiency = ctx.data.requireId("proficiency")
            ctx.revokeProficiency(proficiency)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Свойства оружия
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Урон в ближнем бою.
     *
     * JSON: `{"id": "boundbyfate-core:melee_damage", "data": {"dice": "1d8", "stat": "boundbyfate-core:strength"}}`
     */
    object MeleeDamage : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "melee_damage")

        override fun onEquip(ctx: ItemPropertyContext) {
            // Регистрируем формулу урона для этого оружия
            // TODO: CombatSystem.setMeleeDamage(ctx.entity, ctx.item, dice, stat, ctx.source)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.clearMeleeDamage(ctx.entity, ctx.item, ctx.source)
        }
    }

    /**
     * Урон дальнего боя.
     *
     * JSON: `{"id": "boundbyfate-core:ranged_damage", "data": {"dice": "1d6", "stat": "boundbyfate-core:dexterity", "normal": 80, "long": 320}}`
     */
    object RangedDamage : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "ranged_damage")

        override fun onEquip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.setRangedDamage(ctx.entity, ctx.item, dice, stat, normalRange, longRange, ctx.source)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.clearRangedDamage(ctx.entity, ctx.item, ctx.source)
        }
    }

    /**
     * Финесс — можно использовать DEX вместо STR для атаки и урона.
     *
     * JSON: `{"id": "boundbyfate-core:finesse"}`
     */
    object Finesse : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "finesse")

        override fun onEquip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.addWeaponFlag(ctx.entity, ctx.item, "finesse", ctx.source)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.removeWeaponFlag(ctx.entity, ctx.item, "finesse", ctx.source)
        }
    }

    /**
     * Универсальное — разный урон одной и двумя руками.
     *
     * JSON: `{"id": "boundbyfate-core:versatile", "data": {"one_hand": "1d8", "two_hand": "1d10"}}`
     */
    object Versatile : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "versatile")

        override fun onEquip(ctx: ItemPropertyContext) {
            val oneHand = ctx.data.getString("one_hand", "1d8")
            val twoHand = ctx.data.getString("two_hand", "1d10")
            // TODO: CombatSystem.setVersatileDamage(ctx.entity, ctx.item, oneHand, twoHand, ctx.source)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.clearVersatileDamage(ctx.entity, ctx.item, ctx.source)
        }
    }

    /**
     * Метательное — можно бросить.
     *
     * JSON: `{"id": "boundbyfate-core:thrown", "data": {"normal": 20, "long": 60}}`
     */
    object Thrown : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "thrown")

        override fun onEquip(ctx: ItemPropertyContext) {
            val normal = ctx.data.getInt("normal", 20)
            val long = ctx.data.getInt("long", 60)
            // TODO: CombatSystem.setThrownRange(ctx.entity, ctx.item, normal, long, ctx.source)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.clearThrownRange(ctx.entity, ctx.item, ctx.source)
        }
    }

    /**
     * Двуручное — требует две руки.
     *
     * JSON: `{"id": "boundbyfate-core:two_handed"}`
     */
    object TwoHanded : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "two_handed")

        override fun onEquip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.addWeaponFlag(ctx.entity, ctx.item, "two_handed", ctx.source)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.removeWeaponFlag(ctx.entity, ctx.item, "two_handed", ctx.source)
        }
    }

    /**
     * Лёгкое — можно использовать для двойного оружия.
     *
     * JSON: `{"id": "boundbyfate-core:light"}`
     */
    object Light : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "light")

        override fun onEquip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.addWeaponFlag(ctx.entity, ctx.item, "light", ctx.source)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.removeWeaponFlag(ctx.entity, ctx.item, "light", ctx.source)
        }
    }

    /**
     * Досягаемость — увеличенная дальность атаки.
     *
     * JSON: `{"id": "boundbyfate-core:reach"}`
     */
    object Reach : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "reach")

        override fun onEquip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.addReach(ctx.entity, ctx.item, 5, ctx.source)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            // TODO: CombatSystem.removeReach(ctx.entity, ctx.item, ctx.source)
        }
    }

    /**
     * Тяжёлое — маленькие существа имеют помеху на атаки.
     *
     * JSON: `{"id": "boundbyfate-core:heavy"}`
     */
    object Heavy : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "heavy")

        override fun onEquip(ctx: ItemPropertyContext) {
            // TODO: если существо Small или меньше — добавить помеху на атаки
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            // TODO: убрать помеху
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Свойства брони
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Класс доспеха — формула AC.
     *
     * JSON:
     * ```json
     * {"id": "boundbyfate-core:armor_class", "data": {"formula": "16"}}
     * {"id": "boundbyfate-core:armor_class", "data": {"formula": "13 + min(@dex_modifier, 2)"}}
     * {"id": "boundbyfate-core:armor_class", "data": {"formula": "10 + @dex_modifier"}}
     * ```
     */
    object ArmorClass : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "armor_class")

        override fun onEquip(ctx: ItemPropertyContext) {
            val formula = ctx.data.requireString("formula")
            ctx.setArmorClassFormula(formula)
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            ctx.removeArmorClassFormula()
        }
    }

    /**
     * Помеха на Скрытность (тяжёлая броня).
     *
     * JSON: `{"id": "boundbyfate-core:stealth_disadvantage"}`
     */
    object StealthDisadvantage : ItemPropertyHandler() {
        override val id = Identifier("boundbyfate-core", "stealth_disadvantage")

        override fun onEquip(ctx: ItemPropertyContext) {
            ctx.addDisadvantage(listOf(Identifier("boundbyfate-core", "stealth")))
        }

        override fun onUnequip(ctx: ItemPropertyContext) {
            ctx.removeDisadvantage(listOf(Identifier("boundbyfate-core", "stealth")))
        }
    }
}

