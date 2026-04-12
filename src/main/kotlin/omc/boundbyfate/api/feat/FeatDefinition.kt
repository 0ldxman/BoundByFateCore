package omc.boundbyfate.api.feat

import net.minecraft.util.Identifier

/**
 * Immutable definition of a feat.
 *
 * Loaded from data/<namespace>/bbf_feat/<name>.json
 *
 * Feats are chosen at levels where the class grants an upgrade slot (upgrade: true).
 * The player chooses between an ASI (+2 to one stat or +1/+1 to two stats) or a feat.
 *
 * @property id Unique identifier
 * @property displayName Human-readable name
 * @property description What the feat does
 * @property prerequisites Conditions that must be met to take this feat
 * @property grants What the feat provides
 */
data class FeatDefinition(
    val id: Identifier,
    val displayName: String,
    val description: String = "",
    val prerequisites: FeatPrerequisites = FeatPrerequisites(),
    val grants: FeatGrant = FeatGrant()
) {
    init {
        require(displayName.isNotBlank()) { "FeatDefinition $id: displayName cannot be blank" }
    }
}
