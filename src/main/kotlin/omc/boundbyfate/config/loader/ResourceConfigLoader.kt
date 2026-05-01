package omc.boundbyfate.config.loader

import omc.boundbyfate.api.resource.ResourceDefinition
import omc.boundbyfate.registry.ResourceRegistry

/**
 * Загрузчик [ResourceDefinition] из датапаков.
 *
 * Загружает JSON файлы из `data/<namespace>/bbf_resource/**/*.json` (рекурсивно)
 * и регистрирует их в [ResourceRegistry].
 *
 * ## Структура файлов
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_resource/
 *       ki_points.json
 *       rage_uses.json
 *       spell_slots/
 *         spell_slot_1.json
 *         spell_slot_2.json
 *         spell_slot_3.json
 * ```
 *
 * ## Примеры JSON
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:ki_points",
 *   "recovery": {"type": "on_event", "event": "boundbyfate-core:rest/short"}
 * }
 * ```
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:spell_slot_1",
 *   "recovery": {"type": "on_event", "event": "boundbyfate-core:rest/long"}
 * }
 * ```
 *
 * ## Горячая перезагрузка
 *
 * При `/reload` датапаков все Definition очищаются и загружаются заново.
 */
object ResourceConfigLoader : ConfigLoader<ResourceDefinition>(
    typeName = "resource",
    codec = ResourceDefinition.CODEC,
    registry = ResourceRegistry
) {
    override fun onAfterLoad(loadedCount: Int) {
        super.onAfterLoad(loadedCount)

        val shortRest = ResourceRegistry.getShortRestResources().size
        val longRest = ResourceRegistry.getLongRestResources().size
        val manual = loadedCount - shortRest - longRest
        logger.info("  Short rest: $shortRest, Long rest: $longRest, Manual/other: $manual")
    }
}
