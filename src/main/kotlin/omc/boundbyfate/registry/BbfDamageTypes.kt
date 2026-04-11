package omc.boundbyfate.registry

import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier

/**
 * Registry keys for BoundByFate D&D damage types.
 *
 * These correspond to JSON files in data/boundbyfate-core/damage_type/.
 *
 * For physical damage types (piercing, slashing, bludgeoning) that already
 * exist conceptually in Minecraft, we provide our own typed versions.
 *
 * For magical/elemental types that don't exist in vanilla, we register new ones.
 *
 * Usage:
 * ```kotlin
 * val source = BbfDamage.of(world, BbfDamageTypes.NECROTIC)
 * entity.damage(source, 5.0f)
 * ```
 */
object BbfDamageTypes {

    // ── Physical ─────────────────────────────────────────────────────────────

    /** Колющий урон (стрелы, копья, укусы) */
    val PIERCING = key("piercing")

    /** Рубящий урон (мечи, топоры, когти) */
    val SLASHING = key("slashing")

    /** Дробящий урон (кулаки, молоты, падение) */
    val BLUDGEONING = key("bludgeoning")

    // ── Elemental / Magical ───────────────────────────────────────────────────

    /** Кислотный урон */
    val ACID = key("acid")

    /** Криотический урон (холод, лёд) */
    val COLD = key("cold")

    /** Силовой урон (магическая сила) */
    val FORCE = key("force")

    /** Урон молнией */
    val LIGHTNING = key("lightning")

    /** Некротический урон (тёмная магия, нежить) */
    val NECROTIC = key("necrotic")

    /** Ядовитый урон */
    val POISON = key("poison")

    /** Психический урон (ментальная магия) */
    val PSYCHIC = key("psychic")

    /** Урон излучением (священная магия) */
    val RADIANT = key("radiant")

    /** Громовой урон (звуковая волна) */
    val THUNDER = key("thunder")

    // Note: FIRE is not listed here - use minecraft:on_fire / minecraft:fire directly

    private fun key(path: String) = RegistryKey.of(
        RegistryKeys.DAMAGE_TYPE,
        Identifier("boundbyfate-core", path)
    )
}
