package omc.boundbyfate.client.state

import net.minecraft.util.Identifier

/** Lightweight class info for GM dropdowns */
data class GmClassInfo(val id: Identifier, val displayName: String, val subclasses: List<GmSubclassInfo>, val subclassLevel: Int = 3)
data class GmSubclassInfo(val id: Identifier, val displayName: String)
data class GmRaceInfo(val id: Identifier, val displayName: String)
data class GmSkillInfo(val id: Identifier, val displayName: String, val isSavingThrow: Boolean, val linkedStat: Identifier)
data class GmFeatureInfo(val id: Identifier, val displayName: String)

/**
 * Client-side registry of available classes, races, skills for GM dropdowns.
 * Populated from SYNC_GM_REGISTRY packet.
 */
object ClientGmRegistry {
    val classes: MutableList<GmClassInfo> = mutableListOf()
    val races: MutableList<GmRaceInfo> = mutableListOf()
    val skills: MutableList<GmSkillInfo> = mutableListOf()
    val features: MutableList<GmFeatureInfo> = mutableListOf()
    val availableSkins: MutableList<String> = mutableListOf()
    // Map of skinName → registered texture Identifier (for preview rendering)
    val skinTextures: MutableMap<String, Identifier> = mutableMapOf()

    fun update(
        classes: List<GmClassInfo>,
        races: List<GmRaceInfo>,
        skills: List<GmSkillInfo>,
        features: List<GmFeatureInfo>
    ) {
        this.classes.clear(); this.classes.addAll(classes)
        this.races.clear(); this.races.addAll(races)
        this.skills.clear(); this.skills.addAll(skills)
        this.features.clear(); this.features.addAll(features)
    }

    fun updateSkins(skins: List<String>) {
        this.availableSkins.clear()
        this.availableSkins.addAll(skins)
    }

    fun registerSkinTexture(skinName: String, base64: String) {
        try {
            val bytes = java.util.Base64.getDecoder().decode(base64)
            val image = net.minecraft.client.texture.NativeImage.read(java.io.ByteArrayInputStream(bytes))
            val texture = net.minecraft.client.texture.NativeImageBackedTexture(image)
            val id = net.minecraft.util.Identifier("boundbyfate-core", "gm_skin_preview/${skinName.lowercase()}")
            net.minecraft.client.MinecraftClient.getInstance().execute {
                val tm = net.minecraft.client.MinecraftClient.getInstance().textureManager
                skinTextures[skinName]?.let { tm.destroyTexture(it) }
                tm.registerTexture(id, texture)
                skinTextures[skinName] = id
            }
        } catch (e: Exception) {
            // silently skip broken skin
        }
    }
}
