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
    val PLAYER_LEVEL: AttachmentType<PlayerLevelData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "player_level"),
        PLAYER_LEVEL_CODEC
    )
    
    /**
     * Entity stats data attachment.
     * Stores base stat values and modifiers for players and mobs.
     * Persists through death and world reload.
     */
    val ENTITY_STATS: AttachmentType<EntityStatData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "entity_stats"),
        EntityStatData.CODEC
    )

    /**
     * Entity skill proficiency data attachment.
     * Stores skill and saving throw proficiency levels (0, 1, 2).
     * Persists through death and world reload.
     */
    val ENTITY_SKILLS: AttachmentType<omc.boundbyfate.component.EntitySkillData> = AttachmentRegistry.createPersistent(
        Identifier("boundbyfate-core", "entity_skills"),
        omc.boundbyfate.component.EntitySkillData.CODEC
    )
    
    /**
     * Initialize all attachments. Called during mod initialization.
     */
    fun register() {
        // Attachments are registered on creation, but we call this
        // to ensure the object is initialized during mod startup
    }
}
