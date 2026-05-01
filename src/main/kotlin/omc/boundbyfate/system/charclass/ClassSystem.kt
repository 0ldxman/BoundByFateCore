package omc.boundbyfate.system.charclass

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.charclass.ClassDefinition
import omc.boundbyfate.api.level.LevelGrant
import omc.boundbyfate.component.components.EntityCharacterData
import omc.boundbyfate.component.core.getOrCreate
import omc.boundbyfate.data.world.BbfWorldData
import omc.boundbyfate.data.world.sections.CharacterSection
import omc.boundbyfate.system.feature.FeatureSystem
import org.slf4j.LoggerFactory

/**
 * Система управления классами персонажей.
 *
 * Отвечает за:
 * - Применение грантов класса при получении уровня
 * - Выбор подкласса
 * - Получение информации о классе персонажа
 *
 * ## Использование
 *
 * ```kotlin
 * // Применить гранты 1 уровня Fighter
 * ClassSystem.applyLevelGrants(player, fighterClass, 1)
 *
 * // Выбрать подкласс
 * ClassSystem.selectSubclass(player, championSubclass)
 *
 * // Получить все гранты до уровня
 * val grants = ClassSystem.getGrantsUpToLevel(fighterClass, 5)
 * ```
 */
object ClassSystem {
    
    private val logger = LoggerFactory.getLogger(ClassSystem::class.java)
    
    /**
     * Применяет гранты конкретного уровня класса.
     *
     * @param player игрок
     * @param classDefinition класс
     * @param level уровень
     */
    fun applyLevelGrants(
        player: ServerPlayerEntity,
        classDefinition: ClassDefinition,
        level: Int
    ) {
        val grants = classDefinition.getGrantsForLevel(level)
        
        if (grants.isEmpty()) {
            logger.debug("No grants for ${classDefinition.id} level $level")
            return
        }
        
        logger.info("Applying ${grants.size} grants for ${classDefinition.id} level $level to ${player.name.string}")
        
        for (grant in grants) {
            applyGrant(player, grant, classDefinition, level)
        }
    }
    
    /**
     * Применяет один грант.
     */
    private fun applyGrant(
        player: ServerPlayerEntity,
        grant: LevelGrant,
        classDefinition: ClassDefinition,
        level: Int
    ) {
        when (grant) {
            is LevelGrant.Feature -> {
                FeatureSystem.applyFeature(player, grant.featureId, classDefinition.id)
            }
            
            is LevelGrant.SubclassChoice -> {
                logger.info("Player ${player.name.string} can now choose subclass for ${classDefinition.id}")
                // TODO: открыть UI выбора подкласса
                // Пока что просто логируем
            }
        }
    }
    
    /**
     * Выбирает подкласс для игрока.
     */
    fun selectSubclass(player: ServerPlayerEntity, subclass: ClassDefinition) {
        if (!subclass.isSubclass) {
            logger.error("Attempted to select non-subclass ${subclass.id} as subclass")
            return
        }

        val parentClass = subclass.parentClass
        if (parentClass == null) {
            logger.error("Subclass ${subclass.id} has no parent class")
            return
        }

        logger.info("Player ${player.name.string} selected subclass ${subclass.id} for $parentClass")

        // Сохраняем выбор подкласса в WorldData
        val characterId = player.getOrCreate(EntityCharacterData.TYPE).characterId
        if (characterId != null) {
            val section = BbfWorldData.get(player.server).getSection(CharacterSection.TYPE)
            val character = section.characters[characterId]
            if (character != null) {
                section.characters[characterId] = character.copy(
                    charClass = character.charClass.copy(subclassId = subclass.id)
                )
            }
        }

        // Применяем гранты подкласса с текущего уровня
        val currentLevel = getClassLevel(player, parentClass)
        val grants = subclass.getGrantsUpToLevel(currentLevel)

        for ((level, levelGrants) in grants) {
            for (grant in levelGrants) {
                applyGrant(player, grant, subclass, level)
            }
        }
    }

    /**
     * Получает уровень персонажа из WorldData.
     */
    private fun getClassLevel(player: ServerPlayerEntity, classId: Identifier): Int {
        val characterId = player.getOrCreate(EntityCharacterData.TYPE).characterId
            ?: return 1
        val section = BbfWorldData.get(player.server).getSection(CharacterSection.TYPE)
        return section.characters[characterId]?.progression?.level ?: 1
    }
    
    /**
     * Получает все гранты класса до указанного уровня.
     */
    fun getGrantsUpToLevel(
        classDefinition: ClassDefinition,
        targetLevel: Int
    ): Map<Int, List<LevelGrant>> {
        return classDefinition.getGrantsUpToLevel(targetLevel)
    }
    
    /**
     * Снимает все гранты класса (при смене класса или смерти).
     *
     * @param player игрок
     * @param classDefinition класс
     * @param level до какого уровня снимать
     */
    fun removeClassGrants(
        player: ServerPlayerEntity,
        classDefinition: ClassDefinition,
        level: Int
    ) {
        val grants = classDefinition.getGrantsUpToLevel(level)
        
        logger.info("Removing grants for ${classDefinition.id} up to level $level from ${player.name.string}")
        
        for ((grantLevel, levelGrants) in grants) {
            for (grant in levelGrants) {
                removeGrant(player, grant, classDefinition, grantLevel)
            }
        }
    }
    
    /**
     * Снимает один грант.
     */
    private fun removeGrant(
        player: ServerPlayerEntity,
        grant: LevelGrant,
        classDefinition: ClassDefinition,
        level: Int
    ) {
        when (grant) {
            is LevelGrant.Feature -> {
                FeatureSystem.removeFeature(player, grant.featureId)
            }
            
            is LevelGrant.SubclassChoice -> {
                // Ничего не делаем
            }
        }
    }
}
