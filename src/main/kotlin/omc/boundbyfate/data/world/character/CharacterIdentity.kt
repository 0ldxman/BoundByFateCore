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
 * @property skinPath путь до PNG файла скина (загружается с клиента, хранится на сервере)
 * @property modelType тип модели (steve = широкие руки, alex = тонкие руки)
 */
data class CharacterAppearance(
    val skinPath: String = "",
    val modelType: ModelType = ModelType.STEVE
) {
    companion object {
        val CODEC: Codec<CharacterAppearance> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("skinPath").forGetter { it.skinPath },
                Codec.STRING.xmap(
                    { ModelType.valueOf(it) },
                    { it.name }
                ).fieldOf("modelType").forGetter { it.modelType }
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
