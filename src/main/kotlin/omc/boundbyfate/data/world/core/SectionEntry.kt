package omc.boundbyfate.data.world.core

import net.minecraft.util.Identifier

/**
 * Метаданные зарегистрированной секции WorldData.
 *
 * @param id уникальный идентификатор секции
 * @param fileName имя файла сохранения (без расширения)
 * @param syncStrategy стратегия синхронизации с клиентами
 * @param factory фабрика для создания пустой секции
 */
data class SectionEntry<T : WorldDataSection>(
    val id: Identifier,
    val fileName: String,
    val syncStrategy: SyncStrategy,
    val factory: () -> T
)
