package omc.boundbyfate.config.loader

import omc.boundbyfate.api.stat.StatDefinition
import omc.boundbyfate.registry.StatRegistry

/**
 * Загрузчик [StatDefinition] из датапаков.
 *
 * Загружает JSON файлы из data/namespace/bbf_stat/ и регистрирует их в [StatRegistry].
 */
object StatConfigLoader : ConfigLoader<StatDefinition>(
    typeName = "stat",
    codec = StatDefinition.CODEC,
    registry = StatRegistry
) {

    override fun onAfterLoad(loadedCount: Int) {
        super.onAfterLoad(loadedCount)
        validateRequiredStats()
    }

    private fun validateRequiredStats() {
        val requiredStats = listOf("strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma")
        val namespace = "boundbyfate-core"
        for (statName in requiredStats) {
            val id = net.minecraft.util.Identifier(namespace, statName)
            if (!registry.contains(id)) {
                logger.warn("Required stat not found: $id")
            }
        }
    }
}
