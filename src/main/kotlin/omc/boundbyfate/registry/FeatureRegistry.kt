package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.feature.FeatureDefinition
import omc.boundbyfate.api.feature.FeatureGrant
import omc.boundbyfate.registry.core.BbfRegistry

/**
 * Реестр особенностей классов.
 *
 * Хранит [FeatureDefinition] — определения особенностей.
 * Загружаются из JSON датапаков через [omc.boundbyfate.config.loader.FeatureConfigLoader].
 *
 * ## Использование
 *
 * ```kotlin
 * val secondWind = FeatureRegistry.get(Identifier("boundbyfate-core", "second_wind"))
 * val spellcasting = FeatureRegistry.get(Identifier("boundbyfate-core", "wizard_spellcasting"))
 * ```
 *
 * ## Структура файлов
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_feature/
 *       second_wind.json
 *       wizard_spellcasting.json
 *       extra_attack.json
 * ```
 */
object FeatureRegistry : BbfRegistry<FeatureDefinition>("features") {
    
    /**
     * Получает особенности по тегу.
     */
    fun getByTag(tag: String): List<FeatureDefinition> {
        return getAll().filter { it.hasTag(tag) }
    }
    
    /**
     * Получает особенности с грантами определённого типа.
     */
    inline fun <reified T : FeatureGrant> getFeaturesWithGrantType(): List<FeatureDefinition> {
        return getAll().filter { feature ->
            feature.grants.any { it is T }
        }
    }
    
    /**
     * Получает все особенности с механиками.
     */
    fun getFeaturesWithMechanics(): List<FeatureDefinition> {
        return getFeaturesWithGrantType<FeatureGrant.Mechanic>()
    }
    
    /**
     * Получает все особенности со способностями.
     */
    fun getFeaturesWithAbilities(): List<FeatureDefinition> {
        return getFeaturesWithGrantType<FeatureGrant.Ability>()
    }
}

