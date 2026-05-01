package omc.boundbyfate.component.components

import omc.boundbyfate.component.core.BbfComponent
import omc.boundbyfate.component.core.SyncMode

/**
 * Компонент модели НПС.
 *
 * Хранит путь к GLTF модели, параметры отображения и состояние анимационных слоёв.
 * Рендеринг выполняется на клиенте через kool/GLTF пайплайн.
 *
 * Компонент синхронизируется с клиентом при изменении.
 *
 * ## Управление анимациями
 *
 * ### Основной слой (без указания слоя)
 * Только одна анимация. Переключение через плавный transition.
 * ```kotlin
 * npcModel.playAnimation("walk")       // заменяет текущую через transition
 * npcModel.playAnimation("run")        // плавно переходит от walk к run
 * npcModel.playAnimation("idle")       // плавно переходит от run к idle
 * ```
 *
 * ### Именованные аддитивные слои
 * Играют поверх основного слоя независимо.
 * ```kotlin
 * npcModel.playAnimation("happy", layer = "emotion")   // добавляет эмоцию
 * npcModel.playAnimation("blink", layer = "eyes")      // добавляет моргание
 * npcModel.stopAnimation("emotion")                    // убирает эмоцию
 * npcModel.stopAnimation()                             // останавливает всё
 * ```
 */
class NpcModelComponent : BbfComponent() {

    /**
     * Путь к GLTF модели.
     * Формат: "namespace:models/entity/name.gltf"
     */
    var modelPath by synced("boundbyfate-core:models/entity/player_model.gltf")

    /**
     * ID скина из FileTransferSystem (FileCategory.SKIN).
     * Пустая строка — скин не назначен, используется дефолтный.
     */
    var skinId by synced("")

    /**
     * Масштаб модели (1.0 = стандартный).
     */
    var scale by synced(1.0f)

    /**
     * Включены ли анимации.
     */
    var animationsEnabled by synced(true)

    /**
     * Активные анимационные слои.
     *
     * Максимум один элемент с `layerName = null` (основной слой).
     * Любое количество именованных аддитивных слоёв.
     *
     * Синхронизируется с клиентом при любом изменении.
     */
    val animationLayers by syncedList(AnimLayerState.CODEC)

    // ── API ───────────────────────────────────────────────────────────────

    /**
     * Запускает анимацию.
     *
     * Без [layer] — основной слой, плавный transition от текущей анимации.
     * С [layer] — аддитивный именованный слой поверх основного.
     *
     * @param animation  Имя анимации из модели
     * @param looping    true = зациклить, false = проиграть один раз
     * @param layer      null = основной слой, строка = аддитивный слой
     * @param blendIn    Время плавного появления в секундах
     */
    fun playAnimation(
        animation: String,
        looping: Boolean = true,
        layer: String? = null,
        blendIn: Float = 0.2f
    ) {
        val key = layer ?: AnimLayerState.BASE_LAYER_KEY
        val state = AnimLayerState(layer, animation, looping, blendIn)
        val idx = animationLayers.indexOfFirst { it.key == key }
        if (idx >= 0) {
            animationLayers[idx] = state
        } else {
            animationLayers.add(state)
        }
    }

    /**
     * Останавливает анимацию на указанном слое.
     * Без аргументов — останавливает основной слой.
     *
     * @param layer  null = основной слой, строка = именованный слой
     */
    fun stopAnimation(layer: String? = null) {
        val key = layer ?: AnimLayerState.BASE_LAYER_KEY
        animationLayers.removeAll { it.key == key }
    }

    /**
     * Останавливает все анимации на всех слоях.
     */
    fun stopAllAnimations() {
        animationLayers.clear()
    }

    companion object {
        val TYPE = omc.boundbyfate.component.core.BbfComponents.register(
            id = "boundbyfate-core:npc_model",
            syncMode = SyncMode.ON_CHANGE,
            factory = ::NpcModelComponent
        )
    }
}
