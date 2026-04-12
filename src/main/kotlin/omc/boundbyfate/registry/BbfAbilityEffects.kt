package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.system.ability.effect.*
import org.slf4j.LoggerFactory

/**
 * Регистрация встроенных эффектов способностей.
 */
object BbfAbilityEffects {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    
    fun register() {
        logger.info("Registering ability effects...")
        
        // ═══ Урон и исцеление ═══
        
        AbilityEffectRegistry.register(Identifier("boundbyfate-core", "damage")) { json ->
            DamageEffect(
                dice = parseDice(json),
                damageType = Identifier(json.get("damageType").asString),
                bonusStat = json.get("bonusStat")?.asString?.let { Identifier(it) },
                bonusFlat = json.get("bonusFlat")?.asInt ?: 0,
                bonusLevel = json.get("bonusLevel")?.asBoolean ?: false,
                ignoresArmor = json.get("ignoresArmor")?.asBoolean ?: false
            )
        }
        
        AbilityEffectRegistry.register(Identifier("boundbyfate-core", "heal")) { json ->
            HealEffect(
                dice = parseDice(json),
                bonusStat = json.get("bonusStat")?.asString?.let { Identifier(it) },
                bonusFlat = json.get("bonusFlat")?.asInt ?: 0,
                bonusLevel = json.get("bonusLevel")?.asBoolean ?: false,
                canOverheal = json.get("canOverheal")?.asBoolean ?: false,
                overhealAsTemp = json.get("overhealAsTemp")?.asBoolean ?: false,
                reviveDead = json.get("reviveDead")?.asBoolean ?: false
            )
        }
        
        // ═══ Статусные эффекты ═══
        
        AbilityEffectRegistry.register(Identifier("boundbyfate-core", "status_effect")) { json ->
            StatusEffectEffect(
                effectId = Identifier(json.get("effectId").asString),
                duration = json.get("duration")?.asInt ?: 200,
                amplifier = json.get("amplifier")?.asInt ?: 0,
                ambient = json.get("ambient")?.asBoolean ?: false,
                showParticles = json.get("showParticles")?.asBoolean ?: true,
                showIcon = json.get("showIcon")?.asBoolean ?: true
            )
        }
        
        // ═══ Взаимодействие с миром ═══
        
        AbilityEffectRegistry.register(Identifier("boundbyfate-core", "set_blocks_on_fire")) { json ->
            SetBlocksOnFireEffect(
                radius = json.get("radius")?.asFloat ?: 5f,
                duration = json.get("duration")?.asInt ?: 100,
                onlyFlammable = json.get("onlyFlammable")?.asBoolean ?: true,
                spreadFire = json.get("spreadFire")?.asBoolean ?: false
            )
        }
        
        // ═══ Телепортация ═══
        
        AbilityEffectRegistry.register(Identifier("boundbyfate-core", "teleport")) { json ->
            TeleportEffect(
                mode = TeleportMode.valueOf(json.get("mode")?.asString ?: "TO_CURSOR"),
                maxDistance = json.get("maxDistance")?.asFloat ?: 30f,
                requiresSafeLanding = json.get("requiresSafeLanding")?.asBoolean ?: true
            )
        }
        
        logger.info("Registered ${AbilityEffectRegistry.getAll().size} ability effects")
    }
    
    /**
     * Парсит выражение для броска костей из JSON.
     */
    private fun parseDice(json: com.google.gson.JsonObject): DiceExpression {
        val diceObj = json.getAsJsonObject("dice")
        return DiceExpression(
            count = diceObj.get("count")?.asInt ?: 1,
            type = DiceType.valueOf(diceObj.get("type")?.asString ?: "D6")
        )
    }
}
