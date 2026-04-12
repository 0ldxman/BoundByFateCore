package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.resource.RecoveryType
import omc.boundbyfate.api.resource.ResourceDefinition

/**
 * Built-in D&D 5e resource pool definitions.
 *
 * These are definitions only - actual values per entity are stored in EntityResourceData.
 * Classes assign these resources to players with appropriate maximums.
 */
object BbfResources {

    // ── Spell Slots (all classes) ─────────────────────────────────────────────

    val SPELL_SLOT_1 = resource("spell_slot_1", "Ячейки заклинаний 1 уровня", RecoveryType.LONG_REST)
    val SPELL_SLOT_2 = resource("spell_slot_2", "Ячейки заклинаний 2 уровня", RecoveryType.LONG_REST)
    val SPELL_SLOT_3 = resource("spell_slot_3", "Ячейки заклинаний 3 уровня", RecoveryType.LONG_REST)
    val SPELL_SLOT_4 = resource("spell_slot_4", "Ячейки заклинаний 4 уровня", RecoveryType.LONG_REST)
    val SPELL_SLOT_5 = resource("spell_slot_5", "Ячейки заклинаний 5 уровня", RecoveryType.LONG_REST)
    val SPELL_SLOT_6 = resource("spell_slot_6", "Ячейки заклинаний 6 уровня", RecoveryType.LONG_REST)
    val SPELL_SLOT_7 = resource("spell_slot_7", "Ячейки заклинаний 7 уровня", RecoveryType.LONG_REST)
    val SPELL_SLOT_8 = resource("spell_slot_8", "Ячейки заклинаний 8 уровня", RecoveryType.LONG_REST)
    val SPELL_SLOT_9 = resource("spell_slot_9", "Ячейки заклинаний 9 уровня", RecoveryType.LONG_REST)

    // ── Barbarian ─────────────────────────────────────────────────────────────

    val RAGE = resource("rage", "Ярость", RecoveryType.LONG_REST, defaultMaximum = 2)

    // ── Monk ──────────────────────────────────────────────────────────────────

    val KI_POINTS = resource("ki_points", "Очки ки", RecoveryType.SHORT_REST)

    // ── Fighter ───────────────────────────────────────────────────────────────

    val SECOND_WIND = resource("second_wind", "Второе дыхание", RecoveryType.SHORT_REST, defaultMaximum = 1)
    val ACTION_SURGE = resource("action_surge", "Всплеск действий", RecoveryType.SHORT_REST, defaultMaximum = 1)

    // ── Fighter (Battle Master) ───────────────────────────────────────────────

    val SUPERIORITY_DICE = resource("superiority_dice", "Кости превосходства", RecoveryType.SHORT_REST)

    // ── Paladin ───────────────────────────────────────────────────────────────

    val DIVINE_SENSE = resource("divine_sense", "Божественное чувство", RecoveryType.LONG_REST)
    val LAY_ON_HANDS = resource("lay_on_hands", "Наложение рук", RecoveryType.LONG_REST)

    // ── Bard ──────────────────────────────────────────────────────────────────

    val BARDIC_INSPIRATION = resource("bardic_inspiration", "Бардовское вдохновение", RecoveryType.LONG_REST)

    // ── Druid ─────────────────────────────────────────────────────────────────

    val WILD_SHAPE = resource("wild_shape", "Дикий облик", RecoveryType.SHORT_REST, defaultMaximum = 2)

    // ── Warlock (special - recovers on short rest) ────────────────────────────

    val WARLOCK_SPELL_SLOT = resource("warlock_spell_slot", "Ячейки мистика", RecoveryType.SHORT_REST)

    // ── Sorcerer ──────────────────────────────────────────────────────────────

    val SORCERY_POINTS = resource("sorcery_points", "Очки чародейства", RecoveryType.LONG_REST)

    // ── Ranger ───────────────────────────────────────────────────────────────

    val FAVORED_FOE = resource("favored_foe", "Любимый враг", RecoveryType.LONG_REST)

    /**
     * Registers all built-in resources.
     * Called during mod initialization.
     */
    fun register() {
        ResourceRegistry.register(SPELL_SLOT_1)
        ResourceRegistry.register(SPELL_SLOT_2)
        ResourceRegistry.register(SPELL_SLOT_3)
        ResourceRegistry.register(SPELL_SLOT_4)
        ResourceRegistry.register(SPELL_SLOT_5)
        ResourceRegistry.register(SPELL_SLOT_6)
        ResourceRegistry.register(SPELL_SLOT_7)
        ResourceRegistry.register(SPELL_SLOT_8)
        ResourceRegistry.register(SPELL_SLOT_9)
        ResourceRegistry.register(RAGE)
        ResourceRegistry.register(KI_POINTS)
        ResourceRegistry.register(SUPERIORITY_DICE)
        ResourceRegistry.register(SECOND_WIND)
        ResourceRegistry.register(ACTION_SURGE)
        ResourceRegistry.register(DIVINE_SENSE)
        ResourceRegistry.register(LAY_ON_HANDS)
        ResourceRegistry.register(BARDIC_INSPIRATION)
        ResourceRegistry.register(WILD_SHAPE)
        ResourceRegistry.register(WARLOCK_SPELL_SLOT)
        ResourceRegistry.register(SORCERY_POINTS)
        ResourceRegistry.register(FAVORED_FOE)
    }

    private fun resource(
        path: String,
        displayName: String,
        recoveryType: RecoveryType,
        defaultMaximum: Int = 0
    ) = ResourceDefinition(
        id = Identifier("boundbyfate-core", path),
        displayName = displayName,
        recoveryType = recoveryType,
        defaultMaximum = defaultMaximum
    )
}
