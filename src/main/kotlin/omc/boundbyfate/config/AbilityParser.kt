package omc.boundbyfate.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.util.Identifier
import omc.boundbyfate.api.ability.AbilityDefinition
import omc.boundbyfate.api.ability.AbilityPhase
import omc.boundbyfate.api.ability.EffectEntry
import omc.boundbyfate.api.ability.component.*
import omc.boundbyfate.api.dice.DiceType
import omc.boundbyfate.registry.BbfEffectRegistry
import omc.boundbyfate.registry.AbilityRegistry
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Парсер способностей из JSON.
 */
object AbilityParser {
    private val logger = LoggerFactory.getLogger("boundbyfate-core")
    
    fun parse(id: Identifier, stream: InputStream): AbilityDefinition? {
        return try {
            val json = JsonParser.parseReader(stream.reader()) as? JsonObject ?: run {
                logger.error("Ability $id: root must be a JSON object")
                return null
            }
            
            // Поддержка оверрайдов
            val base = json.get("base")?.asString?.let { baseId ->
                AbilityRegistry.get(Identifier(baseId)) ?: run {
                    logger.error("Ability $id: base ability '$baseId' not found")
                    return null
                }
            }
            
            val overrides = json.getAsJsonObject("overrides") ?: json
            
            val displayName = overrides.get("displayName")?.asString 
                ?: base?.displayName 
                ?: run {
                    logger.error("Ability $id: missing 'displayName'")
                    return null
                }
            
            val description = overrides.get("description")?.asString 
                ?: base?.description 
                ?: ""
            
            val icon = overrides.get("icon")?.asString 
                ?: base?.icon 
                ?: "item:minecraft:nether_star"
            
            val activation = parseActivation(overrides.getAsJsonObject("activation"))
                ?: base?.activation
                ?: run {
                    logger.error("Ability $id: missing 'activation'")
                    return null
                }
            
            val targeting = parseTargeting(overrides.getAsJsonObject("targeting"))
                ?: base?.targeting
                ?: run {
                    logger.error("Ability $id: missing 'targeting'")
                    return null
                }
            
            val effects = parseEffects(overrides.getAsJsonArray("effects"))
                ?: base?.effects
                ?: run {
                    logger.error("Ability $id: missing 'effects'")
                    return null
                }
            
            val costs = parseCosts(overrides.getAsJsonArray("costs"))
                ?: base?.costs
                ?: emptyList()
            
            val conditions = parseConditions(overrides.getAsJsonArray("conditions"))
                ?: base?.conditions
                ?: emptyList()
            
            val visuals = parseVisuals(overrides.getAsJsonArray("visuals"))
                ?: base?.visuals
                ?: emptyList()
            
            val scaling = parseScaling(overrides.getAsJsonArray("scaling"))
                ?: base?.scaling
                ?: emptyList()
            
            val metadata = parseMetadata(overrides.getAsJsonArray("metadata"))
                ?: base?.metadata
                ?: emptyList()
            
            AbilityDefinition(
                id = id,
                displayName = displayName,
                description = description,
                icon = icon,
                activation = activation,
                targeting = targeting,
                effects = effects,
                costs = costs,
                conditions = conditions,
                visuals = visuals,
                scaling = scaling,
                metadata = metadata
            )
        } catch (e: Exception) {
            logger.error("Failed to parse ability $id", e)
            null
        }
    }
    
    private fun parseActivation(json: JsonObject?): ActivationComponent? {
        if (json == null) return null
        
        val type = json.get("type")?.asString ?: return null
        val preparationTime = json.get("preparationTime")?.asInt ?: 0
        val canBeInterrupted = json.get("canBeInterrupted")?.asBoolean ?: true
        
        return when (type) {
            "Instant" -> ActivationComponent.Instant(preparationTime, canBeInterrupted)
            "Channeled" -> {
                val maxChannelDuration = json.get("maxChannelDuration")?.asInt ?: 60
                val interruptOnMove = json.get("interruptOnMove")?.asBoolean ?: true
                val interruptOnDamage = json.get("interruptOnDamage")?.asBoolean ?: true
                ActivationComponent.Channeled(preparationTime, maxChannelDuration, canBeInterrupted, interruptOnMove, interruptOnDamage)
            }
            "Charged" -> {
                val minChargeTicks = json.get("minChargeTicks")?.asInt ?: 20
                val maxChargeTicks = json.get("maxChargeTicks")?.asInt ?: 60
                ActivationComponent.Charged(preparationTime, minChargeTicks, maxChargeTicks, canBeInterrupted)
            }
            "Ritual" -> {
                val requiresStanding = json.get("requiresStanding")?.asBoolean ?: true
                ActivationComponent.Ritual(preparationTime, canBeInterrupted, requiresStanding)
            }
            else -> {
                logger.warn("Unknown activation type: $type")
                null
            }
        }
    }
    
    private fun parseTargeting(json: JsonObject?): TargetingComponent? {
        if (json == null) return null
        
        val type = json.get("type")?.asString ?: return null
        val range = json.get("range")?.asFloat ?: 60f
        val requiresLineOfSight = json.get("requiresLineOfSight")?.asBoolean ?: true
        val targetFilter = json.get("targetFilter")?.asString?.let { 
            TargetFilter.valueOf(it) 
        } ?: TargetFilter.ENEMIES
        
        return when (type) {
            "Self" -> TargetingComponent.Self(range, requiresLineOfSight, targetFilter)
            "SingleTarget" -> TargetingComponent.SingleTarget(range, requiresLineOfSight, targetFilter)
            "Projectile" -> {
                val projectileEntity = json.get("projectileEntity")?.asString?.let { Identifier(it) }
                    ?: return null
                val speed = json.get("speed")?.asFloat ?: 1.5f
                val gravity = json.get("gravity")?.asBoolean ?: false
                val homing = json.get("homing")?.asBoolean ?: false
                TargetingComponent.Projectile(range, requiresLineOfSight, targetFilter, projectileEntity, speed, gravity, homing)
            }
            "Area" -> {
                val shape = json.get("shape")?.asString?.let { AreaShape.valueOf(it) } ?: AreaShape.SPHERE
                val radius = json.get("radius")?.asFloat ?: 20f
                val centerOnCaster = json.get("centerOnCaster")?.asBoolean ?: true
                TargetingComponent.Area(range, requiresLineOfSight, targetFilter, shape, radius, centerOnCaster)
            }
            "Zone" -> {
                val zoneEntity = json.get("zoneEntity")?.asString?.let { Identifier(it) }
                    ?: return null
                val radius = json.get("radius")?.asFloat ?: 10f
                val duration = json.get("duration")?.asInt ?: 600
                TargetingComponent.Zone(range, requiresLineOfSight, targetFilter, zoneEntity, radius, duration)
            }
            else -> {
                logger.warn("Unknown targeting type: $type")
                null
            }
        }
    }
    
    private fun parseEffects(json: com.google.gson.JsonArray?): List<EffectEntry>? {
        if (json == null) return null
        
        val effects = mutableListOf<EffectEntry>()
        json.forEach { el ->
            val obj = el.asJsonObject
            val effectType = obj.get("effectType")?.asString?.let { Identifier(it) } ?: return@forEach
            val phase = obj.get("phase")?.asString?.let { AbilityPhase.valueOf(it) } ?: AbilityPhase.APPLICATION
            
            val effect = BbfEffectRegistry.create(effectType, obj) ?: return@forEach
            
            val conditions = parseConditions(obj.getAsJsonArray("conditions")) ?: emptyList()
            val stopOnFailure = obj.get("stopOnFailure")?.asBoolean ?: false
            
            effects.add(EffectEntry(effect, phase, conditions, stopOnFailure))
        }
        
        return effects
    }
    
    private fun parseCosts(json: com.google.gson.JsonArray?): List<CostComponent>? {
        if (json == null) return null
        
        val costs = mutableListOf<CostComponent>()
        json.forEach { el ->
            val obj = el.asJsonObject
            val type = obj.get("type")?.asString ?: return@forEach
            
            val cost = when (type) {
                "Resource" -> {
                    val resourceId = obj.get("resourceId")?.asString?.let { Identifier(it) } ?: return@forEach
                    val amount = obj.get("amount")?.asInt ?: 1
                    CostComponent.Resource(resourceId, amount)
                }
                "SpellSlot" -> {
                    val level = obj.get("level")?.asInt ?: 1
                    val canUpcast = obj.get("canUpcast")?.asBoolean ?: true
                    CostComponent.SpellSlot(level, canUpcast)
                }
                "Health" -> {
                    val amount = obj.get("amount")?.asInt ?: 0
                    val percentage = obj.get("percentage")?.asFloat ?: 0f
                    val canKill = obj.get("canKill")?.asBoolean ?: false
                    CostComponent.Health(amount, percentage, canKill)
                }
                "Cooldown" -> {
                    val ticks = obj.get("ticks")?.asInt ?: 100
                    CostComponent.Cooldown(ticks)
                }
                "MaterialComponents" -> {
                    val description = obj.get("description")?.asString ?: ""
                    val consumed = obj.get("consumed")?.asBoolean ?: false
                    val items = mutableListOf<CostComponent.ItemRequirement>()
                    
                    obj.getAsJsonArray("items")?.forEach { itemEl ->
                        val itemObj = itemEl.asJsonObject
                        val itemId = itemObj.get("itemId")?.asString?.let { Identifier(it) }
                        val itemTag = itemObj.get("itemTag")?.asString?.let { Identifier(it) }
                        val count = itemObj.get("count")?.asInt ?: 1
                        
                        if (itemId != null || itemTag != null) {
                            items.add(CostComponent.ItemRequirement(itemId, itemTag, count))
                        }
                    }
                    
                    CostComponent.MaterialComponents(description, items, consumed)
                }
                else -> {
                    logger.warn("Unknown cost type: $type")
                    return@forEach
                }
            }
            
            costs.add(cost)
        }
        
        return costs
    }
    
    private fun parseConditions(json: com.google.gson.JsonArray?): List<ConditionComponent>? {
        if (json == null) return null
        
        val conditions = mutableListOf<ConditionComponent>()
        json.forEach { el ->
            val obj = el.asJsonObject
            val type = obj.get("type")?.asString ?: return@forEach
            val negate = obj.get("negate")?.asBoolean ?: false
            
            val condition = when (type) {
                "HasResource" -> {
                    val resourceId = obj.get("resourceId")?.asString?.let { Identifier(it) } ?: return@forEach
                    val amount = obj.get("amount")?.asInt ?: 1
                    ConditionComponent.HasResource(resourceId, amount, negate)
                }
                "HealthThreshold" -> {
                    val threshold = obj.get("threshold")?.asFloat ?: 0.5f
                    val comparison = obj.get("comparison")?.asString?.let { 
                        Comparison.valueOf(it) 
                    } ?: Comparison.LESS_THAN
                    ConditionComponent.HealthThreshold(threshold, comparison, negate)
                }
                "HasStatusEffect" -> {
                    val statusId = obj.get("statusId")?.asString?.let { Identifier(it) } ?: return@forEach
                    val checkTarget = obj.get("checkTarget")?.asBoolean ?: false
                    ConditionComponent.HasStatusEffect(statusId, checkTarget, negate)
                }
                else -> {
                    logger.warn("Unknown condition type: $type")
                    return@forEach
                }
            }
            
            conditions.add(condition)
        }
        
        return conditions
    }
    
    private fun parseVisuals(json: com.google.gson.JsonArray?): List<VisualComponent>? {
        if (json == null) return null
        // TODO: Implement visual parsing
        return emptyList()
    }
    
    private fun parseScaling(json: com.google.gson.JsonArray?): List<ScalingComponent>? {
        if (json == null) return null
        
        val scaling = mutableListOf<ScalingComponent>()
        json.forEach { el ->
            val obj = el.asJsonObject
            val type = obj.get("type")?.asString ?: return@forEach
            
            val component = when (type) {
                "Upcast" -> {
                    val dicePerLevel = obj.get("dicePerLevel")?.asInt ?: 0
                    val targetsPerLevel = obj.get("targetsPerLevel")?.asInt ?: 0
                    val radiusPerLevel = obj.get("radiusPerLevel")?.asFloat ?: 0f
                    ScalingComponent.Upcast(dicePerLevel, targetsPerLevel, radiusPerLevel)
                }
                "CharacterLevel" -> {
                    val scaleAt = obj.getAsJsonArray("scaleAt")?.map { it.asInt } ?: listOf(5, 11, 17)
                    val dicePerTier = obj.get("dicePerTier")?.asInt ?: 1
                    ScalingComponent.CharacterLevel(scaleAt, dicePerTier)
                }
                else -> {
                    logger.warn("Unknown scaling type: $type")
                    return@forEach
                }
            }
            
            scaling.add(component)
        }
        
        return scaling
    }
    
    private fun parseMetadata(json: com.google.gson.JsonArray?): List<MetadataComponent>? {
        if (json == null) return null
        
        val metadata = mutableListOf<MetadataComponent>()
        json.forEach { el ->
            val obj = el.asJsonObject
            val type = obj.get("type")?.asString ?: return@forEach
            
            val component = when (type) {
                "Spell" -> {
                    val level = obj.get("level")?.asInt ?: 1
                    val school = obj.get("school")?.asString?.let { SpellSchool.valueOf(it) } ?: SpellSchool.EVOCATION
                    val ritual = obj.get("ritual")?.asBoolean ?: false
                    val concentration = obj.get("concentration")?.asBoolean ?: false
                    MetadataComponent.Spell(level, school, ritual, concentration)
                }
                "Availability" -> {
                    val classes = mutableListOf<Identifier>()
                    obj.getAsJsonArray("classes")?.forEach { classEl ->
                        classes.add(Identifier(classEl.asString))
                    }
                    val subclasses = mutableListOf<Identifier>()
                    obj.getAsJsonArray("subclasses")?.forEach { subclassEl ->
                        subclasses.add(Identifier(subclassEl.asString))
                    }
                    val requiresLevel = obj.get("requiresLevel")?.asInt ?: 1
                    MetadataComponent.Availability(classes, subclasses, requiresLevel)
                }
                "SavingThrow" -> {
                    val ability = obj.get("ability")?.asString?.let { Identifier(it) } ?: Identifier("boundbyfate-core", "dexterity")
                    val onSuccess = obj.get("onSuccess")?.asString?.let { SavingThrowResult.valueOf(it) } ?: SavingThrowResult.HALF_DAMAGE
                    MetadataComponent.SavingThrow(ability, onSuccess)
                }
                else -> {
                    logger.warn("Unknown metadata type: $type")
                    return@forEach
                }
            }
            
            metadata.add(component)
        }
        
        return metadata
    }
}
