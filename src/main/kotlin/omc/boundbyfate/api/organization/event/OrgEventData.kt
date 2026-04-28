package omc.boundbyfate.api.organization.event

import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import omc.boundbyfate.api.organization.CharacterMembership
import omc.boundbyfate.api.organization.Organization
import omc.boundbyfate.event.core.BaseCancellableEvent
import java.util.UUID

/**
 * Data-классы для событий системы организаций.
 */

// ── BEFORE_JOIN / AFTER_JOIN ──────────────────────────────────────────────

/**
 * Событие перед вступлением персонажа в организацию.
 *
 * Отмена блокирует вступление.
 *
 * ```kotlin
 * OrgEvents.BEFORE_JOIN.register { event ->
 *     // Нельзя вступить в Гильдию Воров если состоишь в Страже
 *     if (event.orgId == THIEVES_GUILD_ID) {
 *         if (OrgSystem.isMember(event.world, event.characterUuid, CITY_GUARD_ID)) {
 *             event.cancel()
 *         }
 *     }
 * }
 * ```
 */
class BeforeOrgJoinEvent(
    val world: ServerWorld,
    val characterUuid: UUID,
    val orgId: Identifier,
    val organization: Organization
) : BaseCancellableEvent()

/**
 * Событие после вступления персонажа в организацию.
 *
 * Не отменяемое.
 */
class AfterOrgJoinEvent(
    val world: ServerWorld,
    val characterUuid: UUID,
    val orgId: Identifier,
    val organization: Organization,
    val membership: CharacterMembership
)

// ── BEFORE_LEAVE / AFTER_LEAVE ────────────────────────────────────────────

/**
 * Событие перед выходом персонажа из организации.
 *
 * Отмена блокирует выход.
 */
class BeforeOrgLeaveEvent(
    val world: ServerWorld,
    val characterUuid: UUID,
    val orgId: Identifier,
    val organization: Organization,
    val membership: CharacterMembership
) : BaseCancellableEvent()

/**
 * Событие после выхода персонажа из организации.
 *
 * Не отменяемое.
 */
class AfterOrgLeaveEvent(
    val world: ServerWorld,
    val characterUuid: UUID,
    val orgId: Identifier,
    val organization: Organization
)

// ── ON_RANK_GRANTED / ON_RANK_REVOKED ─────────────────────────────────────

/**
 * Событие выдачи ранга персонажу.
 *
 * Не отменяемое.
 *
 * Используй для:
 * - Уведомлений ("Вы получили ранг Мастер в Гильдии Воров")
 * - Квестовых триггеров
 */
class OrgRankGrantedEvent(
    val world: ServerWorld,
    val characterUuid: UUID,
    val orgId: Identifier,
    val rankId: String,
    val isAutomatic: Boolean   // true = выдан автоматически по репутации, false = вручную ГМом
)

/**
 * Событие снятия ранга с персонажа.
 *
 * Не отменяемое.
 */
class OrgRankRevokedEvent(
    val world: ServerWorld,
    val characterUuid: UUID,
    val orgId: Identifier,
    val rankId: String,
    val isAutomatic: Boolean
)

// ── ON_CREATED / ON_DELETED ───────────────────────────────────────────────

/**
 * Событие создания организации.
 *
 * Не отменяемое.
 */
class OrgCreatedEvent(
    val world: ServerWorld,
    val organization: Organization
)

/**
 * Событие удаления организации.
 *
 * Не отменяемое.
 */
class OrgDeletedEvent(
    val world: ServerWorld,
    val orgId: Identifier
)
