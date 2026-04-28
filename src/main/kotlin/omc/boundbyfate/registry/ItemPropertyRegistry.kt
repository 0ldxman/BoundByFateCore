package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.item.ItemDefinition
import omc.boundbyfate.api.item.ItemPropertyHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр свойств предметов.
 *
 * Аналог [AbilityRegistry] — хранит два независимых хранилища:
 * - [ItemPropertyHandler] — логика, регистрируется в коде
 * - [ItemDefinition] — данные, загружаются из JSON датапаков
 *
 * ## Регистрация хендлера
 *
 * ```kotlin
 * // В BbfItemProperties.register():
 * ItemPropertyRegistry.register(MeleeDamage)
 * ItemPropertyRegistry.register(StatBonus)
 * ItemPropertyRegistry.register(ArmorClass)
 * ```
 *
 * ## Загрузка Definition
 *
 * Definition загружаются из `data/<namespace>/bbf_item/*.json`.
 *
 * ## Получение
 *
 * ```kotlin
 * val handler = ItemPropertyRegistry.getHandler(propertyId)
 * val definition = ItemPropertyRegistry.getItemDefinition(itemId)
 * ```
 */
object ItemPropertyRegistry {

    private val logger = LoggerFactory.getLogger(ItemPropertyRegistry::class.java)

    /** Хендлеры свойств: propertyId → handler */
    private val handlers: ConcurrentHashMap<Identifier, ItemPropertyHandler> = ConcurrentHashMap()

    /** Определения предметов: itemId → ItemDefinition */
    private val itemDefinitions: ConcurrentHashMap<Identifier, ItemDefinition> = ConcurrentHashMap()

    // ── Регистрация хендлеров ─────────────────────────────────────────────

    fun register(handler: ItemPropertyHandler) {
        if (handlers.containsKey(handler.id)) {
            throw IllegalStateException("ItemPropertyHandler '${handler.id}' is already registered")
        }
        handlers[handler.id] = handler
        logger.debug("Registered item property handler: ${handler.id}")
    }

    fun registerOrReplace(handler: ItemPropertyHandler) {
        if (handlers.containsKey(handler.id)) {
            logger.warn("Replacing item property handler: ${handler.id}")
        }
        handlers[handler.id] = handler
    }

    // ── Регистрация ItemDefinition ────────────────────────────────────────

    fun registerItemDefinition(definition: ItemDefinition) {
        itemDefinitions[definition.item] = definition
        logger.debug("Registered item definition: ${definition.item}")
    }

    fun clearItemDefinitions() {
        itemDefinitions.clear()
        logger.info("Cleared all item definitions")
    }

    // ── Получение ─────────────────────────────────────────────────────────

    fun getHandler(propertyId: Identifier): ItemPropertyHandler? = handlers[propertyId]

    fun getItemDefinition(itemId: Identifier): ItemDefinition? = itemDefinitions[itemId]

    fun getAllHandlers(): Collection<ItemPropertyHandler> = handlers.values

    fun getAllItemDefinitions(): Collection<ItemDefinition> = itemDefinitions.values

    /**
     * Возвращает все тикующие хендлеры.
     */
    fun getTickingHandlers(): Collection<ItemPropertyHandler> =
        handlers.values.filter { it.isTicking }

    fun hasHandler(id: Identifier): Boolean = handlers.containsKey(id)
    fun hasItemDefinition(itemId: Identifier): Boolean = itemDefinitions.containsKey(itemId)
    fun handlerCount(): Int = handlers.size
    fun definitionCount(): Int = itemDefinitions.size

    fun printStatistics() {
        logger.info("=== Item Property Registry ===")
        logger.info("  Handlers: ${handlerCount()}")
        logger.info("  Item definitions: ${definitionCount()}")
        logger.info("  Ticking handlers: ${getTickingHandlers().size}")
        logger.info("==============================")
    }
}
