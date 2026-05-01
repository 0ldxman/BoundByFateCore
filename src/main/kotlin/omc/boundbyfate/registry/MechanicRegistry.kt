package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.mechanic.Mechanic
import omc.boundbyfate.api.mechanic.MechanicDefinition
import omc.boundbyfate.registry.core.BbfDualRegistry

/**
 * Реестр механик персонажа.
 *
 * Хранит два независимых хранилища:
 * - [Mechanic] — логика, регистрируется в коде при старте мода
 * - [MechanicDefinition] — данные, загружаются из JSON датапаков
 *
 * Механики могут быть получены из любого источника: класса, расы, черты, предмета.
 * Они связываются по одному [Identifier].
 *
 * ## Регистрация хендлера
 *
 * ```kotlin
 * MechanicRegistry.register(SpellcastingMechanic)
 * MechanicRegistry.register(RageMechanic)
 * ```
 *
 * ## Загрузка Definition
 *
 * Definition загружаются автоматически из датапаков через [omc.boundbyfate.config.loader.DualDatapackLoader].
 * Путь: `data/<namespace>/bbf_mechanic/<name>.json`
 *
 * ## Получение
 *
 * ```kotlin
 * val handler    = MechanicRegistry.getHandler(id)
 * val definition = MechanicRegistry.getDefinition(id)
 * val (h, d)     = MechanicRegistry.get(id) ?: return  // оба сразу
 * ```
 *
 * ## Фильтрация по тегу
 *
 * ```kotlin
 * val spellcastingMechanics = MechanicRegistry.getDefinitionsByTag("spellcasting")
 * ```
 */
object MechanicRegistry : BbfDualRegistry<Mechanic, MechanicDefinition>("mechanics") {

    /**
     * Регистрирует хендлер механики.
     * Удобная обёртка — берёт ID из самого хендлера.
     *
     * @throws IllegalStateException если хендлер с таким ID уже зарегистрирован
     */
    fun register(handler: Mechanic) = registerHandler(handler.id, handler)

    /**
     * Регистрирует хендлер, перезаписывая существующий.
     * Используй для переопределения встроенных механик из другого мода.
     */
    fun registerOrReplace(handler: Mechanic) = registerOrReplaceHandler(handler.id, handler)

    /**
     * Возвращает все дефиниции с указанным тегом.
     */
    fun getDefinitionsByTag(tag: String): List<MechanicDefinition> =
        getAllDefinitions().filter { it.hasTag(tag) }
}
