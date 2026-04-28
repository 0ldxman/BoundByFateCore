package omc.boundbyfate.config.loader

import omc.boundbyfate.api.status.StatusDefinition
import omc.boundbyfate.registry.StatusRegistry

/**
 * Загрузчик [StatusDefinition] из датапаков.
 *
 * Загружает JSON файлы из `data/<namespace>/bbf_status/**/*.json`
 * и регистрирует их в [StatusRegistry].
 *
 * ## Структура файлов
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_status/
 *       blinded.json
 *       charmed.json
 *       paralyzed.json
 *       stunned.json
 *       ...
 * ```
 *
 * ID состояния берётся из поля `"id"` в JSON.
 *
 * ## Горячая перезагрузка
 *
 * При `/reload` датапаков все Definition очищаются и загружаются заново.
 */
object StatusConfigLoader : ConfigLoader<StatusDefinition>(
    typeName = "status",
    codec = StatusDefinition.CODEC,
    registry = StatusRegistry
)
