package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.charclass.ClassDefinition
import omc.boundbyfate.registry.core.BbfRegistry

/**
 * Реестр классов персонажей.
 *
 * Хранит [ClassDefinition] — определения классов и подклассов.
 * Загружаются из JSON датапаков через [omc.boundbyfate.config.loader.ClassConfigLoader].
 *
 * ## Использование
 *
 * ```kotlin
 * val fighter = ClassRegistry.get(Identifier("boundbyfate-core", "fighter"))
 * val champion = ClassRegistry.get(Identifier("boundbyfate-core", "champion"))
 * ```
 *
 * ## Структура файлов
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_class/
 *       fighter.json
 *       wizard.json
 *       champion.json  (подкласс)
 * ```
 */
object ClassRegistry : BbfRegistry<ClassDefinition>("classes") {
    
    /**
     * Получает все основные классы (не подклассы).
     */
    fun getAllMainClasses(): List<ClassDefinition> {
        return getAll().filter { !it.isSubclass }
    }
    
    /**
     * Получает все подклассы.
     */
    fun getAllSubclasses(): List<ClassDefinition> {
        return getAll().filter { it.isSubclass }
    }
    
    /**
     * Получает подклассы для конкретного класса.
     */
    fun getSubclassesFor(classId: Identifier): List<ClassDefinition> {
        return getAllSubclasses().filter { it.parentClass == classId }
    }
    
    /**
     * Получает класс по тегу.
     */
    fun getByTag(tag: String): List<ClassDefinition> {
        return getAll().filter { it.hasTag(tag) }
    }
}

