package omc.boundbyfate.data.world.character

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Идентичность персонажа — кто он визуально и метаданные.
 *
 * @property displayName отображаемое имя персонажа
 * @property createdAt время создания (Unix ms)
 * @property lastPlayedAt время последней игровой сессии (Unix ms)
 * @property appearance внешний вид персонажа
 */
data class CharacterIdentity(
    val displayName: String,
    val createdAt: Long,
    val lastPlayedAt: Long,
    val appearance: CharacterAppearance = CharacterAppearance()
) {
    companion object {
        val CODEC: Codec<CharacterIdentity> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("displayName").forGetter { it.displayName },
                Codec.LONG.fieldOf("createdAt").forGetter { it.createdAt },
                Codec.LONG.fieldOf("lastPlayedAt").forGetter { it.lastPlayedAt },
                CharacterAppearance.CODEC.fieldOf("appearance").forGetter { it.appearance }
            ).apply(instance, ::CharacterIdentity)
        }
    }
}

/**
 * Внешний вид персонажа.
 *
 * @property skinId ID скина из FileTransferSystem (FileCategory.SKIN).
 *   Пустая строка — скин не назначен, используется дефолтный Minecraft скин.
 *   Соответствует имени файла без расширения (например "elio_skin" → "elio_skin.png").
 * @property modelType тип модели (STEVE = широкие руки, ALEX = тонкие руки)
 * @property npcModelPath путь к GLTF модели НПС-версии персонажа.
 *   Формат: "namespace:models/entity/name.gltf"
 *   Файл должен лежать в assets/<namespace>/models/entity/name.gltf
 *   Пустая строка — используется дефолтная модель (boundbyfate-core:models/entity/player_model.gltf).
 * @property npcSkinId ID скина для НПС-версии персонажа из FileTransferSystem (FileCategory.SKIN).
 *   Пустая строка — используется скин из [skinId] (общий для игрока и НПС).
 */
data class CharacterAppearance(
    val skinId: String = "",
    val modelType: ModelType = ModelType.STEVE,
    val npcModelPath: String = "",
    val npcSkinId: String = ""
) {
    companion object {
        val CODEC: Codec<CharacterAppearance> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("skinId").forGetter { it.skinId },
                Codec.STRING.xmap(
                    { ModelType.valueOf(it) },
                    { it.name }
                ).fieldOf("modelType").forGetter { it.modelType },
                Codec.STRING.optionalFieldOf("npcModelPath", "").forGetter { it.npcModelPath },
                Codec.STRING.optionalFieldOf("npcSkinId", "").forGetter { it.npcSkinId }
            ).apply(instance, ::CharacterAppearance)
        }
    }
}

/**
 * Тип модели персонажа (ширина рук).
 */
enum class ModelType {
    /** Широкие руки (классический Steve). */
    STEVE,
    /** Тонкие руки (Alex). */
    ALEX
}
