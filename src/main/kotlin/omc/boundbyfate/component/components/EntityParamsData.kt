package omc.boundbyfate.component.components

import omc.boundbyfate.api.race.CreatureSize
import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.BbfComponents
import omc.boundbyfate.component.core.SyncMode

/**
 * Физические параметры entity: HP, размер, скорость, голод.
 *
 * ## HP
 *
 * `currentHits` и `maxHits` зеркалятся в ванильные Minecraft атрибуты
 * `entity.health` и `entity.maxHealth`. Компонент хранит значения
 * для синхронизации с клиентом и сохранения в snapshot.
 *
 * ## Размер
 *
 * `basicSize` — от расы, не меняется.
 * `currentSize` — с учётом эффектов (Enlarge/Reduce).
 * `basicModelScale` / `currentModelScale` — для Pekhui API.
 *
 * ## Скорость
 *
 * `basicSpeed` — базовая скорость от расы (в футах D&D).
 * `currentSpeed` — с учётом эффектов (Haste, Slow, тяжёлая броня).
 *
 * ## Голод
 *
 * Зеркало ванильного hunger. Расы могут изменять механику голода
 * через эффекты (некоторые расы не голодают).
 */
class EntityParamsData : BbfComponent() {

    // ── HP ────────────────────────────────────────────────────────────────

    /** Текущие HP. Зеркалится в entity.health. */
    var currentHits by synced(20f)

    /** Максимальные HP. Зеркалится в entity.maxHealth. */
    var maxHits by synced(20f)

    /** Временные HP (от False Life, Aid и т.д.). */
    var temporaryHits by synced(0)

    // ── Размер ────────────────────────────────────────────────────────────

    /** Базовый размер от расы. */
    var basicSize by synced(CreatureSize.MEDIUM, CreatureSize.CODEC)

    /** Текущий размер с учётом эффектов (Enlarge/Reduce). */
    var currentSize by synced(CreatureSize.MEDIUM, CreatureSize.CODEC)

    /** Базовый масштаб модели от расы (для Pekhui). */
    var basicModelScale by synced(1.0f)

    /** Текущий масштаб модели с учётом эффектов. */
    var currentModelScale by synced(1.0f)

    // ── Скорость ──────────────────────────────────────────────────────────

    /** Базовая скорость от расы (в футах D&D, например 30). */
    var basicSpeed by synced(30)

    /** Текущая скорость с учётом эффектов. */
    var currentSpeed by synced(30)

    // ── Голод ─────────────────────────────────────────────────────────────

    /**
     * Текущий уровень голода (0–20, зеркало ванильного).
     * Расы могут изменять механику через эффекты.
     */
    var hunger by synced(20)

    // ── Удобные методы ────────────────────────────────────────────────────

    /** Проверяет изменён ли размер эффектами. */
    val isSizeModified: Boolean get() = currentSize != basicSize

    /** Проверяет изменён ли масштаб модели. */
    val isScaleModified: Boolean get() = currentModelScale != basicModelScale

    companion object {
        val TYPE = BbfComponents.register(
            id = "boundbyfate-core:params",
            syncMode = SyncMode.ON_CHANGE,
            factory = ::EntityParamsData
        )
    }
}
