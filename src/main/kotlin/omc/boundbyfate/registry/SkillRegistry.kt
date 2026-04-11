package omc.boundbyfate.registry

import net.minecraft.util.Identifier
import omc.boundbyfate.api.skill.SkillDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all skill and saving throw definitions.
 *
 * Thread-safe singleton. Skills must be registered during mod initialization.
 *
 * Usage:
 * ```kotlin
 * val mySkill = SkillDefinition(
 *     id = Identifier("mymod", "cooking"),
 *     displayName = "Кулинария",
 *     linkedStat = Identifier("boundbyfate-core", "wisdom")
 * )
 * SkillRegistry.register(mySkill)
 * ```
 */
object SkillRegistry {
    private val skills = ConcurrentHashMap<Identifier, SkillDefinition>()

    fun register(definition: SkillDefinition): SkillDefinition {
        val existing = skills.putIfAbsent(definition.id, definition)
        require(existing == null) { "Skill with ID ${definition.id} is already registered" }
        return definition
    }

    fun get(id: Identifier): SkillDefinition? = skills[id]

    fun getOrThrow(id: Identifier): SkillDefinition =
        get(id) ?: throw IllegalArgumentException("Unknown skill ID: $id")

    fun contains(id: Identifier): Boolean = skills.containsKey(id)

    fun getAll(): Collection<SkillDefinition> = skills.values.toList()

    fun getAllSkills(): Collection<SkillDefinition> = skills.values.filter { !it.isSavingThrow }

    fun getAllSavingThrows(): Collection<SkillDefinition> = skills.values.filter { it.isSavingThrow }

    val size: Int get() = skills.size
}
