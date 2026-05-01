package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityDefinition
import omc.boundbyfate.api.ability.AbilityHandler
import omc.boundbyfate.registry.core.BbfDualRegistry

/**
 * Реестр способностей.
 *
 * Хранит два независимых хранилища:
 * - [AbilityHandler] — логика, регистрируется в коде при старте мода
 * - [AbilityDefinition] — данные, загружаются из JSON датапаков
 *
 * Они связываются по одному [Identifier].
 *
 * ## Регистрация хендлера
 *
 * ```kotlin
 * // В BbfAbilities.register():
 * AbilityRegistry.register(SecondWind)
 * AbilityRegistry.register(Fireball)
 * ```
 *
 * ## Загрузка Definition
 *
 * Definition загружаются автоматически из датапаков через [omc.boundbyfate.config.loader.DualDatapackLoader].
 * Путь: `data/<namespace>/bbf_ability/<name>.json`
 *
 * ## Получение
 *
 * ```kotlin
 * val handler    = AbilityRegistry.getHandler(id)
 * val definition = AbilityRegistry.getDefinition(id)
 * val (h, d)     = AbilityRegistry.get(id) ?: return  // оба сразу
 * ```
 */
object AbilityRegistry : BbfDualRegistry<AbilityHandler, AbilityDefinition>("abilities") {

    /**
     * Регистрирует хендлер способности.
     * Удобная обёртка — берёт ID из самого хендлера.
     *
     * @throws IllegalStateException если хендлер с таким ID уже зарегистрирован
     */
    fun register(handler: AbilityHandler) = registerHandler(handler.id, handler)

    /**
     * Регистрирует хендлер, перезаписывая существующий.
     * Используй для переопределения встроенных способностей из другого мода.
     */
    fun registerOrReplace(handler: AbilityHandler) = registerOrReplaceHandler(handler.id, handler)
}
