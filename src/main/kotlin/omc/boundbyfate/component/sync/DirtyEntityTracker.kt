package omc.boundbyfate.component.sync

import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.ChunkPos
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Трекер сущностей с грязными (изменёнными) компонентами.
 *
 * Вместо O(n) итерации по всем сущностям каждый тик —
 * сущности добавляются сюда только когда их компонент реально изменился.
 *
 * ## Использование
 *
 * Из компонента при изменении:
 * ```kotlin
 * // В BbfComponent.markDirty() — вызывается автоматически через synced-делегаты
 * DirtyEntityTracker.markDirty(this)
 * ```
 *
 * Из ComponentSyncHandler в тике:
 * ```kotlin
 * val dirty = DirtyEntityTracker.drainDirty()
 * for (entity in dirty) { syncDirtyComponents(entity) }
 * ```
 */
object DirtyEntityTracker {

    /**
     * Множество сущностей у которых есть dirty компоненты.
     * ConcurrentHashMap.newKeySet() — потокобезопасно, т.к. markDirty
     * может вызываться из разных потоков (хотя Minecraft однопоточный,
     * лучше перестраховаться).
     */
    private val dirtyEntities: MutableSet<LivingEntity> =
        Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * Помечает сущность как имеющую dirty компоненты.
     * Вызывается из [omc.boundbyfate.component.core.BbfComponent.markDirty].
     *
     * @param entity сущность с изменённым компонентом
     */
    fun markDirty(entity: LivingEntity) {
        dirtyEntities += entity
    }

    /**
     * Возвращает и очищает текущий набор dirty сущностей.
     *
     * Атомарная операция: снимок + очистка.
     * Новые dirty сущности добавленные во время обработки попадут в следующий тик.
     *
     * @return снимок dirty сущностей на момент вызова
     */
    fun drainDirty(): Set<LivingEntity> {
        if (dirtyEntities.isEmpty()) return emptySet()
        val snapshot = dirtyEntities.toHashSet()
        dirtyEntities.removeAll(snapshot)
        return snapshot
    }

    /**
     * Удаляет сущность из трекера (при удалении из мира).
     */
    fun remove(entity: LivingEntity) {
        dirtyEntities -= entity
    }

    /**
     * Количество dirty сущностей (для диагностики).
     */
    fun size(): Int = dirtyEntities.size
}
