package omc.boundbyfate.client.animation

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Центральный менеджер анимационных слоёв игроков.
 *
 * Хранит [PlayerAnimLayer] для каждого видимого игрока.
 * Создаёт слой при первом обращении, уничтожает при выходе.
 *
 * ## Использование
 *
 * Основной слой (заменяет текущую анимацию):
 * ```kotlin
 * PlayerAnimSystem.play(entityId, Identifier("boundbyfate-core", "second_wind"))
 * PlayerAnimSystem.play(entityId, Identifier("boundbyfate-core", "concentration"), looping = true)
 * PlayerAnimSystem.stop(entityId)
 * ```
 *
 * Именованный аддитивный слой (поверх основного):
 * ```kotlin
 * PlayerAnimSystem.play(entityId, Identifier("boundbyfate-core", "glow"), layer = "overlay")
 * PlayerAnimSystem.stop(entityId, layer = "overlay")
 * ```
 */
@Environment(EnvType.CLIENT)
object PlayerAnimSystem {

    private val logger = LoggerFactory.getLogger(PlayerAnimSystem::class.java)

    /** Слои по UUID игрока. ConcurrentHashMap — рендер и тик на разных потоках. */
    private val layers = ConcurrentHashMap<UUID, PlayerAnimLayer>()

    // ── Публичный API ─────────────────────────────────────────────────────

    /**
     * Запускает анимацию для игрока.
     *
     * @param entityId  Сетевой ID сущности
     * @param animId    Идентификатор анимации
     * @param looping   true = зациклить
     * @param layer     null = основной слой, строка = именованный аддитивный слой
     */
    fun play(entityId: Int, animId: Identifier, looping: Boolean = false, layer: String? = null) {
        val player = findPlayer(entityId) ?: return
        getOrCreate(player).play(animId, looping, layer)
    }

    /**
     * Запускает анимацию напрямую по AbstractClientPlayerEntity.
     * Используется для CharacterDummy которые не добавляются в world entity map.
     */
    fun play(player: AbstractClientPlayerEntity, animId: Identifier, looping: Boolean = false, layer: String? = null) {
        getOrCreate(player).play(animId, looping, layer)
    }

    /**
     * Останавливает анимацию для игрока.
     *
     * @param entityId  Сетевой ID сущности
     * @param layer     null = основной слой, строка = именованный слой
     */
    fun stop(entityId: Int, layer: String? = null) {
        val player = findPlayer(entityId) ?: return
        layers[player.uuid]?.stop(layer)
    }

    /**
     * Останавливает все анимации на всех слоях.
     */
    fun stopAll(entityId: Int) {
        val player = findPlayer(entityId) ?: return
        layers[player.uuid]?.stopAll()
    }

    /**
     * Останавливает все анимации напрямую по UUID.
     * Используется для CharacterDummy.
     */
    fun stopAll(playerUuid: UUID) {
        val layer = layers.remove(playerUuid) ?: return
        layer.destroy()
    }

    /**
     * Очищает слой при выходе игрока из мира.
     */
    fun onPlayerRemoved(player: PlayerEntity) {
        val layer = layers.remove(player.uuid) ?: return
        layer.destroy()
        logger.debug("PlayerAnimLayer removed for {}", player.name.string)
    }

    // ── Внутренние ────────────────────────────────────────────────────────

    private fun getOrCreate(player: AbstractClientPlayerEntity): PlayerAnimLayer {
        return layers.getOrPut(player.uuid) {
            logger.debug("Creating PlayerAnimLayer for {}", player.name.string)
            PlayerAnimLayer(player)
        }
    }

    private fun findPlayer(entityId: Int): AbstractClientPlayerEntity? {
        val world = MinecraftClient.getInstance().world ?: return null
        val entity = world.getEntityById(entityId)
        if (entity !is AbstractClientPlayerEntity) {
            logger.debug("Entity {} is not a player, skipping animation", entityId)
            return null
        }
        return entity
    }
}
