package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.status.StatusDefinition
import omc.boundbyfate.registry.core.BbfRegistry

/**
 * Реестр состояний (Status Conditions).
 *
 * Хранит [StatusDefinition] — определения состояний.
 * Загружаются из JSON датапаков через [omc.boundbyfate.config.loader.StatusConfigLoader].
 *
 * ## Использование
 *
 * ```kotlin
 * val paralyzed = StatusRegistry.get(Identifier("boundbyfate-core", "paralyzed"))
 * val allStatuses = StatusRegistry.getAll()
 * ```
 *
 * ## Структура файлов
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_status/
 *       paralyzed.json
 *       poisoned.json
 *       stunned.json
 *       ...
 * ```
 */
object StatusRegistry : BbfRegistry<StatusDefinition>("statuses") {

    /**
     * Получает все состояния которые включают данное.
     *
     * Используется при снятии состояния для проверки
     * нужно ли снимать включённые состояния.
     */
    fun getStatusesThatInclude(statusId: Identifier): List<StatusDefinition> {
        return getAll().filter { it.includes.contains(statusId) }
    }

    /**
     * Получает все includes данного состояния рекурсивно.
     *
     * Например, paralyzed → [incapacitated]
     * incapacitated → []
     * Результат: [incapacitated]
     */
    fun getAllIncludes(statusId: Identifier, visited: MutableSet<Identifier> = mutableSetOf()): Set<Identifier> {
        if (statusId in visited) return emptySet()
        visited.add(statusId)

        val definition = get(statusId) ?: return emptySet()
        val result = mutableSetOf<Identifier>()

        for (includedId in definition.includes) {
            result.add(includedId)
            result.addAll(getAllIncludes(includedId, visited))
        }

        return result
    }
}

