package omc.boundbyfate.system.skin

import net.minecraft.server.network.ServerPlayerEntity
import omc.boundbyfate.component.components.EntityAppearanceData
import omc.boundbyfate.component.components.NpcModelComponent
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.character.CharacterAppearance
import omc.boundbyfate.data.world.character.ModelType
import omc.boundbyfate.data.world.sections.CharacterSection
import omc.boundbyfate.component.components.EntityCharacterData
import omc.boundbyfate.component.core.getComponent
import omc.boundbyfate.entity.NpcEntity
import omc.boundbyfate.system.transfer.FileCategory
import omc.boundbyfate.system.transfer.FileStorage
import org.slf4j.LoggerFactory

/**
 * Серверная система управления скинами.
 *
 * Не вызывай напрямую — используй extension-функции:
 *
 * ```kotlin
 * // Назначить скин игроку
 * player.assignSkin("elio_skin")
 * player.assignSkin("elio_skin", ModelType.ALEX)
 *
 * // Назначить скин НПС
 * npc.assignSkin("guard_skin")
 *
 * // Снять скин
 * player.clearSkin()
 * npc.clearSkin()
 * ```
 *
 * ## Как это работает
 *
 * Скин — это PNG файл загруженный через [omc.boundbyfate.system.transfer.FileTransferSystem]
 * с категорией [FileCategory.SKIN]. Файл хранится на сервере и раздаётся клиентам.
 *
 * Для игрока: skinId сохраняется в [CharacterAppearance] в WorldData.
 * Для НПС: skinId сохраняется в [NpcModelComponent] на сущности.
 *
 * Клиент читает skinId из компонента/WorldData и загружает текстуру из
 * [omc.boundbyfate.client.skin.ClientSkinManager].
 */
object SkinSystem {

    private val logger = LoggerFactory.getLogger(SkinSystem::class.java)

    // ── Игрок ─────────────────────────────────────────────────────────────

    /**
     * Назначает скин активному персонажу игрока.
     *
     * @param player игрок
     * @param skinId ID скина (имя файла без расширения, например "elio_skin")
     * @param modelType тип модели (STEVE = широкие руки, ALEX = тонкие)
     * @return true если скин назначен успешно
     */
    fun assignToPlayer(
        player: ServerPlayerEntity,
        skinId: String,
        modelType: ModelType = ModelType.STEVE
    ): Boolean {
        if (!FileStorage.exists(FileCategory.SKIN, skinId, "png")) {
            logger.warn("Skin '$skinId' not found in FileStorage, cannot assign to ${player.name.string}")
            return false
        }

        val characterId = player.getComponent(EntityCharacterData.TYPE)?.characterId
        if (characterId == null) {
            logger.warn("Player ${player.name.string} has no active character, cannot assign skin")
            return false
        }

        val section = BbfWorldData.get(player.server).getSection(CharacterSection.TYPE)
        val character = section.characters[characterId]
        if (character == null) {
            logger.warn("Character $characterId not found for player ${player.name.string}")
            return false
        }

        section.characters[characterId] = character.copy(
            identity = character.identity.copy(
                appearance = character.identity.appearance.copy(
                    skinId = skinId,
                    modelType = modelType
                )
            )
        )

        // Обновляем компонент на entity — клиент читает его напрямую
        val appearance = player.getOrCreate(EntityAppearanceData.TYPE)
        appearance.skinId = skinId
        appearance.modelType = modelType.name.lowercase()

        logger.info("Assigned skin '$skinId' (${modelType.name}) to ${player.name.string}'s character")
        return true
    }

    /**
     * Снимает скин с активного персонажа игрока.
     */
    fun clearPlayerSkin(player: ServerPlayerEntity) {
        val characterId = player.getComponent(EntityCharacterData.TYPE)?.characterId ?: return
        val section = BbfWorldData.get(player.server).getSection(CharacterSection.TYPE)
        val character = section.characters[characterId] ?: return

        section.characters[characterId] = character.copy(
            identity = character.identity.copy(
                appearance = character.identity.appearance.copy(
                    skinId = "",
                    modelType = ModelType.STEVE
                )
            )
        )

        // Сбрасываем компонент на entity
        val appearance = player.getOrCreate(EntityAppearanceData.TYPE)
        appearance.skinId = ""
        appearance.modelType = "steve"

        logger.info("Cleared skin from ${player.name.string}'s character")
    }

    // ── НПС ───────────────────────────────────────────────────────────────

    /**
     * Назначает скин НПС.
     *
     * @param npc НПС
     * @param skinId ID скина (имя файла без расширения)
     * @return true если скин назначен успешно
     */
    fun assignToNpc(npc: NpcEntity, skinId: String): Boolean {
        if (!FileStorage.exists(FileCategory.SKIN, skinId, "png")) {
            logger.warn("Skin '$skinId' not found in FileStorage, cannot assign to NPC ${npc.uuid}")
            return false
        }

        npc.getOrCreate(NpcModelComponent.TYPE).skinId = skinId
        logger.info("Assigned skin '$skinId' to NPC ${npc.uuid}")
        return true
    }

    /**
     * Снимает скин с НПС.
     */
    fun clearNpcSkin(npc: NpcEntity) {
        npc.getOrCreate(NpcModelComponent.TYPE).skinId = ""
        logger.info("Cleared skin from NPC ${npc.uuid}")
    }
}

// ── Extension-функции ─────────────────────────────────────────────────────

/**
 * Назначает скин активному персонажу игрока.
 *
 * ```kotlin
 * player.assignSkin("elio_skin")
 * player.assignSkin("elio_skin", ModelType.ALEX)
 * ```
 */
fun ServerPlayerEntity.assignSkin(skinId: String, modelType: ModelType = ModelType.STEVE): Boolean =
    SkinSystem.assignToPlayer(this, skinId, modelType)

/**
 * Снимает скин с активного персонажа игрока.
 *
 * ```kotlin
 * player.clearSkin()
 * ```
 */
fun ServerPlayerEntity.clearSkin() = SkinSystem.clearPlayerSkin(this)

/**
 * Назначает скин НПС.
 *
 * ```kotlin
 * npc.assignSkin("guard_skin")
 * ```
 */
fun NpcEntity.assignSkin(skinId: String): Boolean = SkinSystem.assignToNpc(this, skinId)

/**
 * Снимает скин с НПС.
 *
 * ```kotlin
 * npc.clearSkin()
 * ```
 */
fun NpcEntity.clearSkin() = SkinSystem.clearNpcSkin(this)
