package omc.boundbyfate.system.condition

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.entity.LivingEntity
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import omc.boundbyfate.api.condition.ConditionType
import omc.boundbyfate.api.dice.AdvantageType
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Все встроенные типы условий BoundByFate Core.
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

    val WEAPON_PROPERTY = ConditionType.register(
        id = "boundbyfate-core:weapon_property",
        codec = RecordCodecBuilder.create<WeaponPropertyData> { i ->
            i.group(Codec.STRING.fieldOf("property").forGetter(WeaponPropertyData::property))
             .apply(i, ::WeaponPropertyData)
        },
        evaluate = { data, ctx ->
            val weapon = ctx.weapon ?: return@register false
            false
        }
    )

    val WEAPON_TYPE = ConditionType.register(
        id = "boundbyfate-core:weapon_type",
        codec = RecordCodecBuilder.create<WeaponTypeData> { i ->
            i.group(CodecUtil.IDENTIFIER.fieldOf("weapon_type").forGetter(WeaponTypeData::weaponType))
             .apply(i, ::WeaponTypeData)
        },
        evaluate = { data, ctx ->
            val weapon = ctx.weapon ?: return@register false
            false
        }
    )

    // ── Условия экипировки ─────────────────────────────────────────────────

    val WEARING_ARMOR = ConditionType.register(
        id = "boundbyfate-core:wearing_armor",
        codec = RecordCodecBuilder.create<WearingArmorData> { i ->
            i.group(
                Codec.STRING.optionalFieldOf("armor_type")
                    .forGetter { d: WearingArmorData -> java.util.Optional.ofNullable(d.armorType) }
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

    val NOT_WEARING_ARMOR = ConditionType.register(
        id = "boundbyfate-core:not_wearing_armor",
        codec = Codec.unit(Unit),
        evaluate = { _, ctx -> ctx.entity.armorItems.all { it.isEmpty } }
    )

    val USING_SHIELD = ConditionType.register(
        id = "boundbyfate-core:using_shield",
        codec = RecordCodecBuilder.create<UsingShieldData> { i ->
            i.group(Codec.BOOL.optionalFieldOf("using", true).forGetter(UsingShieldData::using))
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

    val HAS_ADVANTAGE = ConditionType.register(
        id = "boundbyfate-core:has_advantage",
        codec = Codec.unit(Unit),
        evaluate = { _, ctx -> ctx.advantageType == AdvantageType.ADVANTAGE }
    )

    val HAS_DISADVANTAGE = ConditionType.register(
        id = "boundbyfate-core:has_disadvantage",
        codec = Codec.unit(Unit),
        evaluate = { _, ctx -> ctx.advantageType == AdvantageType.DISADVANTAGE }
    )

    val HP_BELOW = ConditionType.register(
        id = "boundbyfate-core:hp_below",
        codec = RecordCodecBuilder.create<HpBelowData> { i ->
            i.group(Codec.INT.fieldOf("percent").forGetter(HpBelowData::percent))
             .apply(i, ::HpBelowData)
        },
        evaluate = { data, ctx ->
            val entity = ctx.entity
            if (entity.maxHealth <= 0f) return@register false
            (entity.health / entity.maxHealth * 100).toInt() < data.percent
        }
    )

    val ALLY_WITHIN = ConditionType.register(
        id = "boundbyfate-core:ally_within",
        codec = RecordCodecBuilder.create<AllyWithinData> { i ->
            i.group(Codec.DOUBLE.fieldOf("distance").forGetter(AllyWithinData::distance))
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

    val HAS_CONDITION = ConditionType.register(
        id = "boundbyfate-core:has_condition",
        codec = RecordCodecBuilder.create<HasConditionData> { i ->
            i.group(CodecUtil.IDENTIFIER.fieldOf("condition").forGetter(HasConditionData::condition))
             .apply(i, ::HasConditionData)
        },
        evaluate = { data, ctx ->
            omc.boundbyfate.system.status.StatusSystem.hasStatus(ctx.entity, data.condition)
        }
    )

    val TARGET_IS_UNDEAD = ConditionType.register(
        id = "boundbyfate-core:target_is_undead",
        codec = Codec.unit(Unit),
        evaluate = { _, ctx -> ctx.target?.isUndead ?: false }
    )
}
