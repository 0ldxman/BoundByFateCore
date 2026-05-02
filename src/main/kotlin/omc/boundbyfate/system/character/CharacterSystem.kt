package omc.boundbyfate.system.character

import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.GameMode
import omc.boundbyfate.component.components.EntityAbilitiesData
import omc.boundbyfate.component.components.EntityAppearanceData
import omc.boundbyfate.component.components.EntityCharacterData
import omc.boundbyfate.component.components.EntityEffectsData
import omc.boundbyfate.component.components.EntityParamsData
import omc.boundbyfate.component.core.getComponent
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.character.CharacterController
import omc.boundbyfate.data.world.character.CharacterEquipment
import omc.boundbyfate.data.world.character.CharacterSavedState
import omc.boundbyfate.data.world.character.ControllerType
import omc.boundbyfate.data.world.character.WorldPosition
import omc.boundbyfate.data.world.sections.CharacterSection
import omc.boundbyfate.network.extension.sendPacket
import omc.boundbyfate.network.packet.s2c.CharacterDummyDespawnPacket
import omc.boundbyfate.network.packet.s2c.CharacterDummySpawnPacket
import omc.boundbyfate.network.packet.s2c.CharacterEnterResponsePacket
import omc.boundbyfate.network.packet.s2c.DummyAnimationType
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Серверная система управления персонажами.
 *
 * ## Модель состояний
 *
 * Персонаж существует в [CharacterSection.characters] всегда.
 * Его "присутствие" в мире определяется наличием контроллера:
 *
 * ```
 * activeCharacters[id] = PLAYER(uuid)  →  игрок в персонаже, Dummy нет
 * activeCharacters[id] = SCRIPT(id)    →  скрипт управляет, Dummy нет
 * activeCharacters[id] отсутствует     →  Dummy стоит в мире
 * ```
 *
 * ## При старте сервера
 *
 * Все PLAYER-контроллеры очищаются — никто не в персонаже.
 * Для каждого персонажа без контроллера клиенты получат Dummy при подключении.
 *
 * ## При входе игрока
 *
 * Игрок всегда оказывается в spectator.
 * Сервер отправляет ему все существующие Dummy (персонажи без контроллера).
 *
 * ## При выходе игрока
 *
 * Состояние сохраняется, контроллер убирается.
 * Всем онлайн-игрокам рассылается CharacterDummySpawnPacket.
 */
object CharacterSystem {

    private val logger = LoggerFactory.getLogger(CharacterSystem::class.java)

    // ── Старт сервера ─────────────────────────────────────────────────────

    /**
     * Вызывается при старте сервера.
     *
     * Очищает все PLAYER-контроллеры — при рестарте никто не в персонаже.
     * SCRIPT-контроллеры оставляем — скрипты могут продолжить работу.
     */
    fun onServerStart(server: MinecraftServer) {
        val section = BbfWorldData.get(server).getSection(CharacterSection.TYPE)

        val staleControllers = section.activeCharacters.entries
            .filter { it.value.type == ControllerType.PLAYER }
            .map { it.key }

        staleControllers.forEach { section.activeCharacters.remove(it) }

        if (staleControllers.isNotEmpty()) {
            logger.info("Cleared ${staleControllers.size} stale PLAYER controllers on server start")
        }
    }

    // ── Вход/выход игрока ─────────────────────────────────────────────────

    /**
     * Вызывается когда игрок подключается к серверу.
     *
     * Переводит в spectator и отправляет все существующие Dummy.
     */
    fun onPlayerJoin(player: ServerPlayerEntity) {
        // Всегда spectator при входе — игрок должен выбрать персонажа
        if (player.interactionManager.gameMode != GameMode.CREATIVE) {
            player.changeGameMode(GameMode.SPECTATOR)
        }

        // Отправляем все Dummy (персонажи без активного контроллера)
        sendAllDummiesToPlayer(player)
    }

    /**
     * Вызывается когда игрок отключается от сервера.
     *
     * Сохраняет состояние персонажа, убирает контроллер,
     * рассылает CharacterDummySpawnPacket всем онлайн-игрокам.
     */
    fun onPlayerLeave(player: ServerPlayerEntity) {
        val characterId = player.getComponent(EntityCharacterData.TYPE)?.characterId ?: return

        val section = BbfWorldData.get(player.server).getSection(CharacterSection.TYPE)
        val character = section.characters[characterId] ?: return

        logger.info("Player ${player.name.string} disconnecting, saving character ${character.identity.displayName}")

        // Сохраняем состояние
        val savedState = captureCharacterState(player, character.savedState)
        section.characters[characterId] = character.copy(savedState = savedState)

        // Убираем контроллер — персонаж переходит в состояние "Dummy в мире"
        section.activeCharacters.remove(characterId)

        // Очищаем компонент
        player.getOrCreate(EntityCharacterData.TYPE).characterId = null

        // Рассылаем Dummy всем онлайн-игрокам
        // (при выходе анимация STAND_IDLE — полная анимация засыпания на клиенте)
        broadcastDummySpawn(
            server = player.server,
            characterId = characterId,
            savedState = savedState,
            appearance = player.getComponent(EntityAppearanceData.TYPE),
            animationType = DummyAnimationType.STAND_IDLE
        )
    }

    // ── Вход в персонажа ──────────────────────────────────────────────────

    /**
     * Выполняет переход игрока в персонажа.
     *
     * Вызывается из обработчика [EnterCharacterPacket].
     * К этому моменту экран игрока уже затемнён.
     */
    fun enterCharacter(player: ServerPlayerEntity, characterId: UUID) {
        val section = BbfWorldData.get(player.server).getSection(CharacterSection.TYPE)

        val character = section.characters[characterId]
        if (character == null) {
            logger.error("Character $characterId not found for player ${player.name.string}")
            return
        }

        // Проверяем права
        val isOwner = character.ownerId == player.uuid
        val isGm = player.hasPermissionLevel(2)
        if (!isOwner && !isGm) {
            logger.warn("Player ${player.name.string} tried to enter character $characterId without permission")
            return
        }

        // Персонаж уже под управлением другого игрока?
        val existingController = section.activeCharacters[characterId]
        if (existingController != null && existingController.asPlayerUuid() != player.uuid) {
            logger.warn("Character $characterId is already controlled by ${existingController.value}")
            return
        }

        logger.info("Player ${player.name.string} entering character ${character.identity.displayName}")

        // 1. Убираем Dummy у всех клиентов
        broadcastDummyDespawn(player.server, characterId)

        // 2. Восстанавливаем состояние персонажа
        restoreCharacterState(player, character.savedState)

        // 3. Обновляем компонент EntityCharacterData
        player.getOrCreate(EntityCharacterData.TYPE).characterId = characterId

        // 4. Обновляем внешность (скин)
        val appearance = character.identity.appearance
        val appearanceData = player.getOrCreate(EntityAppearanceData.TYPE)
        appearanceData.skinId = appearance.skinId
        appearanceData.modelType = appearance.modelType.name.lowercase()

        // 5. Переводим из spectator в survival
        player.changeGameMode(GameMode.SURVIVAL)

        // 6. Телепортируем на позицию персонажа
        teleportToPosition(player, character.savedState.worldPosition)

        // 7. Регистрируем контроллер
        section.activeCharacters[characterId] = CharacterController.player(player.uuid)

        // 8. Отправляем клиенту тип анимации пробуждения
        val animType = DummyAnimationType.STAND_IDLE // TODO: хранить в savedState
        player.sendPacket(CharacterEnterResponsePacket(characterId, animType))

        logger.info("Player ${player.name.string} entered character ${character.identity.displayName}")
    }

    // ── Выход из персонажа ────────────────────────────────────────────────

    /**
     * Выполняет переход игрока из персонажа в режим наблюдателя.
     *
     * Вызывается из обработчика [ExitCharacterPacket].
     * К этому моменту анимация засыпания уже проиграна и экран затемнён.
     */
    fun exitCharacter(player: ServerPlayerEntity) {
        val characterId = player.getComponent(EntityCharacterData.TYPE)?.characterId
        if (characterId == null) {
            logger.warn("Player ${player.name.string} tried to exit character but has none")
            return
        }

        val section = BbfWorldData.get(player.server).getSection(CharacterSection.TYPE)
        val character = section.characters[characterId] ?: return

        logger.info("Player ${player.name.string} exiting character ${character.identity.displayName}")

        // 1. Сохраняем состояние
        val savedState = captureCharacterState(player, character.savedState)
        section.characters[characterId] = character.copy(savedState = savedState)

        // 2. Убираем контроллер
        section.activeCharacters.remove(characterId)

        // 3. Очищаем компонент
        player.getOrCreate(EntityCharacterData.TYPE).characterId = null

        // 4. Очищаем инвентарь
        player.inventory.clear()

        // 5. Определяем тип анимации Dummy
        val animType = determineSleepAnimation(player)

        // 6. Переводим в spectator
        player.changeGameMode(GameMode.SPECTATOR)

        // 7. Рассылаем Dummy всем онлайн-игрокам
        broadcastDummySpawn(
            server = player.server,
            characterId = characterId,
            savedState = savedState,
            appearance = player.getComponent(EntityAppearanceData.TYPE),
            animationType = animType
        )

        logger.info("Player ${player.name.string} exited to spectator, dummy spawned for all")
    }

    // ── Вспомогательные методы ────────────────────────────────────────────

    /**
     * Отправляет новому игроку все существующие Dummy.
     *
     * Dummy = персонаж без активного контроллера.
     */
    private fun sendAllDummiesToPlayer(player: ServerPlayerEntity) {
        val section = BbfWorldData.get(player.server).getSection(CharacterSection.TYPE)

        var count = 0
        for ((characterId, character) in section.characters) {
            // Пропускаем персонажей с активным контроллером
            if (section.activeCharacters.containsKey(characterId)) continue

            val pos = character.savedState.worldPosition
            val appearance = character.identity.appearance

            player.sendPacket(CharacterDummySpawnPacket(
                characterId = characterId,
                skinId = appearance.skinId,
                modelType = appearance.modelType.name.lowercase(),
                x = pos.x, y = pos.y, z = pos.z,
                yaw = pos.yaw,
                animationType = DummyAnimationType.STAND_IDLE
            ))
            count++
        }

        if (count > 0) {
            logger.debug("Sent $count dummies to ${player.name.string} on join")
        }
    }

    /**
     * Рассылает CharacterDummySpawnPacket всем онлайн-игрокам.
     */
    private fun broadcastDummySpawn(
        server: MinecraftServer,
        characterId: UUID,
        savedState: CharacterSavedState,
        appearance: EntityAppearanceData?,
        animationType: DummyAnimationType
    ) {
        val pos = savedState.worldPosition
        val packet = CharacterDummySpawnPacket(
            characterId = characterId,
            skinId = appearance?.skinId ?: "",
            modelType = appearance?.modelType ?: "steve",
            x = pos.x, y = pos.y, z = pos.z,
            yaw = pos.yaw,
            animationType = animationType
        )
        server.playerManager.playerList.forEach { it.sendPacket(packet) }
    }

    /**
     * Рассылает CharacterDummyDespawnPacket всем онлайн-игрокам.
     */
    private fun broadcastDummyDespawn(server: MinecraftServer, characterId: UUID) {
        val packet = CharacterDummyDespawnPacket(characterId)
        server.playerManager.playerList.forEach { it.sendPacket(packet) }
    }

    /**
     * Сохраняет текущее состояние игрока в CharacterSavedState.
     */
    private fun captureCharacterState(
        player: ServerPlayerEntity,
        existing: CharacterSavedState
    ): CharacterSavedState {
        val pos = WorldPosition(
            dimension = player.world.registryKey.value,
            x = player.x, y = player.y, z = player.z,
            yaw = player.yaw, pitch = player.pitch
        )
        return CharacterSavedState(
            worldPosition = pos,
            equipment = captureEquipment(player),
            componentsSnapshot = captureComponentSnapshots(player)
        )
    }

    /**
     * Восстанавливает состояние персонажа из CharacterSavedState.
     */
    private fun restoreCharacterState(player: ServerPlayerEntity, state: CharacterSavedState) {
        restoreEquipment(player, state.equipment)
        restoreComponentSnapshots(player, state.componentsSnapshot)
    }

    private fun captureEquipment(player: ServerPlayerEntity): CharacterEquipment {
        val inventory = mutableMapOf<Int, NbtCompound>()
        for (i in 0 until player.inventory.size()) {
            val stack = player.inventory.getStack(i)
            if (!stack.isEmpty) inventory[i] = stack.writeNbt(NbtCompound())
        }

        val equipmentSlots = mutableMapOf<String, NbtCompound>()
        for (slot in EquipmentSlot.values()) {
            val stack = player.getEquippedStack(slot)
            if (!stack.isEmpty) equipmentSlots[slot.name.lowercase()] = stack.writeNbt(NbtCompound())
        }

        val offhand = player.offHandStack.takeIf { !it.isEmpty }?.writeNbt(NbtCompound())

        return CharacterEquipment(
            equipmentSlots = equipmentSlots,
            inventory = inventory,
            offhand = offhand
        )
    }

    private fun restoreEquipment(player: ServerPlayerEntity, equipment: CharacterEquipment) {
        player.inventory.clear()

        for ((slot, nbt) in equipment.inventory) {
            player.inventory.setStack(slot, ItemStack.fromNbt(nbt))
        }

        for ((slotName, nbt) in equipment.equipmentSlots) {
            val slot = try { EquipmentSlot.valueOf(slotName.uppercase()) }
                catch (_: Exception) { continue }
            player.equipStack(slot, ItemStack.fromNbt(nbt))
        }

        equipment.offhand?.let { player.offHandStack = ItemStack.fromNbt(it) }
    }

    private fun captureComponentSnapshots(player: ServerPlayerEntity): Map<String, NbtCompound> {
        val registries = player.server.registryManager
        val snapshots = mutableMapOf<String, NbtCompound>()

        player.getComponent(EntityParamsData.TYPE)?.let {
            snapshots["boundbyfate-core:params"] = it.toNbt(registries)
        }
        player.getComponent(EntityAbilitiesData.TYPE)?.let {
            snapshots["boundbyfate-core:abilities"] = it.toNbt(registries)
        }
        player.getComponent(EntityEffectsData.TYPE)?.let {
            snapshots["boundbyfate-core:effects"] = it.toNbt(registries)
        }

        return snapshots
    }

    private fun restoreComponentSnapshots(
        player: ServerPlayerEntity,
        snapshots: Map<String, NbtCompound>
    ) {
        val registries = player.server.registryManager

        snapshots["boundbyfate-core:params"]?.let {
            player.getOrCreate(EntityParamsData.TYPE).fromNbt(it, registries)
        }
        snapshots["boundbyfate-core:abilities"]?.let {
            player.getOrCreate(EntityAbilitiesData.TYPE).fromNbt(it, registries)
        }
        snapshots["boundbyfate-core:effects"]?.let {
            player.getOrCreate(EntityEffectsData.TYPE).fromNbt(it, registries)
        }
    }

    private fun teleportToPosition(player: ServerPlayerEntity, pos: WorldPosition) {
        val targetWorld = player.server.getWorld(
            net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.WORLD,
                pos.dimension
            )
        ) ?: player.serverWorld

        if (player.serverWorld != targetWorld) {
            player.teleport(targetWorld, pos.x, pos.y, pos.z, pos.yaw, pos.pitch)
        } else {
            player.teleport(pos.x, pos.y, pos.z, pos.yaw, pos.pitch)
        }
    }

    private fun determineSleepAnimation(player: ServerPlayerEntity): DummyAnimationType {
        val hasBedNearby = player.serverWorld
            .getBlockState(player.blockPos).block is net.minecraft.block.BedBlock ||
            player.serverWorld
            .getBlockState(player.blockPos.down()).block is net.minecraft.block.BedBlock

        return when {
            hasBedNearby -> DummyAnimationType.SLEEP
            player.isSneaking -> DummyAnimationType.SIT
            else -> DummyAnimationType.STAND_IDLE
        }
    }
}
