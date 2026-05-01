package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.EffectDefinition
import omc.boundbyfate.api.effect.EffectHandler
import omc.boundbyfate.registry.core.BbfDualRegistry

/**
 * Реестр эффектов.
 *
 * Хранит два независимых хранилища:
 * - [EffectHandler] — логика, регистрируется в коде при старте мода
 * - [EffectDefinition] — данные, загружаются из JSON датапаков
 *
 * Они связываются по одному [Identifier].
 *
 * ## Регистрация хендлера
 *
 * ```kotlin
 * // В BbfEffects.register():
 * EffectRegistry.register(Darkvision)
 * EffectRegistry.register(Poison)
 * ```
 *
 * ## Загрузка Definition
 *
 * Definition загружаются автоматически из датапаков через [omc.boundbyfate.config.loader.DualDatapackLoader].
 * Путь: `data/<namespace>/bbf_effect/<name>.json`
 *
 * ## Получение
 *
 * ```kotlin
 * val handler    = EffectRegistry.getHandler(id)
 * val definition = EffectRegistry.getDefinition(id)
 * val (h, d)     = EffectRegistry.get(id) ?: return  // оба сразу
 * ```
 *
 * ## Тикующие хендлеры
 *
 * ```kotlin
 * val ticking = EffectRegistry.getTickingHandlers()
 * ```
 */
object EffectRegistry : BbfDualRegistry<EffectHandler, EffectDefinition>("effects") {

    /**
     * Регистрирует хендлер эффекта.
     * Удобная обёртка — берёт ID из самого хендлера.
     *
     * @throws IllegalStateException если хендлер с таким ID уже зарегистрирован
     */
    fun register(handler: EffectHandler) = registerHandler(handler.id, handler)

    /**
     * Регистрирует хендлер, перезаписывая существующий.
     * Используй для переопределения встроенных эффектов из другого мода.
     */
    fun registerOrReplace(handler: EffectHandler) = registerOrReplaceHandler(handler.id, handler)

    /**
     * Возвращает все тикующие хендлеры (tickInterval > 0).
     * Используется системой эффектов для оптимизации тиков.
     */
    fun getTickingHandlers(): Collection<EffectHandler> =
        getAllHandlers().filter { it.isTicking }
}
