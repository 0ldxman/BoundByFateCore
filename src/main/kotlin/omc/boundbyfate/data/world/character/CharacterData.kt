package omc.boundbyfate.data.world.character

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.UUID

/**
 * Полные данные персонажа — "лист персонажа" в терминах D&D.
 *
 * Хранится в [CharacterSection] в WorldData.
 * Существует независимо от того онлайн ли игрок.
 *
 * ## Структура
 *
 * - [id] — уникальный UUID персонажа
 * - [ownerId] — UUID игрока-владельца (null для "ничейных" НПС)
 * - [identity] — кто ты (имя, скин, метаданные)
 * - [race] — откуда ты (раса, подраса, пол, дата рождения)
 * - [charClass] — кем ты стал (класс, подкласс, история левелапов)
 * - [progression] — насколько ты вырос (уровень, опыт)
 * - [worldView] — во что ты веришь (мировоззрение, убеждения, слабости, мотивации)
 * - [stats] — что ты умеешь (базовые статы, владения, способности)
 * - [savedState] — где ты сейчас (позиция, инвентарь, снимки компонентов)
 *
 * ## Важно
 *
 * Бонусы от расы/класса/фитов НЕ хранятся здесь.
 * Они вычисляются из Registries и применяются как модификаторы
 * в компонентах (Attachments) при загрузке персонажа.
 */
data class CharacterData(
    val id: UUID,
    val ownerId: UUID?,
    val identity: CharacterIdentity,
    val race: CharacterRace,
    val charClass: CharacterClass,
    val progression: CharacterProgression = CharacterProgression(),
    val worldView: CharacterWorldView = CharacterWorldView(),
    val stats: CharacterStats = CharacterStats(),
    val savedState: CharacterSavedState = CharacterSavedState()
) {
    companion object {
        private val UUID_CODEC: Codec<UUID> = Codec.STRING.xmap(
            { UUID.fromString(it) },
            { it.toString() }
        )

        val CODEC: Codec<CharacterData> = RecordCodecBuilder.create { instance ->
            instance.group(
                UUID_CODEC.fieldOf("id").forGetter { it.id },
                UUID_CODEC.optionalFieldOf("ownerId").forGetter {
                    java.util.Optional.ofNullable(it.ownerId)
                },
                CharacterIdentity.CODEC.fieldOf("identity").forGetter { it.identity },
                CharacterRace.CODEC.fieldOf("race").forGetter { it.race },
                CharacterClass.CODEC.fieldOf("charClass").forGetter { it.charClass },
                CharacterProgression.CODEC.fieldOf("progression").forGetter { it.progression },
                CharacterWorldView.CODEC.fieldOf("worldView").forGetter { it.worldView },
                CharacterStats.CODEC.fieldOf("stats").forGetter { it.stats },
                CharacterSavedState.CODEC.fieldOf("savedState").forGetter { it.savedState }
            ).apply(instance) { id, ownerId, identity, race, charClass,
                                 progression, worldView, stats, savedState ->
                CharacterData(
                    id = id,
                    ownerId = ownerId.orElse(null),
                    identity = identity,
                    race = race,
                    charClass = charClass,
                    progression = progression,
                    worldView = worldView,
                    stats = stats,
                    savedState = savedState
                )
            }
        }

        /**
         * Создаёт нового персонажа с минимальными данными.
         */
        fun create(
            ownerId: UUID?,
            displayName: String,
            raceId: net.minecraft.util.Identifier,
            classId: net.minecraft.util.Identifier
        ): CharacterData {
            val now = System.currentTimeMillis()
            return CharacterData(
                id = UUID.randomUUID(),
                ownerId = ownerId,
                identity = CharacterIdentity(
                    displayName = displayName,
                    createdAt = now,
                    lastPlayedAt = now
                ),
                race = CharacterRace(raceId = raceId),
                charClass = CharacterClass(classId = classId)
            )
        }
    }
}
