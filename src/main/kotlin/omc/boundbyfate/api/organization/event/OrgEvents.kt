package omc.boundbyfate.api.organization.event

import omc.boundbyfate.event.core.EventBus
import omc.boundbyfate.event.core.eventBus

/**
 * EventBus точки вмешательства в систему организаций.
 */
object OrgEvents {

    val BEFORE_JOIN: EventBus<BeforeOrgJoinListener>     = eventBus("org.before_join")
    val AFTER_JOIN: EventBus<AfterOrgJoinListener>       = eventBus("org.after_join")
    val BEFORE_LEAVE: EventBus<BeforeOrgLeaveListener>   = eventBus("org.before_leave")
    val AFTER_LEAVE: EventBus<AfterOrgLeaveListener>     = eventBus("org.after_leave")
    val ON_RANK_GRANTED: EventBus<OrgRankGrantedListener> = eventBus("org.on_rank_granted")
    val ON_RANK_REVOKED: EventBus<OrgRankRevokedListener> = eventBus("org.on_rank_revoked")
    val ON_CREATED: EventBus<OrgCreatedListener>         = eventBus("org.on_created")
    val ON_DELETED: EventBus<OrgDeletedListener>         = eventBus("org.on_deleted")
}

fun interface BeforeOrgJoinListener   { fun onBeforeJoin(event: BeforeOrgJoinEvent) }
fun interface AfterOrgJoinListener    { fun onAfterJoin(event: AfterOrgJoinEvent) }
fun interface BeforeOrgLeaveListener  { fun onBeforeLeave(event: BeforeOrgLeaveEvent) }
fun interface AfterOrgLeaveListener   { fun onAfterLeave(event: AfterOrgLeaveEvent) }
fun interface OrgRankGrantedListener  { fun onRankGranted(event: OrgRankGrantedEvent) }
fun interface OrgRankRevokedListener  { fun onRankRevoked(event: OrgRankRevokedEvent) }
fun interface OrgCreatedListener      { fun onCreated(event: OrgCreatedEvent) }
fun interface OrgDeletedListener      { fun onDeleted(event: OrgDeletedEvent) }
