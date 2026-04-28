package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.mechanic.ClassMechanic
import omc.boundbyfate.api.mechanic.MechanicDefinition
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр механик классов.
 *
 * Хранит два независимых хранилища:
 * - [ClassMechanic] — логика, регистрируется в коде
 * - [MechanicDefinition] — данные, загружаются из JSON датапаков
 *
 * Они связываются по одному [Identifier].
 * Аналогично [omc.boundbyfate.system.ability.AbilityRegistry].
 *
 * ## Регистрация хендлера
 *
 * ```kotlin
 * // В BbfMechanics.register():
 * MechanicRegistry.register(SpellcastingMechanic)
 * MechanicRegistry.register(WizardSpellbookMechanic)
 * ```
 *
 * ## Загрузка Definition
 *
 * Definition загружаются автоматически из датапаков через загрузчик.
 * Вручную можно через [registerDefinition].
 *
 * ## Получение
 *
 * ```kotlin
 * val handler = MechanicRegistry.getHandler(id)
 * val definition = MechanicRegistry.getDefinition(id)
 * val pair = MechanicRegistry.get(id) // Pair<handler, definition> или null
 * ```
 */
object MechanicRegistry {
    
    private val logger = LoggerFactory.getLogger(MechanicRegistry::class.java)
    
    private val handlers: ConcurrentHashMap<Identifier, ClassMechanic> = ConcurrentHashMap()
    private val definitions: ConcurrentHashMap<Identifier, MechanicDefinition> = ConcurrentHashMap()
    
    // ── Регистрация хендлеров ─────────────────────────────────────────────
    
    /**
     * Регистрирует хендлер механики.
     *
     * @param handler хендлер
     * @throws IllegalStateException если хендлер с таким ID уже зарегистрирован
     */
    fun register(handler: ClassMechanic) {
        if (handlers.containsKey(handler.id)) {
            throw IllegalStateException(
                "ClassMechanic '${handler.id}' is already registered"
            )
        }
        handlers[handler.id] = handler
        logger.debug("Registered mechanic handler: ${handler.id}")
    }
    
    /**
     * Регистрирует хендлер, перезаписывая существующий если есть.
     * Используй для переопределения встроенных механик.
     */
    fun registerOrReplace(handler: ClassMechanic) {
        if (handlers.containsKey(handler.id)) {
            logger.warn("Replacing mechanic handler: ${handler.id}")
        }
        handlers[handler.id] = handler
        logger.debug("Registered mechanic handler: ${handler.id}")
    }
    
    // ── Регистрация Definition ────────────────────────────────────────────
    
    /**
     * Регистрирует Definition механики.
     * Вызывается загрузчиком датапаков.
     */
    fun registerDefinition(definition: MechanicDefinition) {
        definition.validate()
        definitions[definition.id] = definition
        logger.debug("Registered mechanic definition: ${definition.id}")
    }
    
    /**
     * Очищает все Definition (вызывается при перезагрузке датапаков).
     */
    fun clearDefinitions() {
        definitions.clear()
        logger.info("Cleared all mechanic definitions")
    }
    
    // ── Получение ─────────────────────────────────────────────────────────
    
    /**
     * Возвращает хендлер по ID или null.
     */
    fun getHandler(id: Identifier): ClassMechanic? = handlers[id]
    
    /**
     * Возвращает Definition по ID или null.
     */
    fun getDefinition(id: Identifier): MechanicDefinition? = definitions[id]
    
    /**
     * Возвращает пару (хендлер, definition) или null если что-то не найдено.
     */
    fun get(id: Identifier): Pair<ClassMechanic, MechanicDefinition>? {
        val handler = handlers[id] ?: return null
        val definition = definitions[id] ?: return null
        return handler to definition
    }
    
    /**
     * Проверяет зарегистрирован ли хендлер.
     */
    fun hasHandler(id: Identifier): Boolean = handlers.containsKey(id)
    
    /**
     * Проверяет загружена ли Definition.
     */
    fun hasDefinition(id: Identifier): Boolean = definitions.containsKey(id)
    
    /**
     * Возвращает все зарегистрированные хендлеры.
     */
    fun getAllHandlers(): Collection<ClassMechanic> = handlers.values
    
    /**
     * Возвращает все загруженные Definition.
     */
    fun getAllDefinitions(): Collection<MechanicDefinition> = definitions.values
    
    /**
     * Получает механики по тегу.
     */
    fun getByTag(tag: String): List<MechanicDefinition> {
        return definitions.values.filter { it.hasTag(tag) }
    }
    
    fun handlerCount(): Int = handlers.size
    fun definitionCount(): Int = definitions.size
    
    fun printStatistics() {
        logger.info("=== Mechanic Registry ===")
        logger.info("  Handlers: ${handlerCount()}")
        logger.info("  Definitions: ${definitionCount()}")
        val missing = handlers.keys.filter { !definitions.containsKey(it) }
        if (missing.isNotEmpty()) {
            logger.warn("  Handlers without definitions: $missing")
        }
        logger.info("=========================")
    }
}
