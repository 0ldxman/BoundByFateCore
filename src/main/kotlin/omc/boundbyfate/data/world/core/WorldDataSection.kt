package omc.boundbyfate.data.world.core

import com.mojang.serialization.Codec
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtString
import net.minecraft.util.Identifier
import omc.boundbyfate.util.sync.decodeFromNbt
import omc.boundbyfate.util.sync.encodeToNbt
import omc.boundbyfate.component.core.SyncedSerializable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Базовый класс для секций WorldData.
 *
 * Секция — это независимый модуль данных мирового уровня.
 * Сохраняется в отдельный файл, синхронизируется адресно.
 *
 * Использует те же [synced] делегаты что и [omc.boundbyfate.component.core.BbfComponent]:
 * - Автоматический dirty tracking
 * - Автоматическая сериализация через Codec или встроенный маппинг примитивов
 * - Индексы для быстрого поиска
 * - События на изменение данных
 *
 * ## Создание секции
 *
 * ```kotlin
 * class CharacterSection : WorldDataSection() {
 *
 *     val characters by syncedMap(UuidCodec, CharacterData.CODEC)
 *     val activeCharacters by syncedMap(UuidCodec, UuidCodec)
 *
 *     // Индексы — обновляются автоматически
 *     val byOwner = index(characters) { it.ownerId }
 *     val byName  = uniqueIndex(characters) { it.displayName }
 *
 *     companion object {
 *         val TYPE = BbfWorldData.registerSection(
 *             id = "boundbyfate-core:characters",
 *             file = "boundbyfate_characters",
 *             syncStrategy = SyncStrategy.ToAll,
 *             factory = ::CharacterSection
 *         )
 *     }
 * }
 * ```
 *
 * ## Версионирование
 *
 * ```kotlin
 * class CharacterSection : WorldDataSection(version = 2) {
 *     override fun migrate(fromVersion: Int, nbt: NbtCompound) = when (fromVersion) {
 *         1 -> nbt.apply { put("alignment", NbtCompound()) }
 *         else -> nbt
 *     }
 * }
 * ```
 *
 * @param version текущая версия схемы данных. Увеличивай при изменении структуры.
 */
abstract class WorldDataSection(val version: Int = 1) {

    /**
     * Флаг — секция изменилась и нужно сохранение на диск.
     */
    var isDirty = false
        internal set

    /**
     * Все зарегистрированные synced-свойства этой секции.
     */
    internal val syncedProperties = mutableListOf<SyncedSerializable>()

    /**
     * Все зарегистрированные индексы для перестройки после загрузки.
     */
    internal val indices = mutableListOf<IndexRebuildable>()

    fun markDirty() { isDirty = true }
    internal fun markClean() { isDirty = false }

    // ── Делегаты ──────────────────────────────────────────────────────────

    /**
     * Синхронизируемое примитивное свойство.
     * Поддерживаемые типы: [Int], [Long], [Float], [Double], [Boolean], [String], [Byte], [Short], [Identifier].
     */
    protected fun <T> synced(initial: T): SectionSyncedProperty<T> =
        SectionSyncedProperty(initial, codec = null) { isDirty = true }
            .also { syncedProperties += it }

    /**
     * Синхронизируемое свойство с явным Codec.
     */
    protected fun <T> synced(initial: T, codec: Codec<T>): SectionSyncedProperty<T> =
        SectionSyncedProperty(initial, codec) { isDirty = true }
            .also { syncedProperties += it }

    /**
     * Синхронизируемый список с Codec элементов.
     */
    protected fun <T : Any> syncedList(
        elementCodec: Codec<T>,
        initial: MutableList<T> = mutableListOf()
    ): SectionSyncedList<T> = SectionSyncedList(initial, elementCodec) { isDirty = true }
        .also { syncedProperties += it }

    /**
     * Синхронизируемая карта с поддержкой индексов и событий изменения.
     *
     * ```kotlin
     * val characters by syncedMap(UuidCodec, CharacterData.CODEC)
     * ```
     */
    protected fun <K : Any, V : Any> syncedMap(
        keyCodec: Codec<K>,
        valueCodec: Codec<V>,
        initial: MutableMap<K, V> = mutableMapOf()
    ): SectionSyncedMap<K, V> = SectionSyncedMap(initial, keyCodec, valueCodec) { isDirty = true }
        .also { syncedProperties += it }

    // ── Индексы ───────────────────────────────────────────────────────────

    /**
     * Создаёт индекс по полю значений карты.
     * Один ключ → список значений.
     *
     * ```kotlin
     * val byOwner = index(characters) { it.ownerId }
     * section.byOwner[playerId] // List<CharacterData>
     * ```
     */
    protected fun <MK : Any, V : Any, IK> index(
        map: SectionSyncedMap<MK, V>,
        extractor: (V) -> IK
    ): WorldDataIndex<IK, V> {
        val idx = WorldDataIndex(extractor)
        map.registerIndex(idx)
        indices += object : IndexRebuildable {
            override fun rebuild(values: Collection<*>) {
                @Suppress("UNCHECKED_CAST")
                idx.rebuild(values as Collection<V>)
            }
        }
        return idx
    }

    /**
     * Создаёт уникальный индекс по полю значений карты.
     * Один ключ → одно значение.
     *
     * ```kotlin
     * val byName = uniqueIndex(characters) { it.displayName }
     * section.byName["Elio_Hellblade"] // CharacterData?
     * ```
     */
    protected fun <MK : Any, V : Any, IK> uniqueIndex(
        map: SectionSyncedMap<MK, V>,
        extractor: (V) -> IK
    ): UniqueWorldDataIndex<IK, V> {
        val idx = UniqueWorldDataIndex(extractor)
        map.registerUniqueIndex(idx)
        indices += object : IndexRebuildable {
            override fun rebuild(values: Collection<*>) {
                @Suppress("UNCHECKED_CAST")
                idx.rebuild(values as Collection<V>)
            }
        }
        return idx
    }

    // ── Сериализация ──────────────────────────────────────────────────────

    /**
     * Сериализует секцию в NBT.
     * По умолчанию — автоматически по всем [synced] полям.
     */
    open fun toNbt(): NbtCompound = NbtCompound().also { nbt ->
        nbt.putInt("__version", version)
        syncedProperties.forEach { it.serializeTo(nbt) }
    }

    /**
     * Десериализует секцию из NBT.
     * Автоматически применяет миграции если версия устарела.
     */
    open fun fromNbt(rawNbt: NbtCompound) {
        val savedVersion = if (rawNbt.contains("__version")) rawNbt.getInt("__version") else 1
        val nbt = if (savedVersion < version) migrate(savedVersion, rawNbt) else rawNbt

        syncedProperties.forEach { it.deserializeFrom(nbt) }
        isDirty = false

        // Перестраиваем индексы после загрузки
        rebuildIndices()
    }

    /**
     * Мигрирует NBT со старой версии на текущую.
     * Переопредели если меняешь структуру данных.
     *
     * ```kotlin
     * override fun migrate(fromVersion: Int, nbt: NbtCompound) = when (fromVersion) {
     *     1 -> nbt.apply { put("newField", NbtInt.of(0)) }
     *     else -> nbt
     * }
     * ```
     */
    open fun migrate(fromVersion: Int, nbt: NbtCompound): NbtCompound = nbt

    /**
     * Перестраивает все индексы.
     * Вызывается автоматически после [fromNbt].
     */
    internal fun rebuildIndices() {
        syncedProperties.filterIsInstance<SectionSyncedMap<*, *>>().forEach { map ->
            map.rebuildIndices()
        }
    }
}

// ── Интерфейс для перестройки индексов ────────────────────────────────────

internal interface IndexRebuildable {
    fun rebuild(values: Collection<*>)
}

// ── SectionSyncedProperty ─────────────────────────────────────────────────

/**
 * Делегат для синхронизируемого свойства секции с автосериализацией.
 */
class SectionSyncedProperty<T>(
    private var value: T,
    private val codec: Codec<T>?,
    private val onChanged: () -> Unit
) : ReadWriteProperty<Any, T>, SyncedSerializable {

    var fieldName: String = ""
        private set

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (fieldName.isEmpty()) fieldName = property.name
        return value
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        if (fieldName.isEmpty()) fieldName = property.name
        this.value = value
        onChanged()
    }

    override fun serializeTo(nbt: NbtCompound) {
        if (fieldName.isEmpty()) return
        encodeToNbt(value, codec)?.let { nbt.put(fieldName, it) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserializeFrom(nbt: NbtCompound) {
        if (fieldName.isEmpty()) return
        val tag = nbt.get(fieldName) ?: return
        val decoded = decodeFromNbt(tag, codec, value) ?: return
        value = decoded as T
    }
}

// ── SectionSyncedList ─────────────────────────────────────────────────────

/**
 * Синхронизируемый список секции с автосериализацией.
 */
class SectionSyncedList<T : Any>(
    private val delegate: MutableList<T> = mutableListOf(),
    private val elementCodec: Codec<T>,
    private val onChanged: () -> Unit
) : MutableList<T> by delegate, SyncedSerializable {

    var fieldName: String = ""

    operator fun provideDelegate(thisRef: WorldDataSection, property: KProperty<*>): SectionSyncedList<T> {
        fieldName = property.name
        return this
    }

    operator fun getValue(thisRef: WorldDataSection, property: KProperty<*>): SectionSyncedList<T> {
        if (fieldName.isEmpty()) fieldName = property.name
        return this
    }

    override fun serializeTo(nbt: NbtCompound) {
        if (fieldName.isEmpty()) return
        elementCodec.listOf()
            .encodeStart(NbtOps.INSTANCE, delegate.toList())
            .result()
            .ifPresent { nbt.put(fieldName, it) }
    }

    override fun deserializeFrom(nbt: NbtCompound) {
        if (fieldName.isEmpty()) return
        val tag = nbt.get(fieldName) ?: return
        elementCodec.listOf()
            .parse(NbtOps.INSTANCE, tag)
            .result()
            .ifPresent { decoded ->
                delegate.clear()
                delegate.addAll(decoded)
            }
    }

    override fun add(element: T): Boolean = delegate.add(element).also { if (it) onChanged() }
    override fun add(index: Int, element: T) { delegate.add(index, element); onChanged() }
    override fun addAll(elements: Collection<T>): Boolean = delegate.addAll(elements).also { if (it) onChanged() }
    override fun remove(element: T): Boolean = delegate.remove(element).also { if (it) onChanged() }
    override fun removeAt(index: Int): T = delegate.removeAt(index).also { onChanged() }
    override fun set(index: Int, element: T): T = delegate.set(index, element).also { onChanged() }
    override fun clear() { delegate.clear(); onChanged() }
    override fun toString(): String = delegate.toString()
}

// ── SectionSyncedMap ──────────────────────────────────────────────────────

/**
 * Синхронизируемая карта секции с автосериализацией, индексами и событиями.
 */
class SectionSyncedMap<K : Any, V : Any>(
    private val delegate: MutableMap<K, V> = mutableMapOf(),
    private val keyCodec: Codec<K>,
    private val valueCodec: Codec<V>,
    private val onChanged: () -> Unit
) : MutableMap<K, V> by delegate, SyncedSerializable {

    var fieldName: String = ""

    private val regularIndices = mutableListOf<WorldDataIndex<*, V>>()
    private val uniqueIndices = mutableListOf<UniqueWorldDataIndex<*, V>>()
    private val changeListeners = mutableListOf<(K, V?, V) -> Unit>()

    operator fun provideDelegate(thisRef: WorldDataSection, property: KProperty<*>): SectionSyncedMap<K, V> {
        fieldName = property.name
        return this
    }

    operator fun getValue(thisRef: WorldDataSection, property: KProperty<*>): SectionSyncedMap<K, V> {
        if (fieldName.isEmpty()) fieldName = property.name
        return this
    }

    // ── Регистрация индексов ───────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    internal fun <IK> registerIndex(index: WorldDataIndex<IK, V>) {
        regularIndices += index as WorldDataIndex<*, V>
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <IK> registerUniqueIndex(index: UniqueWorldDataIndex<IK, V>) {
        uniqueIndices += index as UniqueWorldDataIndex<*, V>
    }

    // ── Слушатели изменений ───────────────────────────────────────────────

    /**
     * Регистрирует слушатель изменений.
     * Вызывается при put/remove с (key, oldValue, newValue).
     *
     * ```kotlin
     * val characters by syncedMap(UuidCodec, CharacterData.CODEC)
     *     .onChange { id, old, new ->
     *         if (old?.level != new.level) CharacterEvents.LEVEL_UP.emit(id, new)
     *     }
     * ```
     */
    fun onChange(listener: (key: K, old: V?, new: V) -> Unit): SectionSyncedMap<K, V> {
        changeListeners += listener
        return this
    }

    // ── Перестройка индексов ───────────────────────────────────────────────

    internal fun rebuildIndices() {
        val values = delegate.values
        regularIndices.forEach { it.rebuild(values) }
        uniqueIndices.forEach { it.rebuild(values) }
    }

    // ── MutableMap overrides ───────────────────────────────────────────────

    override fun put(key: K, value: V): V? {
        val old = delegate.put(key, value)
        updateIndices(old, value)
        changeListeners.forEach { it(key, old, value) }
        onChanged()
        return old
    }

    override fun putAll(from: Map<out K, V>) {
        from.forEach { (k, v) -> put(k, v) }
    }

    override fun remove(key: K): V? {
        val old = delegate.remove(key) ?: return null
        removeFromIndices(old)
        onChanged()
        return old
    }

    override fun clear() {
        delegate.clear()
        regularIndices.forEach { it.clear() }
        uniqueIndices.forEach { it.clear() }
        onChanged()
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateIndices(old: V?, new: V) {
        regularIndices.forEach { (it as WorldDataIndex<Any, V>).update(old, new) }
        uniqueIndices.forEach { (it as UniqueWorldDataIndex<Any, V>).update(old, new) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun removeFromIndices(value: V) {
        regularIndices.forEach { (it as WorldDataIndex<Any, V>).remove(value) }
        uniqueIndices.forEach { (it as UniqueWorldDataIndex<Any, V>).remove(value) }
    }

    // ── Сериализация ───────────────────────────────────────────────────────

    override fun serializeTo(nbt: NbtCompound) {
        if (fieldName.isEmpty()) return
        Codec.unboundedMap(keyCodec, valueCodec)
            .encodeStart(NbtOps.INSTANCE, delegate.toMap())
            .result()
            .ifPresent { nbt.put(fieldName, it) }
    }

    override fun deserializeFrom(nbt: NbtCompound) {
        if (fieldName.isEmpty()) return
        val tag = nbt.get(fieldName) ?: return
        Codec.unboundedMap(keyCodec, valueCodec)
            .parse(NbtOps.INSTANCE, tag)
            .result()
            .ifPresent { decoded ->
                delegate.clear()
                delegate.putAll(decoded)
                rebuildIndices()
            }
    }

    override fun toString(): String = delegate.toString()
}

// ── NBT маппинг примитивов ────────────────────────────────────────────────
// Реализация вынесена в omc.boundbyfate.util.sync.NbtSerializationUtil
