package omc.boundbyfate.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Identifier

/**
 * Stores proficiency IDs that an entity has.
 *
 * Only stores direct proficiency IDs - parent proficiencies automatically
 * grant children via [omc.boundbyfate.system.proficiency.ProficiencySystem].
 *
 * @property proficiencies Set of proficiency IDs the entity has
 */
data class EntityProficiencyData(
    val proficiencies: Set<Identifier> = emptySet()
) {
    fun has(id: Identifier): Boolean = proficiencies.contains(id)

    fun with(id: Identifier): EntityProficiencyData =
        copy(proficiencies = proficiencies + id)

    fun without(id: Identifier): EntityProficiencyData =
        copy(proficiencies = proficiencies - id)

    companion object {
        val CODEC: Codec<EntityProficiencyData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.listOf()
                    .optionalFieldOf("proficiencies", emptyList())
                    .forGetter { it.proficiencies.toList() }
            ).apply(instance) { list -> EntityProficiencyData(list.toSet()) }
        }
    }
}
