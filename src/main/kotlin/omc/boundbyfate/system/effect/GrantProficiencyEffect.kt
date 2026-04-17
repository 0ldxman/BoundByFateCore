package omc.boundbyfate.system.effect

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import omc.boundbyfate.api.effect.BbfEffect
import omc.boundbyfate.api.effect.BbfEffectContext
import omc.boundbyfate.api.skill.ProficiencyLevel
import omc.boundbyfate.registry.BbfAttachments
import omc.boundbyfate.component.EntitySkillData
import org.slf4j.LoggerFactory

/**
 * Grants a skill/save proficiency to the source entity.
 *
 * JSON params:
 * - proficiency: String (skill/save identifier)
 * - level: String (PROFICIENT | EXPERT, default PROFICIENT)
 */
class GrantSkillProficiencyEffect(
    private val proficiencyId: Identifier,
    private val level: ProficiencyLevel = ProficiencyLevel.PROFICIENT
) : BbfEffect {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun apply(context: BbfEffectContext): Boolean {
        val player = context.source as? ServerPlayerEntity ?: return false
        val skillData = player.getAttachedOrElse(BbfAttachments.ENTITY_SKILLS, EntitySkillData())
        player.setAttached(BbfAttachments.ENTITY_SKILLS, skillData.withProficiency(proficiencyId, level))
        logger.debug("Granted skill proficiency $proficiencyId to ${player.name.string}")
        return true
    }
}

/**
 * Grants an item/weapon/armor/tool proficiency to the source entity.
 *
 * JSON params:
 * - proficiency: String (item proficiency identifier)
 */
class GrantItemProficiencyEffect(
    private val proficiencyId: Identifier
) : BbfEffect {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun apply(context: BbfEffectContext): Boolean {
        val player = context.source as? ServerPlayerEntity ?: return false
        omc.boundbyfate.system.proficiency.ProficiencySystem.addProficiency(player, proficiencyId)
        logger.debug("Granted item proficiency $proficiencyId to ${player.name.string}")
        return true
    }
}
