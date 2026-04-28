package omc.boundbyfate.config.loader

import omc.boundbyfate.api.charclass.ClassDefinition
import omc.boundbyfate.system.charclass.ClassRegistry

/**
 * Загрузчик [ClassDefinition] из датапаков.
 *
 * Загружает JSON файлы из `data/<namespace>/bbf_class/**/*.json` (рекурсивно)
 * и регистрирует их в [ClassRegistry].
 *
 * ## Структура файлов
 *
 * Поддерживается произвольная вложенность папок для удобства организации:
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_class/
 *       fighter.json
 *       wizard.json
 *       subclasses/
 *         champion.json
 *         battle_master.json
 *       martial/
 *         barbarian.json
 *         monk.json
 *       spellcasters/
 *         full/
 *           wizard.json
 *           cleric.json
 *         half/
 *           paladin.json
 * ```
 *
 * ID класса берётся из поля `"id"` в JSON, структура папок не влияет на ID.
 *
 * ## Горячая перезагрузка
 *
 * При `/reload` датапаков все Definition очищаются и загружаются заново.
 */
object ClassConfigLoader : ConfigLoader<ClassDefinition>(
    typeName = "class",
    codec = ClassDefinition.CODEC,
    registry = ClassRegistry
) {
    override fun onAfterLoad(loadedCount: Int) {
        super.onAfterLoad(loadedCount)
        
        // Статистика
        val mainClasses = ClassRegistry.getAllMainClasses().size
        val subclasses = ClassRegistry.getAllSubclasses().size
        logger.info("  Main classes: $mainClasses")
        logger.info("  Subclasses: $subclasses")
    }
}

