package omc.boundbyfate.system.ability.abilities

import net.minecraft.text.Text
import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityContext
import omc.boundbyfate.api.ability.AbilityDefinition
import omc.boundbyfate.api.ability.AbilityHandler
import omc.boundbyfate.api.ability.CanUseResult
import omc.boundbyfate.api.ability.heal
import omc.boundbyfate.api.ability.consumeAction
import omc.boundbyfate.api.ability.hasAction
import omc.boundbyfate.api.ability.isOnCooldown
import omc.boundbyfate.api.ability.modify
import omc.boundbyfate.api.ability.roll
import omc.boundbyfate.api.ability.startRecovery
import omc.boundbyfate.api.action.ActionSlotType

/**
 * Second Wind — способность Fighter'а.
 *
 * Бонусным действием восстанавливает 1d10 + уровень HP.
 * Восстанавливается на коротком или длинном отдыхе.
 *
 * ## JSON (`bbf_ability/second_wind.json`)
 *
 * ```json
 * {
 *   "id": "boundbyfate-core:second_wind",
 *   "action_cost": "bonus_action",
 *   "recovery": { "type": "on_event", "event": "boundbyfate-core:rest/short" },
 *   "data": {
 *     "heal_dice": "1d10"
 *   }
 * }
 * ```
 */
object SecondWind : AbilityHandler() {

    override val id: Identifier = Identifier("boundbyfate-core", "second_wind")

    override fun canUse(ctx: AbilityContext): CanUseResult {
        if (!ctx.hasAction(ActionSlotType.BONUS_ACTION))
            return CanUseResult.No(Text.translatable("ability.boundbyfate-core.second_wind.no_bonus_action"))

        if (ctx.isOnCooldown())
            return CanUseResult.No(Text.translatable("ability.boundbyfate-core.second_wind.used"))

        return CanUseResult.Yes
    }

    override fun execute(ctx: AbilityContext) {
        val healDice = ctx.data.requireDiceExpression("heal_dice")

        // Модифицируемое значение — внешние системы могут изменить heal
        val heal = ctx.modify("heal_amount") {
            roll(healDice) + casterLevel
        }

        ctx.heal(ctx.caster, heal)
        ctx.consumeAction(ActionSlotType.BONUS_ACTION)
        ctx.startRecovery()
    }

    override fun buildDescription(definition: AbilityDefinition): Text {
        val dice = definition.abilityData.getDiceExpression("heal_dice")?.toString() ?: "1d10"
        return Text.translatable("ability.boundbyfate-core.second_wind.description", dice)
    }
}

