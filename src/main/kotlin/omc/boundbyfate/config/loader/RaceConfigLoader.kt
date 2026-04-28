package omc.boundbyfate.config.loader

import omc.boundbyfate.api.race.RaceDefinition
import omc.boundbyfate.registry.RaceRegistry

/**
 * Загрузчик [RaceDefinition] из датапаков.
 *
 * Загружает JSON файлы из `data/<namespace>/bbf_race/*.json`
 * и регистрирует их в [RaceRegistry].
 *
 * ## Структура файлов
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_race/
 *       dwarf.json
 *       mountain_dwarf.json
 *       hill_dwarf.json
 *       human.json
 *       elf.json
 *       high_elf.json
 * ```
 *
 * ## Порядок загрузки
 *
 * Порядок не важен — ссылки между расами (subraces, parent_race)
 * валидируются в [RaceRegistry.onRegistrationComplete] после загрузки всех файлов.
 */
object RaceConfigLoader : ConfigLoader<RaceDefinition>(
    typeName = "race",
    codec = RaceDefinition.CODEC,
    registry = RaceRegistry
)
