package omc.boundbyfate.data.world.sections

import com.mojang.serialization.Codec
import net.minecraft.util.Identifier
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.character.CharacterController
import omc.boundbyfate.data.world.character.CharacterData
import omc.boundbyfate.data.world.core.SyncStrategy
import omc.boundbyfate.data.world.core.WorldDataSection
import omc.boundbyfate.util.codec.CodecUtil
import java.util.UUID

/**
 * Секция данных персонажей.
 *
 * Хранит всех персонажей мира, информацию о том кто чем управляет,
 * и маппинг владельцев.
 *
 * Файл: `boundbyfate_characters.dat`
 *
 * ## Использование
 *
 * ```kotlin
 * val section = BbfWorldData.get(server).getSection(CharacterSection.TYPE)
 *
 * // Получить персонажа
 * val character = section.characters[characterId]
 *
 * // Найти всех персонажей игрока
 * val playerChars = section.byOwner[playerId]
 *
 * // Найти по имени
 * val elio = section.byName["Elio_Hellblade"]
 *
 * // Кто управляет персонажем прямо сейчас
 * val controller = section.activeCharacters[characterId]
 * ```
 */
class CharacterSection : WorldDataSection() {

    private val UUID_CODEC: Codec<UUID> = Codec.STRING.xmap(
        { UUID.fromString(it) },
        { it.toString() }
    )

    /** Все персонажи мира. Ключ — UUID персонажа. */
    val characters by syncedMap(UUID_CODEC, CharacterData.CODEC)

    /**
     * Активные персонажи — кто чем управляет прямо сейчас.
     * Ключ — UUID персонажа, значение — контроллер.
     */
    val activeCharacters by syncedMap(UUID_CODEC, CharacterController.CODEC)

    /**
     * Владения — список персонажей каждого игрока.
     * Ключ — UUID игрока, значение — список UUID его персонажей.
     */
    val ownerships by syncedMap(
        UUID_CODEC,
        UUID_CODEC.listOf().xmap({ it }, { it })
    )

    // ── Индексы ───────────────────────────────────────────────────────────

    /** Быстрый поиск персонажей по владельцу. */
    val byOwner = index(characters) { it.ownerId }

    /** Быстрый поиск персонажа по отображаемому имени (уникальный). */
    val byName = uniqueIndex(characters) { it.identity.displayName }

    // ── Companion ─────────────────────────────────────────────────────────

    companion object {
        val TYPE = BbfWorldData.registerSection(
            id = "boundbyfate-core:characters",
            file = "boundbyfate_characters",
            syncStrategy = SyncStrategy.ToOwner { server ->
                // Синхронизируем только с игроками у которых есть активный персонаж
                server.playerManager.playerList
            },
            factory = ::CharacterSection
        )
    }
}
