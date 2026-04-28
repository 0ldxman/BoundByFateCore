package omc.boundbyfate.config.loader

import omc.boundbyfate.api.feature.FeatureDefinition
import omc.boundbyfate.system.feature.FeatureRegistry

/**
 * Загрузчик [FeatureDefinition] из датапаков.
 *
 * Загружает JSON файлы из `data/<namespace>/bbf_feature/**/*.json` (рекурсивно)
 * и регистрирует их в [FeatureRegistry].
 *
 * ## Структура файлов
 *
 * Поддерживается произвольная вложенность папок для удобства организации:
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_feature/
 *       second_wind.json
 *       extra_attack.json
 *       fighter/
 *         action_surge.json
 *         indomitable.json
 *       wizard/
 *         wizard_spellcasting.json
 *         arcane_recovery.json
 *       spellcasting/
 *         wizard_spellcasting.json
 *         sorcerer_spellcasting.json
 * ```
 *
 * ID особенности берётся из поля `"id"` в JSON, структура папок не влияет на ID.
 *
 * ## Горячая перезагрузка
 *
 * При `/reload` датапаков все Definition очищаются и загружаются заново.
 */
object FeatureConfigLoader : ConfigLoader<FeatureDefinition>(
    typeName = "feature",
    codec = FeatureDefinition.CODEC,
    registry = FeatureRegistry
) {
    override fun onAfterLoad(loadedCount: Int) {
        super.onAfterLoad(loadedCount)
        
        // Статистика по типам грантов
        val withMechanics = FeatureRegistry.getFeaturesWithMechanics().size
        val withAbilities = FeatureRegistry.getFeaturesWithAbilities().size
        logger.info("  Features with mechanics: $withMechanics")
        logger.info("  Features with abilities: $withAbilities")
    }
}

