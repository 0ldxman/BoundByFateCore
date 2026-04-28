package omc.boundbyfate.config.loader

import omc.boundbyfate.api.item.ItemDefinition
import omc.boundbyfate.registry.ItemPropertyRegistry

/**
 * Загрузчик [ItemDefinition] из датапаков.
 *
 * Загружает JSON файлы из `data/<namespace>/bbf_item/**\/*.json`
 * и регистрирует их в [ItemPropertyRegistry].
 *
 * ## Структура файлов
 *
 * Можно организовывать по любой структуре папок:
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_item/
 *       weapons/
 *         iron_sword.json
 *         diamond_sword.json
 *       armor/
 *         chainmail_chestplate.json
 *       accessories/
 *         magic_amulet.json
 *       minecraft/
 *         iron_sword.json    ← можно и так
 * ```
 *
 * ID предмета берётся из поля `"item"` в JSON, структура папок не влияет.
 *
 * ## Горячая перезагрузка
 *
 * При `/reload` все Definition очищаются и загружаются заново.
 * После загрузки вызывается пересчёт свойств для всех онлайн игроков.
 */
object ItemConfigLoader : ConfigLoader<ItemDefinition>(
    typeName = "item",
    codec = ItemDefinition.CODEC,
    registry = object : omc.boundbyfate.registry.core.BbfRegistry<ItemDefinition>("items") {
        override fun register(entry: ItemDefinition) {
            entry.validate()
            ItemPropertyRegistry.registerItemDefinition(entry)
        }
    }
) {
    override fun onBeforeLoad() {
        super.onBeforeLoad()
        ItemPropertyRegistry.clearItemDefinitions()
    }

    override fun onAfterLoad(loadedCount: Int) {
        super.onAfterLoad(loadedCount)
        logger.info("  Item definitions loaded: $loadedCount")
        ItemPropertyRegistry.printStatistics()
    }
}

