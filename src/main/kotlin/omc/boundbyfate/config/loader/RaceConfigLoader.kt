package omc.boundbyfate.config.loader

import omc.boundbyfate.api.race.RaceDefinition
import omc.boundbyfate.registry.RaceRegistry

/**
 * Загрузчик [RaceDefinition] из датапаков.
 *
 * Загружает JSON файлы из data/namespace/bbf_race/ и регистрирует их в [RaceRegistry].
 *
 * Порядок загрузки не важен — ссылки между расами валидируются
 * в [RaceRegistry.onRegistrationComplete] после загрузки всех файлов.
 */
object RaceConfigLoader : ConfigLoader<RaceDefinition>(
    typeName = "race",
    codec = RaceDefinition.CODEC,
    registry = RaceRegistry
)
