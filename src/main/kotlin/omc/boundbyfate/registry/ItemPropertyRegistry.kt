package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.item.ItemDefinition
import omc.boundbyfate.api.item.ItemPropertyHandler
import omc.boundbyfate.registry.core.BbfDualRegistry

/**
 * Реестр свойств предметов.
 *
 * Хранит два независимых хранилища:
 * - [ItemPropertyHandler] — логика свойства, регистрируется в коде при старте мода
 * - [ItemDefinition] — привязка свойств к Minecraft item ID, загружается из JSON датапаков
 *
 * ## Регистрация хендлера
 *
 * ```kotlin
 * ItemPropertyRegistry.register(MeleeDamage)
 * ItemPropertyRegistry.register(StatBonus)
 * ItemPropertyRegistry.register(ArmorClass)
 * ```
 *
 * ## Загрузка Definition
 *
 * Definition загружаются автоматически из датапаков через [omc.boundbyfate.config.loader.DualDatapackLoader].
 * Путь: `data/<namespace>/bbf_item/<name>.json`
 *
 * ## Получение
 *
 * ```kotlin
 * val handler    = ItemPropertyRegistry.getHandler(propertyId)
 * val definition = ItemPropertyRegistry.getDefinition(itemId)
 * ```
 *
 * ## Тикующие хендлеры
 *
 * ```kotlin
 * val ticking = ItemPropertyRegistry.getTickingHandlers()
 * ```
 */
object ItemPropertyRegistry : BbfDualRegistry<ItemPropertyHandler, ItemDefinition>("item_properties") {

    /**
     * Регистрирует хендлер свойства предмета.
     * Удобная обёртка — берёт ID из самого хендлера.
     *
     * @throws IllegalStateException если хендлер с таким ID уже зарегистрирован
     */
    fun register(handler: ItemPropertyHandler) = registerHandler(handler.id, handler)

    /**
     * Регистрирует хендлер, перезаписывая существующий.
     * Используй для переопределения встроенных свойств из другого мода.
     */
    fun registerOrReplace(handler: ItemPropertyHandler) = registerOrReplaceHandler(handler.id, handler)

    /**
     * Возвращает все тикующие хендлеры.
     * Используется системой предметов для оптимизации тиков.
     */
    fun getTickingHandlers(): Collection<ItemPropertyHandler> =
        getAllHandlers().filter { it.isTicking }
}
