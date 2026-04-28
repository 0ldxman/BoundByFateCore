package omc.boundbyfate.data.world

import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateManager
import net.minecraft.world.World
import omc.boundbyfate.data.world.core.SectionEntry
import omc.boundbyfate.data.world.core.SyncStrategy
import omc.boundbyfate.data.world.core.WorldDataSection
import omc.boundbyfate.data.world.core.WorldDataTransaction
import omc.boundbyfate.data.world.core.execute
import org.slf4j.LoggerFactory

/**
 * Центральный фасад системы глобальных данных мира.
 *
 * Не является монолитным классом — это **контейнер секций**.
 * Каждая секция — независимый модуль данных, сохраняемый в свой файл.
 *
 * ## Регистрация секции
 *
 * ```kotlin
 * object CharacterSystem {
 *     val SECTION = BbfWorldData.registerSection(
 *         id = "boundbyfate-core:characters",
 *         file = "boundbyfate_characters",
 *         syncStrategy = SyncStrategy.ToAll,
 *         factory = ::CharacterSection
 *     )
 * }
 * ```
 *
 * ## Получение секции
 *
 * ```kotlin
 * val section = BbfWorldData.get(server).getSection(CharacterSystem.SECTION)
 * val character = section.characters[characterId]
 * ```
 *
 * ## Транзакция
 *
 * ```kotlin
 * BbfWorldData.get(server).transaction {
 *     val chars = getSection(CharacterSystem.SECTION)
 *     chars.activeCharacters[playerId] = newCharacterId
 * }
 * ```
 */
class BbfWorldData private constructor(
    private val stateManager: PersistentStateManager
) {

    private val logger = LoggerFactory.getLogger(BbfWorldData::class.java)

    /**
     * Загруженные экземпляры секций.
     * Key: SectionEntry, Value: экземпляр секции.
     */
    private val loadedSections = mutableMapOf<SectionEntry<*>, WorldDataSection>()

    // ── Получение секций ──────────────────────────────────────────────────

    /**
     * Возвращает секцию по её записи.
     * Загружает из файла при первом обращении.
     *
     * ```kotlin
     * val section = worldData.getSection(CharacterSystem.SECTION)
     * ```
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : WorldDataSection> getSection(entry: SectionEntry<T>): T {
        return loadedSections.getOrPut(entry) {
            loadSection(entry)
        } as T
    }

    /**
     * Загружает секцию из PersistentState.
     */
    private fun <T : WorldDataSection> loadSection(entry: SectionEntry<T>): T {
        val persistentState = stateManager.getOrCreate(
            PersistentState.Type(
                { SectionPersistentState(entry.factory()) },
                { nbt, _ ->
                    val section = entry.factory()
                    section.fromNbt(nbt)
                    SectionPersistentState(section)
                },
                null
            ),
            entry.fileName
        )
        logger.debug("Loaded section '${entry.id}' from '${entry.fileName}'")
        return persistentState.section as T
    }

    // ── Транзакции ────────────────────────────────────────────────────────

    /**
     * Выполняет атомарную транзакцию над секциями.
     *
     * При ошибке — все изменения откатываются.
     * При успехе — затронутые секции помечаются dirty.
     *
     * ```kotlin
     * worldData.transaction {
     *     val chars = getSection(CharacterSystem.SECTION)
     *     chars.characters[id] = newCharacter
     *     chars.activeCharacters[playerId] = id
     * }
     * ```
     *
     * @return Result.success если транзакция прошла, Result.failure с исключением если нет
     */
    fun transaction(block: WorldDataTransaction.() -> Unit): Result<Unit> {
        val sectionMap = loadedSections.mapKeys { it.key as Any }
        val tx = WorldDataTransaction(sectionMap)
        return tx.execute(block)
    }

    // ── Сохранение ────────────────────────────────────────────────────────

    /**
     * Принудительно помечает все загруженные секции как dirty.
     * Используется для форс-сохранения.
     */
    fun markAllDirty() {
        loadedSections.values.forEach { it.markDirty() }
    }

    /**
     * Возвращает все загруженные секции.
     */
    fun getAllLoadedSections(): Map<SectionEntry<*>, WorldDataSection> =
        loadedSections.toMap()

    // ── Статистика ────────────────────────────────────────────────────────

    fun printStatistics() {
        logger.info("=== BbfWorldData ===")
        logger.info("  Registered sections: ${registry.size}")
        logger.info("  Loaded sections: ${loadedSections.size}")
        registry.values.forEach { entry ->
            val loaded = loadedSections.containsKey(entry)
            val dirty = loadedSections[entry]?.isDirty ?: false
            logger.info("  [${if (loaded) "L" else " "}${if (dirty) "D" else " "}] ${entry.id} → ${entry.fileName}")
        }
        logger.info("===================")
    }

    // ── Companion: реестр и фабрика ───────────────────────────────────────

    companion object {
        private val logger = LoggerFactory.getLogger(BbfWorldData::class.java)

        /**
         * Реестр всех зарегистрированных секций.
         * Key: Identifier секции.
         */
        private val registry = mutableMapOf<Identifier, SectionEntry<*>>()

        /**
         * Кэш экземпляров BbfWorldData по серверу.
         */
        private var instance: BbfWorldData? = null

        /**
         * Регистрирует секцию WorldData.
         *
         * Вызывается один раз при инициализации системы.
         *
         * ```kotlin
         * val SECTION = BbfWorldData.registerSection(
         *     id = "boundbyfate-core:characters",
         *     file = "boundbyfate_characters",
         *     syncStrategy = SyncStrategy.ToAll,
         *     factory = ::CharacterSection
         * )
         * ```
         *
         * @param id уникальный идентификатор секции
         * @param file имя файла сохранения (без расширения .dat)
         * @param syncStrategy стратегия синхронизации с клиентами
         * @param factory фабрика для создания пустой секции
         */
        fun <T : WorldDataSection> registerSection(
            id: String,
            file: String,
            syncStrategy: SyncStrategy = SyncStrategy.None,
            factory: () -> T
        ): SectionEntry<T> {
            val identifier = Identifier.of(
                id.substringBefore(':'),
                id.substringAfter(':')
            )

            if (registry.containsKey(identifier)) {
                throw IllegalStateException("WorldData section '$identifier' is already registered")
            }

            val entry = SectionEntry(identifier, file, syncStrategy, factory)
            registry[identifier] = entry

            logger.debug("Registered WorldData section: $identifier → $file")
            return entry
        }

        /**
         * Возвращает запись секции по ID.
         */
        fun getEntry(id: Identifier): SectionEntry<*>? = registry[id]

        /**
         * Возвращает все зарегистрированные секции.
         */
        fun getAllEntries(): Collection<SectionEntry<*>> = registry.values

        /**
         * Возвращает секции с заданной стратегией синхронизации.
         */
        fun getSyncableEntries(): List<SectionEntry<*>> =
            registry.values.filter { it.syncStrategy !is SyncStrategy.None }

        /**
         * Получает или создаёт экземпляр BbfWorldData для сервера.
         *
         * Использует Overworld как хранилище PersistentState.
         *
         * ```kotlin
         * val worldData = BbfWorldData.get(server)
         * val section = worldData.getSection(CharacterSystem.SECTION)
         * ```
         */
        fun get(server: MinecraftServer): BbfWorldData {
            // Пересоздаём если сервер перезапустился
            val current = instance
            if (current != null) return current

            val overworld = server.getWorld(World.OVERWORLD)
                ?: throw IllegalStateException("Overworld not found — cannot initialize BbfWorldData")

            val newInstance = BbfWorldData(overworld.persistentStateManager)
            instance = newInstance
            logger.debug("BbfWorldData initialized for server")
            return newInstance
        }

        /**
         * Получает экземпляр BbfWorldData для мира.
         */
        fun get(world: ServerWorld): BbfWorldData = get(world.server)

        /**
         * Сбрасывает кэш экземпляра.
         * Вызывается при остановке сервера.
         */
        internal fun invalidate() {
            instance = null
            logger.debug("BbfWorldData instance invalidated")
        }
    }
}

// ── PersistentState обёртка для секции ────────────────────────────────────

/**
 * Обёртка над [WorldDataSection] для интеграции с Minecraft [PersistentState].
 *
 * Minecraft сам вызывает [writeNbt] при сохранении мира.
 * Мы делегируем сериализацию в [WorldDataSection.toNbt].
 */
private class SectionPersistentState<T : WorldDataSection>(
    val section: T
) : PersistentState() {

    override fun writeNbt(
        nbt: net.minecraft.nbt.NbtCompound,
        registryLookup: net.minecraft.registry.RegistryWrapper.WrapperLookup
    ): net.minecraft.nbt.NbtCompound {
        val sectionNbt = section.toNbt()
        sectionNbt.keys.forEach { key -> nbt.put(key, sectionNbt.get(key)!!) }
        section.markClean()
        return nbt
    }

    override fun isDirty(): Boolean = section.isDirty
}
