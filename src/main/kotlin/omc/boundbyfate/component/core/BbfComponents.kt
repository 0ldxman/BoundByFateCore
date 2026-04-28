package omc.boundbyfate.component.core

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.entity.Entity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtTagSizeTracker
import net.minecraft.registry.RegistryWrapper
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Режим синхронизации компонента с клиентом.
 */
enum class SyncMode {
    /** Не синхронизировать */
    NONE,
    /** Синхронизировать при изменении */
    ON_CHANGE,
    /** Синхронизировать при входе игрока */
    ON_JOIN,
    /** Синхронизировать каждый тик */
    EVERY_TICK
}

/**
 * Центральный реестр компонентов персонажа.
 *
 * ## Регистрация компонента
 *
 * Вызывается из `companion object` самого компонента — одна строка:
 *
 * ```kotlin
 * companion object {
 *     val TYPE = BbfComponents.register<EntityAlignmentData>(
 *         id = "boundbyfate-core:alignment",
 *         syncMode = SyncMode.ON_CHANGE,
 *         factory = ::EntityAlignmentData
 *     )
 * }
 * ```
 *
 * ## Получение компонента
 *
 * ```kotlin
 * val alignment = entity.getOrCreate(EntityAlignmentData.TYPE)
 * alignment.point = AlignmentPoint(3, 2)  // isDirty = true автоматически
 * ```
 */
object BbfComponents {

    private val logger = LoggerFactory.getLogger(BbfComponents::class.java)

    /**
     * Все зарегистрированные компоненты.
     */
    private val entries = mutableMapOf<Identifier, ComponentEntry<*>>()

    /**
     * Регистрирует компонент.
     *
     * @param id уникальный ID компонента
     * @param syncMode режим синхронизации с клиентом
     * @param factory фабрика для создания пустого компонента
     * @return [AttachmentType] для использования в `entity.getOrCreate(TYPE)`
     */
    fun <T : BbfComponent> register(
        id: String,
        syncMode: SyncMode,
        factory: () -> T
    ): AttachmentType<T> {
        val identifier = Identifier(
            id.substringBefore(':'),
            id.substringAfter(':')
        )

        if (entries.containsKey(identifier)) {
            throw IllegalStateException("Component '$identifier' is already registered")
        }

        // Регистрируем в Fabric Attachment API с нашим Codec
        val attachmentType = AttachmentRegistry.builder<T>()
            .persistent(BbfComponentCodec(factory))
            .initializer(factory)
            .buildAndRegister(identifier)

        val entry = ComponentEntry(identifier, attachmentType, syncMode)
        entries[identifier] = entry

        logger.debug("Registered component: $identifier (syncMode=$syncMode)")
        return attachmentType
    }

    /**
     * Возвращает запись компонента по ID.
     */
    fun getEntry(id: Identifier): ComponentEntry<*>? = entries[id]

    /**
     * Возвращает все зарегистрированные компоненты.
     */
    fun getAllEntries(): Collection<ComponentEntry<*>> = entries.values

    /**
     * Возвращает компоненты с одним из указанных режимов синхронизации.
     */
    fun getEntriesBySyncMode(vararg modes: SyncMode): List<ComponentEntry<*>> =
        entries.values.filter { it.syncMode in modes }

    /**
     * Возвращает компоненты которые нужно синхронизировать (не NONE).
     */
    fun getSyncableEntries(): List<ComponentEntry<*>> =
        entries.values.filter { it.syncMode != SyncMode.NONE }

    fun printStatistics() {
        logger.info("=== BbfComponents ===")
        logger.info("  Total: ${entries.size}")
        SyncMode.entries.forEach { mode ->
            val count = getEntriesBySyncMode(mode).size
            if (count > 0) logger.info("  $mode: $count")
        }
        logger.info("=====================")
    }
}

// ── ComponentEntry ────────────────────────────────────────────────────────

/**
 * Метаданные зарегистрированного компонента.
 */
data class ComponentEntry<T : BbfComponent>(
    val id: Identifier,
    val attachmentType: AttachmentType<T>,
    val syncMode: SyncMode
)

// ── Codec для Fabric Attachment API ───────────────────────────────────────

/**
 * Codec для сериализации компонента через Fabric Attachment API.
 *
 * Использует [BbfComponent.toNbt] и [BbfComponent.fromNbt].
 */
private class BbfComponentCodec<T : BbfComponent>(
    private val factory: () -> T
) : com.mojang.serialization.Codec<T> {

    override fun <A> encode(
        input: T,
        ops: com.mojang.serialization.DynamicOps<A>,
        prefix: A
    ): com.mojang.serialization.DataResult<A> {
        return try {
            // Используем пустые registries для сериализации
            val nbt = input.toNbt(net.minecraft.registry.RegistryWrapper.WrapperLookup.of(
                java.util.stream.Stream.empty()
            ))
            val encoded = net.minecraft.nbt.NbtOps.INSTANCE.convertTo(ops, nbt)
            com.mojang.serialization.DataResult.success(encoded)
        } catch (e: Exception) {
            com.mojang.serialization.DataResult.error { "Failed to encode component: ${e.message}" }
        }
    }

    override fun <A> decode(
        ops: com.mojang.serialization.DynamicOps<A>,
        input: A
    ): com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<T, A>> {
        return try {
            val nbt = ops.convertTo(net.minecraft.nbt.NbtOps.INSTANCE, input) as? NbtCompound
                ?: return com.mojang.serialization.DataResult.error { "Expected NbtCompound" }

            val component = factory()
            component.fromNbt(nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup.of(
                java.util.stream.Stream.empty()
            ))
            com.mojang.serialization.DataResult.success(
                com.mojang.datafixers.util.Pair.of(component, ops.empty())
            )
        } catch (e: Exception) {
            com.mojang.serialization.DataResult.error { "Failed to decode component: ${e.message}" }
        }
    }
}

// ── Extension функции для Entity ──────────────────────────────────────────

/**
 * Получает компонент или создаёт новый если не существует.
 *
 * ```kotlin
 * val alignment = player.getOrCreate(EntityAlignmentData.TYPE)
 * ```
 */
fun <T : BbfComponent> Entity.getOrCreate(type: AttachmentType<T>): T =
    this.getAttachedOrCreate(type)

/**
 * Получает компонент или null если не существует.
 */
fun <T : BbfComponent> Entity.getComponent(type: AttachmentType<T>): T? =
    this.getAttached(type)

/**
 * Сериализует компонент в ByteArray для отправки по сети.
 */
fun BbfComponent.toBytes(registries: RegistryWrapper.WrapperLookup): ByteArray {
    val nbt = toNbt(registries)
    val baos = ByteArrayOutputStream()
    NbtIo.write(nbt, DataOutputStream(baos))
    return baos.toByteArray()
}

/**
 * Десериализует компонент из ByteArray полученного по сети.
 */
fun BbfComponent.fromBytes(bytes: ByteArray, registries: RegistryWrapper.WrapperLookup) {
    val nbt = NbtIo.read(DataInputStream(ByteArrayInputStream(bytes)), NbtTagSizeTracker(Long.MAX_VALUE))
    fromNbt(nbt, registries)
}
