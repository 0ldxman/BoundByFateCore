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
 * All player attachments use copyOnDeath() so data survives respawn.
 */
object BbfAttachments {

    private val PLAYER_LEVEL_CODEC: Codec<PlayerLevelData> = RecordCodecBuilder.create { instance ->
        instance.group(
            Codec.INT.fieldOf("level").forGetter { it.level },
            Codec.INT.fieldOf("experience").forGetter { it.experience }
        ).apply(instance, ::PlayerLevelData)
    }

    @JvmField
    val PLAYER_LEVEL: AttachmentType<PlayerLevelData> = AttachmentRegistry.builder<PlayerLevelData>()
        .persistent(PLAYER_LEVEL_CODEC)
        .copyOnDeath()
        .buildAndRegister(Identifier("boundbyfate-core", "player_level"))

    @JvmField
    val ENTITY_STATS: AttachmentType<EntityStatData> = AttachmentRegistry.builder<EntityStatData>()
        .persistent(EntityStatData.CODEC)
        .copyOnDeath()
        .buildAndRegister(Identifier("boundbyfate-core", "entity_stats"))

    @JvmField
    val ENTITY_SKILLS: AttachmentType<omc.boundbyfate.component.EntitySkillData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.EntitySkillData>()
            .persistent(omc.boundbyfate.component.EntitySkillData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "entity_skills"))

    @JvmField
    val ENTITY_RESOURCES: AttachmentType<omc.boundbyfate.component.EntityResourceData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.EntityResourceData>()
            .persistent(omc.boundbyfate.component.EntityResourceData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "entity_resources"))

    @JvmField
    val ENTITY_DAMAGE: AttachmentType<omc.boundbyfate.component.EntityDamageData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.EntityDamageData>()
            .persistent(omc.boundbyfate.component.EntityDamageData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "entity_damage"))

    @JvmField
    val PLAYER_CLASS: AttachmentType<omc.boundbyfate.component.PlayerClassData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.PlayerClassData>()
            .persistent(omc.boundbyfate.component.PlayerClassData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "player_class"))

    @JvmField
    val ENTITY_PROFICIENCIES: AttachmentType<omc.boundbyfate.component.EntityProficiencyData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.EntityProficiencyData>()
            .persistent(omc.boundbyfate.component.EntityProficiencyData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "entity_proficiencies"))

    @JvmField
    val PLAYER_FEATS: AttachmentType<omc.boundbyfate.component.PlayerFeatData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.PlayerFeatData>()
            .persistent(omc.boundbyfate.component.PlayerFeatData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "player_feats"))

    @JvmField
    val PLAYER_RACE: AttachmentType<omc.boundbyfate.component.PlayerRaceData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.PlayerRaceData>()
            .persistent(omc.boundbyfate.component.PlayerRaceData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "player_race"))

    @JvmField
    val ENTITY_FEATURES: AttachmentType<omc.boundbyfate.component.EntityFeatureData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.EntityFeatureData>()
            .persistent(omc.boundbyfate.component.EntityFeatureData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "entity_features"))

    @JvmField
    val ENTITY_ARMOR_CLASS: AttachmentType<omc.boundbyfate.component.EntityArmorClassData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.EntityArmorClassData>()
            .persistent(omc.boundbyfate.component.EntityArmorClassData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "entity_armor_class"))

    @JvmField
    val PLAYER_SKIN: AttachmentType<omc.boundbyfate.component.PlayerSkinData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.PlayerSkinData>()
            .persistent(omc.boundbyfate.component.PlayerSkinData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "player_skin"))

    @JvmField
    val ABILITY_ACTIVATION: AttachmentType<omc.boundbyfate.component.AbilityActivationState> =
        AttachmentRegistry.builder<omc.boundbyfate.component.AbilityActivationState>()
            .persistent(omc.boundbyfate.component.AbilityActivationState.CODEC)
            .buildAndRegister(Identifier("boundbyfate-core", "ability_activation"))

    @JvmField
    val CONCENTRATION: AttachmentType<omc.boundbyfate.component.ConcentrationData> =
        AttachmentRegistry.builder<omc.boundbyfate.component.ConcentrationData>()
            .persistent(omc.boundbyfate.component.ConcentrationData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "concentration"))

    @JvmField
    val PLAYER_GENDER: AttachmentType<String> =
        AttachmentRegistry.builder<String>()
            .persistent(Codec.STRING)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "player_gender"))

    @JvmField
    val SCALE_APPLIED: AttachmentType<Boolean> =
        AttachmentRegistry.builder<Boolean>()
            .persistent(Codec.BOOL)
            .copyOnDeath()
            .buildAndRegister(Identifier("boundbyfate-core", "scale_applied"))

    fun register() {
        // Attachments are registered on creation via buildAndRegister
    }
}
