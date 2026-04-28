package omc.boundbyfate.system.relation

import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import omc.boundbyfate.api.relation.Relation
import omc.boundbyfate.api.relation.RelationKey
import omc.boundbyfate.api.relation.RelationParty
import omc.boundbyfate.api.relation.RelationStatus
import omc.boundbyfate.api.relation.event.AfterRelationShiftEvent
import omc.boundbyfate.api.relation.event.BeforeRelationShiftEvent
import omc.boundbyfate.api.relation.event.RelationEvents
import omc.boundbyfate.api.relation.event.RelationStatusChangedEvent
import omc.boundbyfate.api.relation.event.RelationTagAddedEvent
import omc.boundbyfate.api.relation.event.RelationTagRemovedEvent
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.sections.RelationSection
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Система управления отношениями.
 *
 * Единая система для всех типов отношений:
 * - Организация ↔ Организация
 * - Персонаж ↔ Организация (репутация)
 * - Персонаж ↔ Персонаж
 *
 * На каждом этапе публикует события через [RelationEvents].
 * Пересчёт рангов при изменении репутации происходит автоматически
 * через подписку на [RelationEvents.AFTER_SHIFT].
 */
object RelationSystem {

    private val logger = LoggerFactory.getLogger(RelationSystem::class.java)

    // ── Получение ─────────────────────────────────────────────────────────

    fun get(world: ServerWorld, from: RelationParty, to: RelationParty): Relation {
        val key = RelationKey(from, to)
        return getWorldData(world).relations[key] ?: Relation()
    }

    fun getValue(world: ServerWorld, from: RelationParty, to: RelationParty): Int =
        get(world, from, to).value

    fun getStatus(world: ServerWorld, from: RelationParty, to: RelationParty): RelationStatus =
        get(world, from, to).status

    fun hasTag(world: ServerWorld, from: RelationParty, to: RelationParty, tag: String): Boolean =
        get(world, from, to).hasTag(tag)

    // ── Изменение ─────────────────────────────────────────────────────────

    /**
     * Сдвигает значение отношения.
     *
     * Поток:
     * ```
     * BEFORE_SHIFT (отменяемый, delta можно изменить)
     *     ↓
     * изменяем значение
     *     ↓
     * AFTER_SHIFT
     *     ↓ (если статус изменился)
     * ON_STATUS_CHANGE
     * ```
     */
    fun shift(
        world: ServerWorld,
        from: RelationParty,
        to: RelationParty,
        delta: Int,
        description: String
    ) {
        val key = RelationKey(from, to)
        val before = get(world, from, to)

        // BEFORE_SHIFT — можно отменить или изменить delta
        val beforeEvent = BeforeRelationShiftEvent(world, key, before, delta, description)
        RelationEvents.BEFORE_SHIFT.invokeCancellable(beforeEvent) {
            it.onBeforeShift(beforeEvent)
        }

        if (beforeEvent.isCancelled) {
            logger.debug("Relation shift cancelled by BEFORE_SHIFT: $key")
            return
        }

        val effectiveDelta = beforeEvent.delta
        val after = before.shift(effectiveDelta, description, world.time)
        setRelation(world, key, after)

        logger.info(
            "Relation $key shifted by $effectiveDelta ($description): " +
            "${before.value} → ${after.value} (${after.status})"
        )

        // AFTER_SHIFT
        val afterEvent = AfterRelationShiftEvent(world, key, before, after, effectiveDelta, description)
        RelationEvents.AFTER_SHIFT.invoke { it.onAfterShift(afterEvent) }

        // ON_STATUS_CHANGE — только если статус изменился
        if (before.status != after.status) {
            val statusEvent = RelationStatusChangedEvent(world, key, before.status, after.status, after)
            RelationEvents.ON_STATUS_CHANGE.invoke { it.onStatusChanged(statusEvent) }
            logger.info("Relation status changed: $key ${before.status} → ${after.status}")
        }
    }

    /**
     * Устанавливает значение напрямую (GM команда).
     */
    fun setValue(
        world: ServerWorld,
        from: RelationParty,
        to: RelationParty,
        value: Int,
        description: String = "GM override"
    ) {
        val delta = value - getValue(world, from, to)
        if (delta != 0) shift(world, from, to, delta, description)
    }

    /**
     * Добавляет тег к отношению.
     */
    fun addTag(world: ServerWorld, from: RelationParty, to: RelationParty, tag: String) {
        val key = RelationKey(from, to)
        val updated = get(world, from, to).withTag(tag)
        setRelation(world, key, updated)

        RelationEvents.ON_TAG_ADDED.invoke {
            it.onTagAdded(RelationTagAddedEvent(world, key, tag, updated))
        }
        logger.info("Added tag '$tag' to relation $key")
    }

    /**
     * Удаляет тег из отношения.
     */
    fun removeTag(world: ServerWorld, from: RelationParty, to: RelationParty, tag: String) {
        val key = RelationKey(from, to)
        val updated = get(world, from, to).withoutTag(tag)
        setRelation(world, key, updated)

        RelationEvents.ON_TAG_REMOVED.invoke {
            it.onTagRemoved(RelationTagRemovedEvent(world, key, tag, updated))
        }
        logger.info("Removed tag '$tag' from relation $key")
    }

    // ── Удобные методы для репутации ──────────────────────────────────────

    fun getReputation(world: ServerWorld, characterUuid: UUID, orgId: Identifier): Int =
        getValue(
            world,
            RelationParty.Character(characterUuid),
            RelationParty.Organization(orgId)
        )

    fun shiftReputation(
        world: ServerWorld,
        characterUuid: UUID,
        orgId: Identifier,
        delta: Int,
        description: String
    ) = shift(
        world,
        RelationParty.Character(characterUuid),
        RelationParty.Organization(orgId),
        delta,
        description
    )

    // ── Внутренняя логика ─────────────────────────────────────────────────

    private fun setRelation(world: ServerWorld, key: RelationKey, relation: Relation) {
        getSection(world).relations[key] = relation
    }

    private fun getSection(world: ServerWorld) =
        BbfWorldData.get(world).getSection(RelationSection.TYPE)
}
