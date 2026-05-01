package omc.boundbyfate.client.animation

import dev.kosmx.playerAnim.api.layered.IAnimation
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer
import dev.kosmx.playerAnim.api.layered.ModifierLayer
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

/**
 * Анимационные слои для одного игрока.
 *
 * Поддерживает два типа слоёв:
 *
 * **Основной слой** (`layer = null`) — базовая анимация способности/состояния.
 * Новая анимация заменяет предыдущую. Приоритет [BASE_PRIORITY].
 *
 * **Именованные слои** (`layer = "overlay"` и т.д.) — аддитивные слои поверх основного.
 * Каждый именованный слой независим. Приоритет = [BASE_PRIORITY] + порядковый номер.
 *
 * ## Использование
 *
 * ```kotlin
 * // Основной слой
 * layer.play(Identifier("boundbyfate-core", "second_wind"))
 * layer.playLooping(Identifier("boundbyfate-core", "concentration"))
 * layer.stop()
 *
 * // Именованный слой
 * layer.play(Identifier("boundbyfate-core", "overlay_glow"), layerName = "overlay")
 * layer.stop(layerName = "overlay")
 * ```
 */
@Environment(EnvType.CLIENT)
class PlayerAnimLayer(player: AbstractClientPlayerEntity) {

    private val logger = LoggerFactory.getLogger(PlayerAnimLayer::class.java)

    /** Основной слой — один, заменяет анимацию при каждом вызове play. */
    private val baseLayer: ModifierLayer<IAnimation> = ModifierLayer()

    /** Именованные аддитивные слои. Ключ — имя слоя. */
    private val namedLayers = LinkedHashMap<String, ModifierLayer<IAnimation>>()

    /** Стек анимаций игрока для добавления слоёв. */
    private val animStack = PlayerAnimationAccess.getPlayerAnimLayer(player)

    init {
        animStack.addAnimLayer(BASE_PRIORITY, baseLayer)
    }

    // ── Воспроизведение ───────────────────────────────────────────────────

    /**
     * Запускает анимацию.
     *
     * Без [layerName] — основной слой, заменяет текущую анимацию.
     * С [layerName] — именованный аддитивный слой поверх основного.
     *
     * @param animId     Идентификатор анимации
     * @param looping    true = зациклить, false = один раз
     * @param layerName  null = основной слой, строка = именованный слой
     */
    fun play(animId: Identifier, looping: Boolean = false, layerName: String? = null) {
        val keyframes = loadKeyframes(animId) ?: return
        val animation = if (looping) {
            val builder = keyframes.mutableCopy()
            builder.isLooped = true
            if (builder.returnTick <= 0) builder.returnTick = keyframes.beginTick
            KeyframeAnimationPlayer(builder.build())
        } else {
            KeyframeAnimationPlayer(keyframes)
        }

        if (layerName == null) {
            baseLayer.setAnimation(animation)
        } else {
            getOrCreateNamedLayer(layerName).setAnimation(animation)
        }
    }

    /**
     * Останавливает анимацию.
     *
     * Без [layerName] — останавливает основной слой.
     * С [layerName] — останавливает и удаляет именованный слой.
     *
     * @param layerName  null = основной слой, строка = именованный слой
     */
    fun stop(layerName: String? = null) {
        if (layerName == null) {
            baseLayer.setAnimation(null)
        } else {
            namedLayers.remove(layerName)?.setAnimation(null)
        }
    }

    /**
     * Останавливает все слои.
     */
    fun stopAll() {
        baseLayer.setAnimation(null)
        namedLayers.values.forEach { it.setAnimation(null) }
        namedLayers.clear()
    }

    /**
     * Уничтожает все слои при выходе игрока.
     */
    fun destroy() {
        stopAll()
    }

    // ── Вспомогательные ──────────────────────────────────────────────────

    /**
     * Возвращает существующий именованный слой или создаёт новый.
     * Новый слой добавляется в стек с приоритетом выше основного.
     */
    private fun getOrCreateNamedLayer(name: String): ModifierLayer<IAnimation> {
        return namedLayers.getOrPut(name) {
            val priority = BASE_PRIORITY + namedLayers.size + 1
            ModifierLayer<IAnimation>().also { layer ->
                animStack.addAnimLayer(priority, layer)
                logger.debug("Created named anim layer '{}' with priority {}", name, priority)
            }
        }
    }

    private fun loadKeyframes(animId: Identifier): dev.kosmx.playerAnim.core.data.KeyframeAnimation? {
        val anim = PlayerAnimationRegistry.getAnimation(animId)
        if (anim == null) {
            logger.warn(
                "Animation '{}' not found. " +
                "Make sure the file exists in assets/<namespace>/player_animation/",
                animId
            )
        }
        return anim
    }

    companion object {
        /** Приоритет основного слоя в стеке playerAnimator. */
        const val BASE_PRIORITY = 1500
    }
}
