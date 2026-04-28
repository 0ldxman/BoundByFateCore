package omc.boundbyfate.config.loader

import omc.boundbyfate.api.stat.StatDefinition
import omc.boundbyfate.registry.StatRegistry

/**
 * Загрузчик конфигураций для характеристик (Stats).
 * 
 * Загружает StatDefinition из JSON файлов в datapack:
 * `data/<namespace>/bbf_stat/*.json`
 */
object StatConfigLoader : ConfigLoader<StatDefinition>(
    typeName = "stat",
    codec = StatDefinition.CODEC,
    registry = StatRegistry
) {
    
    override fun onAfterLoad(loadedCount: Int) {
        super.onAfterLoad(loadedCount)
        
        // Можно добавить дополнительную логику после загрузки
        // Например, валидацию что загружены все обязательные статы
        validateRequiredStats()
    }
    
    /**
     * Проверяет что загружены все обязательные D&D статы.
     */
    private fun validateRequiredStats() {
        val requiredStats = listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")
        val namespace = "boundbyfate-core"
        
        for (statName in requiredStats) {
            val id = net.minecraft.util.Identifier.of(namespace, statName)
            if (!registry.contains(id)) {
                logger.warn("Required stat not found: $id")
            }
        }
    }
}
