package omc.boundbyfate.component.components

import net.minecraft.util.Identifier
import omc.boundbyfate.api.status.ActiveStatus
import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.SyncMode
import omc.boundbyfate.util.source.SourceReference
import omc.boundbyfate.util.time.Duration

/**
 * Активные состояния (Status Conditions) на entity.
 *
 * Хранит список активных состояний.
 * Состояния применяются через StatusSystem — компонент только хранит факт активности.
 *
 * Состояния работают вне боя (отравление, невидимость, благословение).
 *
 * ## Snapshot
 *
 * При сохранении персонажа в snapshot попадают состояния с
 * `duration = UntilEvent`, `Permanent` или `UntilSave`.
 * Состояния с `Ticks` — только если не истекли.
 */
class EntityStatusesData : BbfComponent() {

    /** Список активных состояний. */
    val activeStatuses by syncedList(ActiveStatus.CODEC)

    /** Проверяет наличие состояния по ID. */
    fun hasStatus(statusId: Identifier): Boolean =
        activeStatuses.any { it.statusId == statusId }

    /** Возвращает активное состояние по ID или null. */
    fun getStatus(statusId: Identifier): ActiveStatus? =
        activeStatuses.find { it.statusId == statusId }

    /** Добавляет состояние. */
    fun addStatus(status: ActiveStatus) {
        activeStatuses.add(status)
    }

    /** Удаляет состояние по ID. Возвращает true если было удалено. */
    fun removeStatus(statusId: Identifier): Boolean =
        activeStatuses.removeIf { it.statusId == statusId }

    /** Обновляет длительность состояния. */
    fun updateDuration(statusId: Identifier, newDuration: Duration, currentTick: Long) {
        val index = activeStatuses.indexOfFirst { it.statusId == statusId }
        if (index == -1) return
        val existing = activeStatuses[index]
        activeStatuses[index] = existing.copy(
            duration = newDuration,
            appliedAtTick = currentTick
        )
    }

    /** Возвращает все ID активных состояний. */
    fun getActiveStatusIds(): List<Identifier> = activeStatuses.map { it.statusId }

    companion object {
        val TYPE = BbfComponents.register(
            id = "boundbyfate-core:statuses",
            syncMode = SyncMode.ON_CHANGE,
            factory = ::EntityStatusesData
        )
    }
}
