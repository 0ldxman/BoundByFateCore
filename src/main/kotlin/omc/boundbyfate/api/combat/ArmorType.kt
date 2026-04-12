package omc.boundbyfate.api.combat

/**
 * D&D armor category determining DEX bonus cap.
 */
enum class ArmorType(val dexCap: Int) {
    /** No armor: full DEX bonus */
    NONE(Int.MAX_VALUE),
    /** Light armor: full DEX bonus */
    LIGHT(Int.MAX_VALUE),
    /** Medium armor: DEX bonus capped at +2 */
    MEDIUM(2),
    /** Heavy armor: no DEX bonus */
    HEAVY(0)
}
