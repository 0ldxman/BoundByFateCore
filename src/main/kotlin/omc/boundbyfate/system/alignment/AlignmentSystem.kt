package omc.boundbyfate.system.alignment

import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.alignment.AlignmentConfig
import omc.boundbyfate.api.alignment.AlignmentDefinition
import omc.boundbyfate.api.alignment.AlignmentPoint
import omc.boundbyfate.component.components.EntityCharacterData
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.sections.CharacterSection
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Система мировоззрений.
 *
 * Управляет конфигурацией сетки и определяет мировоззрение
 * по координатам персонажа.
 *
 * Мировоззрение хранится в CharacterData.worldView.alignment.alignmentPoint (WorldData).
 * Читается и изменяется через CharacterSection.
 */
object AlignmentSystem {

    private val logger = LoggerFactory.getLogger(AlignmentSystem::class.java)

    var config: AlignmentConfig = AlignmentConfig.DEFAULT
        private set

    // ── Конфигурация ──────────────────────────────────────────────────────

    fun loadConfig(config: AlignmentConfig) {
        this.config = config
        logger.info(
            "Loaded alignment config: grid_size=${config.gridSize}, " +
            "alignments=${config.alignments.size}"
        )
        validateConfig()
    }

    private fun validateConfig() {
        for (alignment in config.alignments) {
            try {
                alignment.validate()
            } catch (e: Exception) {
                logger.error("Invalid alignment definition '${alignment.id}': ${e.message}")
            }
        }
    }

    // ── Определение мировоззрения ─────────────────────────────────────────

    fun resolve(point: AlignmentPoint): AlignmentDefinition? =
        config.alignments.firstOrNull { it.contains(point.x, point.y) }

    fun resolve(x: Int, y: Int): AlignmentDefinition? =
        resolve(AlignmentPoint(x, y))

    fun resolve(entity: LivingEntity): AlignmentDefinition? {
        val point = getPoint(entity) ?: return null
        return resolve(point)
    }

    fun resolveId(entity: LivingEntity): Identifier? =
        resolve(entity)?.id

    // ── Управление координатами ───────────────────────────────────────────

    /**
     * Возвращает текущие координаты мировоззрения персонажа.
     * Читает из CharacterData в WorldData через characterId на entity.
     */
    fun getPoint(entity: LivingEntity): AlignmentPoint? {
        val characterId = entity.getOrCreate(EntityCharacterData.TYPE).characterId
            ?: return AlignmentPoint.NEUTRAL

        if (entity !is ServerPlayerEntity) return AlignmentPoint.NEUTRAL

        val section = BbfWorldData.get(entity.server).getSection(CharacterSection.TYPE)
        return section.characters[characterId]?.worldView?.alignment?.alignmentPoint
    }

    /**
     * Устанавливает координаты мировоззрения персонажа.
     * Пишет в CharacterData в WorldData.
     */
    fun set(entity: LivingEntity, point: AlignmentPoint) {
        val (clampedX, clampedY) = config.clamp(point.x, point.y)
        val clamped = AlignmentPoint(clampedX, clampedY)

        updateAlignmentPoint(entity, clamped)

        val alignment = resolve(clamped)
        logger.debug(
            "Set alignment of '${entity.name.string}' to $clamped " +
            "(${alignment?.id ?: "unknown"})"
        )
    }

    /**
     * Сдвигает координаты мировоззрения персонажа.
     */
    fun shift(entity: LivingEntity, dx: Int, dy: Int) {
        val current = getPoint(entity) ?: AlignmentPoint.NEUTRAL
        val newPoint = current.shift(dx, dy)
        set(entity, newPoint)
    }

    // ── Проверки ──────────────────────────────────────────────────────────

    fun hasAlignment(entity: LivingEntity, alignmentId: Identifier): Boolean =
        resolveId(entity) == alignmentId

    fun isGood(entity: LivingEntity): Boolean =
        (getPoint(entity)?.y ?: 0) > 0

    fun isEvil(entity: LivingEntity): Boolean =
        (getPoint(entity)?.y ?: 0) < 0

    fun isLawful(entity: LivingEntity): Boolean =
        (getPoint(entity)?.x ?: 0) < 0

    fun isChaotic(entity: LivingEntity): Boolean =
        (getPoint(entity)?.x ?: 0) > 0

    fun isNeutralOnGoodEvil(entity: LivingEntity): Boolean =
        !isGood(entity) && !isEvil(entity)

    fun isNeutralOnLawChaos(entity: LivingEntity): Boolean =
        !isLawful(entity) && !isChaotic(entity)

    // ── Внутренняя логика ─────────────────────────────────────────────────

    private fun updateAlignmentPoint(entity: LivingEntity, point: AlignmentPoint) {
        if (entity !is ServerPlayerEntity) return

        val characterId = entity.getOrCreate(EntityCharacterData.TYPE).characterId ?: return
        val section = BbfWorldData.get(entity.server).getSection(CharacterSection.TYPE)
        val character = section.characters[characterId] ?: return

        val oldAlignment = character.worldView.alignment
        val newAlignment = oldAlignment.copy(
            alignmentPoint = point,
            previousAlignment = oldAlignment.alignmentPoint
        )
        val updatedCharacter = character.copy(
            worldView = character.worldView.copy(alignment = newAlignment)
        )
        section.characters[characterId] = updatedCharacter
    }
}
