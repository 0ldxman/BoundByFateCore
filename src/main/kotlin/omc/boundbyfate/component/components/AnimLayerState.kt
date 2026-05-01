package omc.boundbyfate.component.components

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Состояние одного анимационного слоя НПС.
 *
 * Хранится в [NpcModelComponent.animationLayers] и синхронизируется с клиентом.
 *
 * ## Два режима:
 *
 * **Основной слой** (`layerName = null`) — базовая анимация тела.
 * Переключается через transition (плавная замена).
 * Только одна анимация может быть активна на основном слое.
 *
 * **Именованный слой** (`layerName = "emotion"` и т.д.) — аддитивная анимация
 * поверх основного слоя. Несколько именованных слоёв могут играть одновременно.
 *
 * @param layerName  null = основной слой, строка = аддитивный именованный слой
 * @param animation  Имя анимации из GLTF/Bedrock файла
 * @param looping    true = Loop, false = Once
 * @param blendIn    Время плавного появления в секундах
 */
data class AnimLayerState(
    val layerName: String?,
    val animation: String,
    val looping: Boolean = true,
    val blendIn: Float = 0.2f
) {
    /** Внутренний ключ для хранения в map. Основной слой = "__base__". */
    val key: String get() = layerName ?: BASE_LAYER_KEY

    companion object {
        const val BASE_LAYER_KEY = "__base__"

        val CODEC: Codec<AnimLayerState> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("layerName", BASE_LAYER_KEY).forGetter { it.layerName ?: BASE_LAYER_KEY },
                Codec.STRING.fieldOf("animation").forGetter(AnimLayerState::animation),
                Codec.BOOL.optionalFieldOf("looping", true).forGetter(AnimLayerState::looping),
                Codec.FLOAT.optionalFieldOf("blendIn", 0.2f).forGetter(AnimLayerState::blendIn)
            ).apply(instance) { layerName, animation, looping, blendIn ->
                AnimLayerState(
                    layerName = if (layerName == BASE_LAYER_KEY) null else layerName,
                    animation = animation,
                    looping = looping,
                    blendIn = blendIn
                )
            }
        }
    }
}
