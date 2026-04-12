package omc.boundbyfate.api.combat

/**
 * D&D 5e weapon properties.
 *
 * Each property is backed by an item tag in:
 * data/boundbyfate-core/tags/items/weapon_property/<name>.json
 */
enum class WeaponProperty(val displayName: String) {
    /** Requires two hands. Blocks offhand slot usage. */
    TWO_HANDED("Двуручное"),

    /** +1 reach range via ATTACK_RANGE attribute. */
    REACH("Досягаемость"),

    /** Can be dual-wielded. Enables offhand attack. */
    LIGHT("Лёгкое"),

    /** Small/Tiny creatures attack with disadvantage. */
    HEAVY("Тяжёлое"),

    /** Can be used one or two-handed (two-handed deals more damage). */
    VERSATILE("Универсальное"),

    /** Use STR or DEX modifier, whichever is higher. */
    FINESSE("Фехтовальное"),

    /** Can be thrown as a ranged attack. */
    THROWN("Метательное"),

    /** Requires an action to reload between shots. */
    LOADING("Перезарядка")
}
