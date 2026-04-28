package omc.boundbyfate.data.world.sections

import com.mojang.serialization.Codec
import omc.boundbyfate.api.relation.Relation
import omc.boundbyfate.api.relation.RelationKey
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.core.SyncStrategy
import omc.boundbyfate.data.world.core.WorldDataSection
import java.util.UUID

/**
 * Секция отношений между участниками.
 *
 * Хранит все направленные отношения: персонаж↔персонаж,
 * персонаж↔организация, организация↔организация.
 *
 * Организации и членства хранятся отдельно в [OrganizationSection].
 *
 * Файл: `boundbyfate_relations.dat`
 *
 * ## Использование
 *
 * ```kotlin
 * val section = BbfWorldData.get(server).getSection(RelationSection.TYPE)
 *
 * // Получить отношение
 * val key = RelationKey(RelationParty.Character(uuid), RelationParty.Organization(guildId))
 * val relation = section.relations[key] ?: Relation()
 *
 * // Изменить отношение
 * section.relations[key] = relation.shift(+50, "Спас деревню")
 * ```
 */
class RelationSection : WorldDataSection() {

    /**
     * Все отношения между участниками.
     * Ключ — направленная пара (from → to), сериализуется как строка "from|to".
     */
    val relations by syncedMap(RelationKey.CODEC, Relation.CODEC)

    companion object {
        val TYPE = BbfWorldData.registerSection(
            id = "boundbyfate-core:relations",
            file = "boundbyfate_relations",
            syncStrategy = SyncStrategy.ToAll,
            factory = ::RelationSection
        )
    }
}
