package omc.boundbyfate.api.race

import com.mojang.serialization.Codec

/**
 * Игровой размер существа.
 *
 * Влияет на:
 * - Хитбокс
 * - Правила захвата (Grapple)
 * - Некоторые способности и заклинания
 * - Дефолтный масштаб модели для Pekhui
 *
 * ## Масштаб модели
 *
 * Каждый размер имеет [defaultModelScale] — дефолтный множитель для Pekhui API.
 * Конкретная раса может переопределить его через `model_size` в JSON.
 *
 * Итоговый масштаб: `race.modelSize ?: race.resolvedSize.defaultModelScale`
 */
enum class CreatureSize(
    /**
     * Дефолтный множитель масштаба модели для Pekhui.
     * Переопределяется через [omc.boundbyfate.api.race.RaceDefinition.modelSize].
     */
    val defaultModelScale: Float
) {
    TINY(0.4f),
    SMALL(0.8f),
    MEDIUM(1.0f),
    LARGE(1.5f),
    HUGE(2.0f),
    GARGANTUAN(3.0f);

    companion object {
        val CODEC: Codec<CreatureSize> = Codec.STRING.xmap(
            { str -> valueOf(str.uppercase(java.util.Locale.ROOT)) },
            { size -> size.name.lowercase(java.util.Locale.ROOT) }
        )
    }
}
