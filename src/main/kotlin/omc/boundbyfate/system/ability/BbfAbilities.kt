package omc.boundbyfate.system.ability

import org.slf4j.LoggerFactory

/**
 * Регистрация всех встроенных способностей BoundByFate Core.
 *
 * ## Добавление новой способности
 *
 * 1. Создай `object MyAbility : AbilityHandler()` в этом пакете или рядом
 * 2. Добавь `AbilityRegistry.register(MyAbility)` сюда
 * 3. Создай JSON файл в `resources/data/boundbyfate-core/bbf_ability/my_ability.json`
 *
 * ## Порядок инициализации
 *
 * Вызывается из [omc.boundbyfate.BoundByFateCore.onInitialize] на этапе систем.
 * Definition загружаются позже из датапаков — хендлеры и definition
 * связываются по ID автоматически.
 */
object BbfAbilities {

    private val logger = LoggerFactory.getLogger(BbfAbilities::class.java)

    fun register() {
        logger.info("Registering built-in abilities...")

        AbilityRegistry.register(omc.boundbyfate.system.ability.abilities.SecondWind)

        // TODO: добавлять способности сюда по мере реализации
        // AbilityRegistry.register(ActionSurge)
        // AbilityRegistry.register(Rage)
        // AbilityRegistry.register(Fireball)

        logger.info("Registered ${AbilityRegistry.handlerCount()} ability handlers")
    }
}
