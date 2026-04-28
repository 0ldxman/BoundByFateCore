package omc.boundbyfate.data.world.core

import org.slf4j.LoggerFactory

/**
 * Транзакция для атомарных операций над секциями WorldData.
 *
 * Гарантирует что либо все изменения применяются, либо ни одно.
 * Если в блоке транзакции выбрасывается исключение — все изменения откатываются.
 *
 * ## Использование
 *
 * ```kotlin
 * BbfWorldData.get(server).transaction {
 *     val chars = getSection(CharacterSystem.SECTION)
 *     chars.characters[oldId] = savedCharacter
 *     chars.activeCharacters[playerId] = newCharacterId
 *     // если что-то упало — откат, markDirty() не вызывается
 * }
 * ```
 *
 * ## Как работает
 *
 * Перед транзакцией делается снимок (snapshot) NBT всех затронутых секций.
 * При ошибке — секции восстанавливаются из снимка.
 * При успехе — секции помечаются dirty для сохранения.
 */
class WorldDataTransaction(
    private val sections: Map<Any, WorldDataSection>
) {
    private val logger = LoggerFactory.getLogger(WorldDataTransaction::class.java)

    /**
     * Снимки секций до начала транзакции.
     * Key: секция, Value: NBT снимок.
     */
    private val snapshots = mutableMapOf<WorldDataSection, net.minecraft.nbt.NbtCompound>()

    /**
     * Секции затронутые в этой транзакции.
     */
    private val touchedSections = mutableSetOf<WorldDataSection>()

    /**
     * Получает секцию в контексте транзакции.
     * При первом обращении делает снимок секции.
     */
    fun <T : WorldDataSection> getSection(entry: SectionEntry<T>): T {
        @Suppress("UNCHECKED_CAST")
        val section = sections[entry] as? T
            ?: throw IllegalStateException("Section '${entry.id}' not registered in BbfWorldData")

        // Делаем снимок при первом обращении
        if (section !in snapshots) {
            snapshots[section] = section.toNbt()
            touchedSections += section
        }

        return section
    }

    /**
     * Откатывает все изменения к снимкам.
     */
    internal fun rollback() {
        for ((section, snapshot) in snapshots) {
            try {
                section.fromNbt(snapshot)
                section.markClean()
            } catch (e: Exception) {
                logger.error("Failed to rollback section during transaction", e)
            }
        }
        logger.warn("Transaction rolled back (${snapshots.size} sections restored)")
    }

    /**
     * Фиксирует транзакцию — помечает затронутые секции dirty.
     */
    internal fun commit() {
        touchedSections.forEach { it.markDirty() }
    }
}

/**
 * DSL для выполнения транзакции.
 *
 * ```kotlin
 * worldData.transaction {
 *     val chars = getSection(CharacterSystem.SECTION)
 *     chars.characters[id] = newCharacter
 * }
 * ```
 */
inline fun WorldDataTransaction.execute(block: WorldDataTransaction.() -> Unit): Result<Unit> {
    return try {
        block()
        commit()
        Result.success(Unit)
    } catch (e: Exception) {
        rollback()
        Result.failure(e)
    }
}
