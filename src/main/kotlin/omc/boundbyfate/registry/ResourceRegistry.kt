package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.resource.ResourceDefinition
import omc.boundbyfate.api.resource.ResourceRecovery
import omc.boundbyfate.registry.core.BbfRegistry

/**
 * Реестр ресурсов.
 *
 * Хранит [ResourceDefinition] — определения именованных счётчиков с правилами восстановления.
 * Загружаются из JSON датапаков через [omc.boundbyfate.config.loader.ResourceConfigLoader].
 *
 * ## Что такое ресурс
 *
 * Ресурс описывает *тип* очков или ячеек, но не их количество у конкретного персонажа.
 * Количество задаётся через [omc.boundbyfate.api.feature.FeatureGrant.Resource] при получении Feature.
 * Текущее значение хранится в [omc.boundbyfate.component.components.EntityAbilitiesData].
 *
 * ## Использование
 *
 * ```kotlin
 * val kiPoints = ResourceRegistry.get(Identifier("boundbyfate-core", "ki_points"))
 * val recovery = kiPoints?.recovery  // ResourceRecovery.OnEvent(rest/short)
 * ```
 *
 * ## Структура файлов
 *
 * ```
 * data/
 *   boundbyfate-core/
 *     bbf_resource/
 *       ki_points.json
 *       spell_slot_1.json
 *       rage_uses.json
 * ```
 */
object ResourceRegistry : BbfRegistry<ResourceDefinition>("resources") {

    /**
     * Возвращает все ресурсы с восстановлением на коротком отдыхе.
     */
    fun getShortRestResources(): List<ResourceDefinition> =
        getAll().filter { definition ->
            val recovery = definition.recovery
            recovery is ResourceRecovery.OnEvent &&
                recovery.eventId.toString() == "boundbyfate-core:rest/short"
        }

    /**
     * Возвращает все ресурсы с восстановлением на длинном отдыхе.
     */
    fun getLongRestResources(): List<ResourceDefinition> =
        getAll().filter { definition ->
            val recovery = definition.recovery
            recovery is ResourceRecovery.OnEvent &&
                recovery.eventId.toString() == "boundbyfate-core:rest/long"
        }

    /**
     * Возвращает все ресурсы которые восстанавливаются при наступлении события.
     *
     * @param eventId ID события (например "boundbyfate-core:rest/short")
     */
    fun getResourcesForEvent(eventId: Identifier): List<ResourceDefinition> =
        getAll().filter { definition ->
            val recovery = definition.recovery
            recovery is ResourceRecovery.OnEvent && recovery.eventId == eventId
        }
}
