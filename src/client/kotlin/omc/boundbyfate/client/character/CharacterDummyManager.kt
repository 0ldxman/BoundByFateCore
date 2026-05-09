package omc.boundbyfate.client.character

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import omc.boundbyfate.client.animation.PlayerAnimSystem
import omc.boundbyfate.component.components.EntityAppearanceData
import omc.boundbyfate.component.core.getComponent
import omc.boundbyfate.network.packet.s2c.CharacterDummyDespawnPacket
import omc.boundbyfate.network.packet.s2c.CharacterDummySpawnPacket
import omc.boundbyfate.network.packet.s2c.CharacterEnterResponsePacket
import omc.boundbyfate.network.packet.s2c.DummyAnimationType
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Клиентский менеджер манекенов персонажей.
 *
 * Создаёт и удаляет [CharacterDummy] в ответ на серверные пакеты.
 * Запускает анимации отдыха через [PlayerAnimSystem].
 *
 * ## Важно про рендер
 *
 * [CharacterDummy] НЕ добавляется в `ClientWorld.entityList` — это клиентская
 * entity без серверного ID. Вместо этого она хранится в [dummies] и рендерится
 * через отдельный механизм (см. [CharacterDummyRenderer]).
 *
 * Анимации запускаются напрямую через `PlayerAnimSystem.play(dummy, animId)`
 * минуя поиск по entity ID.
 *
 * ## Анимации
 *
 * Файл: `assets/boundbyfate-core/player_animation/bbf_animations.json`
 * - `dnd_sitdown` — садится (заглушка для всех типов пока нет остальных)
 * - `dnd_situp` — встаёт (заглушка для пробуждения)
 */
@Environment(EnvType.CLIENT)
object CharacterDummyManager {

    private val logger = LoggerFactory.getLogger(CharacterDummyManager::class.java)

    /** Активные манекены. Ключ — UUID персонажа. */
    private val dummies = ConcurrentHashMap<UUID, CharacterDummy>()

    // ── Регистрация ───────────────────────────────────────────────────────

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.world != null && !client.isPaused) {
                // Тикаем существующие манекены
                dummies.values.forEach { it.clientTick() }
                
                // Проверяем сущности в мире на наличие компонента внешности
                // (только те, что в радиусе стриминга клиента)
                client.world?.entities?.forEach { entity ->
                    if (entity is LivingEntity && entity !is CharacterDummy && entity !is net.minecraft.client.network.ClientPlayerEntity) {
                        checkAndCreateProxy(entity)
                    }
                }
            }
        }

        // Автоматическое удаление прокси при выгрузке сущности
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity is LivingEntity) {
                removeProxy(entity)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(CharacterDummySpawnPacket.TYPE) { packet, _, _ ->
            MinecraftClient.getInstance().execute { onSpawn(packet) }
        }

        ClientPlayNetworking.registerGlobalReceiver(CharacterDummyDespawnPacket.TYPE) { packet, _, _ ->
            MinecraftClient.getInstance().execute { onDespawn(packet) }
        }

        ClientPlayNetworking.registerGlobalReceiver(CharacterEnterResponsePacket.TYPE) { packet, _, _ ->
            MinecraftClient.getInstance().execute { onEnterResponse(packet) }
        }

        logger.info("CharacterDummyManager registered")
    }

    // ── Публичный доступ ──────────────────────────────────────────────────

    /**
     * Возвращает все активные манекены.
     * Используется рендерером для отрисовки.
     */
    fun getAllDummies(): Collection<CharacterDummy> = dummies.values

    /**
     * Возвращает манекен по UUID персонажа или null.
     */
    fun getDummy(characterId: UUID): CharacterDummy? = dummies[characterId]

    // ── Автоматизация прокси ──────────────────────────────────────────────

    private fun checkAndCreateProxy(entity: LivingEntity) {
        val appearance = entity.getComponent(EntityAppearanceData.TYPE) ?: return
        
        // Если прокси уже есть, проверяем не изменились ли базовые данные (скин/модель)
        val existing = dummies[entity.uuid]
        if (existing != null) {
            // TODO: Можно добавить логику обновления скина если он изменился в компоненте
            return
        }

        val world = entity.world as? net.minecraft.client.world.ClientWorld ?: return
        
        val dummy = CharacterDummy(
            world,
            entity.uuid,
            appearance.skinId,
            appearance.modelType,
            DummyAnimationType.STAND_IDLE // По дефолту для NPC
        )
        
        dummy.sourceEntity = entity
        dummy.syncWithSource()
        
        dummies[entity.uuid] = dummy
        logger.debug("Auto-created CharacterDummy proxy for entity ${entity.uuid} (${entity.name.string})")
    }

    private fun removeProxy(entity: LivingEntity) {
        val dummy = dummies.remove(entity.uuid) ?: return
        PlayerAnimSystem.stopAll(dummy.uuid)
        logger.debug("Auto-removed CharacterDummy proxy for entity ${entity.uuid}")
    }

    // ── Обработка пакетов ─────────────────────────────────────────────────

    private fun onSpawn(packet: CharacterDummySpawnPacket) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return

        // Убираем старый Dummy если был
        dummies.remove(packet.characterId)?.let { old ->
            PlayerAnimSystem.stopAll(old.uuid)
        }

        val dummy = CharacterDummy(
            world,
            packet.characterId,
            packet.skinId,
            packet.modelType,
            packet.animationType
        )

        // Устанавливаем позицию
        dummy.setPosition(packet.x, packet.y, packet.z)
        dummy.yaw = packet.yaw
        dummy.bodyYaw = packet.yaw
        dummy.headYaw = packet.yaw
        dummy.prevYaw = packet.yaw
        dummy.prevBodyYaw = packet.yaw

        dummies[packet.characterId] = dummy

        // Запускаем анимацию отдыха напрямую через entity (не через entity ID)
        val animId = packet.animationType.toAnimIdentifier()
        PlayerAnimSystem.play(dummy, animId, looping = true)

        logger.debug("Spawned CharacterDummy for character ${packet.characterId} at (${packet.x}, ${packet.y}, ${packet.z})")
    }

    private fun onDespawn(packet: CharacterDummyDespawnPacket) {
        val dummy = dummies.remove(packet.characterId) ?: return

        PlayerAnimSystem.stopAll(dummy.uuid)

        logger.debug("Despawned CharacterDummy for character ${packet.characterId}")
    }

    private fun onEnterResponse(packet: CharacterEnterResponsePacket) {
        // Запускаем анимацию пробуждения для локального игрока
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return

        val animId = packet.animationType.toWakeAnimIdentifier()
        PlayerAnimSystem.play(player.id, animId, looping = false)

        logger.debug("Playing wake animation $animId for local player")
    }

    // ── Очистка при дисконнекте ───────────────────────────────────────────

    fun clearAll() {
        dummies.values.forEach { PlayerAnimSystem.stopAll(it.uuid) }
        dummies.clear()
        logger.debug("Cleared all CharacterDummies")
    }
}

// ── Extension функции для анимаций ────────────────────────────────────────

/**
 * Возвращает Identifier анимации отдыха для данного типа.
 *
 * Анимации лежат в assets/boundbyfate-core/player_animation/bbf_animations.json.
 * Имена анимаций в файле: dnd_sitdown, dnd_situp.
 *
 * Пока готова только сидячая анимация — используем её как заглушку для всех типов.
 * TODO: добавить отдельные анимации для SLEEP и STAND_IDLE когда будут готовы.
 */
fun DummyAnimationType.toAnimIdentifier(): Identifier = when (this) {
    DummyAnimationType.SLEEP      -> Identifier("boundbyfate-core", "dnd_sitdown") // заглушка
    DummyAnimationType.SIT        -> Identifier("boundbyfate-core", "dnd_sitdown")
    DummyAnimationType.STAND_IDLE -> Identifier("boundbyfate-core", "dnd_sitdown") // заглушка
}

/**
 * Возвращает Identifier анимации пробуждения для данного типа.
 *
 * dnd_situp — вставание из сидячего положения.
 * Используем как заглушку для всех типов пока нет остальных анимаций.
 */
fun DummyAnimationType.toWakeAnimIdentifier(): Identifier = when (this) {
    DummyAnimationType.SLEEP      -> Identifier("boundbyfate-core", "dnd_situp") // заглушка
    DummyAnimationType.SIT        -> Identifier("boundbyfate-core", "dnd_situp")
    DummyAnimationType.STAND_IDLE -> Identifier("boundbyfate-core", "dnd_situp") // заглушка
}
