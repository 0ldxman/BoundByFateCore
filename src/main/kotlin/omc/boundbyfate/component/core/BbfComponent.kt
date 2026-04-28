package omc.boundbyfate.component.core

import com.mojang.serialization.Codec
import net.minecraft.nbt.AbstractNbtNumber
import net.minecraft.nbt.NbtByte
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtDouble
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtFloat
import net.minecraft.nbt.NbtInt
import net.minecraft.nbt.NbtLong
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtShort
import net.minecraft.nbt.NbtString
import net.minecraft.registry.RegistryWrapper
import net.minecraft.util.Identifier
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Базовый класс для всех компонентов персонажа.
 *
 * Предоставляет:
 * - Автоматический dirty tracking через делегаты [synced], [syncedList], [syncedMap]
 * - Автоматическую сериализацию/десериализацию — писать [toNbt]/[fromNbt] вручную не нужно
 *
 * ## Создание компонента
 *
 * ```kotlin
 * class EntityAlignmentData : BbfComponent() {
 *
 *     // Примитивы — просто synced()
 *     var level by synced(1)
 *     var name by synced("unknown")
 *     var alive by synced(true)
 *
 *     // Объекты с Codec — synced(value, Codec)
 *     var point by synced(AlignmentPoint.NEUTRAL, AlignmentPoint.CODEC)
 *
 *     // Список с Codec элементов
 *     val statuses by syncedList(ActiveStatus.CODEC)
 *
 *     // Карта
 *     val modifiers by syncedMap(CodecUtil.IDENTIFIER, Codec.INT)
 *
 *     companion object {
 *         val TYPE = BbfComponents.register(
 *             id = "boundbyfate-core:alignment",
 *             syncMode = SyncMode.ON_CHANGE,
 *             factory = ::EntityAlignmentData
 *         )
 *     }
 * }
 * ```
 *
 * ## Ручная сериализация (если нужна особая логика)
 *
 * Переопредели [toNbt] и [fromNbt].
 */
abstract class BbfComponent {

    /**
     * Флаг — компонент изменился и нужна синхронизация.
     * Устанавливается автоматически при изменении любого synced поля.
     */
    var isDirty = false
        private set

    /**
     * Все зарегистрированные synced-свойства этого компонента.
     * Заполняется автоматически при создании делегатов.
     */
    internal val syncedProperties = mutableListOf<SyncedSerializable>()

    fun markClean() { isDirty = false }
    fun markDirty() { isDirty = true }

    // ── Делегаты ──────────────────────────────────────────────────────────

    /**
     * Синхронизируемое свойство с автосериализацией.
     *
     * Поддерживаемые типы без Codec: [Int], [Long], [Float], [Double],
     * [Boolean], [String], [Byte], [Short], [Identifier].
     *
     * ```kotlin
     * var strength by synced(10)
     * var name by synced("unknown")
     * ```
     */
    protected fun <T> synced(initial: T): SyncedProperty<T> =
        SyncedProperty(initial, codec = null) { isDirty = true }
            .also { syncedProperties += it }

    /**
     * Синхронизируемое свойство с явным Codec.
     *
     * ```kotlin
     * var point by synced(AlignmentPoint.NEUTRAL, AlignmentPoint.CODEC)
     * ```
     */
    protected fun <T> synced(initial: T, codec: Codec<T>): SyncedProperty<T> =
        SyncedProperty(initial, codec) { isDirty = true }
            .also { syncedProperties += it }

    /**
     * Синхронизируемый список с Codec элементов.
     *
     * ```kotlin
     * val statuses by syncedList(ActiveStatus.CODEC)
     * ```
     */
    protected fun <T : Any> syncedList(
        elementCodec: Codec<T>,
        initial: MutableList<T> = mutableListOf()
    ): SyncedList<T> = SyncedList(initial, elementCodec) { isDirty = true }
        .also { syncedProperties += it }

    /**
     * Синхронизируемая карта.
     *
     * ```kotlin
     * val modifiers by syncedMap(CodecUtil.IDENTIFIER, Codec.INT)
     * ```
     */
    protected fun <K : Any, V : Any> syncedMap(
        keyCodec: Codec<K>,
        valueCodec: Codec<V>,
        initial: MutableMap<K, V> = mutableMapOf()
    ): SyncedMap<K, V> = SyncedMap(initial, keyCodec, valueCodec) { isDirty = true }
        .also { syncedProperties += it }

    // ── Сериализация ──────────────────────────────────────────────────────

    /**
     * Сериализует компонент в NBT.
     * По умолчанию — автоматически по всем [synced] полям.
     */
    open fun toNbt(registries: RegistryWrapper.WrapperLookup): NbtCompound =
        NbtCompound().also { nbt ->
            syncedProperties.forEach { it.serializeTo(nbt) }
        }

    /**
     * Десериализует компонент из NBT.
     * По умолчанию — автоматически по всем [synced] полям.
     */
    open fun fromNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup) {
        syncedProperties.forEach { it.deserializeFrom(nbt) }
        isDirty = false
    }
}

// ── Интерфейс сериализуемого synced-объекта ───────────────────────────────

/**
 * Общий интерфейс для [SyncedProperty], [SyncedList], [SyncedMap].
 * Позволяет хранить их в одном списке и вызывать сериализацию единообразно.
 */
interface SyncedSerializable {
    fun serializeTo(nbt: NbtCompound)
    fun deserializeFrom(nbt: NbtCompound)
}

// ── SyncedProperty ────────────────────────────────────────────────────────

/**
 * Делегат для синхронизируемого свойства с автосериализацией.
 *
 * Запоминает имя поля при первом обращении через [KProperty].
 * Сериализует через [Codec] или встроенный маппинг примитивов.
 */
class SyncedProperty<T>(
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

// ── SyncedList ────────────────────────────────────────────────────────────

/**
 * Синхронизируемый список с автосериализацией через [elementCodec].
 */
class SyncedList<T : Any>(
    private val delegate: MutableList<T> = mutableListOf(),
    private val elementCodec: Codec<T>,
    private val onChanged: () -> Unit
) : MutableList<T> by delegate, SyncedSerializable {

    // Имя поля устанавливается через оператор provideDelegate или вручную
    // Для SyncedList используем ReadOnlyProperty чтобы поймать имя
    var fieldName: String = ""

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

    // Оператор делегирования — перехватывает имя свойства
    operator fun provideDelegate(thisRef: BbfComponent, property: KProperty<*>): SyncedList<T> {
        fieldName = property.name
        return this
    }

    operator fun getValue(thisRef: BbfComponent, property: KProperty<*>): SyncedList<T> {
        if (fieldName.isEmpty()) fieldName = property.name
        return this
    }

    override fun add(element: T): Boolean =
        delegate.add(element).also { if (it) onChanged() }

    override fun add(index: Int, element: T) {
        delegate.add(index, element)
        onChanged()
    }

    override fun addAll(elements: Collection<T>): Boolean =
        delegate.addAll(elements).also { if (it) onChanged() }

    override fun addAll(index: Int, elements: Collection<T>): Boolean =
        delegate.addAll(index, elements).also { if (it) onChanged() }

    override fun remove(element: T): Boolean =
        delegate.remove(element).also { if (it) onChanged() }

    override fun removeAt(index: Int): T =
        delegate.removeAt(index).also { onChanged() }

    override fun removeAll(elements: Collection<T>): Boolean =
        delegate.removeAll(elements).also { if (it) onChanged() }

    override fun retainAll(elements: Collection<T>): Boolean =
        delegate.retainAll(elements).also { if (it) onChanged() }

    override fun set(index: Int, element: T): T =
        delegate.set(index, element).also { onChanged() }

    override fun clear() {
        delegate.clear()
        onChanged()
    }

    override fun iterator(): MutableIterator<T> = SyncedIterator(delegate.iterator(), onChanged)

    override fun listIterator(): MutableListIterator<T> =
        SyncedListIterator(delegate.listIterator(), onChanged)

    override fun listIterator(index: Int): MutableListIterator<T> =
        SyncedListIterator(delegate.listIterator(index), onChanged)

    override fun toString(): String = delegate.toString()
}

// ── SyncedMap ─────────────────────────────────────────────────────────────

/**
 * Синхронизируемая карта с автосериализацией.
 */
class SyncedMap<K : Any, V : Any>(
    private val delegate: MutableMap<K, V> = mutableMapOf(),
    private val keyCodec: Codec<K>,
    private val valueCodec: Codec<V>,
    private val onChanged: () -> Unit
) : MutableMap<K, V> by delegate, SyncedSerializable {

    var fieldName: String = ""

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
            }
    }

    operator fun provideDelegate(thisRef: BbfComponent, property: KProperty<*>): SyncedMap<K, V> {
        fieldName = property.name
        return this
    }

    operator fun getValue(thisRef: BbfComponent, property: KProperty<*>): SyncedMap<K, V> {
        if (fieldName.isEmpty()) fieldName = property.name
        return this
    }

    override fun put(key: K, value: V): V? =
        delegate.put(key, value).also { onChanged() }

    override fun putAll(from: Map<out K, V>) {
        delegate.putAll(from)
        onChanged()
    }

    override fun remove(key: K): V? =
        delegate.remove(key).also { if (it != null) onChanged() }

    override fun clear() {
        delegate.clear()
        onChanged()
    }

    override fun toString(): String = delegate.toString()
}

// ── NBT маппинг примитивов ────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
private fun <T> encodeToNbt(value: T, codec: Codec<T>?): NbtElement? {
    if (codec != null) {
        return codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null)
    }
    return when (value) {
        is Int        -> NbtInt.of(value)
        is Long       -> NbtLong.of(value)
        is Float      -> NbtFloat.of(value)
        is Double     -> NbtDouble.of(value)
        is Boolean    -> NbtByte.of(value)
        is String     -> NbtString.of(value)
        is Byte       -> NbtByte.of(value)
        is Short      -> NbtShort.of(value)
        is Identifier -> NbtString.of(value.toString())
        else          -> null
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> decodeFromNbt(tag: NbtElement, codec: Codec<T>?, fallback: T): T? {
    if (codec != null) {
        return codec.parse(NbtOps.INSTANCE, tag).result().orElse(null)
    }
    return when (fallback) {
        is Int        -> (tag as? AbstractNbtNumber)?.intValue() as? T
        is Long       -> (tag as? AbstractNbtNumber)?.longValue() as? T
        is Float      -> (tag as? AbstractNbtNumber)?.floatValue() as? T
        is Double     -> (tag as? AbstractNbtNumber)?.doubleValue() as? T
        is Boolean    -> (tag as? AbstractNbtNumber)?.byteValue()?.let { it != 0.toByte() } as? T
        is String     -> (tag as? NbtString)?.asString() as? T
        is Byte       -> (tag as? AbstractNbtNumber)?.byteValue() as? T
        is Short      -> (tag as? AbstractNbtNumber)?.shortValue() as? T
        is Identifier -> (tag as? NbtString)?.asString()?.let { Identifier.of(it) } as? T
        else          -> null
    }
}

// ── Итераторы ─────────────────────────────────────────────────────────────

internal class SyncedIterator<T>(
    private val delegate: MutableIterator<T>,
    private val onChanged: () -> Unit
) : MutableIterator<T> by delegate {
    override fun remove() {
        delegate.remove()
        onChanged()
    }
}

internal class SyncedListIterator<T>(
    private val delegate: MutableListIterator<T>,
    private val onChanged: () -> Unit
) : MutableListIterator<T> by delegate {
    override fun remove() {
        delegate.remove()
        onChanged()
    }

    override fun add(element: T) {
        delegate.add(element)
        onChanged()
    }

    override fun set(element: T) {
        delegate.set(element)
        onChanged()
    }
}
