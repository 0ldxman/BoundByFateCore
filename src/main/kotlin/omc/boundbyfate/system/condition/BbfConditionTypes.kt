package omc.boundbyfate.system.condition

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.entity.LivingEntity
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.EntityTypeTags
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import omc.boundbyfate.api.condition.ConditionType
import omc.boundbyfate.api.dice.AdvantageType
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Все встроенные типы условий BoundByFate Core.
 *
 * Каждый тип — это [ConditionType], который регистрируется автоматически
 * при обращении к этому объекту.
 *
 * ## Инициализация
 *
 * ```kotlin
 * BbfConditionTypes  // все val инициализируются, все типы регистрируются
 * ```
 *
 * ## Добавление нового встроенного типа
 *
 * ```kotlin
 * val MY_CONDITION = ConditionType.register(
 *     id = "boundbyfate-core:my_condition",
 *     codec = ...,
 *     evaluate = { data, ctx -> ... }
 * )
 * ```
 *
 * ## Логические операторы
 *
 * `or`, `and`, `not` — встроены в [omc.boundbyfate.api.condition.Condition]
 * и обрабатываются в [ConditionSystem] рекурсивно. Регистрировать не нужно.
 */
object BbfConditionTypes {

    // ── Данные условий ─────────────────────────────────────────────────────

    data class WearingArmorData(val armorType: String?)
    data class UsingShieldData(val using: Boolean)
    data class HpBelowData(val percent: Int)
    data class AllyWithinData(val distance: Double)
    data class HasConditionData(val condition: Identifier)
    data class WeaponPropertyData(val property: String)
    data class WeaponTypeData(val weaponType: Identifier)

    // ── Условия оружия ─────────────────────────────────────────────────────

    /**
     * Проверяет свойство оружия (finesse, heavy, two_handed и т.д.).
     * JSON: `{"type": "boundbyfate-core:weapon_property", "property": "finesse"}`
     */
    val WEAPON_PROPERTY = ConditionType.register(
        id = "boundbyfate-core:weapon_property",
        codec = RecordCodecBuilder.create { i ->
            i.group(Codec.STRING.fieldOf("property").forGetter { it.property })
             .apply(i, ::WeaponPropertyData)
        },
        evaluate = { data, ctx ->
            val weapon = ctx.weapon ?: return@register false
            // TODO: WeaponRegistry.hasProperty(weapon, data.property)
            false
        }
    )

    /**
     * Проверяет тип оружия.
     * JSON: `{"type": "boundbyfate-core:weapon_type", "weapon_type": "boundbyfate-core:sword"}`
     */
    val WEAPON_TYPE = ConditionType.register(
        id = "boundbyfate-core:weapon_type",
        codec = RecordCodecBuilder.create { i ->
            i.group(CodecUtil.IDENTIFIER.fieldOf("weapon_type").forGetter { it.weaponType })
             .apply(i, ::WeaponTypeData)
        },
        evaluate = { data, ctx ->
            val weapon = ctx.weapon ?: return@register false
            // TODO: WeaponRegistry.isType(weapon, data.weaponType)
            false
        }
    )

    // ── Условия экипировки ─────────────────────────────────────────────────

    /**
     * Проверяет, носит ли персонаж броню (опционально — конкретного типа).
     * JSON: `{"type": "boundbyfate-core:wearing_armor"}` или
     *       `{"type": "boundbyfate-core:wearing_armor", "armor_type": "heavy"}`
     */
    val WEARING_ARMOR = ConditionType.register(
        id = "boundbyfate-core:wearing_armor",
        codec = RecordCodecBuilder.create { i ->
            i.group(
                Codec.STRING.optionalFieldOf("armor_type")
                    .forGetter { java.util.Optional.ofNullable(it.armorType) }
            ).apply(i) { opt -> WearingArmorData(opt.orElse(null)) }
        },
        evaluate = { data, ctx ->
            val armorSlots = ctx.entity.armorItems.toList()
            if (armorSlots.all { it.isEmpty }) return@register false
            val armorType = data.armorType ?: return@register true
            armorSlots.any { armor ->
                if (armor.isEmpty) return@any false
                val tag = TagKey.of(RegistryKeys.ITEM, Identifier("boundbyfate-core", "armor_type/$armorType"))
                armor.isIn(tag)
            }
        }
    )

    /**
     * Проверяет, НЕ носит ли персонаж броню.
     * JSON: `{"type": "boundbyfate-core:not_wearing_armor"}`
     */
    val NOT_WEARING_ARMOR = ConditionType.register(
        id = "boundbyfate-core:not_wearing_armor",
        codec = Codec.unit(Unit),
        evaluate = { _, ctx -> ctx.entity.armorItems.all { it.isEmpty } }
    )

    /**
     * Проверяет, использует ли персонаж щит.
     * JSON: `{"type": "boundbyfate-core:using_shield"}` или
     *       `{"type": "boundbyfate-core:using_shield", "using": false}`
     */
    val USING_SHIELD = ConditionType.register(
        id = "boundbyfate-core:using_shield",
        codec = RecordCodecBuilder.create { i ->
            i.group(Codec.BOOL.optionalFieldOf("using", true).forGetter { it.using })
             .apply(i, ::UsingShieldData)
        },
        evaluate = { data, ctx ->
            val offhand = ctx.entity.offHandStack
            val hasShield = !offhand.isEmpty && offhand.isIn(
                TagKey.of(RegistryKeys.ITEM, Identifier("minecraft", "shields"))
            )
            if (data.using) hasShield else !hasShield
        }
    )

    // ── Условия персонажа ──────────────────────────────────────────────────

    /**
     * Проверяет, есть ли преимущество на текущий бросок.
     * JSON: `{"type": "boundbyfate-core:has_advantage"}`
     */
    val HAS_ADVANTAGE = ConditionType.register(
        id = "boundbyfate-core:has_advantage",
        codec = Codec.unit(Unit),
        evaluate = { _, ctx -> ctx.advantageType == AdvantageType.ADVANTAGE }
    )

    /**
     * Проверяет, есть ли помеха на текущий бросок.
     * JSON: `{"type": "boundbyfate-core:has_disadvantage"}`
     */
    val HAS_DISADVANTAGE = ConditionType.register(
        id = "boundbyfate-core:has_disadvantage",
        codec = Codec.unit(Unit),
        evaluate = { _, ctx -> ctx.advantageType == AdvantageType.DISADVANTAGE }
    )

    /**
     * Проверяет, ниже ли HP определённого процента.
     * JSON: `{"type": "boundbyfate-core:hp_below", "percent": 50}`
     */
    val HP_BELOW = ConditionType.register(
        id = "boundbyfate-core:hp_below",
        codec = RecordCodecBuilder.create { i ->
            i.group(Codec.INT.fieldOf("percent").forGetter { it.percent })
             .apply(i, ::HpBelowData)
        },
        evaluate = { data, ctx ->
            val entity = ctx.entity
            if (entity.maxHealth <= 0f) return@register false
            (entity.health / entity.maxHealth * 100).toInt() < data.percent
        }
    )

    /**
     * Проверяет, есть ли союзник в пределах дистанции (в блоках).
     * JSON: `{"type": "boundbyfate-core:ally_within", "distance": 5.0}`
     */
    val ALLY_WITHIN = ConditionType.register(
        id = "boundbyfate-core:ally_within",
        codec = RecordCodecBuilder.create { i ->
            i.group(Codec.DOUBLE.fieldOf("distance").forGetter { it.distance })
             .apply(i, ::AllyWithinData)
        },
        evaluate = { data, ctx ->
            val entity = ctx.entity
            val radiusSq = data.distance * data.distance
            entity.world.getEntitiesByClass(
                LivingEntity::class.java,
                entity.boundingBox.expand(data.distance)
            ) { other ->
                other != entity && other != ctx.target &&
                !other.isDead && entity.squaredDistanceTo(other) <= radiusSq
            }.isNotEmpty()
        }
    )

    /**
     * Проверяет, есть ли у персонажа определённое состояние.
     * JSON: `{"type": "boundbyfate-core:has_condition", "condition": "dnd:poisoned"}`
     */
    val HAS_CONDITION = ConditionType.register(
        id = "boundbyfate-core:has_condition",
        codec = RecordCodecBuilder.create { i ->
            i.group(CodecUtil.IDENTIFIER.fieldOf("condition").forGetter { it.condition })
             .apply(i, ::HasConditionData)
        },
        evaluate = { data, ctx ->
            omc.boundbyfate.system.status.StatusSystem.hasStatus(ctx.entity, data.condition)
        }
    )

    /**
     * Проверяет, является ли цель нежитью.
     * JSON: `{"type": "boundbyfate-core:target_is_undead"}`
     */
    val TARGET_IS_UNDEAD = ConditionType.register(
        id = "boundbyfate-core:target_is_undead",
        codec = Codec.unit(Unit),
        evaluate = { _, ctx -> ctx.target?.type?.isIn(EntityTypeTags.UNDEAD) ?: false }
    )
}

