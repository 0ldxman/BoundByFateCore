package omc.boundbyfate.data.world.core

/**
 * Индекс для быстрого поиска данных в секции.
 *
 * Автоматически обновляется при изменении данных через [syncedMap].
 * Не сериализуется — пересоздаётся при загрузке из данных.
 *
 * ## Пример
 *
 * ```kotlin
 * class CharacterSection : WorldDataSection() {
 *     val characters by syncedMap(UuidCodec, CharacterData.CODEC)
 *
 *     // Индекс: ownerId → список персонажей этого игрока
 *     val byOwner = index(characters) { it.ownerId }
 *
 *     // Уникальный индекс: displayName → персонаж
 *     val byName = uniqueIndex(characters) { it.displayName }
 * }
 *
 * // Использование
 * section.byOwner[playerId]        // List<CharacterData>
 * section.byName["Elio_Hellblade"] // CharacterData?
 * ```
 *
 * @param K тип ключа индекса
 * @param V тип значения
 */
class WorldDataIndex<K, V>(
    private val extractor: (V) -> K
) {
    private val index = mutableMapOf<K, MutableList<V>>()

    /**
     * Возвращает все значения по ключу индекса.
     */
    operator fun get(key: K): List<V> = index[key] ?: emptyList()

    /**
     * Проверяет наличие ключа.
     */
    operator fun contains(key: K): Boolean = index.containsKey(key)

    /**
     * Добавляет значение в индекс.
     * Вызывается автоматически при put в syncedMap.
     */
    internal fun add(value: V) {
        val key = extractor(value)
        index.getOrPut(key) { mutableListOf() }.add(value)
    }

    /**
     * Удаляет значение из индекса.
     * Вызывается автоматически при remove из syncedMap.
     */
    internal fun remove(value: V) {
        val key = extractor(value)
        index[key]?.remove(value)
        if (index[key]?.isEmpty() == true) index.remove(key)
    }

    /**
     * Обновляет значение в индексе (удаляет старое, добавляет новое).
     * Вызывается автоматически при замене значения в syncedMap.
     */
    internal fun update(oldValue: V?, newValue: V) {
        if (oldValue != null) remove(oldValue)
        add(newValue)
    }

    /**
     * Полностью перестраивает индекс из коллекции значений.
     * Вызывается при загрузке секции из NBT.
     */
    internal fun rebuild(values: Collection<V>) {
        index.clear()
        values.forEach { add(it) }
    }

    /**
     * Очищает индекс.
     */
    internal fun clear() {
        index.clear()
    }
}

/**
 * Уникальный индекс — один ключ соответствует одному значению.
 *
 * ```kotlin
 * val byName = uniqueIndex(characters) { it.displayName }
 * section.byName["Elio_Hellblade"] // CharacterData?
 * ```
 */
class UniqueWorldDataIndex<K, V>(
    private val extractor: (V) -> K
) {
    private val index = mutableMapOf<K, V>()

    operator fun get(key: K): V? = index[key]

    operator fun contains(key: K): Boolean = index.containsKey(key)

    internal fun add(value: V) {
        index[extractor(value)] = value
    }

    internal fun remove(value: V) {
        index.remove(extractor(value))
    }

    internal fun update(oldValue: V?, newValue: V) {
        if (oldValue != null) remove(oldValue)
        add(newValue)
    }

    internal fun rebuild(values: Collection<V>) {
        index.clear()
        values.forEach { add(it) }
    }

    internal fun clear() {
        index.clear()
    }
}
