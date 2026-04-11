package omc.boundbyfate.registry

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.util.Identifier
import omc.boundbyfate.component.EntityStatData
import omc.boundbyfate.component.PlayerLevelData

/**
 * Registry for all BoundByFate data attachments.
 */
object BbfAttachments {
    /**
     * Codec for serializing PlayerLevelData.
     */
    private val PLAYER_LEVEL_CODEC: Codec<PlayerLevelData> = RecordCodecBuilder.create { instance ->
        instance.group(
            Codec.INT.fieldOf("level").forGetter { it.level },
            Codec.INT.fieldOf("experience").forGetter { it.experience }
        ).apply(instance, ::PlayerLevelData)
    }
    
    /**
     * Player level and experience data attachment.
     * Persists through death and world reload.
     */
    @JvmField
    val PLAYER_LEVEL: AttachmentType<PlayerLevelData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "player_level"),
        PLAYER_LEVEL_CODEC
    )
    
    /**
     * Entity stats data attachment.
     * Stores base stat values and modifiers for players and mobs.
     * Persists through death and world reload.
     */
    @JvmField
    val ENTITY_STATS: AttachmentType<EntityStatData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "entity_stats"),
        EntityStatData.CODEC
    )

    /**
     * Entity skill proficiency data attachment.
     * Stores skill and saving throw proficiency levels (0, 1, 2).
     * Persists through death and world reload.
     */
    @JvmField
    val ENTITY_SKILLS: AttachmentType<omc.boundbyfate.component.EntitySkillData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "entity_skills"),
        omc.boundbyfate.component.EntitySkillData.CODEC
    )

    /**
     * Entity resource pools attachment.
     * Stores all resource pools (spell slots, rage, ki points, etc.).
     * Persists through death and world reload.
     */
    @JvmField
    val ENTITY_RESOURCES: AttachmentType<omc.boundbyfate.component.EntityResourceData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "entity_resources"),
        omc.boundbyfate.component.EntityResourceData.CODEC
    )

    /**
     * Entity damage resistances attachment.
     * Stores damage type modifiers (immunity, resistance, vulnerability).
     * Persists through death and world reload.
     */
    @JvmField
    val ENTITY_DAMAGE: AttachmentType<omc.boundbyfate.component.EntityDamageData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "entity_damage"),
        omc.boundbyfate.component.EntityDamageData.CODEC
    )

    /**
     * Player class and subclass assignment.
     * Persists through death and world reload.
     */
    @JvmField
    val PLAYER_CLASS: AttachmentType<omc.boundbyfate.component.PlayerClassData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "player_class"),
        omc.boundbyfate.component.PlayerClassData.CODEC
    )
    
    /**
     * Initialize all attachments. Called during mod initialization.
     */
    fun register() {
        // Attachments are registered on creation, but we call this
        // to ensure the object is initialized during mod startup
    }
}
