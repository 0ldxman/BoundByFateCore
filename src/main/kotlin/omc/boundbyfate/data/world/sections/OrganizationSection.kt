package omc.boundbyfate.data.world.sections

import com.mojang.serialization.Codec
import net.minecraft.util.Identifier
import omc.boundbyfate.api.organization.CharacterMembership
import omc.boundbyfate.api.organization.Organization
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.core.SyncStrategy
import omc.boundbyfate.data.world.core.WorldDataSection
import java.util.UUID

/**
 * Секция организаций и членств.
 *
 * Хранит все организации мира и членства персонажей в них.
 * Отношения (репутация) хранятся отдельно в [RelationSection].
 *
 * Файл: `boundbyfate_organizations.dat`
 *
 * ## Использование
 *
 * ```kotlin
 * val section = BbfWorldData.get(server).getSection(OrganizationSection.TYPE)
 *
 * // Получить организацию
 * val guild = section.organizations[guildId]
 *
 * // Найти по имени
 * val thieves = section.byName["Гильдия Воров"]
 *
 * // Членства персонажа
 * val memberships = section.memberships[characterId] ?: emptyMap()
 *
 * // Все члены организации
 * val members = section.membersByOrg[guildId]
 * ```
 */
class OrganizationSection : WorldDataSection() {

    /**
     * Все организации мира.
     * Ключ — ID организации.
     */
    val organizations by syncedMap(Identifier.CODEC, Organization.CODEC)

    /**
     * Членства персонажей в организациях.
     * Ключ — UUID персонажа, значение — Map<orgId, CharacterMembership>.
     */
    val memberships by syncedMap(UUID_CODEC, ORG_MEMBERSHIP_MAP_CODEC)

    // ── Индексы ───────────────────────────────────────────────────────────

    /** Быстрый поиск организации по названию (уникальный). */
    val byName = uniqueIndex(organizations) { it.name }

    /**
     * Быстрый поиск UUID персонажей которые состоят в организации.
     * Индекс строится по ключам memberships — UUID персонажей у которых
     * есть запись о членстве в данной организации.
     *
     * Использование: section.membersByOrg[orgId] → List<UUID>
     */
    // Индекс по организациям строится вручную через memberships
    // т.к. структура Map<UUID, Map<Identifier, Membership>> требует инверсии

    companion object {
        internal val UUID_CODEC: Codec<UUID> = Codec.STRING.xmap(
            { UUID.fromString(it) },
            { it.toString() }
        )

        internal val ORG_MEMBERSHIP_MAP_CODEC: Codec<Map<Identifier, CharacterMembership>> =
            Codec.unboundedMap(Identifier.CODEC, CharacterMembership.CODEC)

        val TYPE = BbfWorldData.registerSection(
            id = "boundbyfate-core:organizations",
            file = "boundbyfate_organizations",
            syncStrategy = SyncStrategy.ToAll,
            factory = ::OrganizationSection
        )
    }
}
