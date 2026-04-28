package omc.boundbyfate.component.components

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier
import omc.boundbyfate.api.action.ActionSlot
import omc.boundbyfate.api.action.ActionSlotType
import omc.boundbyfate.api.action.AttackWindow
import omc.boundbyfate.api.action.EntityActionData
import omc.boundbyfate.api.damage.ResistanceLevel
import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.SyncMode
import omc.boundbyfate.util.codec.CodecUtil

/**
 * Боевые данные entity: AC, ячейки действий, сопротивления, данные урона.
 *
 * ## Класс доспеха (AC)
 *
 * `armorClass` — итоговое значение, вычисляется системой AC
 * при надевании/снятии брони и применении эффектов.
 *
 * ## Ячейки действий
 *
 * Работают через кулдауны (не булевые флаги).
 * При загрузке персонажа сбрасываются в default — кулдауны не сохраняются.
 *
 * ## Сопротивления
 *
 * `damageResistances` — сопротивления к типам урона (от расы/класса/эффектов).
 * `statusImmunities` — иммунитеты к состояниям (нежить иммунна к отравлению).
 *
 * ## Данные урона
 *
 * `lastDamageAmount` и `lastDamageSource` — для проверки концентрации
 * (DC = max(10, damage/2)) и других реакций на урон.
 */
class EntityCombatData : BbfComponent() {

    // ── Класс доспеха ─────────────────────────────────────────────────────

    /** Итоговый класс доспеха. Вычисляется системой AC. */
    var armorClass by synced(10)

    // ── Ячейки действий ───────────────────────────────────────────────────

    /**
     * Ячейки действий с кулдаунами.
     * Ключ — тип действия.
     */
    val actionSlots by syncedMap(
        ActionSlotType.CODEC,
        ActionSlot.CODEC
    )

    /** Открытое окно атак для Extra Attack. null если нет. */
    var attackWindow by synced<AttackWindow?>(null, AttackWindow.CODEC)

    // ── Сопротивления ─────────────────────────────────────────────────────

    /**
     * Сопротивления к типам урона.
     * Ключ — ID типа урона (например "boundbyfate-core:fire").
     * Значение — уровень сопротивления.
     */
    val damageResistances by syncedMap(
        Identifier.CODEC,
        ResistanceLevel.CODEC
    )

    /**
     * Иммунитеты к состояниям.
     * Например нежить иммунна к "boundbyfate-core:poisoned".
     */
    val statusImmunities by syncedList(Identifier.CODEC)

    // ── Данные урона ──────────────────────────────────────────────────────

    /** Количество последнего полученного урона. Для DC концентрации. */
    var lastDamageAmount by synced(0f)

    /** Источник последнего урона. */
    var lastDamageSource by synced<Identifier?>(null)

    // ── Удобные методы ────────────────────────────────────────────────────

    /** Инициализирует стандартные ячейки действий. */
    fun initDefaultSlots() {
        actionSlots[ActionSlotType.ACTION] = ActionSlot.free(ActionSlotType.ACTION)
        actionSlots[ActionSlotType.BONUS_ACTION] = ActionSlot.free(ActionSlotType.BONUS_ACTION)
        actionSlots[ActionSlotType.REACTION] = ActionSlot.free(ActionSlotType.REACTION)
    }

    /** Проверяет доступность ячейки действия. */
    fun isActionAvailable(type: ActionSlotType, currentTick: Long): Boolean =
        actionSlots[type]?.isAvailable(currentTick) ?: true

    /** Проверяет иммунитет к состоянию. */
    fun isImmuneToStatus(statusId: Identifier): Boolean = statusId in statusImmunities

    /** Возвращает уровень сопротивления к типу урона. */
    fun getResistance(damageTypeId: Identifier): ResistanceLevel =
        damageResistances[damageTypeId] ?: ResistanceLevel.NORMAL

    companion object {
        val TYPE = BbfComponents.register(
            id = "boundbyfate-core:combat",
            syncMode = SyncMode.ON_CHANGE,
            factory = ::EntityCombatData
        )
    }
}
