package omc.boundbyfate.system.organization

import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import omc.boundbyfate.api.organization.CharacterMembership
import omc.boundbyfate.api.organization.Organization
import omc.boundbyfate.api.organization.event.AfterOrgJoinEvent
import omc.boundbyfate.api.organization.event.AfterOrgLeaveEvent
import omc.boundbyfate.api.organization.event.BeforeOrgJoinEvent
import omc.boundbyfate.api.organization.event.BeforeOrgLeaveEvent
import omc.boundbyfate.api.organization.event.OrgCreatedEvent
import omc.boundbyfate.api.organization.event.OrgDeletedEvent
import omc.boundbyfate.api.organization.event.OrgEvents
import omc.boundbyfate.api.organization.event.OrgRankGrantedEvent
import omc.boundbyfate.api.organization.event.OrgRankRevokedEvent
import omc.boundbyfate.api.relation.RelationParty
import omc.boundbyfate.api.relation.event.RelationEvents
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.sections.OrganizationSection
import omc.boundbyfate.data.world.sections.RelationSection
import omc.boundbyfate.system.relation.RelationSystem
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Система управления организациями.
 *
 * Публикует события через [OrgEvents] на каждом этапе.
 *
 * Автоматический пересчёт рангов при изменении репутации
 * подключён через [RelationEvents.AFTER_SHIFT] в [registerEventListeners].
 */
object OrganizationSystem {

    private val logger = LoggerFactory.getLogger(OrganizationSystem::class.java)

    /**
     * Регистрирует внутренние подписки на события.
     * Вызывается при инициализации мода.
     */
    fun registerEventListeners() {
        // Автоматический пересчёт рангов при изменении репутации
        RelationEvents.AFTER_SHIFT.register { event ->
            val charParty = event.from as? RelationParty.Character ?: return@register
            val orgParty  = event.to   as? RelationParty.Organization ?: return@register

            // Пересчитываем только если персонаж является членом организации
            if (isMember(event.world, charParty.uuid, orgParty.id)) {
                recalculateAutoRanks(event.world, charParty.uuid, orgParty.id)
            }
        }
    }

    // ── Управление организациями ──────────────────────────────────────────

    fun create(world: ServerWorld, organization: Organization) {
        getOrgSection(world).organizations[organization.id] = organization

        OrgEvents.ON_CREATED.invoke {
            it.onCreated(OrgCreatedEvent(world, organization))
        }
        logger.info("Created organization '${organization.id}' (${organization.name})")
    }

    fun delete(world: ServerWorld, orgId: Identifier) {
        getOrgSection(world).organizations.remove(orgId)

        OrgEvents.ON_DELETED.invoke {
            it.onDeleted(OrgDeletedEvent(world, orgId))
        }
        logger.info("Deleted organization '$orgId'")
    }

    fun get(world: ServerWorld, orgId: Identifier): Organization? =
        getOrgSection(world).organizations[orgId]

    fun getAll(world: ServerWorld): Collection<Organization> =
        getOrgSection(world).organizations.values

    fun update(world: ServerWorld, organization: Organization) {
        getOrgSection(world).organizations[organization.id] = organization
    }

    // ── Членство ──────────────────────────────────────────────────────────

    /**
     * Принимает персонажа в организацию.
     */
    fun join(world: ServerWorld, characterUuid: UUID, orgId: Identifier) {
        val org = get(world, orgId) ?: run {
            logger.warn("Organization '$orgId' not found")
            return
        }

        // BEFORE_JOIN — можно отменить
        val beforeEvent = BeforeOrgJoinEvent(world, characterUuid, orgId, org)
        OrgEvents.BEFORE_JOIN.invokeCancellable(beforeEvent) {
            it.onBeforeJoin(beforeEvent)
        }
        if (beforeEvent.isCancelled) {
            logger.debug("Join to '$orgId' cancelled for $characterUuid")
            return
        }

        val reputation = RelationSystem.getReputation(world, characterUuid, orgId)
        val autoRanks = org.getAutoRanksForReputation(reputation)

        val membership = CharacterMembership(
            organizationId = orgId,
            ranks = autoRanks,
            joinedAt = world.time
        )
        getMemberships(world, characterUuid)[orgId] = membership

        // AFTER_JOIN
        OrgEvents.AFTER_JOIN.invoke {
            it.onAfterJoin(AfterOrgJoinEvent(world, characterUuid, orgId, org, membership))
        }
        logger.info("Character $characterUuid joined '$orgId' with ranks: $autoRanks")
    }

    /**
     * Исключает персонажа из организации.
     */
    fun leave(world: ServerWorld, characterUuid: UUID, orgId: Identifier) {
        val org = get(world, orgId) ?: return
        val membership = getMembership(world, characterUuid, orgId) ?: return

        // BEFORE_LEAVE — можно отменить
        val beforeEvent = BeforeOrgLeaveEvent(world, characterUuid, orgId, org, membership)
        OrgEvents.BEFORE_LEAVE.invokeCancellable(beforeEvent) {
            it.onBeforeLeave(beforeEvent)
        }
        if (beforeEvent.isCancelled) {
            logger.debug("Leave from '$orgId' cancelled for $characterUuid")
            return
        }

        getMemberships(world, characterUuid).remove(orgId)

        OrgEvents.AFTER_LEAVE.invoke {
            it.onAfterLeave(AfterOrgLeaveEvent(world, characterUuid, orgId, org))
        }
        logger.info("Character $characterUuid left '$orgId'")
    }

    fun isMember(world: ServerWorld, characterUuid: UUID, orgId: Identifier): Boolean =
        getMemberships(world, characterUuid).containsKey(orgId)

    fun getMembership(world: ServerWorld, characterUuid: UUID, orgId: Identifier): CharacterMembership? =
        getMemberships(world, characterUuid)[orgId]

    fun getAllMemberships(world: ServerWorld, characterUuid: UUID): Map<Identifier, CharacterMembership> =
        getMemberships(world, characterUuid).toMap()

    // ── Ранги ─────────────────────────────────────────────────────────────

    fun hasRank(world: ServerWorld, characterUuid: UUID, orgId: Identifier, rankId: String): Boolean =
        getMembership(world, characterUuid, orgId)?.hasRank(rankId) ?: false

    /**
     * Назначает ранг вручную (GM).
     */
    fun grantRank(world: ServerWorld, characterUuid: UUID, orgId: Identifier, rankId: String) {
        val org = get(world, orgId) ?: return
        if (!org.hasRank(rankId)) {
            logger.warn("Rank '$rankId' not found in organization '$orgId'")
            return
        }

        updateMembership(world, characterUuid, orgId) { it.withRank(rankId) }

        OrgEvents.ON_RANK_GRANTED.invoke {
            it.onRankGranted(OrgRankGrantedEvent(world, characterUuid, orgId, rankId, isAutomatic = false))
        }
        logger.info("Granted rank '$rankId' in '$orgId' to $characterUuid (manual)")
    }

    /**
     * Снимает ранг.
     */
    fun revokeRank(world: ServerWorld, characterUuid: UUID, orgId: Identifier, rankId: String) {
        updateMembership(world, characterUuid, orgId) { it.withoutRank(rankId) }

        OrgEvents.ON_RANK_REVOKED.invoke {
            it.onRankRevoked(OrgRankRevokedEvent(world, characterUuid, orgId, rankId, isAutomatic = false))
        }
        logger.info("Revoked rank '$rankId' in '$orgId' from $characterUuid (manual)")
    }

    /**
     * Пересчитывает авто-ранги на основе репутации.
     *
     * Вызывается автоматически через [RelationEvents.AFTER_SHIFT].
     * Ручные ранги (не в rankThresholds) не трогает.
     */
    fun recalculateAutoRanks(world: ServerWorld, characterUuid: UUID, orgId: Identifier) {
        val org = get(world, orgId) ?: return
        val membership = getMembership(world, characterUuid, orgId) ?: return
        val reputation = RelationSystem.getReputation(world, characterUuid, orgId)

        val autoRankIds = org.rankThresholds.map { it.rankId }.toSet()
        val newAutoRanks = org.getAutoRanksForReputation(reputation)
        val manualRanks = membership.ranks.filter { it !in autoRankIds }.toSet()
        val updatedRanks = manualRanks + newAutoRanks

        if (updatedRanks == membership.ranks) return  // ничего не изменилось

        // Определяем добавленные и удалённые авто-ранги
        val added = newAutoRanks - (membership.ranks.filter { it in autoRankIds }.toSet())
        val removed = (membership.ranks.filter { it in autoRankIds }.toSet()) - newAutoRanks

        updateMembership(world, characterUuid, orgId) { it.withRanks(updatedRanks) }

        // Публикуем события для каждого изменённого ранга
        for (rankId in added) {
            OrgEvents.ON_RANK_GRANTED.invoke {
                it.onRankGranted(OrgRankGrantedEvent(world, characterUuid, orgId, rankId, isAutomatic = true))
            }
        }
        for (rankId in removed) {
            OrgEvents.ON_RANK_REVOKED.invoke {
                it.onRankRevoked(OrgRankRevokedEvent(world, characterUuid, orgId, rankId, isAutomatic = true))
            }
        }

        if (added.isNotEmpty() || removed.isNotEmpty()) {
            logger.info(
                "Auto-ranks recalculated for $characterUuid in '$orgId': " +
                "+$added -$removed (rep=$reputation)"
            )
        }
    }

    // ── Внутренняя логика ─────────────────────────────────────────────────

    private fun getMemberships(
        world: ServerWorld,
        characterUuid: UUID
    ): MutableMap<Identifier, CharacterMembership> {
        val section = getOrgSection(world)
        return section.memberships.getOrPut(characterUuid) { emptyMap() }.toMutableMap()
            .also { section.memberships[characterUuid] = it }
    }

    private fun updateMembership(
        world: ServerWorld,
        characterUuid: UUID,
        orgId: Identifier,
        update: (CharacterMembership) -> CharacterMembership
    ) {
        val section = getOrgSection(world)
        val memberships = section.memberships[characterUuid]?.toMutableMap() ?: mutableMapOf()
        val current = memberships[orgId] ?: return
        memberships[orgId] = update(current)
        section.memberships[characterUuid] = memberships
    }

    private fun getOrgSection(world: ServerWorld) =
        BbfWorldData.get(world).getSection(OrganizationSection.TYPE)

    private fun getSection(world: ServerWorld) =
        BbfWorldData.get(world).getSection(RelationSection.TYPE)
}

/**
 * Временное in-memory хранилище — оставлено для обратной совместимости.
 * @deprecated Используй RelationSection через BbfWorldData
 */
@Deprecated("Use RelationSection via BbfWorldData")
internal class InMemoryWorldData {
    val organizations: MutableMap<Identifier, Organization> = mutableMapOf()
    val relations: MutableMap<omc.boundbyfate.api.relation.RelationKey, omc.boundbyfate.api.relation.Relation> = mutableMapOf()
    val memberships: MutableMap<UUID, MutableMap<Identifier, CharacterMembership>> = mutableMapOf()

    companion object {
        val INSTANCE = InMemoryWorldData()
    }
}
